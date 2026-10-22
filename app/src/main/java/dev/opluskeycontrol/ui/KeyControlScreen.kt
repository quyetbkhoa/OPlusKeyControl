@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.opluskeycontrol.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
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
    val now = remember(refresh) { ZonedDateTime.now() }
    val labels = listOf(R.string.overview, R.string.mappings, R.string.settings)
    val navIcons = listOf(Icons.Outlined.Home, Icons.AutoMirrored.Outlined.List, Icons.Outlined.Settings)
    if (editor) {
        RuleEditor(config, editing, onClose = { editor = false }, onSave = { rule ->
            app.store.mutate { it.copy(rules = it.rules.filterNot { old -> old.id == rule.id } + rule) }
            editor = false
        })
        return
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }, actions = {
            TextButton(onClick = { app.store.disable() }) { Text(stringResource(R.string.disable)) }
        }) },
        bottomBar = { NavigationBar { labels.forEachIndexed { i, label ->
            NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(navIcons[i], null) }, label = { Text(stringResource(label)) })
        } } },
        floatingActionButton = { if (tab == 1) ExtendedFloatingActionButton(onClick = { editing = null; editor = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.add_rule)) }) }
    ) { insets ->
        LazyColumn(Modifier.padding(insets).fillMaxSize(), state = when (tab) { 0 -> overviewScroll; 1 -> mappingsScroll; else -> settingsScroll }, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            item { if (recording) InfoCard(R.string.recording, R.string.recording_notice) {
                Button(onClick = { context.startService(Intent(context, RecorderService::class.java).setAction(RecorderService.STOP)) }) { Text(stringResource(R.string.stop)) }
            } }
            if (message != null) item { Notice(stringResource(message!!)); TextButton(onClick = { app.actions.message.value = null }) { Text(stringResource(R.string.dismiss)) } }
            if (recordError) item { Notice(stringResource(R.string.record_error)) }
            if (storeError) item { Notice(stringResource(R.string.storage_error)) }
            when (tab) {
                0 -> {
                    item { InfoCard(R.string.your_buttons, R.string.overview_description) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(if (config.enabled && !emergency) R.string.enabled else R.string.disabled), style = MaterialTheme.typography.titleMedium)
                            Switch(checked = config.enabled && !emergency, onCheckedChange = app.store::setEnabled)
                        }
                        Text(stringResource(config.tier.label()), style = MaterialTheme.typography.labelLarge)
                        BackendStatus(config.tier, connected, rootConnected, rootState, shizukuConnected, shizukuGranted)
                        if (!batteryExempt) Text(stringResource(R.string.battery_limited), color = MaterialTheme.colorScheme.error)
                    } }
                    item { Text(stringResource(R.string.now_preview), style = MaterialTheme.typography.titleLarge) }
                    if (config.rules.isEmpty()) item { Notice(stringResource(R.string.no_rules)) }
                    items(config.rules.map { it.trigger }.distinct()) { trigger ->
                        val resolved = RuleResolver.resolve(config.rules, trigger, now)
                        Column(Modifier.fillMaxWidth().widthIn(max = 720.dp)) {
                            Text(triggerTitle(trigger), style = MaterialTheme.typography.titleMedium)
                            Text(when {
                                !config.enabled || emergency -> stringResource(R.string.pass_through)
                                resolved is Resolution.Match -> stringResource(resolved.rule.action.kind.label())
                                resolved is Resolution.Conflict -> stringResource(R.string.conflict)
                                else -> stringResource(R.string.pass_through)
                            })
                        }
                    }
                    item { TextButton(onClick = { refresh++ }) { Text(stringResource(R.string.refresh_preview)) } }
                }
                1 -> {
                    item { Text(stringResource(R.string.mappings), style = MaterialTheme.typography.headlineMedium) }
                    if (config.rules.isEmpty()) item { InfoCard(R.string.no_rules, R.string.no_rules_description) { Button(onClick = { editing = null; editor = true }) { Text(stringResource(R.string.add_rule)) } } }
                    items(config.rules.sortedBy { it.trigger.button.ordinal }, key = { it.id }) { rule ->
                        Card(Modifier.fillMaxWidth().widthIn(max = 720.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(triggerTitle(rule.trigger), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(rule.action.kind.label()))
                            ScheduleText(rule.schedule)
                            if (rule.priority != 0) Text(stringResource(R.string.priority_value, rule.priority))
                            if (!Capabilities.configurableTrigger(config.tier, rule.trigger))
                                Text(stringResource(Capabilities.trigger(context, config.tier, rule.trigger).reason), style = MaterialTheme.typography.bodySmall)
                            if (Capabilities.action(context, rule.action).status != Support.SUPPORTED) {
                                Text(stringResource(Capabilities.action(context, rule.action).reason), style = MaterialTheme.typography.bodySmall)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Switch(rule.enabled, enabled = rule.enabled || ((Capabilities.configurableTrigger(config.tier, rule.trigger) || Capabilities.trigger(context, config.tier, rule.trigger).status == Support.SUPPORTED) && Capabilities.action(context, rule.action).status == Support.SUPPORTED), onCheckedChange = { checked -> app.store.mutate { c -> c.copy(rules = c.rules.map { if (it.id == rule.id) it.copy(enabled = checked) else it }) } })
                                TextButton(onClick = { editing = rule; editor = true }) { Text(stringResource(R.string.edit)) }
                                TextButton(onClick = { app.store.mutate { c -> c.copy(rules = c.rules.filterNot { it.id == rule.id }) } }) { Text(stringResource(R.string.delete)) }
                            }
                        } }
                    }
                }
                2 -> {
                    item { Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium) }
                    item(key = "backend") { InfoCard(R.string.backend, R.string.backend_explanation) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Tier.entries.forEach { tier ->
                            FilterChip(selected = config.tier == tier, onClick = { if (config.tier != tier) { app.store.disable(); app.store.mutate { it.copy(tier = tier, enabled = false) } } }, label = { Text(stringResource(tier.label())) })
                        } }
                        BackendStatus(config.tier, connected, rootConnected, rootState, shizukuConnected, shizukuGranted)
                        if (!config.enabled || emergency) Text(stringResource(R.string.remap_off_after_mode_change), style = MaterialTheme.typography.bodySmall)
                        when (config.tier) {
                            Tier.SHIZUKU -> {
                                if (shizukuRequesting) Text(stringResource(R.string.shizuku_requesting))
                                else if (shizukuDenied && !shizukuGranted) Text(stringResource(R.string.shizuku_denied), color = MaterialTheme.colorScheme.error)
                                OutlinedButton(onClick = { shizukuRequesting = Capabilities.requestShizuku(); shizukuDenied = !shizukuRequesting; refresh++ }, enabled = shizukuConnected && !shizukuGranted && !shizukuRequesting) { Text(stringResource(R.string.request_shizuku)) }
                            }
                            Tier.ROOT -> OutlinedButton(onClick = { app.bridge.retryAuthorization() }) { Text(stringResource(R.string.reconnect_backend)) }
                            Tier.BASIC -> OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text(stringResource(R.string.enable_accessibility)) }
                        }
                    } }
                    item(key = "permissions") { InfoCard(R.string.permissions, R.string.permissions_description) {
                        Text(stringResource(R.string.permission_status,
                            stringResource(if (cameraGranted) R.string.permission_granted else R.string.permission_missing),
                            stringResource(if (micGranted) R.string.permission_granted else R.string.permission_missing),
                            stringResource(if (notificationsGranted) R.string.permission_granted else R.string.permission_missing)))
                        Button(onClick = onPermissions) { Text(stringResource(R.string.grant_permissions)) }
                        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text(stringResource(R.string.enable_accessibility)) }
                        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName))) }) { Text(stringResource(R.string.app_settings)) }
                    } }
                    item(key = "battery") { InfoCard(R.string.battery_title, R.string.battery_help) {
                        Text(stringResource(if (batteryExempt) R.string.battery_exempt else R.string.battery_limited))
                        if (!batteryExempt) OutlinedButton(onClick = {
                            runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                android.net.Uri.parse("package:" + context.packageName))) }
                                .onFailure { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                        }) { Text(stringResource(R.string.battery_allow)) }
                        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:" + context.packageName))) }) { Text(stringResource(R.string.battery_app_settings)) }
                    } }
                    item(key = "manual-actions") { InfoCard(R.string.action_tests, R.string.tests_description) {
                        Button(onClick = { scope.launch { app.actions.execute(Action(ActionKind.TORCH_TOGGLE), true) } }) { Text(stringResource(R.string.torch_toggle)) }
                        OutlinedButton(onClick = { scope.launch { app.actions.execute(Action(ActionKind.RECORD_TOGGLE), true) } }) { Text(stringResource(R.string.recorder_toggle)) }
                        OutlinedButton(onClick = { if (!app.actions.openStockCamera()) app.actions.message.value = R.string.camera_unavailable }) { Text(stringResource(R.string.open_camera_only)) }
                        Text(stringResource(R.string.camera_unverified))
                    } }
                    item(key = "recording-history") { RecordingHistory() }
                    item(key = "timing") { TimingSettings(config) { value -> app.store.mutate { it.copy(timing = value) } } }
                    item(key = "backup") { InfoCard(R.string.backup, R.string.backup_description) {
                        OutlinedButton(onClick = onExport) { Text(stringResource(R.string.export_rules)) }
                        OutlinedButton(onClick = onImport) { Text(stringResource(R.string.import_rules)) }
                    } }
                    item(key = "diagnostics") { InfoCard(R.string.diagnostics, R.string.diagnostics_description) {
                        Text(stringResource(R.string.firmware, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Build.DISPLAY))
                        val identity = remember { mutableStateOf<String?>(null) }
                        OutlinedButton(enabled = config.tier == Tier.ROOT, onClick = { scope.launch {
                            identity.value = withContext(Dispatchers.IO) { dev.opluskeycontrol.root.RootIdentity.probe() }
                        } }) { Text(stringResource(R.string.probe_root)) }
                        identity.value?.let { Text(it) }
                        Text(stringResource(R.string.diagnostics_redacted))
                        for (event in events) Text(event, style = MaterialTheme.typography.bodySmall)
                    } }
                }
            }
        }
    }
}

