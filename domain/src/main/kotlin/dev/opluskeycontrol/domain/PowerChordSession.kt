package dev.opluskeycontrol.domain

import java.time.ZonedDateTime
import kotlin.math.abs

/** Observes physical pairs; only the native OEM combination callback may commit.
 * This session never consumes or replays raw Power or Volume events. */
class PowerChordSession {
    private val gate = PolicyGate(setOf(Button.POWER), semanticPowerChords = true)
    private var firmware = ""
    private val held = mutableMapOf<Button, PolicyEvent>()
    private data class Candidate(val trigger: Trigger, val powerDown: Long, val volumeDown: Long, val at: Long)
    private var candidate: Candidate? = null
    private var committed: Candidate? = null
    private val triggers = setOf(Button.VOLUME_UP, Button.VOLUME_DOWN).map { Trigger(Button.POWER, Gesture.CHORD, it) }.toSet()
    fun configure(configuration: PolicyConfiguration, proof: PolicyProof, lease: BackendLease) {
        cancel(); firmware = proof.firmware
        gate.configure(configuration); gate.verify(proof); gate.attach(lease)
    }
    fun cancel() { held.clear(); candidate = null }
    fun disconnect() { cancel(); gate.disconnect() }
    fun ready(partner: Button, now: Long, local: ZonedDateTime): Boolean = triggers.firstOrNull { it.partner == partner }?.let {
        gate.decide(it, firmware, now, local) is PolicyDecision.Replace
    } == true
    fun observe(button: Button, event: PolicyEvent) {
        if (button !in setOf(Button.POWER, Button.VOLUME_UP, Button.VOLUME_DOWN)) { cancel(); return }
        if (event.edge == Edge.CANCEL) { cancel(); return }
        if (event.edge == Edge.UP) {
            if (held[button]?.let { it.deviceId == event.deviceId && it.downTime == event.downTime } == true) held.remove(button)
            return
        }
        if (event.repeat != 0 || button in held) return
        held[button] = event
        if (held.size > 2) { candidate = null; return }
        val power = held[Button.POWER] ?: return
        val partner = if (Button.VOLUME_UP in held) Button.VOLUME_UP else Button.VOLUME_DOWN
        val volume = held[partner] ?: return
        if (abs(power.time - volume.time) <= 150)
            candidate = Candidate(Trigger(Button.POWER, Gesture.CHORD, partner), power.downTime, volume.downTime, maxOf(power.time, volume.time))
    }
    fun accept(partner: Button, now: Long, local: ZonedDateTime, enqueue: (PolicyDecision.Replace) -> Boolean): Boolean {
        val c = candidate ?: return false
        if (c.trigger.partner != partner || c == committed || now !in c.at..(c.at + 1000)) return false
        val action = gate.decide(c.trigger, firmware, now, local) as? PolicyDecision.Replace ?: return false
        if (!enqueue(action)) { disconnect(); return false }
        committed = c; candidate = null
        return true
    }
}
