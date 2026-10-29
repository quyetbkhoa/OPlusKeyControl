package dev.opluskeycontrol.domain

import java.time.ZonedDateTime

data class PolicyEvent(val deviceId: Int, val downTime: Long, val time: Long, val edge: Edge, val repeat: Int = 0)
data class PolicyOutcome(val consume: Boolean, val actions: List<PolicyDecision.Replace> = emptyList())

/** Owns a Plus sequence whenever at least one current gesture is ready.
 * All three gestures are still recognized so an unmapped hold/double never becomes
 * a mapped single. Unmapped gestures in an owned sequence do nothing; raw events
 * cannot be restored to the OEM after the sequence has already been consumed.
 * Each caller serializes events/deadlines/configuration; only verified partners enter here.
 */
class PlusPolicySession(private val button: Button = Button.PLUS, tier: Tier = Tier.ROOT, private val plusVolumeChords: Boolean = false) {
    init { require(button != Button.POWER) }
    private val triggers = setOf(Gesture.SINGLE, Gesture.DOUBLE, Gesture.LONG, Gesture.TRIPLE).map { Trigger(button, it) }.toSet()
    private val chordTriggers = if (plusVolumeChords && button == Button.PLUS)
        setOf(Button.VOLUME_UP, Button.VOLUME_DOWN, Button.POWER).map { Trigger(Button.PLUS, Gesture.CHORD, it) }.toSet() else emptySet()
    private val allTriggers = triggers + chordTriggers
    private val gate = PolicyGate(setOf(button), tier, plusVolumeChords = plusVolumeChords)
    private var firmware = ""
    private var engine = GestureEngine(allTriggers)
    private var device: Int? = null
    private var ownedDown = false
    private var draining = false
    private var connected = false
    private data class RecognitionPolicy(val configuration: PolicyConfiguration, val timing: Timing, val proof: PolicyProof)
    private var recognitionPolicy: RecognitionPolicy? = null
    private var feedbackGiven = false
    private var feedbackDeadline: Long? = null
    private val recognitions = mutableListOf<PolicyDecision.Replace>()
    private val powerChordPresses = mutableListOf<Long>()
    fun takePowerChordPresses(): List<Long> = powerChordPresses.toList().also { powerChordPresses.clear() }
    fun takeRecognitions(): List<PolicyDecision.Replace> = recognitions.toList().also { recognitions.clear() }
    private fun recognize(actions: List<PolicyDecision.Replace>) {
        if (!feedbackGiven && actions.isNotEmpty()) { feedbackGiven = true; feedbackDeadline = null; recognitions += actions.first() }
    }
    private var deferredLong = false
    private var chordCommitted = false
    private var plusDownAt = 0L
    private var longThreshold = 600L
    private val decisions = LinkedHashMap<PolicyEvent, Boolean>()
    private data class PartnerPress(val deviceId: Int, val downTime: Long, val at: Long = downTime, val consume: Boolean = true)
    // Earlier Volume DOWNs already belong to OEM. Their UP/repeats must also pass.
    private val precedingVolumes = mutableMapOf<Button, PartnerPress>()
    private val partners = mutableMapOf<Button, PartnerPress>()
    private val partnerDecisions = LinkedHashMap<Pair<Button, PolicyEvent>, Boolean>()

