package dev.opluskeycontrol.discovery

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val packageName: String, val label: String, val system: Boolean)
enum class Launchability { LAUNCHER, EXPORTED_UNVERIFIED, PROTECTED, INTERNAL, DISABLED }
data class ActivityEntry(val packageName: String, val name: String, val label: String, val status: Launchability, val exported: Boolean, val permission: String?)
class AppCatalog(private val context: Context) {
    suspend fun apps(): List<AppEntry> = withContext(Dispatchers.IO) {
        context.packageManager.getInstalledApplications(0).map { AppEntry(it.packageName, it.loadLabel(context.packageManager).toString(), it.flags and ApplicationInfo.FLAG_SYSTEM != 0) }.sortedBy { it.label.lowercase() }
    }
    suspend fun activities(packageName: String): List<ActivityEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcher = pm.getLaunchIntentForPackage(packageName)?.component
        @Suppress("DEPRECATION")
        val info = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS)
        info.activities.orEmpty().map { a ->
            ActivityEntry(packageName, a.name, a.loadLabel(pm).toString().ifBlank { a.name.substringAfterLast('.') }, when {
                !a.enabled || !a.applicationInfo.enabled -> Launchability.DISABLED
                !a.exported -> Launchability.INTERNAL
                !a.permission.isNullOrBlank() -> Launchability.PROTECTED
                launcher == ComponentName(packageName, a.name) -> Launchability.LAUNCHER
                else -> Launchability.EXPORTED_UNVERIFIED
            }, a.exported, a.permission)
        }.sortedWith(compareBy<ActivityEntry> { when (it.status) {
            Launchability.LAUNCHER -> 0
            Launchability.EXPORTED_UNVERIFIED -> 1
            Launchability.INTERNAL -> 2
            else -> 3
        } }.thenBy { it.label.lowercase() })
    }
    suspend fun icon(packageName: String): Drawable? = withContext(Dispatchers.IO) { runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull() }
}
