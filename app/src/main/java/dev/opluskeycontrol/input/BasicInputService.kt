package dev.opluskeycontrol.input

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow

/** Read-only observer. No window content, key consumption or injection. */
class BasicInputService : AccessibilityService() {
    companion object {
        val connected = MutableStateFlow(false)
        val events = MutableStateFlow<List<String>>(emptyList())
    }
    override fun onServiceConnected() { connected.value = true }
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode in setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN)) {
            val key = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) "VOLUME_UP" else "VOLUME_DOWN"
            val edge = if (event.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"
            events.value = (events.value + "$key $edge repeat=${event.repeatCount}").takeLast(12)
        }
        return false
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { events.value = emptyList() }
    override fun onDestroy() { connected.value = false; events.value = emptyList(); super.onDestroy() }
}
