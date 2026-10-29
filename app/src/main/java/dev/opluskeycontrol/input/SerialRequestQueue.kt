package dev.opluskeycontrol.input

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** One consumer, bounded pending requests; overflow is returned to the caller. */
internal class SerialRequestQueue<T>(scope: CoroutineScope, capacity: Int = 16, onFailure: (Exception) -> Unit = {}, process: suspend (T) -> Unit) {
    private val queue = Channel<T>(capacity)
    init { scope.launch { for (request in queue) {
        try { process(request) } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { onFailure(failure) }
    } } }
    fun offer(request: T): Boolean = queue.trySend(request).isSuccess
}

internal fun requestExpired(received: Long, now: Long): Boolean = now < received || now - received > 5000L
