package dev.opluskeycontrol.domain

/** Only a completed single from a recognized Plus/Power chord is suppressed.
 * Callers serialize access. Raw pair cleanup and multi/long gestures stay OEM-owned. */
class PowerChordSuppression {
    private val claims = LinkedHashMap<Long, Long>()
    fun claim(downTime: Long, now: Long) {
        claims.entries.removeAll { now < it.value || now - it.value > 5000 }
        claims[downTime] = now
        while (claims.size > 8) claims.remove(claims.keys.first())
    }
    fun suppresses(type: Int, action: Int, count: Int, downTime: Long, now: Long): Boolean {
        if (type != 0 || action != 1 || count != 1) return false
        val claimedAt = claims[downTime] ?: return false
        if (now - claimedAt !in 0..5000) { claims.remove(downTime); return false }
        return true
    }
    fun clear() { claims.clear() }
}
