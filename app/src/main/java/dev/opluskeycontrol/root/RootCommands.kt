package dev.opluskeycontrol.root

import java.util.concurrent.TimeUnit

/** Callers supply only fixed commands or strictly validated ComponentName tokens. */
object RootCommands {
    fun run(command: String, seconds: Long = 8): String? {
        val p = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val bytes = java.io.ByteArrayOutputStream()
        val reader = Thread({ runCatching {
            p.inputStream.use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    val keep = minOf(n, 65536 - bytes.size())
                    if (keep > 0) bytes.write(buffer, 0, keep)
                }
            }
        } }, "OPlusKeyControl-root-output").apply { isDaemon = true; start() }
        try {
            p.outputStream.close()
            if (!p.waitFor(seconds, TimeUnit.SECONDS)) return null
            if (p.exitValue() != 0) return null
            reader.join(1000)
            if (reader.isAlive) return null
            return bytes.toString(Charsets.UTF_8.name())
        } finally { p.destroy(); runCatching { p.inputStream.close() } }
    }
}
