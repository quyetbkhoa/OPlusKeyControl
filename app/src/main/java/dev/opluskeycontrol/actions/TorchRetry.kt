package dev.opluskeycontrol.actions

/** Retry a fixed desired state, never recompute a toggle after a partial success. */
internal suspend fun setTorchWithRetry(
    enabled: Boolean,
    set: (Boolean) -> Unit,
    transient: (Exception) -> Boolean,
    pause: suspend (Long) -> Unit,
    report: (Int, Exception) -> Unit = { _, _ -> }
) {
    for (attempt in 0..2) {
        try { set(enabled); return }
        catch (failure: Exception) {
            report(attempt + 1, failure)
            if (attempt == 2 || !transient(failure)) throw failure
            pause(150L * (attempt + 1))
        }
    }
}
