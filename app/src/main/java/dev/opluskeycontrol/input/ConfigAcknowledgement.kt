package dev.opluskeycontrol.input

/** Access under PolicyBridge's lock. A newer publication supersedes older acknowledgements. */
internal class ConfigAcknowledgement {
    private var pending: Long? = null
    fun sent(revision: Long) { pending = revision }
    fun awaiting(revision: Long): Boolean = pending == revision
    fun applied(revision: Long): Boolean {
        if (!awaiting(revision)) return false
        pending = null
        return true
    }
    fun clear() { pending = null }
}
