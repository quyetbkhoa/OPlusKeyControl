package dev.opluskeycontrol.domain

/** Retry until recovery/cancellation, with a capped delay and no counter overflow. */
class RecoveryBackoff {
    private var attempt = 0
    fun reset() { attempt = 0 }
    fun nextDelay(): Long {
        val delays = longArrayOf(1000, 2000, 4000, 8000, 15000, 30000)
        val delay = delays[attempt]
        attempt = (attempt + 1).coerceAtMost(delays.lastIndex)
        return delay
    }
}
