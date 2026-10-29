package dev.opluskeycontrol.input

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SerialRequestQueueTest {
    @Test fun twoToggleRequestsAreEachProcessedOnce() = runBlocking {
        val owner = CoroutineScope(coroutineContext + Job())
        val finished = CompletableDeferred<Unit>()
        var enabled = false
        var calls = 0
        val queue = SerialRequestQueue<Int>(owner) {
            enabled = !enabled
            calls++
            if (calls == 2) finished.complete(Unit)
        }
        assertTrue(queue.offer(1)); assertTrue(queue.offer(2))
        finished.await()
        assertEquals(2, calls)
        assertFalse(enabled)
        owner.cancel()
    }
    @Test fun executesEachRequestInOrderAndSurvivesFailure() = runBlocking {
        val owner = CoroutineScope(coroutineContext + Job())
        val finished = CompletableDeferred<Unit>()
        val executed = mutableListOf<Int>()
        var failures = 0
        val queue = SerialRequestQueue<Int>(owner, onFailure = { failures++ }) { request ->
            if (request == 2) throw IllegalStateException("test")
            executed += request
            if (request == 4) finished.complete(Unit)
        }
        assertTrue(queue.offer(1)); assertTrue(queue.offer(2)); assertTrue(queue.offer(3)); assertTrue(queue.offer(4))
        finished.await()
        assertEquals(listOf(1, 3, 4), executed)
        assertEquals(1, failures)
        owner.cancel()
    }
    @Test fun expiresOldOrInvalidMonotonicTime() {
        assertFalse(requestExpired(100, 5100))
        assertTrue(requestExpired(100, 5101))
        assertTrue(requestExpired(100, 99))
    }
    @Test fun orderOverflowAndRevocationWhileWaiting() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val executed = mutableListOf<Int>()
        var authorized = true
        val owner = CoroutineScope(coroutineContext + Job())
            val queue = SerialRequestQueue<Int>(owner, 2) { request ->
                if (request == 1) { started.complete(Unit); release.await() }
                if (authorized) executed += request
                if (request == 3) finished.complete(Unit)
            }
            assertTrue(queue.offer(1)); started.await()
            assertTrue(queue.offer(2)); assertTrue(queue.offer(3))
            assertFalse(queue.offer(4))
            authorized = false
            release.complete(Unit)
            finished.await()
        assertEquals(emptyList<Int>(), executed)
        owner.cancel()
    }
}
