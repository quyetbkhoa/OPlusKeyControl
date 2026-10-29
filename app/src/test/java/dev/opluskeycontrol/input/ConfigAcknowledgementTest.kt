package dev.opluskeycontrol.input

import org.junit.Assert.*
import org.junit.Test

class ConfigAcknowledgementTest {
    @Test fun newerPublicationRejectsLateAndDuplicateAcknowledgements() {
        val state = ConfigAcknowledgement()
        state.sent(1)
        state.sent(2)
        assertFalse(state.applied(1))
        assertFalse(state.awaiting(1))
        assertTrue(state.awaiting(2))
        assertTrue(state.applied(2))
        assertFalse(state.applied(2))
        assertFalse(state.awaiting(2))
    }

    @Test fun disconnectInvalidatesPendingAcknowledgement() {
        val state = ConfigAcknowledgement()
        state.sent(3)
        state.clear()
        assertFalse(state.applied(3))
        assertFalse(state.awaiting(3))
    }
}
