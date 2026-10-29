package dev.opluskeycontrol.domain

import org.junit.Assert.*
import org.junit.Test

class RecoveryBackoffTest {
    @Test fun longOemRestartDeferralDoesNotExhaustRecovery() {
        val backoff = RecoveryBackoff()
        assertEquals(listOf(1000L,2000L,4000L,8000L,15000L,30000L), List(6) { backoff.nextDelay() })
        repeat(10000) { assertEquals(30000L, backoff.nextDelay()) }
        backoff.reset()
        assertEquals(1000L, backoff.nextDelay())
    }
}
