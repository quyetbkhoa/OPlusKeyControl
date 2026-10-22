package dev.opluskeycontrol.domain

import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class PlusChordSessionTest {
    private val time = ZonedDateTime.parse("2026-10-22T12:00:00+07:00")
    private val chords = listOf(Button.VOLUME_UP,Button.VOLUME_DOWN).map { Trigger(Button.PLUS,Gesture.CHORD,it) }
    private val rules = chords.map { Rule(it.partner!!.name,it,Action(ActionKind.TORCH_TOGGLE)) }
    private fun session(r: List<Rule> = rules) = PlusPolicySession(plusVolumeChords=true).apply {
        configure(PolicyConfiguration(r,true,Tier.ROOT),Timing(),PolicyProof("fw",chords.toSet()+setOf(Gesture.SINGLE,Gesture.DOUBLE,Gesture.LONG).map { Trigger(Button.PLUS,it) },true),
            BackendLease(1,10000,true,setOf(ActionKind.TORCH_TOGGLE)))
    }
    private fun PlusPolicySession.plus(edge: Edge, at: Long) = accept(PolicyEvent(2,100,at,edge),at,time)
    private fun PlusPolicySession.volume(button: Button, edge: Edge, at: Long, down: Long=200, device: Int=4, repeat: Int=0) =
        acceptPartner(button,PolicyEvent(device,down,at,edge,repeat),at,time)
    @Test fun bothPartnersWorkAcrossPhysicalDevices() {
        for (partner in listOf(Button.VOLUME_UP,Button.VOLUME_DOWN)) {
            val s=session(); assertTrue(s.plus(Edge.DOWN,100).consume)
            val result=s.volume(partner,Edge.DOWN,200)
            assertTrue(result.consume); assertEquals(partner.name,result.actions.single().rule.id)
            assertTrue(s.volume(partner,Edge.UP,250).consume)
            assertTrue(s.plus(Edge.UP,300).consume); assertTrue(s.advance(1000,time).isEmpty())
        }
    }
    @Test fun standaloneVolumeIsNeverConsumed() {
        val s=session(); assertFalse(s.volume(Button.VOLUME_UP,Edge.DOWN,200).consume)
        assertFalse(s.volume(Button.VOLUME_UP,Edge.UP,250).consume)
    }
    @Test fun volumeFirstIsNotRetrospectivelyCaptured() {
        val s=session(); assertFalse(s.volume(Button.VOLUME_UP,Edge.DOWN,80,80).consume)
        s.plus(Edge.DOWN,100)
        assertFalse(s.volume(Button.VOLUME_UP,Edge.UP,250,80).consume)
        assertTrue(s.plus(Edge.UP,300).actions.isEmpty())
    }
    @Test fun duplicateAndRepeatVolumeDoesNotDoubleDispatch() {
        val s=session(); s.plus(Edge.DOWN,100); assertEquals(1,s.volume(Button.VOLUME_UP,Edge.DOWN,200).actions.size)
        assertTrue(s.volume(Button.VOLUME_UP,Edge.DOWN,200).actions.isEmpty())
        assertTrue(s.volume(Button.VOLUME_UP,Edge.DOWN,220,repeat=1).consume)
        assertTrue(s.volume(Button.VOLUME_UP,Edge.UP,250).actions.isEmpty())
    }
    @Test fun releasePlusFirstStillClosesTheOwnedVolumePair() {
        val s=session(); s.plus(Edge.DOWN,100); s.volume(Button.VOLUME_UP,Edge.DOWN,200); s.plus(Edge.UP,230)
        assertTrue(s.volume(Button.VOLUME_UP,Edge.UP,250).consume)
    }
    @Test fun disconnectStillClosesOwnedPairsWithoutAnyAction() {
        val s=session(); s.plus(Edge.DOWN,100); s.volume(Button.VOLUME_UP,Edge.DOWN,200); s.disconnect()
        assertTrue(s.volume(Button.VOLUME_UP,Edge.UP,250).consume)
        assertTrue(s.plus(Edge.UP,300).consume); assertTrue(s.advance(1000,time).isEmpty())
    }
    @Test fun unmappedVolumeCancelsPlusAndPassesThrough() {
        val single=Rule("single",Trigger(Button.PLUS,Gesture.SINGLE),Action(ActionKind.TORCH_TOGGLE))
        val s=session(listOf(single,rules[0])); s.plus(Edge.DOWN,100)
        assertFalse(s.volume(Button.VOLUME_DOWN,Edge.DOWN,200).consume)
        s.plus(Edge.UP,300); assertTrue(s.advance(1000,time).isEmpty())
    }
    @Test fun chordKeepsPriorityBeyondLongThreshold() {
        val long=Rule("long",Trigger(Button.PLUS,Gesture.LONG),Action(ActionKind.TORCH_TOGGLE))
        val s=session(rules+long); s.plus(Edge.DOWN,100); assertTrue(s.advance(700,time).isEmpty())
        assertEquals("VOLUME_UP",s.volume(Button.VOLUME_UP,Edge.DOWN,1800,1800).actions.single().rule.id)
        assertTrue(s.plus(Edge.UP,1900).actions.isEmpty())
    }
    @Test fun longWithoutPartnerFiresOnceOnRelease() {
        val long=Rule("long",Trigger(Button.PLUS,Gesture.LONG),Action(ActionKind.TORCH_TOGGLE))
        val s=session(rules+long); s.plus(Edge.DOWN,100); assertTrue(s.advance(700,time).isEmpty())
        assertEquals("long",s.plus(Edge.UP,900).actions.single().rule.id)
        assertTrue(s.plus(Edge.UP,900).actions.isEmpty())
    }
    @Test fun invalidPartnerCancelsDeferredLongToo() {
        val long=Rule("long",Trigger(Button.PLUS,Gesture.LONG),Action(ActionKind.TORCH_TOGGLE))
        val s=session(listOf(rules[0],long)); s.plus(Edge.DOWN,100)
        assertFalse(s.volume(Button.VOLUME_DOWN,Edge.DOWN,900,900).consume)
        assertTrue(s.plus(Edge.UP,1000).actions.isEmpty())
    }
    @Test fun plusPowerOwnsOnlyTheMappedPartnerPair() {
        val chord=Trigger(Button.PLUS,Gesture.CHORD,Button.POWER)
        val s=PlusPolicySession(plusVolumeChords=true).apply {
            configure(PolicyConfiguration(listOf(Rule("power",chord,Action(ActionKind.TORCH_TOGGLE))),true,Tier.ROOT), Timing(),
                PolicyProof("fw",setOf(chord),true),BackendLease(1,10000,true,setOf(ActionKind.TORCH_TOGGLE)))
        }
        assertFalse(s.acceptPartner(Button.POWER,PolicyEvent(4,50,50,Edge.DOWN),50,time).consume)
        s.plus(Edge.DOWN,100)
        assertEquals("power",s.acceptPartner(Button.POWER,PolicyEvent(4,200,200,Edge.DOWN),200,time).actions.single().rule.id)
        s.plus(Edge.UP,300)
        assertTrue(s.acceptPartner(Button.POWER,PolicyEvent(4,200,400,Edge.UP),400,time).consume)
    }
    @Test fun noChordRuleCannotOwnPlusOrVolume() {
        val s=session(rules.map { it.copy(enabled=false) }); assertFalse(s.plus(Edge.DOWN,100).consume)
        assertFalse(s.volume(Button.VOLUME_UP,Edge.DOWN,200).consume)
    }
    @Test fun reversedRuleUsesSameLogicalChord() {
        val s=session(listOf(rules[0].copy(trigger=Trigger(Button.VOLUME_UP,Gesture.CHORD,Button.PLUS))))
        s.plus(Edge.DOWN,100); assertEquals("VOLUME_UP",s.volume(Button.VOLUME_UP,Edge.DOWN,200).actions.single().rule.id)
    }
    @Test fun foreignDeviceCannotCloseOwnedVolume() {
        val s=session(); s.plus(Edge.DOWN,100); s.volume(Button.VOLUME_UP,Edge.DOWN,200)
        assertFalse(s.volume(Button.VOLUME_UP,Edge.UP,250,device=9).consume)
        assertTrue(s.volume(Button.VOLUME_UP,Edge.UP,260).consume)
    }
    @Test fun cancelledPhysicalUpClosesOwnedPair() {
        val s=session(); s.plus(Edge.DOWN,100); s.volume(Button.VOLUME_UP,Edge.DOWN,200)
        assertTrue(s.volume(Button.VOLUME_UP,Edge.CANCEL,250).consume)
        assertTrue(s.plus(Edge.CANCEL,300).consume)
    }
    @Test fun unmappedLongDoesNotExpireThePlusModifier() {
        val s=session(); s.plus(Edge.DOWN,100); assertTrue(s.advance(700,time).isEmpty())
        assertEquals("VOLUME_UP",s.volume(Button.VOLUME_UP,Edge.DOWN,1200,1200).actions.single().rule.id)
    }
    @Test fun releasingLongModifierDoesNotRunMappedSingle() {
        val single=Rule("single",Trigger(Button.PLUS,Gesture.SINGLE),Action(ActionKind.TORCH_TOGGLE))
        val s=session(rules+single); s.plus(Edge.DOWN,100); s.advance(700,time)
        assertTrue(s.plus(Edge.UP,1000).actions.isEmpty()); assertTrue(s.advance(1500,time).isEmpty())
    }
}
