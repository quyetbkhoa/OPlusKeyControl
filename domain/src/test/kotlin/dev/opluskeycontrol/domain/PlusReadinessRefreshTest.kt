package dev.opluskeycontrol.domain

import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class PlusReadinessRefreshTest {
    private val local = ZonedDateTime.parse("2026-10-25T14:00:00+07:00")
    private val trigger = Trigger(Button.PLUS, Gesture.SINGLE)
    private val rule = Rule("torch", trigger, Action(ActionKind.TORCH_TOGGLE))
    private val proof = PolicyProof("test", setOf(trigger), true)
    private val config = PolicyConfiguration(listOf(rule), true, Tier.ROOT)
    private fun PlusPolicySession.publish(version: Long, ready: Boolean = true, enabled: Boolean = true) =
        configure(config.copy(enabled = enabled), Timing(), proof,
            BackendLease(version, Long.MAX_VALUE, true,
                if (ready) setOf(ActionKind.TORCH_TOGGLE) else emptySet()))
    private fun PlusPolicySession.press(edge: Edge, at: Long) = accept(PolicyEvent(1, 100, at, edge), at, local)

    @Test fun leaseRefreshDuringHeldKeyDoesNotDiscardFirstClick() {
        val s = PlusPolicySession().apply { publish(1) }
        s.press(Edge.DOWN, 100)
        s.publish(2)
        s.press(Edge.UP, 150)
        assertEquals(450L, s.nextDeadline())
        assertEquals(2L, s.advance(450, local).single().session)
    }

    @Test fun readinessReturnsBeforeDeadlineWithoutNeedingAnotherPress() {
        val s = PlusPolicySession().apply { publish(1) }
        s.press(Edge.DOWN, 100); s.press(Edge.UP, 150)
        s.publish(2, ready = false)
        s.publish(3)
        assertEquals(450L, s.nextDeadline())
        assertEquals(3L, s.advance(450, local).single().session)
        assertTrue(s.advance(500, local).isEmpty())
    }

    @Test fun revocationStillPreventsPendingAction() {
        val s = PlusPolicySession().apply { publish(1) }
        s.press(Edge.DOWN, 100); s.press(Edge.UP, 150)
        s.publish(2, ready = false)
        assertTrue(s.advance(450, local).isEmpty())
    }

    @Test fun disablingConfigurationStillCancelsPendingGesture() {
        val s = PlusPolicySession().apply { publish(1) }
        s.press(Edge.DOWN, 100); s.press(Edge.UP, 150)
        s.publish(2, enabled = false)
        assertNull(s.nextDeadline())
        assertTrue(s.advance(450, local).isEmpty())
    }
}
