package dev.opluskeycontrol.domain

enum class CaptureMode { OBSERVE_ONLY, VERIFIED_POLICY }
data class SourceCapabilities(val mode: CaptureMode, val buttons: Set<Button>, val screenOffVerified: Boolean = false)

/** Backends must have one owner. Raw Linux observation alone never grants suppression.
 * disconnect/cancel invalidates every outstanding gesture before ownership transfer.
 */
interface InputSource : AutoCloseable {
    val id: String
    val capabilities: SourceCapabilities
    fun start(onEvent: (ButtonEvent) -> Unit, onDisconnected: () -> Unit)
}
