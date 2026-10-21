package dev.opluskeycontrol.domain

import java.time.ZonedDateTime

data class PolicyEvent(val deviceId: Int, val downTime: Long, val time: Long, val edge: Edge, val repeat: Int = 0)
data class PolicyOutcome(val consume: Boolean, val actions: List<PolicyDecision.Replace> = emptyList())

/** Raw Plus interception is safe to start only with a complete SINGLE/DOUBLE/LONG
 * mapping for the current time. Partial mappings need a verified semantic OEM adapter;
 * replaying raw events or guessing which gesture the user will finish is forbidden.
 * Each caller serializes events/deadlines/configuration. Power/volume never enter here.
 */
class PlusPolicySession(private val button: Button = Button.PLUS, tier: Tier = Tier.ROOT) {
    init { require(button != Button.POWER) }
    private val triggers = setOf(Gesture.SINGLE, Gesture.DOUBLE, Gesture.LONG).map { Trigger(button, it) }.toSet()
    private val gate = PolicyGate(setOf(button), tier)
    private var firmware = ""
    private var engine = GestureEngine(triggers)
    private var device: Int? = null
    private var ownedDown = false
    private var draining = false
    private var connected = false
    private val decisions = LinkedHashMap<PolicyEvent, Boolean>()

    fun configure(configuration: PolicyConfiguration, timing: Timing, proof: PolicyProof, lease: BackendLease) {
        engine = GestureEngine(triggers, timing)
        draining = ownedDown
        if (!ownedDown) device = null
        firmware = proof.firmware
        // Chords cannot be partially owned at the raw Plus boundary.
        gate.configure(if (configuration.rules.any { it.enabled && it.trigger.gesture == Gesture.CHORD &&
            button in setOf(it.trigger.button, it.trigger.partner) }) configuration.copy(enabled = false) else configuration)
        gate.verify(proof); gate.attach(lease); connected = true
    }

    fun disconnect() { connected = false; gate.disconnect(); engine.reset(); draining = ownedDown }
    fun cancelGesture() { engine.reset(); draining = ownedDown } // paired UP closes an owned DOWN
    fun nextDeadline(): Long? = if (connected) engine.nextDeadline() else null

    private fun resolve(candidates: List<Trigger>, now: Long, local: ZonedDateTime) = candidates.mapNotNull {
        gate.decide(it, firmware, now, local) as? PolicyDecision.Replace
    }

    fun accept(event: PolicyEvent, now: Long, local: ZonedDateTime): PolicyOutcome {
        decisions[event]?.let { return PolicyOutcome(it) } // duplicate callback: no second action
        if (event.edge == Edge.CANCEL) { cancelGesture(); return PolicyOutcome(false) }
        if (device != null && device != event.deviceId) return PolicyOutcome(false)
        if (event.edge == Edge.DOWN && !ownedDown) {
            val complete = connected && triggers.all { gate.decide(it, firmware, now, local) is PolicyDecision.Replace }
            if (!complete || event.repeat != 0) { engine.reset(); return PolicyOutcome(false) }
            ownedDown = true; device = event.deviceId
        }
        if (!ownedDown) return PolicyOutcome(false)
        val actions = if (connected && !draining) resolve(engine.accept(ButtonEvent("owned-input", event.deviceId, button,
            event.edge, event.time, event.repeat)), now, local) else emptyList()
        if (event.edge == Edge.UP) { ownedDown = false; draining = false; if (engine.nextDeadline() == null) device = null }
        decisions[event] = true
        while (decisions.size > 32) decisions.remove(decisions.keys.first())
        return PolicyOutcome(true, actions)
    }

    fun advance(now: Long, local: ZonedDateTime): List<PolicyDecision.Replace> {
        if (!connected) return emptyList()
        val result = resolve(engine.advance(now), now, local)
        if (!ownedDown && engine.nextDeadline() == null) device = null
        return result
    }
}
