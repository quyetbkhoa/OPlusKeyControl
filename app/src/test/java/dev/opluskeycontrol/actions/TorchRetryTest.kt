package dev.opluskeycontrol.actions

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TorchRetryTest {
    @Test fun reconnectRetriesSameDesiredStateWithoutTogglingBack() = runBlocking {
        val states = mutableListOf<Boolean>()
        val delays = mutableListOf<Long>()
        setTorchWithRetry(true, { states += it; if (states.size < 3) throw IllegalStateException() },
            { true }, { delays += it })
        assertEquals(listOf(true, true, true), states)
        assertEquals(listOf(150L, 300L), delays)
    }
    @Test fun permanentFailureIsNotRetried() = runBlocking {
        var attempts = 0
        val failure = SecurityException()
        try {
            setTorchWithRetry(false, { attempts++; throw failure }, { false }, { fail("must not wait") })
            fail("must fail")
        } catch (caught: SecurityException) { assertSame(failure, caught) }
        assertEquals(1, attempts)
    }
    @Test fun transientFailureIsBounded() = runBlocking {
        var attempts = 0
        try {
            setTorchWithRetry(false, { attempts++; throw IllegalStateException() }, { true }, {})
            fail("must fail")
        } catch (_: IllegalStateException) { }
        assertEquals(3, attempts)
    }
}
