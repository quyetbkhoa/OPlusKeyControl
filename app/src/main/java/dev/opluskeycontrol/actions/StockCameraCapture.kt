package dev.opluskeycontrol.actions

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import dev.opluskeycontrol.domain.CameraCapture
import dev.opluskeycontrol.domain.CameraEffect
import dev.opluskeycontrol.domain.CameraStage
import dev.opluskeycontrol.root.RootCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Explicitly tested root adapter only. Never retries a shutter or closes the camera.
 * Foreground/client readiness and saved-photo ID must both be observed. No photo bytes
 * or filenames are read, logged or uploaded. Failure to parse readiness fails closed.
 */
class StockCameraCapture(private val context: Context) {
    suspend fun capture(): Boolean = withContext(Dispatchers.IO) {
        if (Build.MODEL != "CPH2745" || Build.DISPLAY != "CPH2745_16.0.10.601(EX01)" || android.os.Process.myUid() / 100000 != 0) return@withContext false
        val coordinator = CameraCapture(12_000)
        val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val effect = coordinator.start(SystemClock.uptimeMillis(), locked) as CameraEffect.OpenStock
        val action = if (locked) MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE else MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
        val handlers = context.packageManager.queryIntentActivities(Intent(action).setPackage("com.oplus.camera"), 0).filter {
            it.activityInfo.exported && it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
        }
        val target = handlers.singleOrNull()?.activityInfo ?: return@withContext false
        val component = ComponentName(target.packageName, target.name).flattenToString()
        require(component.matches(Regex("[A-Za-z0-9_./$]+")))
        val before = latestPhoto() ?: return@withContext false
        val launched = RootCommands.run("am start -W --user 0 -a '$action' -n '$component'", 10) ?: return@withContext false
        if (!launched.contains("Status: ok")) return@withContext false
        val start = SystemClock.uptimeMillis()
        var ready = false
        while (SystemClock.uptimeMillis() - start < 6000) {
            if (cameraReady()) { ready = true; break }
            delay(200) // finite action readiness only; no idle polling or wake lock
        }
        if (!ready || coordinator.ready(effect.session, SystemClock.uptimeMillis()) !is CameraEffect.ShutterOnce) return@withContext false
        // Recheck foreground immediately before issuing exactly one stock-camera shutter key.
        val apk = context.applicationInfo.sourceDir
        require(apk.startsWith("/data/app/") && apk.endsWith("/base.apk") && apk.matches(Regex("[A-Za-z0-9_./~=+\\-]+")))
        val uid = target.applicationInfo.uid
        if (uid !in 10000..99999 || !cameraReady()) return@withContext false
        val shutter = RootCommands.run("CLASSPATH='$apk' exec /system/bin/app_process /system/bin dev.opluskeycontrol.root.CameraShutterMain $uid", 8)
        if (shutter?.contains("SHUTTER_SENT") != true) return@withContext false
        while (SystemClock.uptimeMillis() - start < 8500) {
            val after = latestPhoto()
            if (after != null && after > before) {
                coordinator.photoSaved(effect.session, SystemClock.uptimeMillis())
                return@withContext coordinator.stage == CameraStage.COMPLETE
            }
            delay(250)
        }
        coordinator.timeout(SystemClock.uptimeMillis() + 12_000)
        false
    }
    private fun latestPhoto(): Long? {
        val text = RootCommands.run("content query --uri 'content://media/external/images/media?limit=1' --projection _id --where \"owner_package_name='com.oplus.camera' AND is_pending=0\" --sort '_id DESC'", 3) ?: return null
        if (text.contains("No result found")) return 0
        if (text.contains("Error") || text.contains("Exception")) return null
        return Regex("_id=(\\d+)").findAll(text).map { it.groupValues[1].toLong() }.maxOrNull()
    }
    private fun cameraReady(): Boolean {
        val tasks = RootCommands.run("dumpsys activity activities", 3) ?: return false
        val foreground = tasks.lineSequence().any { (it.contains("topResumedActivity") || it.contains("mResumedActivity")) && it.contains("com.oplus.camera/") }
        if (!foreground) return false
        val camera = RootCommands.run("dumpsys media.camera", 3) ?: return false
        // Restrict to current active-client block; historical camera events are not readiness.
        if (!camera.contains("Active Camera Clients:") || !camera.contains("Allowed user IDs:")) return false
        val active = camera.substringAfter("Active Camera Clients:", "").substringBefore("Allowed user IDs:")
        return active.contains("com.oplus.camera")
    }
}
