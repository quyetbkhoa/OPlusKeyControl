package dev.opluskeycontrol.domain

import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class PowerChordSuppressionTest {
    private val local=ZonedDateTime.parse("2026-10-25T16:00:00+07:00")
    private val chord=Trigger(Button.PLUS,Gesture.CHORD,Button.POWER)
    private fun session(enabled: Boolean = true)=PlusPolicySession(plusVolumeChords=true).apply {
        configure(PolicyConfiguration(listOf(Rule("chord",chord,Action(ActionKind.TORCH_TOGGLE))),enabled,Tier.ROOT),
            Timing(),PolicyProof("fw",setOf(chord),true),BackendLease(1,10000,true,setOf(ActionKind.TORCH_TOGGLE)))
    }
    @Test fun bothOrdersClaimExactlyThePhysicalPowerPress() {
        for (powerFirst in listOf(true,false)) {
            val s=session()
            if(powerFirst) s.acceptPartner(Button.POWER,PolicyEvent(2,90,90,Edge.DOWN),90,local)
            val plus=s.accept(PolicyEvent(1,100,100,Edge.DOWN),100,local)
            val actions=if(powerFirst) plus.actions else s.acceptPartner(Button.POWER,PolicyEvent(2,110,110,Edge.DOWN),110,local).actions
            assertEquals(chord,actions.single().rule.trigger)
            val claimed=s.takePowerChordPresses().single()
            assertEquals(if(powerFirst) 90L else 110L,claimed)
            val guard=PowerChordSuppression().apply {claim(claimed,120)}
            assertTrue(guard.suppresses(0,1,1,claimed,500))
            assertTrue(guard.suppresses(0,1,1,claimed,510))
            assertFalse(guard.suppresses(0,1,1,claimed+1,510))
            assertTrue(s.takePowerChordPresses().isEmpty())
        }
    }
    @Test fun doubleTripleLongCancelAndEmergencyAreNeverSuppressed() {
        val guard=PowerChordSuppression().apply {claim(100,120)}
        for(count in listOf(2,3,4,5,6)) assertFalse(guard.suppresses(0,1,count,100,500))
        for(type in listOf(1,2,3)) assertFalse(guard.suppresses(type,1,1,100,500))
        for(action in listOf(0,2)) assertFalse(guard.suppresses(0,action,1,100,500))
    }
    @Test fun disabledChordAndOrdinaryPowerDoNotCreateClaims() {
        val s=session(false)
        s.accept(PolicyEvent(1,100,100,Edge.DOWN),100,local)
        s.acceptPartner(Button.POWER,PolicyEvent(2,110,110,Edge.DOWN),110,local)
        assertTrue(s.takePowerChordPresses().isEmpty())
        assertFalse(PowerChordSuppression().suppresses(0,1,1,110,500))
    }
    @Test fun claimsExpireAndDisconnectClearsAuthority() {
        val guard=PowerChordSuppression().apply {claim(100,120)}
        assertFalse(guard.suppresses(0,1,1,100,5121))
        guard.claim(200,300);guard.clear()
        assertFalse(guard.suppresses(0,1,1,200,400))
    }
}
