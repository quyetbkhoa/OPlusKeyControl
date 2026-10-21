package dev.opluskeycontrol

import android.app.Application
import dev.opluskeycontrol.actions.ActionExecutor
import dev.opluskeycontrol.storage.RuleStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class KeyControlApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val store by lazy { RuleStore(this, scope) }
    val actions by lazy { ActionExecutor(this) }
    override fun onCreate() { super.onCreate(); store }
}
