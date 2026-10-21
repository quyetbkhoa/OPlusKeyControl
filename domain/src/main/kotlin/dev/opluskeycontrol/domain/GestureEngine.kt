package dev.opluskeycontrol.domain

enum class Edge { DOWN, UP, CANCEL }
data class ButtonEvent(val source: String, val deviceId: Int, val button: Button, val edge: Edge, val timeMs: Long, val repeat: Int = 0)
data class Timing(val doubleMs: Long = 300, val longMs: Long = 600, val chordMs: Long = 100, val debounceMs: Long = 30) {
    init { require(doubleMs in 150..600); require(longMs in 400..1500); require(chordMs in 30..250); require(debounceMs in 0..100) }
}

/** Candidate recognizer only. Never grants an adapter permission to suppress an event.
 * Serialized owner calls advance only at nextDeadline, never on a repeating idle timer.
 */
class GestureEngine(private val enabled: Set<Trigger>, private val timing: Timing = Timing()) {
    private data class Press(val down: Long, var consumed: Boolean = false, val secondClick: Boolean = false)
    private val held = mutableMapOf<Button, Press>()
    private val pending = mutableMapOf<Button, Long>()
    private val lastUp = mutableMapOf<Button, Long>()
    private var owner: Pair<String, Int>? = null
    private var lastTime = Long.MIN_VALUE
    private fun supports(button: Button, gesture: Gesture) = enabled.any { it.button == button && it.gesture == gesture }
    fun reset() { held.clear(); pending.clear(); lastUp.clear(); owner = null; lastTime = Long.MIN_VALUE }
    fun nextDeadline(): Long? = (pending.values.map { it + timing.doubleMs } +
        held.filter { !it.value.consumed && supports(it.key, Gesture.LONG) }.values.map { it.down + timing.longMs }).minOrNull()
    fun advance(now: Long): List<Trigger> {
        if (now < lastTime) { reset(); return emptyList() }
        lastTime = now
        val result = mutableListOf<Trigger>()
        for ((button, at) in pending.toMap()) if (now >= at + timing.doubleMs) {
            pending.remove(button)
            if (supports(button, Gesture.SINGLE)) result += Trigger(button, Gesture.SINGLE)
        }
        for ((button, press) in held) if (!press.consumed && supports(button, Gesture.LONG) && now >= press.down + timing.longMs) {
            press.consumed = true
            pending.remove(button)
            result += Trigger(button, Gesture.LONG)
        }
        return result
    }
    fun accept(event: ButtonEvent): List<Trigger> {
        val incoming = event.source to event.deviceId
        if (owner != null && owner != incoming) return emptyList()
        if (event.edge == Edge.CANCEL || event.timeMs < lastTime) { reset(); return emptyList() }
        owner = incoming
        val result = advance(event.timeMs).toMutableList()
        if (event.repeat > 0) return result
        val button = event.button
        when (event.edge) {
            Edge.DOWN -> {
                if (button in held || event.timeMs - (lastUp[button] ?: Long.MIN_VALUE / 2) < timing.debounceMs) return result
                val secondClick = pending.remove(button) != null && supports(button, Gesture.DOUBLE)
                held[button] = Press(event.timeMs, secondClick = secondClick)
                val chord = enabled.firstOrNull { it.gesture == Gesture.CHORD && button in setOf(it.button, it.partner) &&
                    held[it.button]?.consumed == false && held[it.partner]?.consumed == false &&
                    (button == it.partner || event.timeMs - held.getValue(it.partner!!).down <= timing.chordMs) }
                if (chord != null) {
                    // A held modifier is valid beyond the simultaneous window until committed.
                    held.getValue(chord.button).consumed = true
                    held.getValue(chord.partner!!).consumed = true
                    pending.remove(chord.button); pending.remove(chord.partner)
                    result += chord
                }
            }
            Edge.UP -> {
                val press = held.remove(button) ?: return result
                lastUp[button] = event.timeMs
                if (!press.consumed && !(button == Button.POWER && event.timeMs - press.down >= timing.longMs)) {
                    if (press.secondClick && supports(button, Gesture.DOUBLE)) result += Trigger(button, Gesture.DOUBLE)
                    else if (supports(button, Gesture.DOUBLE)) pending[button] = event.timeMs
                    else if (supports(button, Gesture.SINGLE)) result += Trigger(button, Gesture.SINGLE)
                }
            }
            Edge.CANCEL -> Unit
        }
        return result
    }
}

/** Explicit ownership prevents accidental fallback/duplicate capture across privilege tiers. */
class SourceOwnership {
    private var owner: String? = null
    fun acquire(source: String): Boolean = synchronized(this) {
        if (owner == null || owner == source) { owner = source; true } else false
    }
    fun release(source: String) = synchronized(this) { if (owner == source) owner = null }
    fun accepts(source: String): Boolean = synchronized(this) { owner == source }
}
