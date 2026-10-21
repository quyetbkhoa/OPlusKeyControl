package dev.opluskeycontrol.actions

import android.content.Context
import android.os.Build
import dev.opluskeycontrol.domain.Action
import dev.opluskeycontrol.domain.ActionKind

/** Successful explicit manual target tests only. Replaced firmware/package invalidates proof.
 * Foreground recorder tests never establish background microphone eligibility.
 */
object ActionProofs {
    private fun key(context: Context, action: Action): String? = runCatching {
        val pkg = if (action.kind == ActionKind.STOCK_CAMERA_CAPTURE) "com.oplus.camera" else action.packageName ?: return null
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(pkg, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
        val cert = info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray() ?: return null
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(cert).joinToString("") { "%02x".format(it) }
        "${Build.DISPLAY}|$pkg|${info.longVersionCode}|${info.lastUpdateTime}|$digest|${action.kind}|${action.activity.orEmpty()}"
    }.getOrNull()
    fun verified(context: Context, action: Action) = key(context, action)?.let {
        context.getSharedPreferences("action_proofs", Context.MODE_PRIVATE).getBoolean(it, false)
    } ?: false
    fun record(context: Context, action: Action) { key(context, action)?.let {
        context.getSharedPreferences("action_proofs", Context.MODE_PRIVATE).edit().putBoolean(it, true).apply()
    } }
}
