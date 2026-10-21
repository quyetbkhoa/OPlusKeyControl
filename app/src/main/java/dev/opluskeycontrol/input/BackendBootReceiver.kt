package dev.opluskeycontrol.input

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.opluskeycontrol.KeyControlApp

/** Application initialization reloads the user's saved tier; never enables master/rules. */
class BackendBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED)
            (context.applicationContext as KeyControlApp).bridge.start()
    }
}
