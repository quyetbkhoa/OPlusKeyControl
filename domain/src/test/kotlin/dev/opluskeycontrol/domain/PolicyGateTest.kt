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
    @Test fun oneUnverifiedActivityDoesNotBlockAnotherGesture() {
        val double = rule.copy(id = "verified-double", action = Action(ActionKind.ACTIVITY, "test.app", "test.app.Double"))
        val single = double.copy(id = "unverified-single", trigger = Trigger(Button.PLUS, Gesture.SINGLE))
        val g = ready()
        g.configure(PolicyConfiguration(listOf(double, single), true, Tier.ROOT))
        g.verify(PolicyProof("tested-firmware", setOf(double.trigger, single.trigger), true))
        g.attach(BackendLease(1, 2000, true, setOf(ActionKind.ACTIVITY), setOf(double.id)))
        assertEquals(PolicyDecision.Replace(1, double), g.decision(t = double.trigger))
        assertEquals(PolicyDecision.PassThrough, g.decision(t = single.trigger))
        g.attach(BackendLease(2, 2000, true, setOf(ActionKind.ACTIVITY), setOf(double.id, single.id)))
        assertEquals(PolicyDecision.Replace(2, single), g.decision(t = single.trigger))
    }
    @Test fun unreadyWinnerDoesNotFallBackToAnotherAction() {
        val winner = rule.copy(id = "winner", priority = 10)
        val fallback = rule.copy(id = "fallback", priority = 0)
        val g = ready()
        g.configure(PolicyConfiguration(listOf(winner, fallback), true, Tier.ROOT))
        g.attach(BackendLease(1, 2000, true, setOf(ActionKind.TORCH_TOGGLE), setOf(fallback.id)))
        assertEquals(PolicyDecision.PassThrough, g.decision())
    }
    @Test fun emptyReadinessAndRevocationBlockPreviouslyReadyRule() {
        val g = ready()
        g.attach(BackendLease(1, 2000, true, setOf(ActionKind.TORCH_TOGGLE), emptySet()))
        assertEquals(PolicyDecision.PassThrough, g.decision())
        g.attach(BackendLease(2, 2000, true, emptySet(), setOf(rule.id)))
        assertEquals(PolicyDecision.PassThrough, g.decision())
    }
}
