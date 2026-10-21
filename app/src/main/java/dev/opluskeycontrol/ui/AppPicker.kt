@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.opluskeycontrol.ui

import android.content.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.opluskeycontrol.R
import dev.opluskeycontrol.discovery.*
import kotlinx.coroutines.launch

@Composable
fun AppPicker(activityMode: Boolean, onClose: () -> Unit, onSelect: (AppEntry, String?) -> Unit) {
    val context = LocalContext.current
    val catalog = remember { AppCatalog(context) }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var activities by remember { mutableStateOf<List<ActivityEntry>?>(null) }
    var selected by remember { mutableStateOf<AppEntry?>(null) }
    var query by remember { mutableStateOf("") }
    var system by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { revision++ } }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package") }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(revision) { apps = null; error = false; runCatching { catalog.apps() }.onSuccess { apps = it }.onFailure { error = true }; selected = null; activities = null }
    LaunchedEffect(selected) { activities = null; selected?.let { app -> runCatching { catalog.activities(app.packageName) }.onSuccess { activities = it }.onFailure { error = true } } }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(if (selected != null) R.string.choose_activity else R.string.choose_app)) }, navigationIcon = { TextButton(onClick = { if (selected != null) { selected = null; query = "" } else onClose() }) { Text(stringResource(R.string.back)) } }, actions = { TextButton(onClick = { revision++ }) { Text(stringResource(R.string.refresh)) } }) }) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.search_apps)) }, singleLine = true)
            if (selected == null) FilterChip(system, onClick = { system = !system }, label = { Text(stringResource(R.string.include_system)) })
            else {
                Text(selected!!.label, style = MaterialTheme.typography.titleLarge)
                FilterChip(advanced, onClick = { advanced = !advanced }, label = { Text(stringResource(R.string.advanced)) })
                Notice(stringResource(R.string.activity_identity))
            }
            if (error) Notice(stringResource(R.string.scan_failed))
            if (apps == null || (selected != null && activities == null)) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selected == null) {
                    val filtered = apps.orEmpty().filter { (system || !it.system) && it.label.contains(query, true) }
                    if (apps != null && filtered.isEmpty()) item { Text(stringResource(R.string.no_results)) }
                    items(filtered, key = { it.packageName }) { app ->
                        ListItem(headlineContent = { Text(app.label) }, leadingContent = { AppIcon(catalog, app.packageName) },
                            trailingContent = { TextButton(onClick = {
                                if (activityMode) { selected = app; query = "" }
                                else if (context.packageManager.getLaunchIntentForPackage(app.packageName) != null) onSelect(app, null) else error = true
                            }) { Text(stringResource(R.string.select)) } })
                    }
                } else {
                    val filtered = activities.orEmpty().filter { (advanced || it.status == Launchability.LAUNCHER) && (it.label.contains(query, true) || it.name.contains(query, true)) }
                    if (activities != null && filtered.isEmpty()) item { Text(stringResource(R.string.no_results)) }
                    items(filtered, key = { it.name }) { activity ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(activity.label, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(activity.status.label()))
                            // Technical details intentionally read-only. No arbitrary intent/shell input.
                            Text(activity.name, style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.exported_value, activity.exported.toString()))
                            Text(stringResource(R.string.current_profile_only), style = MaterialTheme.typography.bodySmall)
                            Button(enabled = activity.status == Launchability.LAUNCHER, onClick = { onSelect(selected!!, activity.name) }) { Text(stringResource(R.string.select)) }
                        } }
                    }
                }
            }
        }
    }
}
@Composable private fun AppIcon(catalog: AppCatalog, packageName: String) {
    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, packageName) {
        value = catalog.icon(packageName)?.toBitmap(96, 96)?.asImageBitmap()
    }
    if (icon != null) Image(icon!!, contentDescription = null, modifier = Modifier.size(40.dp)) else Spacer(Modifier.size(40.dp))
}
