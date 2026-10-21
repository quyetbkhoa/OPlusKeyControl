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
    val torchReady = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    private val mutex = Mutex()
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val torch = mutableMapOf<String, Boolean>()
    private val available = mutableMapOf<String, Boolean>()
    init {
        cameras.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(id: String, enabled: Boolean) { torch[id] = enabled; available[id] = true; torchReady.value = available.values.any { it } }
            override fun onTorchModeUnavailable(id: String) { available[id] = false; torchReady.value = available.values.any { it } }
        }, Handler(Looper.getMainLooper()))
    }
    suspend fun execute(action: Action, visibleActivity: Boolean = false, rootAuthorized: Boolean = false) {
        if (!mutex.tryLock()) { message.value = R.string.action_busy; return }
        busy.value = true
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
                    if (rootAuthorized) {
                        message.value = if (intent.component?.let { RootActivityLauncher.launch(context, it) } == true) R.string.launch_requested else R.string.action_failed
                    } else { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); message.value = R.string.launch_requested }
                }
                ActionKind.ACTIVITY -> {
                    val component = ComponentName(requireNotNull(action.packageName), requireNotNull(action.activity))
                    if (rootAuthorized) {
                        val success = RootActivityLauncher.launch(context, component)
                        message.value = if (success) R.string.launch_requested else R.string.action_failed
                        if (success && visibleActivity) ActionProofs.record(context, action)
                    }
                    else {
                        @Suppress("DEPRECATION")
                        val info = context.packageManager.getActivityInfo(component, 0)
                        if (!visibleActivity || !info.enabled || !info.applicationInfo.enabled || !info.exported || !info.permission.isNullOrBlank() ||
                            context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) { message.value = R.string.activity_unverified; return }
                        context.startActivity(Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        message.value = R.string.launch_requested
                    }
                }
                ActionKind.STOCK_CAMERA_CAPTURE -> {
                    if (!rootAuthorized || (!visibleActivity && !ActionProofs.verified(context, action))) { message.value = R.string.camera_unverified; return }
                    val success = StockCameraCapture(context).capture()
                    message.value = if (success) R.string.photo_confirmed else R.string.action_failed
                    if (success && visibleActivity) ActionProofs.record(context, action)
                }
                ActionKind.RECORD_TOGGLE -> {
                    if (RecorderService.recording.value) context.startService(Intent(context, RecorderService::class.java).setAction(RecorderService.STOP))
                    else if (visibleActivity && Capabilities.permission(context, android.Manifest.permission.RECORD_AUDIO))
                        context.startForegroundService(Intent(context, RecorderService::class.java).putExtra("seconds", action.durationSeconds))
                    else message.value = R.string.recorder_visible
                }
            }
        } catch (_: Exception) { message.value = R.string.action_failed }
        finally { mutex.unlock(); busy.value = false }
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
