package dev.opluskeycontrol.actions

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dev.opluskeycontrol.R
import dev.opluskeycontrol.domain.Action
import dev.opluskeycontrol.domain.ActionKind
import dev.opluskeycontrol.capabilities.Capabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ActionExecutor(private val context: Context) {
    val message = MutableStateFlow<Int?>(null)
    private val mutex = Mutex()
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val torch = mutableMapOf<String, Boolean>()
    private val available = mutableMapOf<String, Boolean>()
    init {
        cameras.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, enabled: Boolean) { torch[id] = enabled; available[id] = true }
            override fun onTorchModeUnavailable(id: String) { available[id] = false }
        }, Handler(Looper.getMainLooper()))
    }
    suspend fun execute(action: Action, visibleActivity: Boolean = false) {
        if (!mutex.tryLock()) { message.value = R.string.action_busy; return }
        try {
            when (action.kind) {
                ActionKind.TORCH_TOGGLE, ActionKind.TORCH_ON, ActionKind.TORCH_OFF -> {
                    if (!Capabilities.permission(context, android.Manifest.permission.CAMERA)) { message.value = R.string.camera_permission; return }
                    val id = cameras.cameraIdList.firstOrNull { cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                        cameras.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
                    if (id == null || available[id] == false) { message.value = R.string.torch_unavailable; return }
                    cameras.setTorchMode(id, when (action.kind) { ActionKind.TORCH_ON -> true; ActionKind.TORCH_OFF -> false; else -> !(torch[id] ?: false) })
                    message.value = R.string.action_requested
                }
                ActionKind.APP -> {
                    if (context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) { message.value = R.string.unlock_required; return }
                    val intent = context.packageManager.getLaunchIntentForPackage(requireNotNull(action.packageName))
                    if (intent == null) { message.value = R.string.app_unavailable; return }
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); message.value = R.string.launch_requested
                }
                ActionKind.ACTIVITY -> { message.value = R.string.activity_unverified }
                ActionKind.STOCK_CAMERA_CAPTURE -> { message.value = R.string.camera_unverified }
                ActionKind.RECORD_TOGGLE -> {
                    if (RecorderService.recording.value) context.startService(Intent(context, RecorderService::class.java).setAction(RecorderService.STOP))
                    else if (visibleActivity && Capabilities.permission(context, android.Manifest.permission.RECORD_AUDIO))
                        context.startForegroundService(Intent(context, RecorderService::class.java).putExtra("seconds", action.durationSeconds))
                    else message.value = R.string.recorder_visible
                }
            }
        } catch (_: Exception) { message.value = R.string.action_failed }
        finally { mutex.unlock() }
    }
    /** Separate, honestly labelled preview: no shutter is fired or claimed. */
    fun openStockCamera(): Boolean = runCatching {
        val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val action = if (locked) MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE else MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
        val intent = Intent(action)
        val candidates = context.packageManager.queryIntentActivities(intent, 0).filter {
            (it.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        }
        // Require an unambiguous stock handler; never use an arbitrary third-party camera.
        val resolved = candidates.singleOrNull() ?: return false
        context.startActivity(intent.setComponent(ComponentName(resolved.activityInfo.packageName, resolved.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
