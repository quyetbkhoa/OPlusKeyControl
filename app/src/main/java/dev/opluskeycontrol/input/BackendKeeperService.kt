package dev.opluskeycontrol.input

import android.app.Service
import android.content.Intent
import android.os.Binder

/** Bound by system_server only while an authenticated Root session is connected.
 * Empty binder: no privileged methods or execution surface. No idle foreground service.
 */
class BackendKeeperService : Service() {
    override fun onBind(intent: Intent?) = Binder()
}
