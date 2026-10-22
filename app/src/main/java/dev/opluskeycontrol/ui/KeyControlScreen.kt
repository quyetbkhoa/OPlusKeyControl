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
    var buttonFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf<Rule?>(null) }
    var rootIdentity by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settingsPage) { settingsScroll.scrollToItem(0) }
    val now = remember(refresh) { ZonedDateTime.now() }
    val labels = listOf(R.string.overview, R.string.mappings, R.string.settings)
    val navIcons = listOf(Icons.Outlined.Home, Icons.AutoMirrored.Outlined.List, Icons.Outlined.Settings)
    val subpage = tab == 2 && settingsPage != "root"
    androidx.activity.compose.BackHandler(enabled = subpage || tab != 0) { if (subpage) settingsPage = "root" else tab = 0 }
    val openAppSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName))) }
    val titles = mapOf("permissions" to R.string.ui_connection, "battery" to R.string.ui_battery, "recordings" to R.string.recordings,
        "timing" to R.string.timings, "backup" to R.string.backup, "tests" to R.string.action_tests,
        "diagnostics" to R.string.diagnostics, "about" to R.string.ui_about)
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
    Scaffold(
        topBar = { OpAppBar(stringResource(if (subpage) titles.getValue(settingsPage) else if (tab == 0) R.string.app_name else labels[tab]),
            onBack = if (subpage) ({ settingsPage = "root" }) else null) {
            if (!subpage && tab == 0) OutlinedButton(onClick = { app.store.disable() }, colors = ButtonDefaults.outlinedButtonColors(contentColor = OpColors.Red)) {
                OpGlyph("power", OpColors.Red, 18.dp); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.disable))
            }
            if (!subpage && tab == 1) TextButton(onClick = { editing = null; editor = true }) {
                Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.add_rule))
            }
        } },
        bottomBar = { if (!subpage) NavigationBar(containerColor = OpColors.Surface) { labels.forEachIndexed { index, label ->
            NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(navIcons[index], null) }, label = { Text(stringResource(label)) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = OpColors.Mint, selectedTextColor = OpColors.Mint, indicatorColor = OpColors.Mint.copy(alpha = .12f), unselectedIconColor = OpColors.Muted, unselectedTextColor = OpColors.Muted))
        } } }
    ) { insets ->
        LazyColumn(Modifier.padding(insets).fillMaxSize(), state = when(tab) { 0 -> overviewScroll; 1 -> mappingsScroll; else -> settingsScroll },
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (recording) item { OpCard(selected = true, tint = OpColors.Red) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile("mic", OpColors.Red, 40.dp)
                    Text(stringResource(R.string.recording), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { context.startService(Intent(context, RecorderService::class.java).setAction(RecorderService.STOP)) }, colors = ButtonDefaults.buttonColors(containerColor = OpColors.Red)) { Text(stringResource(R.string.stop)) }
                }
            } }
            if (message != null) item { OpCard { Text(stringResource(message!!)); TextButton(onClick = { app.actions.message.value = null }) { Text(stringResource(R.string.dismiss)) } } }
            if (recordError) item { Notice(stringResource(R.string.record_error)) }
            if (storeError) item { Notice(stringResource(R.string.storage_error)) }
            when (tab) {
                0 -> {
                    item { OpCard(selected = config.enabled && !emergency) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            IconTile("plus", if (config.enabled && !emergency) OpColors.Mint else OpColors.Muted, 60.dp)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.ui_remap_plus), color = OpColors.Muted, style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(if (config.enabled && !emergency) R.string.enabled else R.string.disabled), style = MaterialTheme.typography.headlineMedium)
                            }
                            Switch(config.enabled && !emergency, onCheckedChange = app.store::setEnabled)
                        }
                    } }
                    item { OpCard {
                        OpRow(stringResource(config.tier.label()), stringResource(R.string.ui_selected_mode), "shield", onClick = { tab = 2; settingsPage = "permissions" })
                        BackendStatus(config.tier, connected, rootConnected, rootState, shizukuConnected, shizukuGranted)
                        TextButton(onClick = { tab = 2; settingsPage = "permissions" }) { Text(stringResource(R.string.ui_resolve_connection)); Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) }
                    } }
                    item { SectionTitle(stringResource(R.string.ui_current_plus)) }
                    if (config.rules.isEmpty()) item { OpCard {
                        OpRow(stringResource(R.string.no_rules), stringResource(R.string.ui_first_rule), "plus")
                        Button(onClick = { editing = null; editor = true }) { Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.add_rule)) }
                    } }
                    items(listOf(Gesture.SINGLE, Gesture.DOUBLE, Gesture.LONG), key = { "preview-${it.name}" }) { gesture ->
                        val trigger = Trigger(Button.PLUS, gesture)
                        val resolution = RuleResolver.resolve(config.rules, trigger, now)
                        val rule = (resolution as? Resolution.Match)?.rule
                        val plusActive = config.enabled && !emergency && config.tier == Tier.ROOT && rootConnected && config.rules.any { it.enabled && it.trigger.button == Button.PLUS && it.schedule?.matches(now) != false && Capabilities.action(context, it.action).status == Support.SUPPORTED }
                        OpCard {
                            if (rule != null) {
                                val status = ruleStatus(rule, config, rootConnected, emergency, now, context, backendFeatures)
                                OpRow(stringResource(rule.action.kind.label()), stringResource(gesture.label()), rule.action.kind.glyph(), rule.action.kind.tint())
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { StatusChip(stringResource(status.first), status.second) }
                                if (rule.schedule != null) ScheduleText(rule.schedule)
                            } else {
                                OpRow(stringResource(gesture.label()), stringResource(if (plusActive) R.string.ui_unassigned_no_action else R.string.pass_through), "plus", OpColors.Muted)
                            }
                        }
                    }
                    item { TextButton(onClick = { refresh++ }) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.refresh_preview)) } }
                    if (!batteryExempt) item { OpCard { OpRow(stringResource(R.string.battery_limited), stringResource(R.string.ui_battery_hint), "leaf", OpColors.Amber, onClick = { tab = 2; settingsPage = "battery" }) } }
                    item { InfoCard(R.string.ui_plus_help, R.string.root_full_coverage) }
                }
                1 -> {
                    item { androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(R.string.ui_all, R.string.plus, R.string.power, R.string.ui_volume).forEachIndexed { index, label -> FilterChip(buttonFilter == index, onClick = { buttonFilter = index }, label = { Text(stringResource(label)) }) }
                    } }
                    val filtered = config.rules.filter { when(buttonFilter) { 1 -> it.trigger.button == Button.PLUS; 2 -> it.trigger.button == Button.POWER; 3 -> it.trigger.button in setOf(Button.VOLUME_UP,Button.VOLUME_DOWN); else -> true } }
                    if (filtered.isEmpty()) item { OpCard {
                        OpRow(stringResource(R.string.no_rules), stringResource(R.string.ui_first_rule), "plus")
                        Button(onClick = { editing = null; editor = true }) { Text(stringResource(R.string.add_rule)) }
                    } }
                    val groups = filtered.partition { it.enabled && Capabilities.configurableTrigger(config.tier, it.trigger) }
                    listOf(groups.first to R.string.ui_enabled_rules, groups.second to R.string.ui_draft_rules).forEach { (rules, title) ->
                        if (rules.isNotEmpty()) item { SectionTitle(stringResource(title, rules.size)) }
                        items(rules, key = { it.id }) { rule ->
                            val status = ruleStatus(rule, config, rootConnected, emergency, now, context, backendFeatures)
                            var menu by remember { mutableStateOf(false) }
                            val canEnable = (Capabilities.configurableTrigger(config.tier, rule.trigger) || Capabilities.trigger(context, config.tier, rule.trigger).status == Support.SUPPORTED) && Capabilities.action(context, rule.action).status == Support.SUPPORTED
                            OpCard {
                                OpRow(stringResource(rule.action.kind.label()), triggerTitle(rule.trigger), rule.action.kind.glyph(), rule.action.kind.tint())
                                rule.action.packageName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = OpColors.Muted) }
                                ScheduleText(rule.schedule)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) { StatusChip(stringResource(status.first), status.second) }
                                    Switch(rule.enabled, enabled = rule.enabled || canEnable, onCheckedChange = { checked ->
                                        if (checked && RuleResolver.conflicts(rule.copy(enabled = true), config.rules)) app.actions.message.value = R.string.conflict
                                        else app.store.mutate { c -> c.copy(rules = c.rules.map { if (it.id == rule.id) it.copy(enabled = checked) else it }) }
                                    })
                                    Box {
                                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.ui_rule_options)) }
                                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                            DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { editing = rule; editor = true; menu = false })
                                            DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = OpColors.Red) }, onClick = { deleting = rule; menu = false })
                                        }
                                    }
                                }
                                if (!canEnable && !rule.enabled) Text(stringResource(if (!Capabilities.configurableTrigger(config.tier,rule.trigger)) Capabilities.trigger(context,config.tier,rule.trigger).reason else Capabilities.action(context,rule.action).reason), style = MaterialTheme.typography.bodySmall, color = OpColors.Muted)
                            }
                        }
                    }
                }
                2 -> when (settingsPage) {
                    "root" -> {
                        val pages = listOf(
                            Triple("permissions", R.string.ui_connection, "shield"), Triple("battery", R.string.ui_battery, "leaf"),
                            Triple("recordings", R.string.recordings, "file"), Triple("timing", R.string.timings, "clock"),
                            Triple("backup", R.string.backup, "file"), Triple("tests", R.string.action_tests, "power"),
                            Triple("diagnostics", R.string.diagnostics, "pulse"), Triple("about", R.string.ui_about, "apps"))
                        items(pages, key = { it.first }) { (page, title, glyph) ->
                            val subtitle = when(page) {
                                "permissions" -> stringResource(config.tier.label())
                                "battery" -> stringResource(if (batteryExempt) R.string.battery_exempt else R.string.battery_limited)
                                "recordings" -> stringResource(R.string.ui_recordings_hint)
                                "timing" -> stringResource(R.string.ui_timing_summary, config.timing.doubleMs, config.timing.longMs)
                                "backup" -> stringResource(R.string.ui_json_hint)
                                "tests" -> stringResource(R.string.ui_test_hint)
                                "diagnostics" -> stringResource(R.string.ui_diagnostics_hint)
                                else -> stringResource(R.string.app_name)
                            }
                            OpCard { OpRow(stringResource(title), subtitle, glyph, when(page) { "tests" -> OpColors.Red; "timing" -> OpColors.Amber; "backup" -> OpColors.Purple; else -> OpColors.Mint }, onClick = { settingsPage = page }) }
                        }
                    }
                    "permissions" -> {
                        item { OpCard {
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Tier.entries.forEach { tier ->
                                FilterChip(config.tier == tier, onClick = { if (config.tier != tier) { app.store.disable(); app.store.mutate { it.copy(tier = tier, enabled = false) } } }, label = { Text(stringResource(tier.label())) })
                            } }
                            BackendStatus(config.tier, connected, rootConnected, rootState, shizukuConnected, shizukuGranted)
                            if (!config.enabled || emergency) Text(stringResource(R.string.remap_off_after_mode_change), style = MaterialTheme.typography.bodySmall, color = OpColors.Muted)
                            when(config.tier) {
                                Tier.ROOT -> OutlinedButton(onClick = { app.bridge.retryAuthorization() }) { Icon(Icons.Outlined.Refresh, null); Text(stringResource(R.string.reconnect_backend)) }
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
                    "diagnostics" -> {
                        item { OpCard { OpRow(stringResource(R.string.ui_device_info), glyph = "pulse", tint = OpColors.Blue); Text(stringResource(R.string.firmware, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Build.DISPLAY)) } }
                        item { OpCard {
                            OpRow(stringResource(R.string.ui_root_check), glyph = "shield")
                            OutlinedButton(enabled = config.tier == Tier.ROOT, onClick = { scope.launch { rootIdentity = withContext(Dispatchers.IO) { dev.opluskeycontrol.root.RootIdentity.probe() } } }) { Text(stringResource(R.string.probe_root)) }
                            rootIdentity?.let { Text(it) }
                        } }
                        item { InfoCard(R.string.diagnostics, R.string.diagnostics_redacted) { for(event in events) Text(event, style = MaterialTheme.typography.bodySmall) } }
                    }
                    "about" -> item { OpCard { IconTile("plus", size = 64.dp); SectionTitle(stringResource(R.string.app_name)); Text(dev.opluskeycontrol.BuildConfig.VERSION_NAME, color = OpColors.Muted); Text(stringResource(R.string.ui_about_description)) } }
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
    else -> R.string.ui_ready to OpColors.Mint
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
