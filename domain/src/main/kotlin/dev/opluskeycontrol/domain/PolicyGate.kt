package dev.opluskeycontrol.domain

import java.time.ZonedDateTime

/** Separates a recognized OEM gesture from authority to replace its action.
 * A raw listener, profile guess or compile-time Xposed constant is not proof.
 */
data class PolicyProof(val firmware: String, val allowed: Set<Trigger>, val verified: Boolean)
data class BackendLease(val session: Long, val expiresAtMs: Long, val connected: Boolean,
    val readyActions: Set<ActionKind> = emptySet())
data class PolicyConfiguration(val rules: List<Rule>, val enabled: Boolean, val tier: Tier)

sealed interface PolicyDecision {
    data object PassThrough : PolicyDecision
    data class Replace(val session: Long, val rule: Rule) : PolicyDecision
}

class PolicyGate {
    @Volatile private var proof: PolicyProof? = null
    @Volatile private var lease: BackendLease? = null
    @Volatile private var configuration = PolicyConfiguration(emptyList(), false, Tier.BASIC)
    fun configure(value: PolicyConfiguration) { configuration = value.copy(rules = value.rules.toList()) }
    fun verify(value: PolicyProof) { proof = value.copy(allowed = value.allowed.toSet()) }
    fun attach(value: BackendLease) { require(value.session > 0); lease = value.copy(readyActions = value.readyActions.toSet()) }
    fun disconnect() { lease = null }
    fun emergencyDisable() { lease = null; configuration = configuration.copy(enabled = false) }

    fun decide(trigger: Trigger, firmware: String, uptimeMs: Long, localTime: ZonedDateTime): PolicyDecision {
        val currentProof = proof ?: return PolicyDecision.PassThrough
        val currentLease = lease ?: return PolicyDecision.PassThrough
        val config = configuration
        if (!config.enabled || config.tier != Tier.ROOT || !currentLease.connected || uptimeMs >= currentLease.expiresAtMs ||
            !currentProof.verified || currentProof.firmware != firmware ||
            currentProof.allowed.none { it.sameAs(trigger) }) return PolicyDecision.PassThrough
        // The first adapter is deliberately Plus-only. No Power path can be opted in.
        if (trigger.button != Button.PLUS || trigger.gesture == Gesture.CHORD) return PolicyDecision.PassThrough
        return when (val resolution = RuleResolver.resolve(config.rules, trigger, localTime)) {
            is Resolution.Match -> if (resolution.rule.action.kind in currentLease.readyActions)
                PolicyDecision.Replace(currentLease.session, resolution.rule) else PolicyDecision.PassThrough
            else -> PolicyDecision.PassThrough
        }
    }
}
