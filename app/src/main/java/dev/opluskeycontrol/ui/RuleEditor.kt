@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.opluskeycontrol.ui

import android.app.TimePickerDialog
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
    var confirmTest by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(0) }
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
    val triggerCapability = trigger?.let { Capabilities.trigger(context, config.tier, it) }
    val actionCapability = action?.let { Capabilities.action(context, it) }
    val canEnable = triggerCapability?.status == Support.SUPPORTED && actionCapability?.status == Support.SUPPORTED
    if (confirmTest && action != null) AlertDialog(onDismissRequest = { confirmTest = false },
        title = { Text(stringResource(R.string.test_action)) },
        text = { Text(stringResource(R.string.test_action_confirmation, stringResource(action.kind.label()))) },
        confirmButton = { TextButton(onClick = { confirmTest = false; scope.launch { app.actions.execute(action, true) } }) { Text(stringResource(R.string.test_action)) } },
        dismissButton = { TextButton(onClick = { confirmTest = false }) { Text(stringResource(R.string.cancel)) } })
    val titles = listOf(R.string.choose_trigger, R.string.choose_schedule, R.string.choose_action, R.string.review_rule)
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.rule_editor)) }, navigationIcon = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } }) }, bottomBar = {
        Surface(tonalElevation = 3.dp) { Row(Modifier.navigationBarsPadding().fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = step > 0, onClick = { step-- }) { Text(stringResource(R.string.back)) }
            Button(enabled = if (step == 3) rule != null && !conflict && (!enabled || canEnable) else when (step) { 0 -> trigger != null; 1 -> !scheduled || schedule != null; else -> action != null },
                onClick = { if (step < 3) step++ else rule?.let { onSave(it) } }) { Text(stringResource(if (step < 3) R.string.next else R.string.save)) }
        } }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.step_of, step + 1, 4), style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress = { (step + 1) / 4f }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(titles[step]), style = MaterialTheme.typography.headlineMedium)
            when (step) {
                0 -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button.entries.forEach { candidate ->
                        FilterChip(selected = button == candidate, onClick = {
                            button = candidate
                            if (candidate == Button.POWER) gesture = Gesture.DOUBLE
                            if (partner == candidate) partner = if (candidate == Button.VOLUME_UP) Button.PLUS else Button.VOLUME_UP
                        }, label = { Text(stringResource(candidate.label())) })
                    } }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (if (button == Button.POWER) listOf(Gesture.DOUBLE) else Gesture.entries).forEach { candidate ->
                            FilterChip(selected = gesture == candidate, onClick = { gesture = candidate }, label = { Text(stringResource(candidate.label())) })
                        }
                    }
                    if (gesture == Gesture.CHORD) {
                        Text(stringResource(R.string.second_button))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button.entries.filter { it != button && it != Button.POWER }.forEach { candidate ->
                            FilterChip(partner == candidate, onClick = { partner = candidate }, label = { Text(stringResource(candidate.label())) })
                        } }
                    }
                    Notice(stringResource(R.string.power_safety))
                    if (trigger == null) Notice(stringResource(R.string.reserved_chord))
                    triggerCapability?.let { Notice(stringResource(it.reason)) }
                }
                1 -> {
                    Row { Checkbox(scheduled, { scheduled = it }); Text(stringResource(R.string.use_schedule), Modifier.padding(top = 12.dp)) }
                    if (scheduled) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> start = h * 60 + m }, start / 60, start % 60, true).show() }) { Text(stringResource(R.string.start_time, minuteLabel(start))) }
                            OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> end = h * 60 + m }, end / 60, end % 60, true).show() }) { Text(stringResource(R.string.end_time, minuteLabel(end))) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { DayOfWeek.entries.forEach { day ->
                            FilterChip(day in days, onClick = { days = if (day in days) days - day else days + day }, label = { Text(day.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())) })
                        } }
                        Notice(stringResource(R.string.schedule_explanation))
                        if (schedule == null) Notice(stringResource(R.string.invalid_schedule)) else ScheduleText(schedule)
                    } else Text(stringResource(R.string.default_rule_hint))
                    Text(stringResource(R.string.priority_value, priority.toInt()))
                    Slider(priority, { priority = it }, valueRange = 0f..100f, steps = 19)
                }
                2 -> {
                    ActionKind.entries.forEach { candidate ->
                        Row { RadioButton(kind == candidate, onClick = { kind = candidate; activity = null }); TextButton(onClick = { kind = candidate; activity = null }) { Text(stringResource(candidate.label())) } }
                    }
                    if (kind in setOf(ActionKind.APP, ActionKind.ACTIVITY)) {
                        Button(onClick = { picker = true }) { Text(stringResource(R.string.choose_app)) }
                        target?.let { Text(it.label) }
                        if (kind == ActionKind.ACTIVITY) Notice(stringResource(R.string.activity_unverified))
                    }
                    if (kind == ActionKind.RECORD_TOGGLE) {
                        Text(stringResource(R.string.autostop_seconds, duration.toInt()))
                        Slider(duration, { duration = it }, valueRange = 10f..3600f)
                        Notice(stringResource(R.string.recorder_visible))
                    }
                    actionCapability?.let { Notice(stringResource(it.reason)) }
                }
                3 -> {
                    trigger?.let { Text(triggerTitle(it), style = MaterialTheme.typography.titleLarge) }
                    Text(stringResource(kind.label()))
                    target?.let { Text(it.label) }
                    ScheduleText(schedule)
                    Text(stringResource(R.string.priority_value, priority.toInt()))
                    if (conflict) Notice(stringResource(R.string.conflict))
                    if (!canEnable) Notice(stringResource(R.string.save_disabled_hint))
                    Row { Switch(enabled, { enabled = it }, enabled = canEnable); Text(stringResource(R.string.enable_rule), Modifier.padding(start = 12.dp, top = 12.dp)) }
                    rule?.let { candidate ->
                        val resolved = RuleResolver.resolve(config.rules.filterNot { it.id == id } + candidate.copy(enabled = true), candidate.trigger, ZonedDateTime.now())
                        Text(stringResource(R.string.now_preview), style = MaterialTheme.typography.titleMedium)
                        Text(if (resolved is Resolution.Match) stringResource(resolved.rule.action.kind.label()) else stringResource(R.string.pass_through))
                    }
                    Notice(stringResource(R.string.preview_logical))
                    OutlinedButton(enabled = actionCapability?.status == Support.SUPPORTED, onClick = { confirmTest = true }) { Text(stringResource(R.string.test_action)) }
                    actionMessage?.let { Text(stringResource(it)) }
                }
            }
        }
    }
}
