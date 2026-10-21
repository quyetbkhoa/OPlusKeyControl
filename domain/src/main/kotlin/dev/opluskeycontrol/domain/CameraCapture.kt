package dev.opluskeycontrol.domain

/** No OEM adapter is enabled. This coordinator defines the exactly-once contract
 * a firmware-verified readiness/shutter/acknowledgement adapter must implement.
 */
enum class CameraStage { IDLE, OPENING, WAITING_FOR_PHOTO, COMPLETE, FAILED }
sealed interface CameraEffect {
    data class OpenStock(val session: Long, val secure: Boolean) : CameraEffect
    data class ShutterOnce(val session: Long) : CameraEffect
    data object None : CameraEffect
}
class CameraCapture(private val timeoutMs: Long = 8000) {
    var stage = CameraStage.IDLE; private set
    private var session = 0L
    private var deadline = 0L
    fun start(now: Long, locked: Boolean): CameraEffect {
        if (stage in setOf(CameraStage.OPENING, CameraStage.WAITING_FOR_PHOTO)) return CameraEffect.None
        session++; stage = CameraStage.OPENING; deadline = now + timeoutMs
        return CameraEffect.OpenStock(session, locked)
    }
    fun ready(session: Long, now: Long): CameraEffect {
        if (session != this.session || stage != CameraStage.OPENING) return CameraEffect.None
        if (now >= deadline) { stage = CameraStage.FAILED; return CameraEffect.None }
        stage = CameraStage.WAITING_FOR_PHOTO
        return CameraEffect.ShutterOnce(session)
    }
    fun photoSaved(session: Long, now: Long) {
        if (session == this.session && stage == CameraStage.WAITING_FOR_PHOTO) {
            stage = if (now < deadline) CameraStage.COMPLETE else CameraStage.FAILED
        }
        // Never closes stock camera or exposes gallery; no retry after a shutter.
    }
    fun timeout(now: Long) {
        if (stage in setOf(CameraStage.OPENING, CameraStage.WAITING_FOR_PHOTO) && now >= deadline) stage = CameraStage.FAILED
    }
    fun cancel() { session++; stage = CameraStage.IDLE }
}

/** A recording's originating trigger remains a stop control after a schedule
 * boundary. Do not replace a stop with the new window's action while recording.
 */
class RecordingContinuation {
    private var origin: Trigger? = null
    fun started(trigger: Trigger) { origin = trigger }
    fun stopped() { origin = null }
    fun shouldStop(trigger: Trigger) = origin?.sameAs(trigger) == true
}
