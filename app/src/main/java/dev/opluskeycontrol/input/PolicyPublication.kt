package dev.opluskeycontrol.input

/** Readiness is part of a snapshot; a UI refresh alone must not reset gestures. */
internal data class PublishedPolicy(val text: String, val readyMask: Int, val recording: Boolean,
                                    val readyRuleIds: List<String>)

internal class PolicyPublication {
    private var last: PublishedPolicy? = null
    fun changed(value: PublishedPolicy) = value != last
    fun sent(value: PublishedPolicy) { last = value }
    fun reset() { last = null }
}