@Composable fun triggerTitle(t: Trigger): String = stringResource(t.button.label()) + " · " + stringResource(t.gesture.label()) + (t.partner?.let { " + " + stringResource(it.label()) } ?: "")
@Composable fun ScheduleText(schedule: Schedule?) {
    if (schedule == null) Text(stringResource(R.string.all_times))
    else {
        Text(stringResource(R.string.schedule_range, minuteLabel(schedule.startMinute), minuteLabel(schedule.endMinute)))
        val locale = java.util.Locale.getDefault()
        Text(schedule.days.sorted().joinToString(" · ") { it.getDisplayName(java.time.format.TextStyle.SHORT, locale) })
        if (schedule.startMinute > schedule.endMinute) Text(stringResource(R.string.overnight_hint), style = MaterialTheme.typography.bodySmall)
    }
}
@Composable fun Notice(text: String) { Surface(Modifier.fillMaxWidth().widthIn(max = 720.dp), color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) { Text(text, Modifier.padding(16.dp)) } }
@Composable fun InfoCard(title: Int, detail: Int, content: @Composable ColumnScope.() -> Unit = {}) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().widthIn(max = 720.dp)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { expanded = !expanded }) { Icon(Icons.Outlined.Info, stringResource(R.string.show_details)) }
        }
        if (expanded) Text(stringResource(detail), style = MaterialTheme.typography.bodyMedium)
        content()
    } }
}
@Composable fun TimingSettings(config: Configuration, save: (Timing) -> Unit) {
    var double by remember(config.timing) { mutableFloatStateOf(config.timing.doubleMs.toFloat()) }
    var long by remember(config.timing) { mutableFloatStateOf(config.timing.longMs.toFloat()) }
    var chord by remember(config.timing) { mutableFloatStateOf(config.timing.chordMs.toFloat()) }
    var debounce by remember(config.timing) { mutableFloatStateOf(config.timing.debounceMs.toFloat()) }
    InfoCard(R.string.timings, R.string.timings_description) {
        Text(stringResource(R.string.double_ms, double.toInt())); Slider(double, { double = it }, valueRange = 150f..600f, steps = 8)
        Text(stringResource(R.string.long_ms, long.toInt())); Slider(long, { long = it }, valueRange = 400f..1500f, steps = 10)
        Text(stringResource(R.string.chord_ms, chord.toInt())); Slider(chord, { chord = it }, valueRange = 30f..250f, steps = 10)
        Text(stringResource(R.string.debounce_ms, debounce.toInt())); Slider(debounce, { debounce = it }, valueRange = 0f..100f, steps = 9)
        OutlinedButton(onClick = { save(Timing(double.toLong(), long.toLong(), chord.toLong(), debounce.toLong())) }) { Text(stringResource(R.string.save)) }
    }
}
