package dev.opluskeycontrol.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class RuleTest {
    private val trigger = Trigger(Button.PLUS, Gesture.DOUBLE)
    private val now = ZonedDateTime.parse("2026-10-22T12:00:00+07:00")
    private fun rule(id: String, schedule: Schedule? = null, priority: Int = 0) = Rule(id, trigger, Action(ActionKind.TORCH_TOGGLE), schedule, priority)
    @Test fun noRulesPassThrough() { assertEquals(Resolution.PassThrough, RuleResolver.resolve(emptyList(), trigger, now)) }
    @Test fun scheduledWinsOverHigherPriorityDefault() {
        val scheduled = rule("specific", Schedule(360, 1080))
        assertEquals(Resolution.Match(scheduled), RuleResolver.resolve(listOf(rule("default", priority = 100), scheduled), trigger, now))
    }
    @Test fun defaultOutsideWindow() {
        val default = rule("default")
        assertEquals(Resolution.Match(default), RuleResolver.resolve(listOf(default, rule("night", Schedule(1320, 360))), trigger, now))
    }
    @Test fun priorityResolvesOverlap() {
        val high = rule("high", Schedule(0, 1439), 10)
        assertEquals(Resolution.Match(high), RuleResolver.resolve(listOf(high, rule("low", Schedule(360, 1080))), trigger, now))
    }
    @Test fun ambiguousDataFailsOpenAsConflict() {
        val a = rule("a", Schedule(360, 1080)); val b = rule("b", Schedule(600, 1200))
        assertTrue(RuleResolver.conflicts(a, listOf(b)))
        assertTrue(RuleResolver.resolve(listOf(a, b), trigger, now) is Resolution.Conflict)
    }
    @Test fun specificAndDefaultAreNotConflicts() { assertFalse(RuleResolver.conflicts(rule("a"), listOf(rule("b", Schedule(360, 1080))))) }
    @Test fun disabledRulePassesThrough() { assertEquals(Resolution.PassThrough, RuleResolver.resolve(listOf(rule("a").copy(enabled = false)), trigger, now)) }
    @Test fun reverseChordIsSameTrigger() {
        assertTrue(Trigger(Button.PLUS, Gesture.CHORD, Button.VOLUME_UP).sameAs(Trigger(Button.VOLUME_UP, Gesture.CHORD, Button.PLUS)))
    }
    @Test(expected = IllegalArgumentException::class) fun noPowerSingle() { Trigger(Button.POWER, Gesture.SINGLE) }
    @Test(expected = IllegalArgumentException::class) fun noPowerLong() { Trigger(Button.POWER, Gesture.LONG) }
    @Test fun powerChordsCanBeConfigured() {
        assertTrue(Trigger(Button.PLUS, Gesture.CHORD, Button.POWER).isPlusChord())
        assertTrue(Trigger(Button.POWER, Gesture.CHORD, Button.VOLUME_UP).isPowerVolumeChord())
    }
    @Test(expected = IllegalArgumentException::class) fun reserveBothVolumeChord() { Trigger(Button.VOLUME_UP, Gesture.CHORD, Button.VOLUME_DOWN) }
    @Test(expected = IllegalArgumentException::class) fun noSelfChord() { Trigger(Button.PLUS, Gesture.CHORD, Button.PLUS) }
    @Test(expected = IllegalArgumentException::class) fun noShellComponents() { Action(ActionKind.APP, "pkg; id") }
    @Test(expected = IllegalArgumentException::class) fun noShellActivity() { Action(ActionKind.ACTIVITY, "dev.test", "Activity;id") }
    @Test fun sourceOwnershipNeverSilentlyFallsBack() {
        val owner = SourceOwnership()
        assertTrue(owner.acquire("basic")); assertFalse(owner.acquire("root")); assertFalse(owner.accepts("root"))
        owner.release("root"); assertTrue(owner.accepts("basic"))
        owner.release("basic"); assertTrue(owner.acquire("root"))
    }
}