    fun configure(configuration: PolicyConfiguration, timing: Timing, proof: PolicyProof, lease: BackendLease) {
        val next = RecognitionPolicy(configuration.copy(rules = configuration.rules.toList()), timing,
            proof.copy(allowed = proof.allowed.toSet()))
        if (connected && recognitionPolicy == next) {
            // Refresh authority/readiness without discarding a physical press or
            // its pending single/double deadline. Resolution uses the new lease.
            gate.attach(lease)
            return
        }
        recognitionPolicy = next
        recognitions.clear(); feedbackDeadline = null; feedbackGiven = false
        precedingVolumes.clear()
        // Triple counting adds a wait after the second click only when explicitly mapped.
        val tripleEnabled = configuration.rules.any { it.enabled && it.trigger == Trigger(button, Gesture.TRIPLE) }
        engine = GestureEngine(if (tripleEnabled) allTriggers else allTriggers.filterNot { it.gesture == Gesture.TRIPLE }.toSet(), timing)
        longThreshold = timing.longMs
        deferredLong = false; chordCommitted = false
        draining = ownedDown
        if (!ownedDown) device = null
        firmware = proof.firmware
        // Chords cannot be partially owned at the raw Plus boundary.
        gate.configure(if (configuration.rules.any { it.enabled && it.trigger.gesture == Gesture.CHORD &&
            button in setOf(it.trigger.button, it.trigger.partner) && (!plusVolumeChords || !it.trigger.isPlusChord()) }) configuration.copy(enabled = false) else configuration)
        gate.verify(proof); gate.attach(lease); connected = true
    }

