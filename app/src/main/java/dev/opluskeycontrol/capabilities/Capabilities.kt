package dev.opluskeycontrol.capabilities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.opluskeycontrol.domain.*
import dev.opluskeycontrol.R
import rikka.shizuku.Shizuku
import dev.opluskeycontrol.BuildConfig
import dev.opluskeycontrol.KeyControlApp
import dev.opluskeycontrol.actions.ActionProofs
import dev.opluskeycontrol.input.PolicyBridge

enum class Support { SUPPORTED, PERMISSION, VERIFY, UNSUPPORTED }
data class Capability(val status: Support, val reason: Int)
object Capabilities {
    // Rule preparation does not grant runtime authority to consume input.
    fun configurableTrigger(tier: Tier, trigger: Trigger): Boolean =
        BuildConfig.POLICY_RUNTIME && tier == Tier.ROOT &&
            ((trigger.button == Button.PLUS && trigger.gesture in setOf(Gesture.SINGLE, Gesture.DOUBLE, Gesture.LONG)) ||
                (BuildConfig.ADVANCED_INPUTS && (trigger == Trigger(Button.POWER, Gesture.DOUBLE) || trigger.isPlusChord() || trigger.isPowerVolumeChord())))
    fun featureFor(trigger: Trigger): Int = when {
        trigger == Trigger(Button.POWER, Gesture.DOUBLE) -> dev.opluskeycontrol.input.PolicyBinder.POWER_DOUBLE
        trigger.isPlusChord() -> dev.opluskeycontrol.input.PolicyBinder.PLUS_VOLUME
        trigger.isPowerVolumeChord() -> dev.opluskeycontrol.input.PolicyBinder.POWER_VOLUME
        else -> dev.opluskeycontrol.input.PolicyBinder.PLUS
    }
    fun accessibility(context: Context): Boolean {
        val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return services.split(':').any { android.content.ComponentName.unflattenFromString(it)?.let { c ->
            c.packageName == context.packageName && c.className == "dev.opluskeycontrol.input.BasicInputService"
        } == true }
    }
    fun shizukuConnected() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    fun shizukuGranted() = runCatching { shizukuConnected() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
    fun requestShizuku(): Boolean = shizukuConnected() && runCatching { Shizuku.requestPermission(10) }.isSuccess
    fun permission(context: Context, name: String) = ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
    fun trigger(context: Context, tier: Tier, trigger: Trigger): Capability {
        if (tier == Tier.SHIZUKU) return Capability(Support.VERIFY, R.string.shizuku_limitation)
        if (tier == Tier.ROOT) return when {
            configurableTrigger(tier, trigger) && PolicyBridge.connected.value && PolicyBridge.features.value and featureFor(trigger) != 0 ->
                Capability(Support.SUPPORTED, if (trigger.gesture == Gesture.CHORD) R.string.chord_plus_first else if (trigger.button == Button.POWER) R.string.power_double_experimental else R.string.root_full_coverage)
            configurableTrigger(tier, trigger) && PolicyBridge.connected.value -> Capability(Support.VERIFY, R.string.advanced_backend_missing)
            configurableTrigger(tier, trigger) -> Capability(Support.VERIFY, R.string.root_disconnected)
            trigger.button == Button.POWER -> Capability(Support.UNSUPPORTED, R.string.power_backend_missing)
            else -> Capability(Support.VERIFY, R.string.root_limitation)
        }
        if (trigger.button !in setOf(Button.VOLUME_UP, Button.VOLUME_DOWN) || trigger.gesture != Gesture.SINGLE)
            return Capability(Support.UNSUPPORTED, R.string.basic_limitation)
        // Public key filtering cannot defer single while guaranteeing OEM long
        // pass-through without replay. Replacement stays gated.
        return Capability(Support.VERIFY, R.string.basic_limitation)
    }
    fun action(context: Context, action: Action): Capability = when {
        action.kind == ActionKind.ACTIVITY -> if (ActionProofs.verified(context, action))
            Capability(Support.SUPPORTED, R.string.action_available) else Capability(Support.VERIFY, if (action.kind == ActionKind.ACTIVITY) R.string.activity_unverified else R.string.camera_unverified)
        action.kind in setOf(ActionKind.TORCH_ON, ActionKind.TORCH_OFF, ActionKind.TORCH_TOGGLE) && !permission(context, Manifest.permission.CAMERA) -> Capability(Support.PERMISSION, R.string.camera_permission)
        action.kind == ActionKind.RECORD_TOGGLE -> if (BuildConfig.POLICY_RUNTIME && (context.applicationContext as? KeyControlApp)?.store?.config?.value?.tier == Tier.ROOT &&
            permission(context, Manifest.permission.RECORD_AUDIO) && permission(context, Manifest.permission.POST_NOTIFICATIONS))
            Capability(Support.SUPPORTED, R.string.recorder_root_experimental) else Capability(Support.PERMISSION, R.string.recorder_visible)
        else -> Capability(Support.SUPPORTED, R.string.action_available)
    }
}
