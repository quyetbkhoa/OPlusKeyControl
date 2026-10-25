package dev.opluskeycontrol.actions

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager

/** One short confirmation after an accepted action; follows the device's haptic policy. */
class ActionFeedback(private val context: Context, private val enabled: () -> Boolean) {
    @Suppress("DEPRECATION")
    fun confirm() {
        if (!enabled()) return
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= 31)
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else context.getSystemService(Vibrator::class.java)
            if (vibrator?.hasVibrator() != true) return
            val effect = VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)
            if (Build.VERSION.SDK_INT >= 33) vibrator.vibrate(effect,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK))
            else vibrator.vibrate(effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        }
    }
}
