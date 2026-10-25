package dev.opluskeycontrol

import android.app.Application
import dev.opluskeycontrol.actions.ActionExecutor
import dev.opluskeycontrol.actions.ActionFeedback
import dev.opluskeycontrol.storage.RuleStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import android.app.Activity
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import dev.opluskeycontrol.input.PolicyBridge

class KeyControlApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val store by lazy { RuleStore(this, scope) }
    val feedback by lazy { ActionFeedback(this) { store.config.value.hapticFeedback } }
    val actions by lazy { ActionExecutor(this) { feedback.confirm() } }
    val bridge by lazy { PolicyBridge(this) }
    val visible = MutableStateFlow(false)
    val permissionRevision = MutableStateFlow(0)
    override fun onCreate() {
        super.onCreate(); store; bridge.start()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var resumed = 0
            override fun onActivityResumed(a: Activity) { resumed++; visible.value = true; bridge.refresh() }
            override fun onActivityPaused(a: Activity) { resumed = (resumed - 1).coerceAtLeast(0); visible.value = resumed > 0; bridge.refresh() }
            override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
            override fun onActivityStarted(a: Activity) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        })
    }
}
