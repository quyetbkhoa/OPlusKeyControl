package dev.opluskeycontrol.input

import dev.opluskeycontrol.domain.*
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class PolicyPublicationTest {
    private val value = PublishedPolicy("saved-config", 1, false, listOf("torch"))

    @Test fun unchangedRefreshDoesNotCancelPendingSinglePress() {
        val publications = PolicyPublication()
        val session = PlusPolicySession()
        val trigger = Trigger(Button.PLUS, Gesture.SINGLE)
        val rule = Rule("torch", trigger, Action(ActionKind.TORCH_TOGGLE))
        var revision = 0L
        fun publish(snapshot: PublishedPolicy) {
            if (!publications.changed(snapshot)) return
            session.configure(PolicyConfiguration(listOf(rule), true, Tier.ROOT), Timing(),
                PolicyProof("test", setOf(trigger), true),
                BackendLease(++revision, Long.MAX_VALUE, true, setOf(ActionKind.TORCH_TOGGLE)))
            publications.sent(snapshot)
        }
        val local = ZonedDateTime.parse("2026-10-25T14:00:00+07:00")
        publish(value)
        assertTrue(session.accept(PolicyEvent(1, 100, 100, Edge.DOWN), 100, local).consume)
        // Screen/activity refresh arrives while the key is held, then again
        // during the double-press waiting period. Neither changes the policy.
        publish(value.copy())
        session.accept(PolicyEvent(1, 100, 150, Edge.UP), 150, local)
        publish(value.copy())
        val actions = session.advance(450, local)
        assertEquals("torch", actions.single().rule.id)
        assertEquals(1L, actions.single().session)
    }

    @Test fun realReadinessAndConfigurationChangesAreNeverSuppressed() {
        val publications = PolicyPublication().apply { sent(value) }
        assertFalse(publications.changed(value.copy()))
        assertTrue(publications.changed(value.copy(text = "disabled")))
        assertTrue(publications.changed(value.copy(readyMask = 0)))
        assertTrue(publications.changed(value.copy(recording = true)))
        assertTrue(publications.changed(value.copy(readyRuleIds = emptyList())))
    }

    @Test fun reconnectAlwaysPublishesEvenWhenSettingsAreIdentical() {
        val publications = PolicyPublication().apply { sent(value); reset() }
        assertTrue(publications.changed(value))
    }
}
