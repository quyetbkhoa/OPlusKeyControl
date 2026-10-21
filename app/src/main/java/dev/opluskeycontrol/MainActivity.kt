package dev.opluskeycontrol

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import dev.opluskeycontrol.storage.RuleCodec
import dev.opluskeycontrol.ui.KeyControlScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val app get() = application as KeyControlApp
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) app.scope.launch {
            val data = RuleCodec.encode(app.store.config.value)
            val result = withContext(Dispatchers.IO) { runCatching { contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray()) } ?: error("stream") } }
            app.actions.message.value = if (result.isSuccess) R.string.backup_done else R.string.backup_error
        }
    }
    private val import = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) app.scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val text = contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 1_000_000)
                        output.write(buffer, 0, count)
                    }
                    output.toString("UTF-8")
                } ?: error("stream")
                RuleCodec.decode(text)
                text
            } }
            result.onSuccess { app.store.restore(it) }
            app.actions.message.value = if (result.isSuccess) R.string.restore_done else R.string.backup_error
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = if (Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            } else if (dark) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                KeyControlScreen(app, onPermissions = {
                    permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO) +
                        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray())
                }, onExport = { export.launch("OPlusKeyControl-rules.json") }, onImport = { import.launch(arrayOf("application/json", "text/plain")) })
            }
        }
    }
}
