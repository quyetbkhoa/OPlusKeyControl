package dev.opluskeycontrol.domain

import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class PowerDoubleSessionTest {
    private val time = ZonedDateTime.parse("2026-10-22T12:00:00+07:00")
    private val trigger = Trigger(Button.POWER, Gesture.DOUBLE)
    private val rule = Rule("power", trigger, Action(ActionKind.TORCH_TOGGLE))
    private fun session(rules: List<Rule> = listOf(rule), ready: Boolean = true, enabled: Boolean = true) = PowerDoubleSession().apply {
        configure(PolicyConfiguration(rules, enabled, Tier.ROOT), PolicyProof("fw", setOf(trigger), true),
            BackendLease(8, 10000, true, if(ready) setOf(ActionKind.TORCH_TOGGLE) else emptySet()), 300)
    }
    private fun PowerDoubleSession.click(down: Long, device: Int = 1) {
        observe(PolicyEvent(device, down, down, Edge.DOWN)); observe(PolicyEvent(device, down, down + 40, Edge.UP))
    }
    private fun PowerDoubleSession.double(): PowerDoubleSession = apply { click(100); click(250) }
    @Test fun extendsOnlyAnActiveReadyRule() {
        assertEquals(5, session().maxPressCount(1, 100, time))
        assertEquals(6, session().maxPressCount(6, 100, time))
        assertEquals(1, session(enabled = false).maxPressCount(1, 100, time))
        assertEquals(1, session(ready = false).maxPressCount(1, 100, time))
        assertEquals(1, session(listOf(rule.copy(enabled = false))).maxPressCount(1, 100, time))
    }
    @Test fun onlyACompletedPhysicalDoubleCanDispatch() {
        var count = 0
        val s = session().double()
        assertTrue(s.accept(2,250,0,600,time) { count++; assertEquals(rule,it.rule); true })
        assertEquals(1,count)
    }
    @Test fun newSemanticEntryWaitsForCompletedOrdinaryGesture() {
        var count = 0; val s = session().double()
        assertFalse(s.acceptGesture(0,0,2,250,0,300,time) { fail(); true })
        assertTrue(s.acceptGesture(0,1,2,250,0,600,time) { count++; true })
        assertTrue(s.acceptGesture(0,1,2,250,0,610,time) { count++; true })
        assertEquals(1,count)
    }
    @Test fun longVeryLongUnknownAndCancelledGesturesNeverDispatch() {
        for ((type, action) in listOf(1 to 0, 1 to 1, 2 to 1, 3 to 1, 0 to 2)) {
            val s = session().double()
            assertFalse(s.acceptGesture(type,action,2,250,0,600,time) { fail(); true })
            assertFalse(s.acceptGesture(0,1,2,250,0,610,time) { fail(); true })
        }
    }
    @Test fun nativeEmergencyAndHigherCountsStayUnconsumedAtNewEntry() {
        for(count in listOf(1,3,4,5,6)) assertFalse(session().double().acceptGesture(0,1,count,250,0,600,time) { fail(); true })
    }
    @Test fun semanticCallWithoutPhysicalEdgesPasses() {
        assertFalse(session().accept(2,250,0,600,time) { fail(); true })
    }
    @Test fun duplicateCallbackIsSuppressedWithoutSecondAction() {
        var count = 0; val s = session().double()
        assertTrue(s.accept(2,250,0,600,time) { count++; true })
        assertTrue(s.accept(2,250,0,610,time) { count++; true })
        assertEquals(1,count)
    }
    @Test fun singleTripleAndEmergencyCountsAlwaysPass() {
        for (count in listOf(1,3,4,5,6)) assertFalse(session().double().accept(count,250,0,600,time) { fail(); true })
    }
    @Test fun triplePhysicalSequenceCannotCommitStaleDouble() {
        val s = session().double(); s.click(400)
        assertFalse(s.accept(2,250,0,650,time) { fail(); true })
        assertFalse(s.accept(3,400,0,750,time) { fail(); true })
    }
    @Test fun disconnectedOrRevokedAuthorityPasses() {
        val s = session().double(); s.disconnect()
        assertEquals(1,s.maxPressCount(1,600,time))
        assertFalse(s.accept(2,250,0,600,time) { fail(); true })
    }
    @Test fun keyUpRequiredBeforeDispatch() {
        val s = session(); s.click(100); s.observe(PolicyEvent(1,250,250,Edge.DOWN))
        assertFalse(s.accept(2,250,0,550,time) { fail(); true })
    }
    @Test fun repeatsDoNotTurnSingleIntoDouble() {
        val s = session(); s.observe(PolicyEvent(1,100,100,Edge.DOWN)); s.observe(PolicyEvent(1,100,180,Edge.DOWN,1)); s.observe(PolicyEvent(1,100,220,Edge.UP))
        assertFalse(s.accept(2,100,0,550,time) { fail(); true })
    }
    @Test fun physicalDeviceSwitchAndLateSecondTapFailOpen() {
        val s = session(); s.click(100); s.click(250,2)
        assertFalse(s.accept(2,250,0,600,time) { fail(); true })
        val late = session(); late.click(100); late.click(400)
        assertFalse(late.accept(2,400,0,750,time) { fail(); true })
    }
    @Test fun fullDispatchQueueDoesNotConsumeAndRevokes() {
        val s = session().double()
        assertFalse(s.accept(2,250,0,600,time) { false })
        assertEquals(1,s.maxPressCount(1,700,time))
    }
    @Test fun configUpdateAndCancellationDiscardPendingSequence() {
        val s = session().double(); s.cancel()
        assertFalse(s.accept(2,250,0,600,time) { fail(); true })
        val changed = session().double()
        changed.configure(PolicyConfiguration(listOf(rule),true,Tier.ROOT),PolicyProof("fw",setOf(trigger),true),BackendLease(9,10000,true,setOf(ActionKind.TORCH_TOGGLE)),300)
        assertFalse(changed.accept(2,250,0,600,time) { fail(); true })
    }
    @Test fun conflictsAndSchedulesDoNotEnableRecognition() {
        assertEquals(1,session(listOf(rule,rule.copy(id="duplicate"))).maxPressCount(1,100,time))
        assertEquals(1,session(listOf(rule.copy(schedule=Schedule(1320,360)))).maxPressCount(1,100,time))
    }
    @Test fun staleCallbackAndBackwardsClockDoNotDispatch() {
        assertFalse(session().double().accept(2,250,0,10000,time) { fail(); true })
        val s = session().double(); s.observe(PolicyEvent(1,100,100,Edge.DOWN))
        assertFalse(s.accept(2,250,0,600,time) { fail(); true })
    }
}
