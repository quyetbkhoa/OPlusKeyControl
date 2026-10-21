package dev.opluskeycontrol.root

import android.os.FileObserver
import android.os.Process
import kotlin.system.exitProcess

/** Transient root child, launched only while the experimental Root tier is chosen.
 * One inotify observer, blocking stdin lifetime pipe, no timer or child processes.
 * Process death/uninstall closes stdin; exitProcess terminates the native observer too.
 */
object ScopeWatchMain {
    @JvmStatic fun main(args: Array<String>) {
        if (Process.myUid() != 0 || args.isNotEmpty()) exitProcess(1)
        @Suppress("DEPRECATION")
        val observer = object : FileObserver("/data/adb/lspd/config", MODIFY or CLOSE_WRITE or CREATE or DELETE or
            MOVED_TO or MOVED_FROM or DELETE_SELF or MOVE_SELF) {
            override fun onEvent(event: Int, path: String?) {
                if (path == "modules_config.db" || path == "modules_config.db-wal" || path == null) {
                    System.out.println("CHANGED"); System.out.flush()
                }
            }
        }
        try {
            check(java.io.File("/data/adb/lspd/config/modules_config.db").canRead())
            observer.startWatching()
            println("READY"); System.out.flush()
            while (System.`in`.read() != -1) { /* stdin is only a lifetime token */ }
        } finally { observer.stopWatching(); exitProcess(0) }
    }
}
