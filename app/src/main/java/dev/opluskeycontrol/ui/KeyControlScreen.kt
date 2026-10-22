@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.opluskeycontrol.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.opluskeycontrol.KeyControlApp
import dev.opluskeycontrol.R
import dev.opluskeycontrol.actions.RecorderService
import dev.opluskeycontrol.capabilities.*
import dev.opluskeycontrol.domain.*
import dev.opluskeycontrol.input.BasicInputService
import dev.opluskeycontrol.storage.Configuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

@Composable
fun KeyControlScreen(app: KeyControlApp, onPermissions: () -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    val config by app.store.config.collectAsStateWithLifecycle()
    val recording by RecorderService.recording.collectAsStateWithLifecycle()
    val recordError by RecorderService.error.collectAsStateWithLifecycle()
    val storeError by app.store.error.collectAsStateWithLifecycle()
    val emergency by app.store.emergency.collectAsStateWithLifecycle()
    val connected by BasicInputService.connected.collectAsStateWithLifecycle()
    val rootConnected by dev.opluskeycontrol.input.PolicyBridge.connected.collectAsStateWithLifecycle()
    val backendFeatures by dev.opluskeycontrol.input.PolicyBridge.features.collectAsStateWithLifecycle()
    val rootState by app.bridge.authorizationState.collectAsStateWithLifecycle()
    val permissionRevision by app.permissionRevision.collectAsStateWithLifecycle()
    val events by BasicInputService.events.collectAsStateWithLifecycle()
    val message by app.actions.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Rule?>(null) }
    var editor by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var shizukuRevision by remember { mutableIntStateOf(0) }
    var shizukuRequesting by remember { mutableStateOf(false) }
    var shizukuDenied by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val received = rikka.shizuku.Shizuku.OnBinderReceivedListener { scope.launch { shizukuRevision++ } }
        val died = rikka.shizuku.Shizuku.OnBinderDeadListener { scope.launch { shizukuRevision++ } }
        val result = rikka.shizuku.Shizuku.OnRequestPermissionResultListener { _, grant -> scope.launch {
            shizukuRequesting = false; shizukuDenied = grant != android.content.pm.PackageManager.PERMISSION_GRANTED; shizukuRevision++
        } }
        rikka.shizuku.Shizuku.addBinderReceivedListenerSticky(received)
        rikka.shizuku.Shizuku.addBinderDeadListener(died)
        rikka.shizuku.Shizuku.addRequestPermissionResultListener(result)
        onDispose {
            rikka.shizuku.Shizuku.removeBinderReceivedListener(received)
            rikka.shizuku.Shizuku.removeBinderDeadListener(died)
            rikka.shizuku.Shizuku.removeRequestPermissionResultListener(result)
        }
    }
    val shizukuConnected = remember(refresh, shizukuRevision) { Capabilities.shizukuConnected() }
    val shizukuGranted = remember(refresh, shizukuRevision) { Capabilities.shizukuGranted() }
    val batteryExempt = remember(refresh) { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }
    val cameraGranted = remember(refresh, permissionRevision) { Capabilities.permission(context, android.Manifest.permission.CAMERA) }
    val micGranted = remember(refresh, permissionRevision) { Capabilities.permission(context, android.Manifest.permission.RECORD_AUDIO) }
    val notificationsGranted = remember(refresh, permissionRevision) { Build.VERSION.SDK_INT < 33 || Capabilities.permission(context, android.Manifest.permission.POST_NOTIFICATIONS) }
    val overviewScroll = rememberLazyListState()
    val mappingsScroll = rememberLazyListState()
    val settingsScroll = rememberLazyListState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var settingsPage by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("root") }
    var buttonFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(1) }
    var deleting by remember { mutableStateOf<Rule?>(null) }
    var rootIdentity by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settingsPage) { settingsScroll.scrollToItem(0) }
    val now = remember(refresh) { ZonedDateTime.now() }
    val labels = listOf(R.string.nd_keys, R.string.nd_tools)
    val navIcons = listOf(Icons.Outlined.Home, Icons.Outlined.Settings)
    val subpage = tab == 1 && settingsPage != "root"
    androidx.activity.compose.BackHandler(enabled = subpage || tab != 0) { if (subpage) settingsPage = "root" else tab = 0 }
    val openAppSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName))) }
    val titles = mapOf("permissions" to R.string.ui_connection, "battery" to R.string.ui_battery, "recordings" to R.string.recordings,
        "timing" to R.string.timings, "backup" to R.string.backup, "tests" to R.string.action_tests,
        "inspector" to R.string.inspector_title, "diagnostics" to R.string.diagnostics, "about" to R.string.ui_about)
    if (editor) {
        RuleEditor(config, editing, onClose = { editor = false }, onSave = { rule ->
            app.store.mutate { it.copy(rules = it.rules.filterNot { old -> old.id == rule.id } + rule) }; editor = false
        })
        return
    }
    deleting?.let { rule -> AlertDialog(onDismissRequest = { deleting = null },
        title = { Text(stringResource(R.string.ui_delete_rule)) }, text = { Text(triggerTitle(rule.trigger)) },
        confirmButton = { TextButton(onClick = { app.store.mutate { c -> c.copy(rules = c.rules.filterNot { it.id == rule.id }) }; deleting = null }) { Text(stringResource(R.string.delete), color = OpColors.Red) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 720.dp
        Row(Modifier.fillMaxSize()) {
            if (expanded && !subpage) NavigationRail(containerColor = OpColors.Background) {
                Spacer(Modifier.height(24.dp))
                labels.forEachIndexed { index, label -> NavigationRailItem(selected = tab == index, onClick = { tab = index },
                    icon = { if(index==0) OpGlyph("plus",if(tab==index) OpColors.Accent else OpColors.Muted) else Icon(navIcons[index],null) }, label = { Text(stringResource(label)) }) }
            }
            Scaffold(modifier = Modifier.weight(1f),
                topBar = { OpAppBar(stringResource(if(subpage) titles.getValue(settingsPage) else R.string.app_name),
                    onBack = if(subpage) ({ settingsPage = "root" }) else null) {
                    if (!subpage) TextButton(onClick = { app.store.disable() }) {
                        Text(stringResource(R.string.disable),color=OpColors.Red)
                    }
                } },
                bottomBar = { if (!subpage && !expanded) NavigationBar(containerColor = OpColors.Background, tonalElevation = 0.dp) {
                    labels.forEachIndexed { index, label -> NavigationBarItem(selected = tab == index, onClick = { tab = index },
                        icon = { if(index==0) OpGlyph("plus",if(tab==index) OpColors.Accent else OpColors.Muted) else Icon(navIcons[index],null) }, label = { Text(stringResource(label)) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = OpColors.Accent, selectedTextColor = OpColors.Accent,
                            indicatorColor = OpColors.Accent.copy(alpha=.12f), unselectedIconColor = OpColors.Muted, unselectedTextColor = OpColors.Muted)) }
                } }
            ) { insets ->
                Box(Modifier.padding(insets).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(Modifier.widthIn(max=840.dp).fillMaxSize(), state = if(tab == 0) mappingsScroll else settingsScroll,
                        contentPadding = PaddingValues(start=20.dp,end=20.dp,top=8.dp,bottom=24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (recording) item { OpCard(selected = true, tint = OpColors.Red) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OpGlyph("mic", OpColors.Red)
                                Text(stringResource(R.string.recording), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Button(onClick = { context.startService(Intent(context, RecorderService::class.java).setAction(RecorderService.STOP)) },
                                    colors = ButtonDefaults.buttonColors(containerColor = OpColors.Red)) { Text(stringResource(R.string.stop)) }
                            }
                        } }
                        if (message != null) item { OpCard { Text(stringResource(message!!)); TextButton(onClick = { app.actions.message.value = null }) { Text(stringResource(R.string.dismiss)) } } }
                        if (recordError) item { Notice(stringResource(R.string.record_error)) }
                        if (storeError) item { Notice(stringResource(R.string.storage_error)) }
                        if (tab == 0) {
                            item {
                                Text(stringResource(R.string.nd_keys_title), style = MaterialTheme.typography.headlineMedium)
                                Text(stringResource(R.string.nd_rules_count, config.rules.count { it.enabled }, config.rules.size),
                                    color = OpColors.Muted, style = MaterialTheme.typography.bodyMedium)
                            }
                            item { OpCard {
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(R.string.nd_master),style=MaterialTheme.typography.titleMedium)
                                        Row(Modifier.fillMaxWidth().clickable {tab=1;settingsPage="permissions"}.heightIn(min=48.dp),
                                            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                            val ready=when(config.tier) {Tier.ROOT->rootConnected;Tier.BASIC->connected;Tier.SHIZUKU->shizukuGranted}
                                            Column(Modifier.weight(1f)) {
                                                Text(stringResource(config.tier.label()),style=MaterialTheme.typography.bodySmall,color=OpColors.Muted)
                                                Text(stringResource(if(ready) R.string.nd_connected else R.string.nd_check_connection),
                                                    color=if(ready) OpColors.Success else OpColors.Amber,style=MaterialTheme.typography.bodySmall)
                                            }
                                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null,tint=OpColors.Muted,modifier=Modifier.size(18.dp))
                                        }
                                    }
                                    Column(horizontalAlignment=Alignment.End) {
                                        Switch(config.enabled && !emergency,onCheckedChange=app.store::setEnabled)
                                        Text(stringResource(if(config.enabled && !emergency) R.string.enabled else R.string.disabled),
                                            color=if(config.enabled && !emergency) OpColors.Accent else OpColors.Muted,style=MaterialTheme.typography.bodySmall)
                                    }
                                }
                            } }
                            item { FlowRow(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                listOf(R.string.ui_all,R.string.plus,R.string.power,R.string.ui_volume,R.string.nd_chords).forEachIndexed { index,label ->
                                    FilterChip(buttonFilter==index,onClick={buttonFilter=index},label={if(index==0) Icon(Icons.AutoMirrored.Outlined.List,stringResource(label),modifier=Modifier.size(18.dp)) else Text(stringResource(label))})
                                }
                            } }
                            item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                Text(stringResource(R.string.nd_mappings),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                                FilledTonalButton(onClick={ editing=null;editor=true },modifier=Modifier.heightIn(min=48.dp)) {
                                    Icon(Icons.Outlined.Add,null,modifier=Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.nd_add))
                                }
                            } }
                            val filtered=config.rules.filter { when(buttonFilter) {
                                1 -> it.trigger.button==Button.PLUS && it.trigger.gesture!=Gesture.CHORD
                                2 -> it.trigger.button==Button.POWER && it.trigger.gesture!=Gesture.CHORD
                                3 -> it.trigger.button in setOf(Button.VOLUME_UP,Button.VOLUME_DOWN) && it.trigger.gesture!=Gesture.CHORD
                                4 -> it.trigger.gesture==Gesture.CHORD
                                else -> true
                            } }
                            if(filtered.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                OpGlyph("plus",OpColors.Muted,32.dp)
                                Text(stringResource(R.string.no_rules),style=MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.nd_empty_hint),color=OpColors.Muted,style=MaterialTheme.typography.bodyMedium)
                            } }
                            val groups=filtered.partition { it.enabled && Capabilities.configurableTrigger(config.tier,it.trigger) }
                            listOf(groups.first to R.string.ui_enabled_rules,groups.second to R.string.ui_draft_rules).forEach { (rules,title) ->
                                if(rules.isNotEmpty()) item { SectionTitle(stringResource(title,rules.size)) }
                                items(rules,key={it.id}) { rule ->
                                    val status=ruleStatus(rule,config,rootConnected,emergency,now,context,backendFeatures)
                                    var menu by remember { mutableStateOf(false) }
                                    val canEnable=(Capabilities.configurableTrigger(config.tier,rule.trigger) || Capabilities.trigger(context,config.tier,rule.trigger).status==Support.SUPPORTED) && Capabilities.action(context,rule.action).status==Support.SUPPORTED
                                    OpCard {
                                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                            Column(Modifier.weight(1f).clickable { editing=rule;editor=true },verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                                Text(triggerTitle(rule.trigger),color=OpColors.Accent,style=MaterialTheme.typography.labelMedium)
                                                Text(stringResource(rule.action.kind.label()),style=MaterialTheme.typography.titleMedium)
                                                rule.action.packageName?.let { Text(it,color=OpColors.Muted,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis) }
                                                ScheduleText(rule.schedule)
                                                StatusChip(stringResource(status.first),status.second)
                                            }
                                            Column(horizontalAlignment=Alignment.End) {
                                                Switch(rule.enabled,enabled=rule.enabled || canEnable,onCheckedChange={checked ->
                                                    if(checked && RuleResolver.conflicts(rule.copy(enabled=true),config.rules)) app.actions.message.value=R.string.conflict
                                                    else app.store.mutate { c -> c.copy(rules=c.rules.map { if(it.id==rule.id) it.copy(enabled=checked) else it }) }
                                                })
                                                Box {
                                                    IconButton(onClick={menu=true}) { Icon(Icons.Outlined.MoreVert,stringResource(R.string.ui_rule_options),tint=OpColors.Muted) }
                                                    DropdownMenu(menu,onDismissRequest={menu=false}) {
                                                        DropdownMenuItem(text={Text(stringResource(R.string.edit))},onClick={editing=rule;editor=true;menu=false})
                                                        DropdownMenuItem(text={Text(stringResource(R.string.delete),color=OpColors.Red)},onClick={deleting=rule;menu=false})
                                                    }
                                                }
                                            }
                                        }
                                        if(!canEnable && !rule.enabled) Text(stringResource(if(!Capabilities.configurableTrigger(config.tier,rule.trigger)) Capabilities.trigger(context,config.tier,rule.trigger).reason else Capabilities.action(context,rule.action).reason),style=MaterialTheme.typography.bodySmall,color=OpColors.Muted)
                                    }
                                }
                            }
                            item { TextButton(onClick={refresh++}) { Icon(Icons.Outlined.Refresh,null,modifier=Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.refresh)) } }
                            item { InfoCard(R.string.ui_plus_help,R.string.root_full_coverage) }
                            if(!batteryExempt) item { Notice(stringResource(R.string.ui_battery_hint));TextButton(onClick={tab=1;settingsPage="battery"}) {Text(stringResource(R.string.ui_battery))} }
                        } else when(settingsPage) {
                            "root" -> {
                                item { Text(stringResource(R.string.nd_tools_title),style=MaterialTheme.typography.headlineMedium) }
                                val groups=listOf(
                                    R.string.nd_setup to listOf(Triple("permissions",R.string.ui_connection,"shield"),Triple("battery",R.string.ui_battery,"leaf"),Triple("timing",R.string.timings,"clock")),
                                    R.string.nd_data to listOf(Triple("recordings",R.string.recordings,"file"),Triple("backup",R.string.backup,"file")),
                                    R.string.nd_utilities to listOf(Triple("tests",R.string.action_tests,"power"),Triple("inspector",R.string.inspector_title,"apps"),Triple("diagnostics",R.string.diagnostics,"pulse")),
                                    R.string.nd_app to listOf(Triple("about",R.string.ui_about,"plus")))
                                groups.forEach { (heading,pages) ->
                                    item { SectionTitle(stringResource(heading)) }
                                    item { OpCard { pages.forEachIndexed { index,(page,title,glyph) ->
                                        if(index>0) HorizontalDivider(color=OpColors.Border)
                                        OpRow(stringResource(title),when(page) {
                                            "permissions" -> stringResource(config.tier.label())
                                            "battery" -> stringResource(if(batteryExempt) R.string.battery_exempt else R.string.battery_limited)
                                            "timing" -> stringResource(R.string.ui_timing_summary,config.timing.doubleMs,config.timing.longMs)
                                            else -> null
                                        },glyph,OpColors.Muted,onClick={settingsPage=page})
                                    } } }
                                }
                            }
                    "permissions" -> {
                        item { OpCard {
                            Text(stringResource(R.string.nd_mode),style=MaterialTheme.typography.titleMedium)
                            Tier.entries.forEach { tier ->
                                Row(Modifier.fillMaxWidth().clickable { if(config.tier!=tier) { app.store.disable();app.store.mutate {it.copy(tier=tier,enabled=false)} } }.heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically) {
                                    RadioButton(config.tier==tier,onClick={if(config.tier!=tier) {app.store.disable();app.store.mutate {it.copy(tier=tier,enabled=false)}}})
                                    Text(stringResource(tier.label()),Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                                }
                            }
                            HorizontalDivider(color=OpColors.Border)
                            Text(stringResource(R.string.nd_actual_status),style=MaterialTheme.typography.titleMedium)
                            BackendStatus(config.tier, connected, rootConnected, rootState, shizukuConnected, shizukuGranted)
                            if (!config.enabled || emergency) Text(stringResource(R.string.remap_off_after_mode_change), style = MaterialTheme.typography.bodySmall, color = OpColors.Muted)
                            when(config.tier) {
                                Tier.ROOT -> OutlinedButton(onClick = { app.bridge.retryAuthorization() }) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.reconnect_backend)) }
                                Tier.BASIC -> OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text(stringResource(R.string.enable_accessibility)) }
                                Tier.SHIZUKU -> {
                                    if (shizukuRequesting) Text(stringResource(R.string.shizuku_requesting))
                                    if (shizukuDenied && !shizukuGranted) Text(stringResource(R.string.shizuku_denied), color = OpColors.Red)
                                    OutlinedButton(enabled = shizukuConnected && !shizukuGranted && !shizukuRequesting, onClick = { shizukuRequesting = Capabilities.requestShizuku(); shizukuDenied = !shizukuRequesting; refresh++ }) { Text(stringResource(R.string.request_shizuku)) }
                                }
                            }
                        } }
                        item { OpCard {
                            SectionTitle(stringResource(R.string.permissions))
                            PermissionLine(R.string.ui_camera, "camera", cameraGranted, onPermissions)
                            HorizontalDivider(color = OpColors.Border)
                            PermissionLine(R.string.ui_microphone, "mic", micGranted, onPermissions)
                            HorizontalDivider(color = OpColors.Border)
                            PermissionLine(R.string.ui_notifications, "file", notificationsGranted, onPermissions)
                            TextButton(onClick = openAppSettings) { Text(stringResource(R.string.app_settings)) }
                        } }
                    }
                    "battery" -> {
                        item { OpCard(selected = batteryExempt, tint = if(batteryExempt) OpColors.Mint else OpColors.Amber) {
                            OpRow(stringResource(if(batteryExempt) R.string.battery_exempt else R.string.battery_limited), glyph = "leaf", tint = if(batteryExempt) OpColors.Mint else OpColors.Amber)
                            if (!batteryExempt) Button(onClick = {
                                runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:" + context.packageName))) }
                                    .onFailure { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                            }) { Text(stringResource(R.string.battery_allow)) }
                        } }
                        item { OpCard { OpRow(stringResource(R.string.ui_coloros_battery), stringResource(R.string.ui_battery_hint), "leaf", OpColors.Blue); OutlinedButton(onClick = openAppSettings) { Text(stringResource(R.string.battery_app_settings)) } } }
                        item { InfoCard(R.string.show_details, R.string.battery_help) }
                    }
                    "recordings" -> item { RecordingHistory() }
                    "timing" -> item { TimingSettings(config) { value -> app.store.mutate { it.copy(timing = value) } } }
                    "backup" -> {
                        item { OpCard {
                            OpRow(stringResource(R.string.export_rules), stringResource(R.string.ui_json_hint), "file", OpColors.Purple, onClick = onExport)
                            HorizontalDivider(color = OpColors.Border)
                            OpRow(stringResource(R.string.import_rules), stringResource(R.string.ui_import_hint), "file", OpColors.Blue, onClick = onImport)
                        } }
                        item { Notice(stringResource(R.string.backup_description)) }
                    }
                    "tests" -> {
                        item { Notice(stringResource(R.string.tests_description)) }
                        item { OpCard { OpRow(stringResource(R.string.torch_toggle), glyph = "torch", tint = OpColors.Amber); Button(onClick = { scope.launch { app.actions.execute(Action(ActionKind.TORCH_TOGGLE), true) } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_try_torch)) } } }
                        item { OpCard { OpRow(stringResource(R.string.recorder_toggle), glyph = "mic", tint = OpColors.Red); Button(onClick = { scope.launch { app.actions.execute(Action(ActionKind.RECORD_TOGGLE), true) } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.recorder_toggle)) } } }
                        item { OpCard { OpRow(stringResource(R.string.open_camera_only), glyph = "camera", tint = OpColors.Purple); OutlinedButton(onClick = { if (!app.actions.openStockCamera()) app.actions.message.value = R.string.camera_unavailable }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_open_camera)) } } }
                    }
                    "inspector" -> item { OpCard {
                        OpRow(stringResource(R.string.inspector_title), stringResource(R.string.inspector_help), "apps", OpColors.Mint)
                        Text(stringResource(if(connected) R.string.inspector_access_ready else R.string.inspector_access_needed), color = if(connected) OpColors.Mint else OpColors.Amber)
                        Button(enabled = connected, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = {
                            if (!BasicInputService.showInspector()) app.actions.message.value = R.string.inspector_start_failed
                        }) { Text(stringResource(R.string.inspector_open)) }
                        OutlinedButton(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) { Text(stringResource(R.string.inspector_access_settings)) }
                        Text(stringResource(R.string.inspector_storage), style = MaterialTheme.typography.bodySmall, color = OpColors.Muted)
                    } }
                    "diagnostics" -> {
                        item { OpCard { OpRow(stringResource(R.string.ui_device_info), glyph = "pulse", tint = OpColors.Blue); Text(stringResource(R.string.firmware, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Build.DISPLAY)) } }
                        item { OpCard {
                            OpRow(stringResource(R.string.ui_root_check), glyph = "shield")
                            OutlinedButton(enabled = config.tier == Tier.ROOT, onClick = { scope.launch { rootIdentity = withContext(Dispatchers.IO) { dev.opluskeycontrol.root.RootIdentity.probe() } } }) { Text(stringResource(R.string.probe_root)) }
                            rootIdentity?.let { Text(it) }
                        } }
                        item { Text("Backend features: $backendFeatures", style = MaterialTheme.typography.bodySmall) }
                        item { InfoCard(R.string.diagnostics, R.string.diagnostics_redacted) { for(event in events) Text(event, style = MaterialTheme.typography.bodySmall) } }
                    }
                    "about" -> item { OpCard { IconTile("plus", size = 64.dp); SectionTitle(stringResource(R.string.app_name)); Text(dev.opluskeycontrol.BuildConfig.VERSION_NAME, color = OpColors.Muted); Text(stringResource(R.string.ui_about_description)) } }
                        }
                    }
                }
            }
        }
    }
}

