package dev.opluskeycontrol.actions

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dev.opluskeycontrol.root.RootCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RootActivityLauncher {
    suspend fun launch(context: Context, component: ComponentName): Boolean = withContext(Dispatchers.IO) {
        // Root remains the caller; never impersonate the target UID/signature or cross a lockscreen.
        if (context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return@withContext false
        require(component.packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")))
        require(component.className.matches(Regex("[A-Za-z_][A-Za-z0-9_.$]*")))
        @Suppress("DEPRECATION")
        val info = context.packageManager.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
        if (!info.enabled || !info.applicationInfo.enabled || !info.permission.isNullOrBlank()) return@withContext false
        val token = component.flattenToString()
        // '$' is legal in a Java nested class; single quotes prevent shell expansion.
        val user = android.os.Process.myUid() / 100000
        val result = RootCommands.run("am start -W --user $user -n '$token'", 10) ?: return@withContext false
        result.lineSequence().any { it.trim() == "Status: ok" } && !result.contains("Error:")
    }
}
