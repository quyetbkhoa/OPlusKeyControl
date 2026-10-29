package dev.opluskeycontrol.input

import android.app.Service
import android.content.Intent
import dev.opluskeycontrol.KeyControlApp

/** Private app endpoint; PolicyBridge checks calling UID on every transaction. */
class BackendKeeperService : Service() {
    override fun dump(fd: java.io.FileDescriptor, writer: java.io.PrintWriter, args: Array<out String>?) {
        writer.println((application as KeyControlApp).bridge.diagnosticSummary())
    }
    override fun onBind(intent: Intent?) = (application as KeyControlApp).bridge.binder
    override fun onUnbind(intent: Intent?): Boolean {
        (application as KeyControlApp).bridge.disconnect()
        return false
    }
}
