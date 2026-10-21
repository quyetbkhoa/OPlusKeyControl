package dev.opluskeycontrol.root

import java.util.concurrent.TimeUnit

/** Explicit opt-in identity probe only. No daemon, input grab, settings or launch. */
object RootIdentity {
    fun probe(): String {
        var process: Process? = null
        return try {
            process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            process.outputStream.close()
            if (!process.waitFor(8, TimeUnit.SECONDS)) { process.destroyForcibly(); "UNKNOWN (timeout)" }
            else if (process.exitValue() != 0) "UNKNOWN (denied)"
            else process.inputStream.bufferedReader().use { it.readText().take(256) }
        } catch (_: Exception) { "UNKNOWN" }
        finally { process?.destroy() }
    }
}
