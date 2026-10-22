package dev.opluskeycontrol.input

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow

/** Read-only key observer plus an explicitly opened screen inspector. Never injects or consumes keys. */
class BasicInputService : AccessibilityService() {
    companion object {
        @Volatile private var instance: BasicInputService? = null
        fun showInspector(): Boolean = instance?.openInspector() == true
        val connected = MutableStateFlow(false)
        val events = MutableStateFlow<List<String>>(emptyList())
    }
    private var inspector: dev.opluskeycontrol.inspector.ScreenInspector? = null
    override fun onServiceConnected() { instance = this; connected.value = true; inspectorStopped() }
    private fun openInspector(): Boolean {
        serviceInfo = (serviceInfo ?: return false).apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        }
        val view = inspector ?: dev.opluskeycontrol.inspector.ScreenInspector(this).also { inspector = it }
        return view.show().also { if (!it) inspectorStopped() }
    }
    fun inspectorStopped() {
        serviceInfo = (serviceInfo ?: return).apply {
            flags = android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            eventTypes = 0
        }
    }
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode in setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN)) {
            val key = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) "VOLUME_UP" else "VOLUME_DOWN"
            val edge = if (event.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"
            events.value = (events.value + "$key $edge repeat=${event.repeatCount}").takeLast(12)
        }
        return false
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !dev.opluskeycontrol.inspector.ScreenInspector.showing.value) return
        inspector?.onWindowEvent(event.packageName?.toString().orEmpty(), event.className?.toString().orEmpty(),
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
    }
    override fun onInterrupt() { events.value = emptyList() }
    override fun onDestroy() { instance = null; inspector?.destroy(); inspector = null; connected.value = false; events.value = emptyList(); super.onDestroy() }
}
