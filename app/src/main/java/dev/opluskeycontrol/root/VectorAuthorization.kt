package dev.opluskeycontrol.root

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class RootAuthorizationState { INACTIVE, CHECKING, AUTHORIZED, UNAVAILABLE }

/** Event-driven root scope watcher. No installed daemon/script. Its stdin pipe is
 * a lifetime token: app death/uninstall closes it and ScopeWatchMain exits.
 * Invalidate BEFORE re-reading scope, including an unreadable database/framework.
 */
class VectorAuthorization(private val scope: CoroutineScope, private val apkPath: String) {
    val allowed = MutableStateFlow(false)
    val state = MutableStateFlow(RootAuthorizationState.INACTIVE)
    private fun checked(value: Boolean) {
        allowed.value = value
        state.value = if (value) RootAuthorizationState.AUTHORIZED else RootAuthorizationState.UNAVAILABLE
    }
    private var job: Job? = null
    @Volatile private var process: Process? = null
    private val epoch = java.util.concurrent.atomic.AtomicLong()
    fun start() {
        if (job?.isActive == true) return
        val token = epoch.incrementAndGet()
        state.value = RootAuthorizationState.CHECKING
        job = scope.launch(Dispatchers.IO) {
            var ownedProcess: Process? = null
            try {
                // The path comes from our own ApplicationInfo, never from rules or IPC.
                require(apkPath.startsWith("/data/app/") && apkPath.endsWith("/base.apk") &&
                    apkPath.matches(Regex("[A-Za-z0-9_./~=+\\-]+")))
                val script = "CLASSPATH='$apkPath' exec /system/bin/app_process /system/bin dev.opluskeycontrol.root.ScopeWatchMain"
                val p = ProcessBuilder("su", "-c", script).redirectErrorStream(true).start()
                ownedProcess = p
                ensureActive()
                if (epoch.get() != token) return@launch
                process = p
                p.inputStream.bufferedReader().use { lines ->
                    var ready = false
                    for (attempt in 0 until 32) {
                        val line = lines.readLine() ?: break
                        if (line == "READY") { ready = true; break }
                    }
                    if (!ready) return@use
                    if (epoch.get() == token) checked(check())
                    while (isActive) {
                        val line = lines.readLine() ?: break
                        if (epoch.get() != token) break
                        allowed.value = false
                        state.value = RootAuthorizationState.CHECKING
                        if (line != "CHANGED") continue
                        // A bounded debounce only after filesystem activity, never an idle timer.
                        delay(150)
                        var drained = 0
                        while (lines.ready() && drained++ < 128) {
                            if (lines.readLine() != "CHANGED") break
                        }
                        if (epoch.get() == token) checked(check())
                    }
                }
            } catch (_: Exception) { if (epoch.get() == token) allowed.value = false }
            finally {
                if (epoch.get() == token) checked(false)
                if (process === ownedProcess) process = null
                ownedProcess?.let { closeProcess(it) }
            }
        }
    }
    private fun check(): Boolean = runCatching {
        val modules = JSONObject(RootCommands.run("/data/adb/lspd/cli modules ls --json") ?: return false)
        val scopes = JSONObject(RootCommands.run("/data/adb/lspd/cli scope ls dev.opluskeycontrol --json") ?: return false)
        if (!modules.getBoolean("success") || !scopes.getBoolean("success")) return false
        val m = modules.getJSONArray("data")
        val enabled = (0 until m.length()).any { m.getJSONObject(it).let { row ->
            row.getString("PACKAGE") == "dev.opluskeycontrol" && row.getString("STATUS") == "enabled"
        } }
        val s = scopes.getJSONArray("data")
        enabled && (0 until s.length()).any { s.getJSONObject(it).let { row ->
            row.getString("APP_PACKAGE") == "system" && row.getInt("USER_ID") == 0
        } }
    }.getOrDefault(false)
    fun stop() {
        epoch.incrementAndGet(); allowed.value = false; job?.cancel(); job = null
        state.value = RootAuthorizationState.INACTIVE
        val p = process; process = null; p?.let { closeProcess(it) }
    }
    private fun closeProcess(p: Process) {
        // EOF is the root child's lifecycle signal; no detached child should survive.
        runCatching { p.outputStream.close() }
        runCatching { p.inputStream.close() }
        runCatching { if (!p.waitFor(2, TimeUnit.SECONDS)) p.destroy() }
    }
}
