package dev.opluskeycontrol.domain

import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class EarlyRecognitionFeedbackTest {
    private val local = ZonedDateTime.parse("2026-10-24T12:00:00+07:00")
    private val hold = Rule("hold", Trigger(Button.PLUS, Gesture.LONG), Action(ActionKind.TORCH_TOGGLE))
    private val chord = Rule("chord", Trigger(Button.PLUS, Gesture.CHORD, Button.VOLUME_UP), Action(ActionKind.TORCH_TOGGLE))
    private fun session(ready: Set<String> = setOf("hold", "chord")) = PlusPolicySession(plusVolumeChords = true).apply {
        configure(PolicyConfiguration(listOf(hold,chord),true,Tier.ROOT), Timing(),
            PolicyProof("fw",setOf(hold.trigger,chord.trigger),true),
            BackendLease(1,10000,true,setOf(ActionKind.TORCH_TOGGLE),ready))
    }
    private fun PlusPolicySession.edge(at: Long, edge: Edge) = accept(PolicyEvent(1,100,at,edge),at,local)
    @Test fun holdFeedbackArrivesAtThresholdWhileActionStillWaitsForRelease() {
        val s=session();s.edge(100,Edge.DOWN)
        assertEquals(700L,s.nextDeadline())
        assertTrue(s.advance(699,local).isEmpty());assertTrue(s.takeRecognitions().isEmpty())
        assertTrue(s.advance(700,local).isEmpty());assertEquals(hold,s.takeRecognitions().single().rule)
        assertNull(s.nextDeadline());assertTrue(s.advance(900,local).isEmpty());assertTrue(s.takeRecognitions().isEmpty())
        assertEquals(hold,s.edge(1000,Edge.UP).actions.single().rule);assertTrue(s.takeRecognitions().isEmpty())
    }
    @Test fun lateChordStillWinsWithoutSecondFeedbackOrLongAction() {
        val s=session();s.edge(100,Edge.DOWN);s.advance(700,local);assertEquals(1,s.takeRecognitions().size)
        assertEquals(chord,s.acceptPartner(Button.VOLUME_UP,PolicyEvent(2,900,900,Edge.DOWN),900,local).actions.single().rule)
        assertTrue(s.takeRecognitions().isEmpty());assertTrue(s.edge(1000,Edge.UP).actions.isEmpty())
    }
    @Test fun cancelledDisconnectedOrUnreadyHoldNeverGivesEarlyFeedback() {
        for(mode in 0..2) {
            val s=if(mode==2) session(setOf("chord")) else session()
            s.edge(100,Edge.DOWN)
            if(mode==0)s.cancelGesture() else if(mode==1)s.disconnect()
            s.advance(700,local);assertTrue(s.takeRecognitions().isEmpty())
        }
    }
}
