package dev.opluskeycontrol.domain

import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class DelayedPartnerTest {
    private val local = ZonedDateTime.parse("2026-10-25T16:00:00+07:00")
    private val chord = Trigger(Button.PLUS,Gesture.CHORD,Button.POWER)
    private val long = Trigger(Button.PLUS,Gesture.LONG)
    private val single = Trigger(Button.PLUS,Gesture.SINGLE)
    private fun session() = PlusPolicySession(plusVolumeChords=true).apply {
        val rules=listOf(chord,long,single).map {Rule(it.gesture.name,it,Action(ActionKind.TORCH_TOGGLE))}
        configure(PolicyConfiguration(rules,true,Tier.ROOT),Timing(),PolicyProof("fw",setOf(chord,long,single),true),
            BackendLease(1,10000,true,setOf(ActionKind.TORCH_TOGGLE)))
    }
    @Test fun delayedPowerDownAfterLongFeedbackDeadlineStillFormsChord() {
        val s=session()
        s.accept(PolicyEvent(1,100,100,Edge.DOWN),100,local)
        s.advance(705,local) // worker clock moves ahead of an input event in transit
        val result=s.acceptPartner(Button.POWER,PolicyEvent(2,700,700,Edge.DOWN),710,local)
        assertTrue(result.consume)
        assertEquals(chord,result.actions.single().rule.trigger)
        s.acceptPartner(Button.POWER,PolicyEvent(2,700,740,Edge.UP),745,local)
        assertTrue(s.accept(PolicyEvent(1,100,800,Edge.UP),805,local).actions.isEmpty())
        assertTrue(s.advance(1200,local).isEmpty())
    }
    @Test fun powerFirstKeepsItsOriginalPairButStillTriggersPlusChord() {
        for(plusReleasedFirst in listOf(true,false)) {
            val s=session()
            assertFalse(s.acceptPartner(Button.POWER,PolicyEvent(2,90,90,Edge.DOWN),90,local).consume)
            val result=s.accept(PolicyEvent(1,100,100,Edge.DOWN),100,local)
            assertEquals(chord,result.actions.single().rule.trigger)
            if(plusReleasedFirst) s.accept(PolicyEvent(1,100,150,Edge.UP),150,local)
            assertFalse(s.acceptPartner(Button.POWER,PolicyEvent(2,90,200,Edge.UP),200,local).consume)
            if(!plusReleasedFirst) s.accept(PolicyEvent(1,100,250,Edge.UP),250,local)
            assertTrue(s.advance(1000,local).isEmpty())
        }
    }
    @Test fun timerClockDoesNotEraseDelayedShortPressRelease() {
        val g=GestureEngine(setOf(single,Trigger(Button.PLUS,Gesture.DOUBLE)))
        g.accept(ButtonEvent("test",1,Button.PLUS,Edge.DOWN,100))
        g.advance(200)
        g.accept(ButtonEvent("test",1,Button.PLUS,Edge.UP,150))
        assertEquals(listOf(single),g.advance(450))
    }
    @Test fun actualHardwareTimestampRegressionStillCancels() {
        val g=GestureEngine(setOf(single))
        g.accept(ButtonEvent("test",1,Button.PLUS,Edge.DOWN,100))
        assertTrue(g.accept(ButtonEvent("test",1,Button.PLUS,Edge.UP,90)).isEmpty())
        assertNull(g.nextDeadline())
    }
}
