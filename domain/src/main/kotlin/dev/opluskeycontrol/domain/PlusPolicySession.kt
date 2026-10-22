package dev.opluskeycontrol.domain

import java.time.ZonedDateTime

data class PolicyEvent(val deviceId: Int, val downTime: Long, val time: Long, val edge: Edge, val repeat: Int = 0)
data class PolicyOutcome(val consume: Boolean, val actions: List<PolicyDecision.Replace> = emptyList())

/** Owns a Plus sequence whenever at least one current gesture is ready.
 * All three gestures are still recognized so an unmapped hold/double never becomes
 * a mapped single. Unmapped gestures in an owned sequence do nothing; raw events
 * cannot be restored to the OEM after the sequence has already been consumed.
 * Each caller serializes events/deadlines/configuration. Power/volume never enter here.
 */
class PlusPolicySession(private val button: Button = Button.PLUS, tier: Tier = Tier.ROOT, private val plusVolumeChords: Boolean = false) {
    init { require(button != Button.POWER) }
    private val triggers = setOf(Gesture.SINGLE, Gesture.DOUBLE, Gesture.LONG).map { Trigger(button, it) }.toSet()
    private val chordTriggers = if (plusVolumeChords && button == Button.PLUS)
        setOf(Trigger(Button.PLUS, Gesture.CHORD, Button.VOLUME_UP), Trigger(Button.PLUS, Gesture.CHORD, Button.VOLUME_DOWN)) else emptySet()
    private val allTriggers = triggers + chordTriggers
    private val gate = PolicyGate(setOf(button), tier, plusVolumeChords = plusVolumeChords)
    private var firmware = ""
    private var engine = GestureEngine(allTriggers)
    private var device: Int? = null
    private var ownedDown = false
    private var draining = false
    private var connected = false
    private val decisions = LinkedHashMap<PolicyEvent, Boolean>()
    private data class PartnerPress(val deviceId: Int, val downTime: Long)
    private val partners = mutableMapOf<Button, PartnerPress>()
    private val partnerDecisions = LinkedHashMap<Pair<Button, PolicyEvent>, Boolean>()

    fun configure(configuration: PolicyConfiguration, timing: Timing, proof: PolicyProof, lease: BackendLease) {
        engine = GestureEngine(allTriggers, timing)
        draining = ownedDown
        if (!ownedDown) device = null
        firmware = proof.firmware
        // Chords cannot be partially owned at the raw Plus boundary.
        gate.configure(if (configuration.rules.any { it.enabled && it.trigger.gesture == Gesture.CHORD &&
            button in setOf(it.trigger.button, it.trigger.partner) && (!plusVolumeChords || !it.trigger.isPlusVolumeChord()) }) configuration.copy(enabled = false) else configuration)
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
        if (event.edge == Edge.CANCEL) {
            val consumed = ownedDown && device == event.deviceId
            if (consumed) { cancelGesture(); ownedDown = false; device = null; draining = false }
            return PolicyOutcome(consumed)
        }
        if (device != null && device != event.deviceId) return PolicyOutcome(false)
        if (event.edge == Edge.DOWN && !ownedDown) {
            val active = connected && allTriggers.any { gate.decide(it, firmware, now, local) is PolicyDecision.Replace }
            if (!active || event.repeat != 0) { engine.reset(); return PolicyOutcome(false) }
            val chordReady = chordTriggers.any { gate.decide(it, firmware, now, local) is PolicyDecision.Replace }
            // An unmapped hold can remain a modifier. A mapped long action still wins
            // once committed; releasing a long modifier must never run a short click.
            engine.allowLongRecognition(button, !chordReady || gate.decide(Trigger(button, Gesture.LONG), firmware, now, local) is PolicyDecision.Replace)
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

    /** A Plus-first chord may own a Volume pair from another hardware device.
     * An earlier standalone Volume DOWN is never swallowed or replayed.
     */
    fun acceptPartner(partner: Button, event: PolicyEvent, now: Long, local: ZonedDateTime): PolicyOutcome {
        if (partner !in setOf(Button.VOLUME_UP, Button.VOLUME_DOWN)) return PolicyOutcome(false)
        val key = partner to event
        partnerDecisions[key]?.let { return PolicyOutcome(it) }
        val tracked = partners[partner]
        if (tracked != null) {
            if (tracked.deviceId != event.deviceId || tracked.downTime != event.downTime) return PolicyOutcome(false)
            if (event.edge != Edge.DOWN) {
                partners.remove(partner)
                if (connected && !draining && event.edge == Edge.UP && device != null)
                    engine.accept(ButtonEvent("owned-input", device!!, partner, Edge.UP, event.time, event.repeat))
                else cancelGesture()
            }
            rememberPartner(key)
            return PolicyOutcome(true)
        }
        val chord = chordTriggers.firstOrNull { it.partner == partner }
        if (chord == null || !connected || !ownedDown || draining || event.edge != Edge.DOWN || event.repeat != 0 ||
            gate.decide(chord, firmware, now, local) !is PolicyDecision.Replace) {
            cancelGesture(); return PolicyOutcome(false)
        }
        val due = resolve(engine.advance(event.time), now, local)
        if (!engine.canStartChord(button, partner)) { cancelGesture(); return PolicyOutcome(false, due) }
        // The IMS adapter verifies each physical driver before reaching this logical session.
        val candidates = engine.accept(ButtonEvent("owned-input", device!!, partner, Edge.DOWN, event.time))
        if (candidates.none { it.sameAs(chord) }) { cancelGesture(); return PolicyOutcome(false, due) }
        partners[partner] = PartnerPress(event.deviceId, event.downTime)
        rememberPartner(key)
        return PolicyOutcome(true, due + resolve(candidates, now, local))
    }
    private fun rememberPartner(key: Pair<Button, PolicyEvent>) {
        partnerDecisions[key] = true
        while (partnerDecisions.size > 32) partnerDecisions.remove(partnerDecisions.keys.first())
    }

    fun advance(now: Long, local: ZonedDateTime): List<PolicyDecision.Replace> {
        if (!connected) return emptyList()
        val result = resolve(engine.advance(now), now, local)
        if (!ownedDown && engine.nextDeadline() == null) device = null
        return result
    }
}