    fun disconnect() { recognitions.clear(); feedbackDeadline = null; precedingVolumes.clear(); deferredLong = false; connected = false; gate.disconnect(); engine.reset(); draining = ownedDown }
    fun cancelGesture() { recognitions.clear(); feedbackDeadline = null; precedingVolumes.clear(); deferredLong = false; engine.reset(); draining = ownedDown } // paired UP closes an owned DOWN
    fun nextDeadline(): Long? = if (connected) listOfNotNull(engine.nextDeadline(), feedbackDeadline).minOrNull() else null

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
            // Active chords reserve the modifier until release; mapped LONG is deferred
            // to release so it cannot steal a slower physical chord.
            deferredLong = chordReady
            feedbackGiven = false
            feedbackDeadline = if (chordReady && gate.decide(Trigger(button, Gesture.LONG), firmware, now, local) is PolicyDecision.Replace)
                event.time + longThreshold else null
            chordCommitted = false; plusDownAt = event.time
            engine.allowLongRecognition(button, !chordReady)
            ownedDown = true; device = event.deviceId
            if (precedingVolumes.size > 1 || partners.values.any { !it.consume }) cancelGesture()
        }
        if (!ownedDown) return PolicyOutcome(false)
        // Normalize only inside the recognizer, never replay/inject a hardware key.
        // A reverse-order Volume pair stays OEM-owned even after its chord commits.
        val reverse = if (event.edge == Edge.DOWN && event.repeat == 0 && !draining && !chordCommitted && precedingVolumes.size == 1)
            precedingVolumes.entries.single().takeIf { event.time >= it.value.at &&
                gate.decide(Trigger(button, Gesture.CHORD, it.key), firmware, now, local) is PolicyDecision.Replace } else null
        val actions = (if (connected && !draining) resolve(engine.accept(ButtonEvent("owned-input", event.deviceId, button,
            event.edge, event.time, event.repeat)), now, local) else emptyList()) +
            if (event.edge == Edge.UP && connected && !draining && deferredLong && !chordCommitted && event.time - plusDownAt >= longThreshold)
                resolve(listOf(Trigger(button, Gesture.LONG)), now, local) else emptyList()
        val reverseActions = if (reverse != null && engine.canStartChord(button, reverse.key)) {
            val chord = Trigger(button, Gesture.CHORD, reverse.key)
            val candidates = engine.accept(ButtonEvent("owned-input", event.deviceId, reverse.key, Edge.DOWN, event.time))
            if (candidates.any { it.sameAs(chord) }) {
                chordCommitted = true
                partners[reverse.key] = reverse.value.copy(consume = false)
                precedingVolumes.clear()
                resolve(candidates, now, local).also { actions ->
                    if (reverse.key == Button.POWER && actions.any { it.rule.trigger.sameAs(chord) })
                        powerChordPresses += reverse.value.downTime
                }
            } else emptyList()
        } else emptyList()
        recognize(actions + reverseActions)
        if (event.edge == Edge.UP) { feedbackDeadline = null; ownedDown = false; draining = false; if (engine.nextDeadline() == null) device = null }
        decisions[event] = true
        while (decisions.size > 32) decisions.remove(decisions.keys.first())
        return PolicyOutcome(true, actions + reverseActions)
    }

    /** Plus-first owns its partner pair; Volume-first observes a pair already owned
     * by OEM and replaces only the Plus sequence. No hardware event replay.
     */
    fun acceptPartner(partner: Button, event: PolicyEvent, now: Long, local: ZonedDateTime): PolicyOutcome {
        if (partner !in setOf(Button.VOLUME_UP, Button.VOLUME_DOWN, Button.POWER)) return PolicyOutcome(false)
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
            rememberPartner(key, tracked.consume)
            return PolicyOutcome(tracked.consume)
        }
        val earlier = precedingVolumes[partner]
        if (earlier != null && (event.deviceId != earlier.deviceId || event.downTime != earlier.downTime))
            return PolicyOutcome(false)
        if (event.edge != Edge.DOWN) precedingVolumes.remove(partner)
        val chord = chordTriggers.firstOrNull { it.partner == partner }
        if (chord == null || !connected || !ownedDown || draining || event.edge != Edge.DOWN || event.repeat != 0 ||
            gate.decide(chord, firmware, now, local) !is PolicyDecision.Replace) {
            if (!ownedDown && connected && partner in setOf(Button.VOLUME_UP, Button.VOLUME_DOWN, Button.POWER) &&
                event.edge == Edge.DOWN && chord != null && gate.decide(chord, firmware, now, local) is PolicyDecision.Replace) {
                if (event.repeat == 0) {
                    // Cancel pending Plus clicks but retain other physical Volume holds:
                    // two held Volumes are ambiguous/reserved, never pick one arbitrarily.
                    engine.reset(); deferredLong = false
                    precedingVolumes.putIfAbsent(partner, PartnerPress(event.deviceId, event.downTime, event.time, consume = false))
                }
            } else if (event.repeat == 0 || event.edge != Edge.DOWN) cancelGesture()
            rememberPartner(key, false)
            return PolicyOutcome(false)
        }
        val due = resolve(engine.advance(event.time), now, local)
        if (!engine.canStartChord(button, partner)) { cancelGesture(); recognize(due); return PolicyOutcome(false, due) }
        // The IMS adapter verifies each physical driver before reaching this logical session.
        val candidates = engine.accept(ButtonEvent("owned-input", device!!, partner, Edge.DOWN, event.time))
        if (candidates.none { it.sameAs(chord) }) { cancelGesture(); return PolicyOutcome(false, due) }
        chordCommitted = true
        partners[partner] = PartnerPress(event.deviceId, event.downTime)
        rememberPartner(key)
        val actions = due + resolve(candidates, now, local)
        if (partner == Button.POWER && actions.any { it.rule.trigger.sameAs(chord) })
            powerChordPresses += event.downTime
        recognize(actions)
        return PolicyOutcome(true, actions)
    }
    private fun rememberPartner(key: Pair<Button, PolicyEvent>, consume: Boolean = true) {
        partnerDecisions[key] = consume
        while (partnerDecisions.size > 32) partnerDecisions.remove(partnerDecisions.keys.first())
    }

    fun advance(now: Long, local: ZonedDateTime): List<PolicyDecision.Replace> {
        if (!connected) return emptyList()
        val result = resolve(engine.advance(now), now, local)
        recognize(result)
        if (feedbackDeadline?.let { now >= it } == true) {
            feedbackDeadline = null
            if (ownedDown && !draining && !chordCommitted) recognize(resolve(listOf(Trigger(button, Gesture.LONG)), now, local))
        }
        if (!ownedDown && engine.nextDeadline() == null) device = null
        return result
    }
}
