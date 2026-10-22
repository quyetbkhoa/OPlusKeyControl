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
    var details by remember { mutableStateOf(original?.schedule != null || (original?.priority ?: 0) != 0) }
    var actionMenu by remember { mutableStateOf(false) }
    var buttonMenu by remember { mutableStateOf(false) }
    var gestureMenu by remember { mutableStateOf(false) }
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
    BackHandler { if (picker) picker = false else onClose() }
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
    Scaffold(topBar={OpAppBar(stringResource(if(original==null) R.string.add_rule else R.string.edit),onBack=onClose)},
        bottomBar={Surface(color=OpColors.Background) {
            Column(Modifier.navigationBarsPadding().imePadding().fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(enabled=rule!=null && !conflict,onClick={rule?.let { onSave(it.copy(enabled=false)) }},modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text(stringResource(R.string.ui_save_draft))}
                    Button(enabled=rule!=null && !conflict && (!enabled || canEnable),onClick={rule?.let(onSave)},modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text(stringResource(R.string.save))}
                }
            }
        }}) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(),contentAlignment=Alignment.TopCenter) {
            Column(Modifier.widthIn(max=720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.nd_editor_title),style=MaterialTheme.typography.headlineMedium)
                SectionTitle(stringResource(R.string.nd_trigger))
                FlowRow(Modifier.fillMaxWidth(),maxItemsInEachRow=if(androidx.compose.ui.platform.LocalDensity.current.fontScale>1.25f) 1 else 2,horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.nd_key),style=MaterialTheme.typography.bodySmall,color=OpColors.Muted)
                        Box {
                            OutlinedButton(onClick={buttonMenu=true},shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
                                Text(stringResource(button.label()),Modifier.weight(1f));Icon(Icons.Outlined.KeyboardArrowDown,null,modifier=Modifier.size(18.dp))
                            }
                            DropdownMenu(buttonMenu,onDismissRequest={buttonMenu=false}) {
                                Button.entries.forEach { candidate -> DropdownMenuItem(text={Text(stringResource(candidate.label()))},onClick={
                                    button=candidate;buttonMenu=false
                                    if(candidate==Button.POWER) gesture=Gesture.DOUBLE
                                    if(partner==candidate) partner=if(candidate==Button.VOLUME_UP) Button.PLUS else Button.VOLUME_UP
                                }) }
                            }
                        }
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.nd_press),style=MaterialTheme.typography.bodySmall,color=OpColors.Muted)
                        Box {
                            OutlinedButton(onClick={gestureMenu=true},shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
                                Text(stringResource(gesture.label()),Modifier.weight(1f));Icon(Icons.Outlined.KeyboardArrowDown,null,modifier=Modifier.size(18.dp))
                            }
                            DropdownMenu(gestureMenu,onDismissRequest={gestureMenu=false}) {
                                (if(button==Button.POWER) listOf(Gesture.DOUBLE,Gesture.CHORD) else Gesture.entries).forEach { candidate ->
                                    DropdownMenuItem(text={Text(stringResource(candidate.label()))},onClick={gesture=candidate;gestureMenu=false})
                                }
                            }
                        }
                    }
                }
                if(gesture==Gesture.CHORD) {
                    Text(stringResource(R.string.second_button),style=MaterialTheme.typography.bodyMedium,color=OpColors.Muted)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button.entries.filter { it!=button }.forEach { candidate -> FilterChip(partner==candidate,onClick={partner=candidate},label={Text(stringResource(candidate.label()))}) }
                    }
                    InfoCard(R.string.nd_chord_help,R.string.chord_plus_first)
                }
                if(button==Button.POWER) InfoCard(R.string.power,R.string.power_safety)
                if(button==Button.POWER && gesture==Gesture.DOUBLE && dev.opluskeycontrol.BuildConfig.ADVANCED_INPUTS) Notice(stringResource(R.string.power_double_experimental))
                if(trigger==null) Notice(stringResource(R.string.reserved_chord))
                if(triggerCapability?.status!=Support.SUPPORTED) triggerCapability?.let { Text(stringResource(it.reason),color=OpColors.Amber,style=MaterialTheme.typography.bodySmall) }
                HorizontalDivider(color=OpColors.Border)
                SectionTitle(stringResource(R.string.nd_action))
                Box {
                    OutlinedButton(onClick={actionMenu=true},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=MaterialTheme.shapes.medium) {
                        OpGlyph(kind.glyph(),OpColors.Accent,22.dp);Spacer(Modifier.width(12.dp))
                        Text(stringResource(kind.label()),Modifier.weight(1f));Icon(Icons.Outlined.KeyboardArrowDown,null)
                    }
                    DropdownMenu(actionMenu,onDismissRequest={actionMenu=false}) {
                        ActionKind.entries.forEach { candidate -> DropdownMenuItem(text={Text(stringResource(candidate.label()))},
                            leadingIcon={OpGlyph(candidate.glyph(),OpColors.Accent,20.dp)},onClick={kind=candidate;activity=null;actionMenu=false}) }
                    }
                }
                if(kind in setOf(ActionKind.APP,ActionKind.ACTIVITY)) {
                    OpCard {
                        Text(target?.label ?: stringResource(R.string.nd_no_target),style=MaterialTheme.typography.titleMedium)
                        if(kind==ActionKind.ACTIVITY) activity?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=OpColors.Muted) }
                        TextButton(onClick={picker=true}) {Text(stringResource(if(kind==ActionKind.ACTIVITY) R.string.choose_activity else R.string.choose_app))}
                    }
                }
                if(kind==ActionKind.RECORD_TOGGLE) {
                    Text(stringResource(R.string.autostop_seconds,duration.toInt()),style=MaterialTheme.typography.titleMedium)
                    Slider(duration,{duration=it},valueRange=10f..3600f)
                    Text(stringResource(R.string.ui_record_format),color=OpColors.Muted,style=MaterialTheme.typography.bodySmall)
                }
                if(actionCapability?.status!=Support.SUPPORTED) actionCapability?.let { Text(stringResource(it.reason),color=OpColors.Amber,style=MaterialTheme.typography.bodySmall) }
                OutlinedButton(enabled=action!=null && (actionCapability?.status==Support.SUPPORTED || (config.tier==Tier.ROOT && kind in setOf(ActionKind.ACTIVITY,ActionKind.STOCK_CAMERA_CAPTURE))),
                    onClick={confirmTest=true},modifier=Modifier.heightIn(min=48.dp)) {
                    Icon(Icons.Outlined.PlayArrow,null,modifier=Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.test_action))
                }
                actionMessage?.let { Text(stringResource(it),color=OpColors.Accent,style=MaterialTheme.typography.bodyMedium) }
                HorizontalDivider(color=OpColors.Border)
                Row(Modifier.fillMaxWidth().clickable { details=!details }.heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.nd_schedule_priority),style=MaterialTheme.typography.titleMedium)
                        if(!details) Text(stringResource(if(scheduled) R.string.use_schedule else R.string.all_times),style=MaterialTheme.typography.bodySmall,color=OpColors.Muted)
                    }
                    Icon(if(details) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,null,tint=OpColors.Muted)
                }
                if(details) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(stringResource(R.string.use_schedule),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                        Switch(scheduled,{scheduled=it})
                    }
                    if(scheduled) {
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick={TimePickerDialog(context,{_,h,m->start=h*60+m},start/60,start%60,true).show()}) {Text(stringResource(R.string.start_time,minuteLabel(start)))}
                            OutlinedButton(onClick={TimePickerDialog(context,{_,h,m->end=h*60+m},end/60,end%60,true).show()}) {Text(stringResource(R.string.end_time,minuteLabel(end)))}
                        }
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { DayOfWeek.entries.forEach { day ->
                            FilterChip(day in days,onClick={days=if(day in days) days-day else days+day},label={Text(day.getDisplayName(java.time.format.TextStyle.SHORT,java.util.Locale.getDefault()))})
                        } }
                        if(schedule==null) Text(stringResource(R.string.invalid_schedule),color=OpColors.Red) else ScheduleText(schedule)
                    }
                    Text(stringResource(R.string.priority_value,priority.toInt()),style=MaterialTheme.typography.bodyMedium)
                    Slider(priority,{priority=it},valueRange=0f..100f,steps=19)
                }
                HorizontalDivider(color=OpColors.Border)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.enable_rule),style=MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.nd_save_hint),style=MaterialTheme.typography.bodySmall,color=OpColors.Muted)
                    }
                    Switch(enabled,{enabled=it},enabled=canEnable || enabled)
                }
                if(conflict) Notice(stringResource(R.string.conflict))
                if(!canEnable) {
                    triggerCapability?.takeIf { it.status!=Support.SUPPORTED && trigger?.let { t-> !Capabilities.configurableTrigger(config.tier,t) }==true }?.let { Notice(stringResource(it.reason)) }
                }
                if(button==Button.PLUS) InfoCard(R.string.ui_plus_help,R.string.root_full_coverage)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
