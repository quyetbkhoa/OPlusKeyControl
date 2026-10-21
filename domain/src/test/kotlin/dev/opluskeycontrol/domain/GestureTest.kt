package dev.opluskeycontrol.domain

import org.junit.Assert.*
import org.junit.Test

class GestureTest {
    private val single = Trigger(Button.PLUS, Gesture.SINGLE)
    private val double = Trigger(Button.PLUS, Gesture.DOUBLE)
    private val long = Trigger(Button.PLUS, Gesture.LONG)
    private val chord = Trigger(Button.PLUS, Gesture.CHORD, Button.VOLUME_UP)
    private fun e(edge: Edge, time: Long, button: Button = Button.PLUS, repeat: Int = 0, source: String = "test") = ButtonEvent(source, 1, button, edge, time, repeat)
    @Test fun singleHasNoDelayWithoutDouble() {
        val g = GestureEngine(setOf(single)); assertTrue(g.accept(e(Edge.DOWN, 0)).isEmpty()); assertEquals(listOf(single), g.accept(e(Edge.UP, 20)))
    }
    @Test fun singleWaitsForDoubleWindow() {
        val g = GestureEngine(setOf(single, double)); g.accept(e(Edge.DOWN, 0)); g.accept(e(Edge.UP, 20))
        assertEquals(320L, g.nextDeadline()); assertTrue(g.advance(319).isEmpty()); assertEquals(listOf(single), g.advance(320)); assertNull(g.nextDeadline())
    }
    @Test fun doubleFiresOnceAndCancelsSingle() {
        val g = GestureEngine(setOf(single, double)); g.accept(e(Edge.DOWN, 0)); g.accept(e(Edge.UP, 20)); g.accept(e(Edge.DOWN, 100))
        assertEquals(listOf(double), g.accept(e(Edge.UP, 120))); assertTrue(g.advance(800).isEmpty())
    }
    @Test fun secondDownInsideWindowMayReleaseAfterDeadline() {
        val g = GestureEngine(setOf(single, double)); g.accept(e(Edge.DOWN, 0)); g.accept(e(Edge.UP, 20)); g.accept(e(Edge.DOWN, 310))
        assertEquals(listOf(double), g.accept(e(Edge.UP, 400))); assertTrue(g.advance(900).isEmpty())
    }
    @Test fun longCancelsSingleAndDouble() {
        val g = GestureEngine(setOf(single, double, long)); g.accept(e(Edge.DOWN, 0))
        assertEquals(listOf(long), g.advance(600)); assertTrue(g.accept(e(Edge.UP, 900)).isEmpty()); assertTrue(g.advance(1300).isEmpty())
    }
    @Test fun repeatsAndDuplicateDownDoNotFire() {
        val g = GestureEngine(setOf(single)); g.accept(e(Edge.DOWN, 0)); assertTrue(g.accept(e(Edge.DOWN, 10)).isEmpty()); assertTrue(g.accept(e(Edge.DOWN, 20, repeat = 1)).isEmpty())
        assertEquals(listOf(single), g.accept(e(Edge.UP, 40))); assertTrue(g.accept(e(Edge.UP, 45)).isEmpty())
    }
    @Test fun chordSuppressesBothComponents() {
        val g = GestureEngine(setOf(single, double, long, chord, Trigger(Button.VOLUME_UP, Gesture.SINGLE)))
        g.accept(e(Edge.DOWN, 0)); assertEquals(listOf(chord), g.accept(e(Edge.DOWN, 50, Button.VOLUME_UP)))
        assertTrue(g.accept(e(Edge.UP, 100)).isEmpty()); assertTrue(g.accept(e(Edge.UP, 150, Button.VOLUME_UP)).isEmpty()); assertTrue(g.advance(1000).isEmpty())
    }
    @Test fun heldModifierChordIsSupported() {
        val g = GestureEngine(setOf(chord)); g.accept(e(Edge.DOWN, 0)); assertEquals(listOf(chord), g.accept(e(Edge.DOWN, 2000, Button.VOLUME_UP)))
    }
    @Test fun reverseSimultaneousChordUsesWindow() {
        val g = GestureEngine(setOf(chord)); g.accept(e(Edge.DOWN, 0, Button.VOLUME_UP))
        assertEquals(listOf(chord), g.accept(e(Edge.DOWN, 80)))
    }
    @Test fun reverseHeldPartnerOutsideWindowDoesNotChord() {
        val g = GestureEngine(setOf(chord)); g.accept(e(Edge.DOWN, 0, Button.VOLUME_UP))
        assertTrue(g.accept(e(Edge.DOWN, 150)).isEmpty())
    }
    @Test fun powerLongThenShortIsNotDouble() {
        val d = Trigger(Button.POWER, Gesture.DOUBLE)
        val g = GestureEngine(setOf(d)); g.accept(e(Edge.DOWN, 0, Button.POWER)); g.accept(e(Edge.UP, 1000, Button.POWER))
        g.accept(e(Edge.DOWN, 1100, Button.POWER)); assertTrue(g.accept(e(Edge.UP, 1150, Button.POWER)).isEmpty())
    }
    @Test fun committedLongCannotLaterFireChord() {
        val g = GestureEngine(setOf(long, chord)); g.accept(e(Edge.DOWN, 0)); assertEquals(listOf(long), g.advance(600)); assertTrue(g.accept(e(Edge.DOWN, 700, Button.VOLUME_UP)).isEmpty())
    }
    @Test fun cancellationResetsPendingAndHeld() {
        val g = GestureEngine(setOf(single, double, long)); g.accept(e(Edge.DOWN, 0)); g.accept(e(Edge.CANCEL, 10)); assertTrue(g.advance(1000).isEmpty()); assertNull(g.nextDeadline())
    }
    @Test fun disconnectResetsOwnership() {
        val g = GestureEngine(setOf(single)); g.accept(e(Edge.DOWN, 0)); g.reset(); g.accept(e(Edge.DOWN, 10, source = "new")); assertEquals(listOf(single), g.accept(e(Edge.UP, 20, source = "new")))
    }
    @Test fun foreignSourceCannotDuplicate() {
        val g = GestureEngine(setOf(single)); g.accept(e(Edge.DOWN, 0)); assertTrue(g.accept(e(Edge.DOWN, 1, source = "root")).isEmpty()); assertEquals(listOf(single), g.accept(e(Edge.UP, 20)))
    }
    @Test fun monotonicRegressionFailsOpen() {
        val g = GestureEngine(setOf(single)); g.accept(e(Edge.DOWN, 100)); assertTrue(g.accept(e(Edge.UP, 50)).isEmpty()); assertNull(g.nextDeadline())
    }
    @Test fun powerOnlyDoubleNeverEmitsSingleLong() {
        val powerDouble = Trigger(Button.POWER, Gesture.DOUBLE)
        val g = GestureEngine(setOf(powerDouble)); g.accept(e(Edge.DOWN, 0, Button.POWER)); assertTrue(g.advance(10000).isEmpty()); assertTrue(g.accept(e(Edge.UP, 10001, Button.POWER)).isEmpty()); assertTrue(g.advance(11000).isEmpty())
    }
    @Test fun idleHasNoDeadline() { assertNull(GestureEngine(setOf(single, double, long, chord)).nextDeadline()) }
}
