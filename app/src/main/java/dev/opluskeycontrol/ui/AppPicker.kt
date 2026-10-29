@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.opluskeycontrol.ui

import android.content.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.core.graphics.drawable.toBitmap
import dev.opluskeycontrol.R
import dev.opluskeycontrol.discovery.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppPicker(activityMode: Boolean, allowActivitySelection: Boolean = false, onClose: () -> Unit, onSelect: (AppEntry, String?) -> Unit) {
    val context = LocalContext.current
    val backendConfig by (context.applicationContext as dev.opluskeycontrol.KeyControlApp).store.config.collectAsState()
    val catalog = remember { AppCatalog(context) }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var activities by remember { mutableStateOf<List<ActivityEntry>?>(null) }
    var selected by remember { mutableStateOf<AppEntry?>(null) }
    var query by remember { mutableStateOf("") }
    var system by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var launchable by remember { mutableStateOf<Set<String>>(emptySet()) }
    BackHandler { if (selected != null) { selected = null; query = "" } else onClose() }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { revision++ } }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package") }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(revision) { apps = null; error = false; runCatching {
        val entries = catalog.apps()
        launchable = withContext(Dispatchers.IO) { entries.filter { context.packageManager.getLaunchIntentForPackage(it.packageName) != null }.map { it.packageName }.toSet() }
        entries
    }.onSuccess { apps = it }.onFailure { error = true }; selected = null; activities = null }
    LaunchedEffect(selected) { activities = null; error = false; selected?.let { app -> runCatching { catalog.activities(app.packageName) }.onSuccess { activities = it }.onFailure { error = true; activities = emptyList() } } }
    Scaffold(topBar={OpAppBar(stringResource(if(selected!=null) R.string.choose_activity else if(activityMode) R.string.choose_activity_app else R.string.choose_app),
        onBack={if(selected!=null) {selected=null;query=""} else onClose()}) {
        TextButton(onClick={revision++}) {Text(stringResource(R.string.refresh))}
    }}) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(),contentAlignment=Alignment.TopCenter) {
            Column(Modifier.widthIn(max=840.dp).fillMaxSize().imePadding().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(selected!=null) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    AppIcon(catalog,selected!!.packageName)
                    Column(Modifier.weight(1f)) {
                        Text(selected!!.label,style=MaterialTheme.typography.titleMedium)
                        Text(selected!!.packageName,style=MaterialTheme.typography.bodySmall,color=OpColors.Muted)
                    }
                }
                OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text(stringResource(R.string.search_apps))},singleLine=true,shape=MaterialTheme.shapes.medium)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    if(selected==null) FilterChip(system,onClick={system=!system},label={Text(stringResource(R.string.include_system))})
                    else FilterChip(advanced,onClick={advanced=!advanced},label={Text(stringResource(R.string.advanced))})
                }
                if(selected!=null && advanced) Text(stringResource(R.string.activity_identity),style=MaterialTheme.typography.bodySmall,color=OpColors.Muted)
                if(error) Notice(stringResource(R.string.scan_failed))
                if(!error && (apps==null || (selected!=null && activities==null))) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(0.dp)) {
                    if(selected==null) {
                        val filtered=apps.orEmpty().filter {(activityMode || it.packageName in launchable) && (system || !it.system || it.packageName in launchable) && (it.label.contains(query,true) || it.packageName.contains(query,true))}
                        if(!error && apps!=null && filtered.isEmpty()) item { Text(stringResource(R.string.no_results),Modifier.padding(vertical=24.dp),color=OpColors.Muted) }
                        items(filtered,key={it.packageName}) { app ->
                            val choose={
                                if(activityMode) {selected=app;query=""}
                                else if(app.packageName in launchable) onSelect(app,null) else {selected=app;query=""}
                            }
                            Row(Modifier.fillMaxWidth().clickable(onClick=choose).heightIn(min=56.dp).padding(vertical=6.dp),
                                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                AppIcon(catalog,app.packageName)
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    Text(app.label,style=MaterialTheme.typography.titleMedium)

                                }
                                if(allowActivitySelection) TextButton(onClick={selected=app;query=""}) {Text(stringResource(R.string.ux_screens))} else Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null,tint=OpColors.Muted)
                            }
                            HorizontalDivider(color=OpColors.Border.copy(alpha=.6f))
                        }
                    } else {
                        val filtered=activities.orEmpty().filter {(advanced || it.status in setOf(Launchability.LAUNCHER,Launchability.EXPORTED_UNVERIFIED) ||
                            (it.status==Launchability.INTERNAL && backendConfig.tier==dev.opluskeycontrol.domain.Tier.ROOT)) &&
                            (it.label.contains(query,true) || it.name.contains(query,true))}
                        if(!error && activities!=null && filtered.isEmpty()) item { Text(stringResource(R.string.no_results),Modifier.padding(vertical=24.dp),color=OpColors.Muted) }
                        items(filtered,key={it.name}) { activity ->
                            val selectable=activity.status in setOf(Launchability.LAUNCHER,Launchability.EXPORTED_UNVERIFIED) ||
                                (activity.status==Launchability.INTERNAL && backendConfig.tier==dev.opluskeycontrol.domain.Tier.ROOT)
                            Column(Modifier.fillMaxWidth().clickable(enabled=selectable) {onSelect(selected!!,activity.name)}.heightIn(min=56.dp).padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Text(activity.label,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,color=if(selectable) MaterialTheme.colorScheme.onSurface else OpColors.Muted)
                                    if(selectable) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null,tint=OpColors.Muted)
                                }
                                if(advanced || filtered.count { it.label == activity.label } > 1) Text(if(advanced) activity.name else activity.name.substringAfterLast('.'),style=MaterialTheme.typography.bodySmall,color=OpColors.Muted)
                                if(advanced || !selectable) StatusChip(stringResource(activity.status.label()),when(activity.status) {
                                    Launchability.LAUNCHER -> OpColors.Success
                                    Launchability.EXPORTED_UNVERIFIED -> OpColors.Accent
                                    Launchability.INTERNAL -> OpColors.Amber
                                    Launchability.PROTECTED -> OpColors.Red
                                    Launchability.DISABLED -> OpColors.Muted
                                })
                                if(advanced) {
                                    Text(stringResource(R.string.exported_value,activity.exported.toString()),style=MaterialTheme.typography.bodySmall)
                                    Text(stringResource(R.string.current_profile_only),style=MaterialTheme.typography.bodySmall)
                                }
                            }
                            HorizontalDivider(color=OpColors.Border.copy(alpha=.6f))
                        }
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
    if (icon != null) Image(icon!!, contentDescription = null, modifier = Modifier.size(32.dp)) else IconTile("apps", OpColors.Blue, 32.dp)
}
