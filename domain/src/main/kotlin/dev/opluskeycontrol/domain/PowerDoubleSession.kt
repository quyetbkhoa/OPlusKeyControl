package dev.opluskeycontrol.domain

import java.time.ZonedDateTime

/** Observer of physical Power edges + the OEM's completed semantic callback.
 * This class never consumes a raw Power event or synthesizes one. A minimum
 * count of five prevents a rapid 3/4/5-press sequence committing our double early.
 * Long/very-long/SOS callbacks remain entirely in the OEM policy.
 */
class PowerDoubleSession {
    private val trigger = Trigger(Button.POWER, Gesture.DOUBLE)
    private val gate = PolicyGate(setOf(Button.POWER), semanticPowerDouble = true)
    private var firmware = ""
    private var device: Int? = null
    private var down = 0L
    private var lastTime = 0L
    private var held = false
    private var presses = 0
    private var timeout = 300L
    private val committed = LinkedHashSet<Pair<Long, Int>>()

    fun configure(configuration: PolicyConfiguration, proof: PolicyProof, lease: BackendLease, multiPressTimeout: Long) {
        require(multiPressTimeout in 100..1000)
        firmware = proof.firmware; timeout = multiPressTimeout
        gate.configure(configuration); gate.verify(proof); gate.attach(lease)
        cancel()
    }
    fun disconnect() { gate.disconnect(); cancel() }
    fun cancel() { device = null; down = 0; lastTime = 0; held = false; presses = 0 }
    fun observe(event: PolicyEvent) {
        if (event.edge == Edge.CANCEL || event.time < lastTime) { cancel(); return }
        if (event.edge == Edge.DOWN && event.repeat == 0) {
            if (held && device == event.deviceId && down == event.downTime) return
            presses = if (!held && device == event.deviceId && event.time - down in 0 until timeout) presses + 1 else 1
            device = event.deviceId; down = event.downTime; held = true
        } else if (event.edge == Edge.UP && device == event.deviceId && down == event.downTime) held = false
        lastTime = event.time
    }
    fun maxPressCount(original: Int, now: Long, local: ZonedDateTime): Int =
        if (gate.decide(trigger, firmware, now, local) is PolicyDecision.Replace) maxOf(original, 5) else original

    /** API 37 reports ordinary completed presses through SingleKeyGestureEvent. */
    fun acceptGesture(type: Int, action: Int, count: Int, downTime: Long, displayId: Int,
                      now: Long, local: ZonedDateTime, enqueue: (PolicyDecision.Replace) -> Boolean): Boolean {
        if (type != 0 || action != 1) {
            if (type != 0 || action != 0) cancel()
            return false
        }
        return accept(count, downTime, displayId, now, local, enqueue)
    }

    fun accept(count: Int, downTime: Long, displayId: Int, now: Long, local: ZonedDateTime,
               enqueue: (PolicyDecision.Replace) -> Boolean): Boolean {
        val key = downTime to displayId
        if (count != 2) return false
        if (key in committed) return true // duplicate callback: suppress OEM, no second dispatch
        if (presses != 2 || held || downTime != down || now - lastTime !in 0..5000 || displayId < -1) return false
        val action = gate.decide(trigger, firmware, now, local) as? PolicyDecision.Replace ?: return false
        if (!enqueue(action)) { disconnect(); return false }
        committed += key
        while (committed.size > 32) committed.remove(committed.first())
        cancel()
        return true
    }
}
