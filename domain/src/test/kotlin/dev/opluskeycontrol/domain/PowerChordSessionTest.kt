package dev.opluskeycontrol.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime
class PowerChordSessionTest {
    private val local = ZonedDateTime.parse("2026-10-22T12:00:00+07:00")
    private val triggers = listOf(Button.VOLUME_UP,Button.VOLUME_DOWN).map { Trigger(Button.POWER,Gesture.CHORD,it) }
    private fun session(enabled: Boolean = true, expires: Long = 10000) = PowerChordSession().apply {
        configure(PolicyConfiguration(triggers.map { Rule(it.partner!!.name,it,Action(ActionKind.TORCH_TOGGLE)) },enabled,Tier.ROOT),
            PolicyProof("fw",triggers.toSet(),true),BackendLease(1,expires,true,setOf(ActionKind.TORCH_TOGGLE)))
    }
    private fun PowerChordSession.down(button: Button, at: Long, repeat: Int = 0) = observe(button,PolicyEvent(if(button==Button.POWER) 1 else 2,at,at,Edge.DOWN,repeat))
    private fun PowerChordSession.commit(partner: Button, at: Long = 220): Boolean = accept(partner,at,local) { true }
    @Test fun nativeCallbackOnlyNeverFabricatesPhysicalPair() { assertFalse(session().commit(Button.VOLUME_UP)) }
    @Test fun bothPartnersAndBothOrders() {
        for(partner in listOf(Button.VOLUME_UP,Button.VOLUME_DOWN)) for(first in listOf(Button.POWER,partner)) {
            val s=session();s.down(first,100);s.down(if(first==Button.POWER) partner else Button.POWER,200)
            assertTrue(s.commit(partner)); assertFalse(s.commit(partner))
        }
    }
    @Test fun tooFarApartDoesNotReplaceNative() { val s=session();s.down(Button.POWER,100);s.down(Button.VOLUME_UP,251);assertFalse(s.commit(Button.VOLUME_UP,260)) }
    @Test fun nativePartnerMustMatch() { val s=session();s.down(Button.POWER,100);s.down(Button.VOLUME_UP,200);assertFalse(s.commit(Button.VOLUME_DOWN)) }
    @Test fun thirdButtonInvalidatesPair() { val s=session();s.down(Button.POWER,100);s.down(Button.VOLUME_UP,150);s.down(Button.VOLUME_DOWN,180);assertFalse(s.commit(Button.VOLUME_UP)) }
    @Test fun queueFailureFallsBackAndRevokes() { val s=session();s.down(Button.POWER,100);s.down(Button.VOLUME_UP,200);assertFalse(s.accept(Button.VOLUME_UP,220,local) { false });assertFalse(s.ready(Button.VOLUME_UP,220,local)) }
    @Test fun expiredOrDisabledNeverReplace() { for(s in listOf(session(false),session(expires=210))) { s.down(Button.POWER,100);s.down(Button.VOLUME_UP,200);assertFalse(s.commit(Button.VOLUME_UP)) } }
    @Test fun disconnectRevokesImmediately() { val s=session();s.down(Button.POWER,100);s.down(Button.VOLUME_UP,200);s.disconnect();assertFalse(s.commit(Button.VOLUME_UP)) }
    @Test fun cancelRevokesPendingPair() { val s=session();s.down(Button.POWER,100);s.down(Button.VOLUME_UP,200);s.observe(Button.POWER,PolicyEvent(1,100,210,Edge.CANCEL));assertFalse(s.commit(Button.VOLUME_UP)) }
    @Test fun repeatsCannotInventSecondPhysicalKey() { val s=session();s.down(Button.POWER,100);s.down(Button.VOLUME_UP,200,1);assertFalse(s.commit(Button.VOLUME_UP)) }
    @Test fun lateCallbackDoesNotReplace() { val s=session();s.down(Button.POWER,100);s.down(Button.VOLUME_UP,200);assertFalse(s.commit(Button.VOLUME_UP,1300)) }
}