private fun ruleStatus(rule: Rule, config: Configuration, connected: Boolean, emergency: Boolean, now: ZonedDateTime, context: android.content.Context, features: Int): Pair<Int, androidx.compose.ui.graphics.Color> = when {
    !Capabilities.configurableTrigger(config.tier, rule.trigger) -> R.string.ui_unsupported to OpColors.Red
    !rule.enabled -> R.string.disabled to OpColors.Muted
    connected && features and Capabilities.featureFor(rule.trigger) == 0 -> R.string.advanced_backend_missing to OpColors.Amber
    Capabilities.action(context,rule.action).status == Support.PERMISSION -> R.string.ui_needs_permission to OpColors.Amber
    Capabilities.action(context,rule.action).status != Support.SUPPORTED -> R.string.ui_needs_test to OpColors.Amber
    rule.action.kind == ActionKind.APP && context.packageManager.getLaunchIntentForPackage(rule.action.packageName.orEmpty()) == null -> R.string.app_unavailable to OpColors.Red
    !config.enabled || emergency -> R.string.ui_paused to OpColors.Muted
    !connected -> R.string.ui_waiting_backend to OpColors.Amber
    rule.schedule?.matches(now) == false -> R.string.ui_outside_schedule to OpColors.Blue
    RuleResolver.resolve(config.rules,rule.trigger,now) != Resolution.Match(rule) -> R.string.ui_not_selected to OpColors.Muted
    else -> R.string.ui_ready to OpColors.Success
}
@Composable private fun PermissionLine(title: Int, glyph: String, granted: Boolean, request: () -> Unit) {
    OpRow(stringResource(title), stringResource(if(granted) R.string.permission_granted else R.string.permission_missing), glyph, if(granted) OpColors.Mint else OpColors.Amber,
        onClick = if(granted) null else request, trailing = { if(granted) Icon(Icons.Outlined.CheckCircle, null, tint = OpColors.Mint) else TextButton(onClick = request) { Text(stringResource(R.string.ui_grant)) } })
}
@Composable fun triggerTitle(t: Trigger): String = stringResource(t.button.label()) + " · " + stringResource(t.gesture.label()) + (t.partner?.let { " + " + stringResource(it.label()) } ?: "")
@Composable fun ScheduleText(schedule: Schedule?) {
    if (schedule == null) Text(stringResource(R.string.all_times), style = MaterialTheme.typography.bodyMedium, color = OpColors.Muted)
    else {
        Text(stringResource(R.string.schedule_range, minuteLabel(schedule.startMinute), minuteLabel(schedule.endMinute)), style = MaterialTheme.typography.bodyMedium, color = OpColors.Blue)
        Text(schedule.days.sorted().joinToString(" · ") { it.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) }, style = MaterialTheme.typography.bodySmall, color = OpColors.Muted)
        if (schedule.startMinute > schedule.endMinute) Text(stringResource(R.string.overnight_hint), style = MaterialTheme.typography.bodySmall, color = OpColors.Muted)
    }
}
@Composable fun Notice(text: String) { OpCard { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Outlined.Info, null, tint = OpColors.Blue); Text(text, style = MaterialTheme.typography.bodyMedium, color = OpColors.Muted) } } }
@Composable fun InfoCard(title: Int, detail: Int, content: @Composable ColumnScope.() -> Unit = {}) {
    var expanded by remember { mutableStateOf(false) }
    OpCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { expanded = !expanded }) { Icon(Icons.Outlined.Info, stringResource(R.string.show_details), tint = OpColors.Blue) }
        }
        if (expanded) Text(stringResource(detail), style = MaterialTheme.typography.bodyMedium, color = OpColors.Muted)
        content()
    }
}
@Composable fun TimingSettings(config: Configuration, save: (Timing) -> Unit) {
    var double by remember(config.timing) { mutableFloatStateOf(config.timing.doubleMs.toFloat()) }
    var long by remember(config.timing) { mutableFloatStateOf(config.timing.longMs.toFloat()) }
    var chord by remember(config.timing) { mutableFloatStateOf(config.timing.chordMs.toFloat()) }
    var debounce by remember(config.timing) { mutableFloatStateOf(config.timing.debounceMs.toFloat()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OpCard { OpRow(stringResource(R.string.double_ms, double.toInt()), glyph = "clock"); Slider(double, { double = it }, valueRange = 150f..600f, steps = 8); RangeLabels("150 ms", "600 ms") }
        OpCard { OpRow(stringResource(R.string.long_ms, long.toInt()), glyph = "clock"); Slider(long, { long = it }, valueRange = 400f..1500f, steps = 10); RangeLabels("400 ms", "1500 ms") }
        OpCard { OpRow(stringResource(R.string.chord_ms, chord.toInt()), glyph = "link", tint = OpColors.Amber); StatusChip(stringResource(R.string.ui_unsupported), OpColors.Amber); Slider(chord, { chord = it }, valueRange = 30f..250f, steps = 10); RangeLabels("30 ms", "250 ms") }
        OpCard { OpRow(stringResource(R.string.debounce_ms, debounce.toInt()), glyph = "pulse"); Slider(debounce, { debounce = it }, valueRange = 0f..100f, steps = 9); RangeLabels("0 ms", "100 ms") }
        Button(onClick = { save(Timing(double.toLong(), long.toLong(), chord.toLong(), debounce.toLong())) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.save)) }
    }
}
@Composable private fun RangeLabels(start: String, end: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(start, style = MaterialTheme.typography.bodySmall, color = OpColors.Muted); Text(end, style = MaterialTheme.typography.bodySmall, color = OpColors.Muted) } }
