@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.opluskeycontrol.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Alignment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.opluskeycontrol.R
import dev.opluskeycontrol.KeyControlApp
import dev.opluskeycontrol.capabilities.*
import dev.opluskeycontrol.discovery.AppEntry
import dev.opluskeycontrol.domain.*
import dev.opluskeycontrol.storage.Configuration
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun RuleEditor(config: Configuration, original: Rule?, onClose: () -> Unit, onSave: (Rule) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as KeyControlApp
    val scope = rememberCoroutineScope()
    val actionMessage by app.actions.message.collectAsState()
    val permissionRevision by app.permissionRevision.collectAsState()
    val backendConnected by dev.opluskeycontrol.input.PolicyBridge.connected.collectAsState()
    val backendFeatures by dev.opluskeycontrol.input.PolicyBridge.features.collectAsState()
    var confirmTest by remember { mutableStateOf(false) }
    var validationRevision by remember { mutableIntStateOf(0) }
    var step by remember { mutableIntStateOf(0) }
    var details by remember { mutableStateOf(false) }
    var button by remember { mutableStateOf(original?.trigger?.button ?: Button.PLUS) }
    var gesture by remember { mutableStateOf(original?.trigger?.gesture ?: Gesture.DOUBLE) }
    var partner by remember { mutableStateOf(original?.trigger?.partner ?: Button.VOLUME_UP) }
    var scheduled by remember { mutableStateOf(original?.schedule != null) }
    var start by remember { mutableIntStateOf(original?.schedule?.startMinute ?: 360) }
    var end by remember { mutableIntStateOf(original?.schedule?.endMinute ?: 1080) }
    var days by remember { mutableStateOf(original?.schedule?.days ?: DayOfWeek.entries.toSet()) }
    var kind by remember { mutableStateOf(original?.action?.kind ?: ActionKind.TORCH_TOGGLE) }
    var target by remember { mutableStateOf(original?.action?.packageName?.let { AppEntry(it, it, false) }) }
    var activity by remember { mutableStateOf(original?.action?.activity) }
    var picker by remember { mutableStateOf(false) }
    var priority by remember { mutableFloatStateOf(original?.priority?.toFloat() ?: 0f) }
    var enabled by remember { mutableStateOf(original?.enabled ?: false) }
    var duration by remember { mutableFloatStateOf(original?.action?.durationSeconds?.toFloat() ?: 300f) }
    val id = remember { original?.id ?: UUID.randomUUID().toString() }
    BackHandler { if (picker) picker = false else if (step > 0) step-- else onClose() }
    if (picker) {
        AppPicker(activityMode = kind == ActionKind.ACTIVITY, onClose = { picker = false }, onSelect = { app, selected -> target = app; activity = selected; picker = false })
        return
    }
    val trigger = runCatching { Trigger(button, gesture, if (gesture == Gesture.CHORD) partner else null) }.getOrNull()
    val schedule = if (scheduled) runCatching { Schedule(start, end, days) }.getOrNull() else null
    val action = runCatching { Action(kind, if (kind in setOf(ActionKind.APP, ActionKind.ACTIVITY)) target?.packageName else null, if (kind == ActionKind.ACTIVITY) activity else null, duration.toInt()) }.getOrNull()
    val rule = if (trigger != null && action != null && (!scheduled || schedule != null)) Rule(id, trigger, action, schedule, priority.toInt(), enabled) else null
    val conflict = rule != null && RuleResolver.conflicts(rule, config.rules)
    val triggerCapability = remember(trigger, config.tier, backendConnected, backendFeatures) { trigger?.let { Capabilities.trigger(context, config.tier, it) } }
    val actionCapability = remember(action, validationRevision, actionMessage, permissionRevision, config.tier) { action?.let { Capabilities.action(context, it) } }
    val canEnable = (triggerCapability?.status == Support.SUPPORTED || trigger?.let { Capabilities.configurableTrigger(config.tier, it) } == true) && actionCapability?.status == Support.SUPPORTED
    if (confirmTest && action != null) AlertDialog(onDismissRequest = { confirmTest = false },
        title = { Text(stringResource(R.string.test_action)) },
        text = { Text(stringResource(R.string.test_action_confirmation, stringResource(action.kind.label()))) },
        confirmButton = { TextButton(onClick = { confirmTest = false; scope.launch { app.actions.execute(action, true, config.tier == Tier.ROOT); validationRevision++; app.bridge.refresh() } }) { Text(stringResource(R.string.test_action)) } },
        dismissButton = { TextButton(onClick = { confirmTest = false }) { Text(stringResource(R.string.cancel)) } })
    val titles = listOf(R.string.choose_trigger, R.string.choose_schedule, R.string.choose_action, R.string.review_rule)
    Scaffold(topBar = { OpAppBar(stringResource(titles[step]), onBack = { if (step > 0) step-- else onClose() }) {
        TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) }
    } }, bottomBar = {
        Surface(color = OpColors.Surface) { Row(Modifier.navigationBarsPadding().fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step == 3) OutlinedButton(enabled = rule != null && !conflict, onClick = { rule?.let { onSave(it.copy(enabled = false)) } }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_save_draft)) }
            else OutlinedButton(onClick = { if (step > 0) step-- else onClose() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(if (step > 0) R.string.back else R.string.cancel)) }
            Button(enabled = if (step == 3) rule != null && !conflict && (!enabled || canEnable) else when (step) { 0 -> trigger != null; 1 -> !scheduled || schedule != null; else -> action != null },
                onClick = { if (step < 3) step++ else rule?.let(onSave) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(if (step < 3) R.string.next else R.string.save)) }
        } }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.step_of, step + 1, 4), color = OpColors.Mint, style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.rule_editor), color = OpColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
            LinearProgressIndicator(progress = { (step + 1) / 4f }, modifier = Modifier.fillMaxWidth())
            when (step) {
                0 -> {
                    SectionTitle(stringResource(R.string.ui_choose_button))
                    Button.entries.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { pair.forEach { candidate ->
                            OpCard(Modifier.weight(1f).clickable {
                                button = candidate
                                if (candidate == Button.POWER) gesture = Gesture.DOUBLE
                                if (partner == candidate) partner = if (candidate == Button.VOLUME_UP) Button.PLUS else Button.VOLUME_UP
                            }, selected = button == candidate) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    IconTile(when(candidate) { Button.PLUS -> "plus"; Button.POWER -> "power"; else -> "volume" }, if(candidate == Button.PLUS) OpColors.Mint else OpColors.Muted, 40.dp)
                                    if (button == candidate) Icon(Icons.Outlined.CheckCircle, null, tint = OpColors.Mint)
                                }
                                Text(stringResource(candidate.label()), style = MaterialTheme.typography.titleMedium)
                                val configurable = Capabilities.configurableTrigger(config.tier, Trigger(candidate, if(candidate == Button.POWER) Gesture.DOUBLE else Gesture.SINGLE))
                                Text(stringResource(if(configurable) R.string.ui_configurable else R.string.ui_unsupported), color = if(configurable) OpColors.Mint else OpColors.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        } }
                    }
                    SectionTitle(stringResource(R.string.ui_choose_gesture))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (if (button == Button.POWER) listOf(Gesture.DOUBLE, Gesture.CHORD) else Gesture.entries).forEach { candidate ->
                            FilterChip(gesture == candidate, onClick = { gesture = candidate }, label = { Text(stringResource(candidate.label())) })
                        }
                    }
                    if (gesture == Gesture.CHORD) OpCard {
                        Text(stringResource(R.string.second_button), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button.entries.filter { it != button }.forEach { candidate ->
                            FilterChip(partner == candidate, onClick = { partner = candidate }, label = { Text(stringResource(candidate.label())) })
                        } }
                    }
                    if (button == Button.POWER) Notice(stringResource(R.string.power_safety))
                    if (button == Button.POWER && gesture == Gesture.DOUBLE && dev.opluskeycontrol.BuildConfig.ADVANCED_INPUTS) Notice(stringResource(R.string.power_double_experimental))
                    if (gesture == Gesture.CHORD) Notice(stringResource(R.string.chord_plus_first))
                    if (trigger == null) Notice(stringResource(R.string.reserved_chord))
                    if (triggerCapability?.status != Support.SUPPORTED) triggerCapability?.let { Notice(stringResource(it.reason)) }
                    if (button == Button.PLUS) InfoCard(R.string.ui_plus_help, R.string.root_full_coverage)
                }
                1 -> {
                    ChoiceCard(stringResource(R.string.all_times), stringResource(R.string.ui_always_hint), "clock", !scheduled) { scheduled = false }
                    ChoiceCard(stringResource(R.string.use_schedule), stringResource(R.string.ui_scheduled_hint), "clock", scheduled) { scheduled = true }
                    if (scheduled) OpCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> start = h * 60 + m }, start / 60, start % 60, true).show() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.start_time, minuteLabel(start))) }
                            OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> end = h * 60 + m }, end / 60, end % 60, true).show() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.end_time, minuteLabel(end))) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { DayOfWeek.entries.forEach { day ->
                            FilterChip(day in days, onClick = { days = if (day in days) days - day else days + day }, label = { Text(day.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())) })
                        } }
                        if (schedule == null) Text(stringResource(R.string.invalid_schedule), color = OpColors.Red) else ScheduleText(schedule)
                    }
                    if (conflict) Notice(stringResource(R.string.conflict))
                    OpCard {
                        TextButton(onClick = { details = !details }) { Text(stringResource(R.string.advanced)); Icon(if(details) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null) }
                        if (details || priority != 0f) {
                            Text(stringResource(R.string.priority_value, priority.toInt()))
                            Slider(priority, { priority = it }, valueRange = 0f..100f, steps = 19)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("0", color = OpColors.Muted); Text("100", color = OpColors.Muted) }
                        }
                    }
                }
                2 -> {
                    ActionKind.entries.forEach { candidate -> OpCard(selected = kind == candidate) {
                        OpRow(stringResource(candidate.label()), glyph = candidate.glyph(), tint = candidate.tint(), onClick = { kind = candidate; activity = null },
                            trailing = { RadioButton(kind == candidate, onClick = { kind = candidate; activity = null }) })
                        if (candidate == kind) {
                            if (kind in setOf(ActionKind.APP, ActionKind.ACTIVITY)) {
                                OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (kind == ActionKind.ACTIVITY) R.string.choose_activity else R.string.choose_app)) }
                                target?.let { Text(it.label, color = OpColors.Blue) }
                                if (kind == ActionKind.ACTIVITY) activity?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = OpColors.Muted) }
                            }
                            if (kind == ActionKind.RECORD_TOGGLE) {
                                Text(stringResource(R.string.autostop_seconds, duration.toInt()), style = MaterialTheme.typography.bodyMedium)
                                Slider(duration, { duration = it }, valueRange = 10f..3600f)
                                Text(stringResource(R.string.ui_record_format), color = OpColors.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            if (actionCapability?.status != Support.SUPPORTED) actionCapability?.let { Text(stringResource(it.reason), color = OpColors.Amber, style = MaterialTheme.typography.bodySmall) }
                        } else if(candidate == ActionKind.ACTIVITY) StatusChip(stringResource(R.string.ui_camera_trial), OpColors.Amber)
                    } }
                }
                3 -> {
                    OpCard {
                        SectionTitle(stringResource(R.string.ui_summary))
                        OpRow(stringResource(kind.label()), trigger?.let { triggerTitle(it) }, kind.glyph(), kind.tint())
                        if (kind in setOf(ActionKind.APP, ActionKind.ACTIVITY)) target?.let { Text(it.label, color = OpColors.Blue) }
                        if(kind == ActionKind.ACTIVITY) activity?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = OpColors.Muted) }
                        if(kind == ActionKind.RECORD_TOGGLE) Text(stringResource(R.string.autostop_seconds, duration.toInt()), style = MaterialTheme.typography.bodyMedium, color = OpColors.Muted)
                        HorizontalDivider(color = OpColors.Border)
                        ScheduleText(schedule)
                        Text(stringResource(R.string.priority_value, priority.toInt()), color = OpColors.Muted, style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.enable_rule), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Switch(enabled, { enabled = it }, enabled = canEnable || enabled)
                        }
                    }
                    if (conflict) Notice(stringResource(R.string.conflict))
                    if (!canEnable) {
                        triggerCapability?.takeIf { it.status != Support.SUPPORTED && trigger?.let { t -> !Capabilities.configurableTrigger(config.tier, t) } == true }?.let { Notice(stringResource(it.reason)) }
                        actionCapability?.takeIf { it.status != Support.SUPPORTED }?.let { Notice(stringResource(it.reason)) }
                    }
                    if (Capabilities.configurableTrigger(config.tier, trigger ?: Trigger(Button.PLUS, Gesture.SINGLE)) && !backendConnected) Notice(stringResource(R.string.root_disconnected))
                    OpCard {
                        Text(stringResource(R.string.test_action), style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(enabled = actionCapability?.status == Support.SUPPORTED || (config.tier == Tier.ROOT && kind in setOf(ActionKind.ACTIVITY, ActionKind.STOCK_CAMERA_CAPTURE)),
                            onClick = { confirmTest = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(Icons.Outlined.PlayArrow, null); Text(stringResource(R.string.test_action))
                        }
                        Text(stringResource(R.string.ui_current_action_hint), color = OpColors.Muted, style = MaterialTheme.typography.bodySmall)
                        actionMessage?.let { Text(stringResource(it), color = OpColors.Blue) }
                    }
                    if (button == Button.PLUS) InfoCard(R.string.ui_plus_help, R.string.root_full_coverage)
                }
            }
        }
    }
}

@Composable private fun ChoiceCard(title: String, subtitle: String, glyph: String, selected: Boolean, onClick: () -> Unit) {
    OpCard(selected = selected) { OpRow(title, subtitle, glyph, onClick = onClick, trailing = { RadioButton(selected, onClick) }) }
}
