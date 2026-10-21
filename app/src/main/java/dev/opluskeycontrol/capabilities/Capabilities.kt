package dev.opluskeycontrol.capabilities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.opluskeycontrol.domain.*
import dev.opluskeycontrol.R
import rikka.shizuku.Shizuku

enum class Support { SUPPORTED, PERMISSION, VERIFY, UNSUPPORTED }
data class Capability(val status: Support, val reason: Int)
object Capabilities {
    fun accessibility(context: Context): Boolean {
        val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return services.split(':').any { android.content.ComponentName.unflattenFromString(it)?.let { c ->
            c.packageName == context.packageName && c.className == "dev.opluskeycontrol.input.BasicInputService"
        } == true }
    }
    fun shizukuConnected() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    fun shizukuGranted() = runCatching { shizukuConnected() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
    fun requestShizuku() { if (shizukuConnected()) runCatching { Shizuku.requestPermission(10) } }
    fun permission(context: Context, name: String) = ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
    fun trigger(context: Context, tier: Tier, trigger: Trigger): Capability {
        if (tier == Tier.SHIZUKU) return Capability(Support.VERIFY, R.string.shizuku_limitation)
        if (tier == Tier.ROOT) return Capability(Support.VERIFY, R.string.root_limitation)
        if (trigger.button !in setOf(Button.VOLUME_UP, Button.VOLUME_DOWN) || trigger.gesture != Gesture.SINGLE)
            return Capability(Support.UNSUPPORTED, R.string.basic_limitation)
        // Public key filtering cannot defer single while guaranteeing OEM long
        // pass-through without replay. Replacement stays gated.
        return Capability(Support.VERIFY, R.string.basic_limitation)
    }
    fun action(context: Context, action: Action): Capability = when {
        action.kind == ActionKind.STOCK_CAMERA_CAPTURE -> Capability(Support.VERIFY, R.string.camera_unverified)
        action.kind == ActionKind.ACTIVITY -> Capability(Support.VERIFY, R.string.activity_unverified)
        action.kind in setOf(ActionKind.TORCH_ON, ActionKind.TORCH_OFF, ActionKind.TORCH_TOGGLE) && !permission(context, Manifest.permission.CAMERA) -> Capability(Support.PERMISSION, R.string.camera_permission)
        action.kind == ActionKind.RECORD_TOGGLE -> Capability(Support.PERMISSION, R.string.recorder_visible)
        else -> Capability(Support.SUPPORTED, R.string.action_available)
    }
}
