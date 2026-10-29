package dev.opluskeycontrol.domain

import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class TriplePressTest {
    private val local = ZonedDateTime.parse("2026-10-25T14:00:00+07:00")
    private val gestures = setOf(Gesture.SINGLE, Gesture.DOUBLE, Gesture.TRIPLE, Gesture.LONG)
    private val triggers = gestures.map { Trigger(Button.PLUS, it) }.toSet()
    private fun plus(includeTriple: Boolean = true) = PlusPolicySession().apply {
        val rules = triggers.filter { includeTriple || it.gesture != Gesture.TRIPLE }.map { Rule(it.gesture.name,it,Action(ActionKind.TORCH_TOGGLE)) }
        configure(PolicyConfiguration(rules,true,Tier.ROOT),Timing(),PolicyProof("fw",triggers,true),
            BackendLease(1,10000,true,setOf(ActionKind.TORCH_TOGGLE)))
    }
    private fun PlusPolicySession.click(at: Long): List<PolicyDecision.Replace> {
        val down = accept(PolicyEvent(1,at,at,Edge.DOWN),at,local)
        val up = accept(PolicyEvent(1,at,at+40,Edge.UP),at+40,local)
        assertTrue(down.consume); assertTrue(up.consume)
        return down.actions + up.actions
    }
    @Test fun threeClicksExecuteOnlyTripleOnce() {
        val s = plus()
        assertTrue(s.click(100).isEmpty()); assertTrue(s.click(240).isEmpty())
        assertEquals(Gesture.TRIPLE,s.click(380).single().rule.trigger.gesture)
        assertTrue(s.advance(1000,local).isEmpty())
    }
    @Test fun twoClicksWaitForThirdThenExecuteDouble() {
        val s = plus(); s.click(100); assertTrue(s.click(240).isEmpty())
        assertTrue(s.advance(579,local).isEmpty())
        assertEquals(Gesture.DOUBLE,s.advance(580,local).single().rule.trigger.gesture)
    }
    @Test fun singleStillUsesOneInterval() {
        val s = plus(); s.click(100)
        assertEquals(Gesture.SINGLE,s.advance(440,local).single().rule.trigger.gesture)
    }
    @Test fun unmappedTripleDoesNotDelayExistingDouble() {
        val s = plus(false); s.click(100)
        assertEquals(Gesture.DOUBLE,s.click(240).single().rule.trigger.gesture)
    }
    @Test fun longThirdPressDoesNotAlsoCommitTriple() {
        val s = plus(); s.click(100); s.click(240)
        s.accept(PolicyEvent(1,380,380,Edge.DOWN),380,local)
        assertEquals(Gesture.LONG,s.advance(980,local).single().rule.trigger.gesture)
        assertTrue(s.accept(PolicyEvent(1,380,1000,Edge.UP),1000,local).actions.isEmpty())
        assertTrue(s.advance(1500,local).isEmpty())
    }
    @Test fun thirdClickAtExpiredWindowStartsSeparateSingle() {
        val s = plus(); s.click(100); s.click(240)
        assertEquals(Gesture.DOUBLE,s.click(600).single().rule.trigger.gesture)
        assertEquals(Gesture.SINGLE,s.advance(940,local).single().rule.trigger.gesture)
    }

    private val powerTriggers = setOf(Gesture.DOUBLE,Gesture.TRIPLE).map { Trigger(Button.POWER,it) }.toSet()
    private fun power(ready: Boolean = true) = PowerDoubleSession().apply {
        configure(PolicyConfiguration(powerTriggers.map {Rule(it.gesture.name,it,Action(ActionKind.TORCH_TOGGLE))},true,Tier.ROOT),
            PolicyProof("fw",powerTriggers,true), BackendLease(1,10000,true,if(ready) setOf(ActionKind.TORCH_TOGGLE) else emptySet()),300)
    }
    private fun PowerDoubleSession.click(at: Long) {
        observe(PolicyEvent(1,at,at,Edge.DOWN));observe(PolicyEvent(1,at,at+40,Edge.UP))
    }
    @Test fun powerTripleRequiresThreePhysicalPressesAndCompletedCallback() {
        val s=power();s.click(100);s.click(240);s.click(380)
        var actions=0
        assertFalse(s.acceptGesture(0,0,3,380,0,600,local) {fail();true})
        assertTrue(s.acceptGesture(0,1,3,380,0,700,local) {assertEquals(Gesture.TRIPLE,it.rule.trigger.gesture);actions++;true})
        assertTrue(s.acceptGesture(0,1,3,380,0,710,local) {actions++;true})
        assertEquals(1,actions)
    }
    @Test fun powerEmergencyHigherCountsAndUnreadyTriplePassThrough() {
        for(count in 4..6) {
            val s=power();repeat(count) {s.click(100+it*140L)}
            assertFalse(s.acceptGesture(0,1,count,100+(count-1)*140L,0,1000,local) {fail();true})
        }
        val s=power(false);s.click(100);s.click(240);s.click(380)
        assertFalse(s.acceptGesture(0,1,3,380,0,700,local) {fail();true})
    }
}
