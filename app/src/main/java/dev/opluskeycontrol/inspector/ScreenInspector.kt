package dev.opluskeycontrol.inspector

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.*
import dev.opluskeycontrol.KeyControlApp
import dev.opluskeycontrol.R
import dev.opluskeycontrol.domain.Tier
import dev.opluskeycontrol.input.PolicyBridge
import dev.opluskeycontrol.root.RootCommands
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** User-started accessibility overlay. No auto-save, screen shots, key injection or background polling. */
class ScreenInspector(private val service: AccessibilityService) {
    companion object { val showing = MutableStateFlow(false) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val wm = service.getSystemService(WindowManager::class.java)
    private var panel: LinearLayout? = null
    private var tree: TextView? = null
    private var status: TextView? = null
    private var scroll: ScrollView? = null
    private var save: android.widget.Button? = null
    private var capture: Job? = null
    private var frozen = false
    private var snapshot: String? = null
    private var eventPackage = ""
    private var eventClass = ""
    private var expanded = true
    private val params = WindowManager.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        android.graphics.PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 12; y = 160 }
    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()
    private fun text(value: Int) = service.getString(value)
    fun show(): Boolean {
        if (panel != null) return true
        return runCatching {
            params.width = minOf(service.resources.displayMetrics.widthPixels - dp(24), dp(360))
            val root = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(6), dp(10), dp(10))
                background = GradientDrawable().apply { setColor(Color.rgb(18, 26, 29)); cornerRadius = dp(16).toFloat(); setStroke(dp(1), Color.rgb(51, 207, 145)) }
                elevation = dp(8).toFloat()
            }
            val heading = TextView(service).apply { setText(R.string.inspector_title); setTextColor(Color.rgb(66, 230, 163)); textSize = 16f; minHeight = dp(48); gravity = Gravity.CENTER_VERTICAL }
            var startX = 0f; var startY = 0f; var originX = 0; var originY = 0
            heading.setOnTouchListener { _, event ->
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { startX = event.rawX; startY = event.rawY; originX = params.x; originY = params.y; true }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = (originX + event.rawX - startX).toInt().coerceIn(0, (service.resources.displayMetrics.widthPixels - params.width).coerceAtLeast(0))
                        params.y = (originY + event.rawY - startY).toInt().coerceIn(0, (service.resources.displayMetrics.heightPixels - dp(80)).coerceAtLeast(0))
                        runCatching { wm.updateViewLayout(root, params) }; true
                    }
                    MotionEvent.ACTION_UP -> { heading.performClick(); true }
                    else -> false
                }
            }
            heading.contentDescription = text(R.string.inspector_drag)
            root.addView(heading)
            status = TextView(service).apply { setTextColor(Color.LTGRAY); textSize = 12f; setText(R.string.inspector_loading) }.also(root::addView)
            fun row() = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL; root.addView(this) }
            fun button(row: LinearLayout, label: Int, action: () -> Unit): android.widget.Button = android.widget.Button(service).apply {
                setText(label); textSize = 12f; isAllCaps = false; minHeight = dp(48); minimumWidth = 0
                setTextColor(Color.WHITE); setOnClickListener { action() }
                row.addView(this, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            val first = row()
            button(first, R.string.inspector_refresh) { request(manual = true) }
            save = button(first, R.string.inspector_save) { saveSnapshot() }.apply { isEnabled = false }
            val pause = button(first, R.string.inspector_pause) { frozen = !frozen; if (!frozen) request(manual = true) }
            pause.setOnClickListener { frozen = !frozen; pause.setText(if(frozen) R.string.inspector_resume else R.string.inspector_pause); if(!frozen) request(manual = true) }
            val second = row()
            val toggle = button(second, R.string.inspector_collapse) { }
            toggle.setOnClickListener { expanded = !expanded; scroll?.visibility = if(expanded) View.VISIBLE else View.GONE; toggle.setText(if(expanded) R.string.inspector_collapse else R.string.inspector_expand) }
            button(second, R.string.inspector_close) { hide() }
            tree = TextView(service).apply { setTextColor(Color.WHITE); textSize = 11f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true); setPadding(dp(4), dp(8), dp(4), dp(8)) }
            scroll = ScrollView(service).apply { addView(tree); root.addView(this, LinearLayout.LayoutParams(-1, minOf(dp(280), service.resources.displayMetrics.heightPixels / 3))) }
            wm.addView(root, params); panel = root; showing.value = true
            request(manual = true)
        }.isSuccess.also { if (!it) { panel = null; showing.value = false } }
    }
    fun hide() {
        capture?.cancel(); capture = null; snapshot = null
        panel?.let { runCatching { wm.removeViewImmediate(it) } }
        panel = null; frozen = false; expanded = true; showing.value = false; tree = null; status = null; save = null; scroll = null
        (service as? dev.opluskeycontrol.input.BasicInputService)?.inspectorStopped()
    }
    fun destroy() { hide(); scope.cancel() }
    fun onWindowEvent(pkg: String, className: String, windowChanged: Boolean) {
        if (panel == null || pkg == service.packageName) return
        if (windowChanged) { eventPackage = pkg; eventClass = className }
        if (!frozen) request(manual = false)
    }
    private fun request(manual: Boolean) {
        if (panel == null) return
        // Coalesce event bursts without cancelling a running Binder tree walk.
        if (capture?.isActive == true) return
        capture = scope.launch {
            if (!manual) delay(350)
            status?.setText(R.string.inspector_loading); save?.isEnabled = false
            val pkgHint = eventPackage; val classHint = eventClass
            val result = withContext(Dispatchers.IO) { runCatching { snapshot(pkgHint, classHint) } }
            if (panel == null) return@launch
            snapshot = result.getOrNull()
            tree?.text = snapshot ?: text(R.string.inspector_empty)
            status?.text = if(snapshot != null) text(R.string.inspector_captured) else text(R.string.inspector_empty)
            save?.isEnabled = snapshot != null
        }
    }
    @Suppress("DEPRECATION")
    private fun snapshot(pkgHint: String, classHint: String): String {
        val roots = service.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isActive || it.isFocused) }
            .mapNotNull { it.root }.filter { if (it.packageName?.toString() == service.packageName) { it.recycle(); false } else true }
        val root = roots.firstOrNull() ?: service.rootInActiveWindow?.takeIf { it.packageName?.toString() != service.packageName }
            ?: error("No inspectable window")
        roots.drop(1).forEach { it.recycle() }
        val pkg = root.packageName?.toString().orEmpty()
        val app = service.applicationContext as KeyControlApp
        val component = if (app.store.config.value.tier == Tier.ROOT && PolicyBridge.connected.value)
            InspectorText.activity(RootCommands.run("dumpsys activity activities", 3), pkg) else null
        val output = StringBuilder("OPlusKeyControl UI snapshot\nCaptured: ${Instant.now()}\nPackage: $pkg\n")
        output.append("Activity (root): ${component ?: "unavailable"}\nWindow event class (may be a view): ${if(pkg == pkgHint) classHint else "unavailable"}\nPassword fields: redacted\n\n")
        var count = 0; var truncated = false; val start = SystemClock.uptimeMillis()
        fun visit(node: AccessibilityNodeInfo, depth: Int, secret: Boolean) {
            try {
                if (++count > 1500 || depth > 40 || output.length > 220_000 || SystemClock.uptimeMillis() - start > 1500) { truncated = true; return }
                val password = secret || node.isPassword; val bounds = Rect(); node.getBoundsInScreen(bounds)
                output.append("  ".repeat(depth)).append(node.className).append(" id=").append(node.viewIdResourceName ?: "-")
                    .append(" text=\"").append(InspectorText.field(node.text, password)).append("\" desc=\"")
                    .append(InspectorText.field(node.contentDescription, password)).append("\" bounds=").append(bounds.toShortString())
                    .append(" clickable=").append(node.isClickable).append(" enabled=").append(node.isEnabled).append(" visible=").append(node.isVisibleToUser).append('\n')
                for (i in 0 until node.childCount) { if (truncated) break; node.getChild(i)?.let { visit(it, depth + 1, password) } }
            } finally { node.recycle() }
        }
        visit(root, 0, false)
        if (truncated) output.append("\n[Truncated: tree size/depth/time limit]\n")
        return output.toString()
    }
    private fun saveSnapshot() {
        val captured = snapshot ?: return
        save?.isEnabled = false
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val name = "UI-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + ".txt"
                if (Build.VERSION.SDK_INT >= 29) {
                    val resolver = service.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download/OPlusKeyControl"); put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Create file")
                    try {
                        resolver.openOutputStream(uri, "w")?.use { it.write(captured.toByteArray(Charsets.UTF_8)) } ?: error("Open file")
                        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                    } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
                    "Download/OPlusKeyControl/$name"
                } else {
                    val dir = service.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: error("Storage unavailable")
                    File(dir, name).writeText(captured, Charsets.UTF_8); "${dir.absolutePath}/$name"
                }
            } }
            val feedback = result.fold({ text(R.string.inspector_saved) + "\n" + it }, { text(R.string.inspector_save_failed) })
            status?.text = feedback
            Toast.makeText(service, feedback, Toast.LENGTH_LONG).show()
            save?.isEnabled = snapshot != null
        }
    }
}
