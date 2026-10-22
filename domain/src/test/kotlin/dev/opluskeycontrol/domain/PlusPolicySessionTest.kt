package dev.opluskeycontrol.domain

import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class PlusPolicySessionTest {
    private val local = ZonedDateTime.parse("2026-10-22T12:00:00+07:00")
    private val triggers = listOf(Gesture.SINGLE, Gesture.DOUBLE, Gesture.LONG).map { Trigger(Button.PLUS, it) }
    private val rules = triggers.map { Rule(it.gesture.name, it, Action(ActionKind.TORCH_TOGGLE)) }
    private fun session(r: List<Rule> = rules, expiry: Long = 10000) = PlusPolicySession().apply {
        configure(PolicyConfiguration(r, true, Tier.ROOT), Timing(), PolicyProof("firmware", triggers.toSet(), true),
            BackendLease(1, expiry, true, setOf(ActionKind.TORCH_TOGGLE)))
    }
    private fun PlusPolicySession.event(at: Long, edge: Edge, down: Long = 100, device: Int = 1, repeat: Int = 0) =
        accept(PolicyEvent(device, down, at, edge, repeat), at, local)
    @Test fun noSessionPasses() { assertFalse(PlusPolicySession().event(100, Edge.DOWN).consume) }
    @Test fun partialMappingOwnsDown() { assertTrue(session(rules.dropLast(1)).event(100, Edge.DOWN).consume) }
    @Test fun expiredSessionNeverOwnsDown() { assertFalse(session(expiry = 100).event(100, Edge.DOWN).consume) }
    @Test fun singleWaitsThenRunsOnce() {
        val s = session(); assertTrue(s.event(100, Edge.DOWN).consume); assertTrue(s.event(150, Edge.UP).consume)
        assertTrue(s.advance(449, local).isEmpty()); assertEquals("SINGLE", s.advance(450, local).single().rule.id)
        assertTrue(s.advance(451, local).isEmpty())
    }
    @Test fun doubleDoesNotAlsoSingle() {
        val s = session(); s.event(100, Edge.DOWN); s.event(150, Edge.UP); s.event(250, Edge.DOWN, 250)
        assertEquals("DOUBLE", s.event(300, Edge.UP, 250).actions.single().rule.id)
        assertTrue(s.advance(1000, local).isEmpty())
    }
    @Test fun longDoesNotAlsoClick() {
        val s = session(); s.event(100, Edge.DOWN); assertEquals("LONG", s.advance(700, local).single().rule.id)
        assertTrue(s.event(800, Edge.UP).actions.isEmpty()); assertTrue(s.advance(1500, local).isEmpty())
    }
    @Test fun duplicateCallbackDoesNotDispatchTwice() {
        val s = session(); s.event(100, Edge.DOWN); s.event(150, Edge.UP); s.event(250, Edge.DOWN, 250)
        assertEquals(1, s.event(300, Edge.UP, 250).actions.size)
        assertTrue(s.event(300, Edge.UP, 250).consume); assertTrue(s.event(300, Edge.UP, 250).actions.isEmpty())
    }
    @Test fun foreignDeviceCannotCloseOwnedPress() {
        val s = session(); s.event(100, Edge.DOWN); assertFalse(s.event(150, Edge.UP, device = 2).consume)
        assertTrue(s.event(160, Edge.UP).consume)
    }
    @Test fun disconnectCancelsActionButClosesPair() {
        val s = session(); s.event(100, Edge.DOWN); s.disconnect(); assertTrue(s.event(150, Edge.UP).consume)
        assertTrue(s.advance(1000, local).isEmpty()); assertFalse(s.event(1100, Edge.DOWN, 1100).consume)
    }
    @Test fun cancelForSystemChordPreventsLongAction() {
        val s = session(); s.event(100, Edge.DOWN); s.cancelGesture(); assertTrue(s.advance(700, local).isEmpty())
        assertTrue(s.event(800, Edge.UP).consume)
    }
    @Test fun activeChordDisablesRawOwnership() {
        val chord = Rule("chord", Trigger(Button.PLUS, Gesture.CHORD, Button.VOLUME_UP), Action(ActionKind.TORCH_TOGGLE))
        assertFalse(session(rules + chord).event(100, Edge.DOWN).consume)
    }
    @Test fun unmatchedUpIsNeverConsumed() { assertFalse(session().event(150, Edge.UP).consume) }
    @Test fun unavailableActionDoesNotBlockOtherGestures() {
        val unavailable = rules.map { if (it.trigger.gesture == Gesture.LONG) it.copy(action = Action(ActionKind.RECORD_TOGGLE)) else it }
        assertTrue(session(unavailable).event(100, Edge.DOWN).consume)
    }
    @Test fun disabledGestureDoesNotBlockOtherGestures() {
        assertTrue(session(rules.map { if (it.trigger.gesture == Gesture.LONG) it.copy(enabled = false) else it }).event(100, Edge.DOWN).consume)
    }
    @Test fun scheduleWithoutCurrentMatchPreventsOwnership() {
        val night = rules.map { it.copy(schedule = Schedule(22 * 60, 6 * 60)) }
        assertFalse(session(night).event(100, Edge.DOWN).consume)
    }
    @Test fun conflictingRulesFailOpenWithoutSelectingOne() {
        val s = session(listOf(rules[0], rules[0].copy(id = "duplicate-priority")))
        assertFalse(s.event(100, Edge.DOWN).consume)
    }
    @Test fun repeatDoesNotCreateDoubleOrExtraLong() {
        val s = session(); s.event(100, Edge.DOWN); assertTrue(s.event(200, Edge.DOWN, repeat = 1).actions.isEmpty())
        assertEquals("LONG", s.advance(700, local).single().rule.id)
        assertTrue(s.event(750, Edge.DOWN, repeat = 2).actions.isEmpty()); assertTrue(s.event(800, Edge.UP).actions.isEmpty())
    }
    @Test fun newPressAfterDisconnectGoesToOem() {
        val s = session(); s.event(100, Edge.DOWN); s.event(150, Edge.UP); s.disconnect()
        assertFalse(s.event(500, Edge.DOWN, 500).consume); assertTrue(s.advance(1000, local).isEmpty())
    }
    @Test(expected = IllegalArgumentException::class) fun rawPowerSessionCannotBeConstructed() {
        PlusPolicySession(Button.POWER)
    }
    @Test fun configurationUpdateCannotRearmAnOwnedHeldPress() {
        val s = session(); s.event(100, Edge.DOWN); assertEquals(1, s.advance(700, local).size)
        s.configure(PolicyConfiguration(rules, true, Tier.ROOT), Timing(), PolicyProof("firmware", triggers.toSet(), true),
            BackendLease(2, 10000, true, setOf(ActionKind.TORCH_TOGGLE)))
        assertTrue(s.event(100, Edge.DOWN).consume) // exact callback duplicate survives snapshot update
        assertTrue(s.event(800, Edge.DOWN).consume) // a repeated DOWN with changed eventTime cannot rearm
        assertTrue(s.advance(1400, local).isEmpty())
        assertTrue(s.event(1500, Edge.UP).consume)
        assertTrue(s.event(2000, Edge.DOWN, 2000).consume)
        s.event(2050, Edge.UP, 2000)
        assertEquals("SINGLE", s.advance(2350, local).single().rule.id)
    }

    @Test fun eachGestureWorksIndependently() {
        for (rule in rules) {
            val s = session(listOf(rule))
            assertTrue(s.event(100, Edge.DOWN).consume)
            val actions = when (rule.trigger.gesture) {
                Gesture.LONG -> s.advance(700, local).also { s.event(800, Edge.UP) }
                Gesture.DOUBLE -> { s.event(150, Edge.UP); s.event(250, Edge.DOWN, 250); s.event(300, Edge.UP, 250).actions }
                else -> { s.event(150, Edge.UP); s.advance(450, local) }
            }
            assertEquals(rule.id, actions.single().rule.id)
            assertTrue(s.advance(2000, local).isEmpty())
        }
    }
    @Test fun unmappedDoubleAndHoldNeverFallBackToSingle() {
        val s = session(listOf(rules[0]))
        s.event(100, Edge.DOWN); s.event(150, Edge.UP); s.event(250, Edge.DOWN, 250)
        assertTrue(s.event(300, Edge.UP, 250).actions.isEmpty())
        assertTrue(s.advance(1000, local).isEmpty())
        s.event(1500, Edge.DOWN, 1500)
        assertTrue(s.advance(2100, local).isEmpty())
        assertTrue(s.event(2200, Edge.UP, 1500).actions.isEmpty())
        assertTrue(s.advance(3000, local).isEmpty())
    }
    @Test fun noReadyOrEnabledRulesPassThrough() {
        assertFalse(session(emptyList()).event(100, Edge.DOWN).consume)
        assertFalse(session(rules.map { it.copy(enabled = false) }).event(100, Edge.DOWN).consume)
        assertFalse(session(rules.map { it.copy(action = Action(ActionKind.RECORD_TOGGLE)) }).event(100, Edge.DOWN).consume)
    }
}
