package dev.opluskeycontrol.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class PolicyGateTest {
    private val trigger = Trigger(Button.PLUS, Gesture.DOUBLE)
    private val now = ZonedDateTime.parse("2026-10-22T12:00:00+07:00")
    private val rule = Rule("torch", trigger, Action(ActionKind.TORCH_TOGGLE))
    private fun ready() = PolicyGate().apply {
        configure(PolicyConfiguration(listOf(rule), true, Tier.ROOT))
        verify(PolicyProof("tested-firmware", setOf(trigger), true))
        attach(BackendLease(1, 2000, true, setOf(ActionKind.TORCH_TOGGLE)))
    }
    private fun PolicyGate.decision(time: Long = 1000, firmware: String = "tested-firmware", t: Trigger = trigger) = decide(t, firmware, time, now)
    @Test fun nothingInstalledPassesThrough() { assertEquals(PolicyDecision.PassThrough, PolicyGate().decision()) }
    @Test fun readyGateUsesResolvedRuleAndSession() { assertEquals(PolicyDecision.Replace(1, rule), ready().decision()) }
    @Test fun expiredLeasePassesThroughAtBoundary() { assertEquals(PolicyDecision.PassThrough, ready().decision(2000)) }
    @Test fun unprovenFirmwarePassesThrough() { assertEquals(PolicyDecision.PassThrough, ready().decision(firmware = "another-build")) }
    @Test fun observationIsNotVerification() { val g = ready(); g.verify(PolicyProof("tested-firmware", setOf(trigger), false)); assertEquals(PolicyDecision.PassThrough, g.decision()) }
    @Test fun crashOrUninstallLivenessLossPassesThrough() { val g = ready(); g.disconnect(); assertEquals(PolicyDecision.PassThrough, g.decision()) }
    @Test fun emergencyDisableRevokesImmediately() { val g = ready(); g.emergencyDisable(); assertEquals(PolicyDecision.PassThrough, g.decision()) }
    @Test fun tierTwoNeverFallsBackToRoot() { val g = ready(); g.configure(PolicyConfiguration(listOf(rule), true, Tier.SHIZUKU)); assertEquals(PolicyDecision.PassThrough, g.decision()) }
    @Test fun disabledRuleDoesNotReplace() { val g = ready(); g.configure(PolicyConfiguration(listOf(rule.copy(enabled = false)), true, Tier.ROOT)); assertEquals(PolicyDecision.PassThrough, g.decision()) }
    @Test fun scheduleGapDoesNotReplace() { val g = ready(); g.configure(PolicyConfiguration(listOf(rule.copy(schedule = Schedule(1320, 360))), true, Tier.ROOT)); assertEquals(PolicyDecision.PassThrough, g.decision()) }
    @Test fun ambiguityPassesThrough() { val g = ready(); g.configure(PolicyConfiguration(listOf(rule, rule.copy(id = "other")), true, Tier.ROOT)); assertEquals(PolicyDecision.PassThrough, g.decision()) }
    @Test fun missingTriggerProofPassesThrough() { assertEquals(PolicyDecision.PassThrough, ready().decision(t = Trigger(Button.PLUS, Gesture.LONG))) }
    @Test fun unavailableExecutorDoesNotConsumeGesture() { val g = ready(); g.attach(BackendLease(1, 2000, true)); assertEquals(PolicyDecision.PassThrough, g.decision()) }
    @Test fun unverifiedCameraActionDoesNotConsumeGesture() {
        val g = ready(); g.configure(PolicyConfiguration(listOf(rule.copy(action = Action(ActionKind.STOCK_CAMERA_CAPTURE))), true, Tier.ROOT))
        assertEquals(PolicyDecision.PassThrough, g.decision())
    }
    @Test fun powerDoubleCannotBeOptedIntoPlusAdapter() {
        val power = Trigger(Button.POWER, Gesture.DOUBLE); val g = ready()
        g.verify(PolicyProof("tested-firmware", setOf(power), true)); g.configure(PolicyConfiguration(listOf(rule.copy(trigger = power)), true, Tier.ROOT))
        assertEquals(PolicyDecision.PassThrough, g.decision(t = power))
    }
}
