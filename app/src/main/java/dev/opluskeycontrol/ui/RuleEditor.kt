@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package dev.opluskeycontrol.ui

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
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun RuleEditor(config: Configuration, original: Rule?, initialTrigger: Trigger? = null, onDelete: (() -> Unit)? = null, onClose: () -> Unit, onSave: (Rule) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as KeyControlApp
    val scope = rememberCoroutineScope()
    val actionMessage by app.actions.message.collectAsState()
    val permissionRevision by app.permissionRevision.collectAsState()
    val backendConnected by dev.opluskeycontrol.input.PolicyBridge.connected.collectAsState()
    val backendFeatures by dev.opluskeycontrol.input.PolicyBridge.features.collectAsState()
    var deleteConfirm by remember { mutableStateOf(false) }
    var actionChosen by remember { mutableStateOf(original != null) }
    var confirmTest by remember { mutableStateOf(false) }
    var validationRevision by remember { mutableIntStateOf(0) }
    var details by remember { mutableStateOf((original?.priority ?: 0) != 0) }
    var actionMenu by remember { mutableStateOf(false) }
    var buttonMenu by remember { mutableStateOf(false) }
    var button by remember { mutableStateOf(original?.trigger?.button ?: initialTrigger?.button ?: Button.PLUS) }
    var gesture by remember { mutableStateOf(original?.trigger?.gesture ?: initialTrigger?.gesture ?: Gesture.DOUBLE) }
    var partner by remember { mutableStateOf(original?.trigger?.partner ?: initialTrigger?.partner ?: Button.VOLUME_UP) }
    var kind by remember { mutableStateOf(original?.action?.kind ?: ActionKind.TORCH_TOGGLE) }
    var target by remember { mutableStateOf(original?.action?.packageName?.let { AppEntry(it, it, false) }) }
    var activity by remember { mutableStateOf(original?.action?.activity) }
    var picker by remember { mutableStateOf(false) }
    var priority by remember { mutableFloatStateOf(original?.priority?.toFloat() ?: 0f) }
    var enabled by remember { mutableStateOf(original?.enabled ?: true) }
    var duration by remember { mutableFloatStateOf(original?.action?.durationSeconds?.toFloat() ?: 300f) }
    val id = remember { original?.id ?: UUID.randomUUID().toString() }
    BackHandler { if (picker) picker = false else onClose() }
    if (picker) {
        AppPicker(activityMode = false, allowActivitySelection = true, onClose = { picker = false }, onSelect = { app, selected -> target = app; activity = selected; kind = if(selected == null) ActionKind.APP else ActionKind.ACTIVITY; actionChosen = true; picker = false })
        return
    }
    val trigger = runCatching { Trigger(button, gesture, if (gesture == Gesture.CHORD) partner else null) }.getOrNull()
    val action = runCatching { Action(kind, if (kind in setOf(ActionKind.APP, ActionKind.ACTIVITY)) target?.packageName else null, if (kind == ActionKind.ACTIVITY) activity else null, duration.toInt()) }.getOrNull()
    val rule = if (actionChosen && trigger != null && action != null) Rule(id, trigger, action, null, priority.toInt(), enabled) else null
    val conflict = rule != null && RuleResolver.conflicts(rule, config.rules)
    val triggerCapability = remember(trigger, config.tier, backendConnected, backendFeatures) { trigger?.let { Capabilities.trigger(context, config.tier, it) } }
    val actionCapability = remember(action, validationRevision, actionMessage, permissionRevision, config.tier) { action?.let { Capabilities.action(context, it) } }
    val canEnable = (triggerCapability?.status == Support.SUPPORTED || trigger?.let { Capabilities.configurableTrigger(config.tier, it) } == true) && actionCapability?.status == Support.SUPPORTED
    if (confirmTest && action != null) AlertDialog(onDismissRequest = { confirmTest = false },
        title = { Text(stringResource(R.string.test_action)) },
        text = { Text(stringResource(R.string.test_action_confirmation, stringResource(action.kind.label()))) },
        confirmButton = { TextButton(onClick = { confirmTest = false; scope.launch { app.actions.execute(action, true, config.tier == Tier.ROOT); validationRevision++; app.bridge.refresh() } }) { Text(stringResource(R.string.test_action)) } },
        dismissButton = { TextButton(onClick = { confirmTest = false }) { Text(stringResource(R.string.cancel)) } })
    if(buttonMenu) TriggerPicker(config.tier, onDismiss={buttonMenu=false}) {
        button=it.button; gesture=it.gesture; partner=it.partner ?: Button.VOLUME_UP; buttonMenu=false
    }
    if(actionMenu) ActionPicker(config.rules.map { it.action }, onDismiss={actionMenu=false}, onSelect={
        kind=it.kind; target=it.packageName?.let { p -> AppEntry(p,p,false) }; activity=it.activity
        duration=it.durationSeconds.toFloat(); actionChosen=true; actionMenu=false
    },onApps={actionMenu=false;picker=true})
    if(deleteConfirm) AlertDialog(onDismissRequest={deleteConfirm=false}, title={Text(stringResource(R.string.ui_delete_rule))},
        confirmButton={TextButton(onClick={deleteConfirm=false;onDelete?.invoke()}) {Text(stringResource(R.string.delete),color=OpColors.Red)}},
        dismissButton={TextButton(onClick={deleteConfirm=false}) {Text(stringResource(R.string.cancel))}})
    Scaffold(topBar={OpAppBar(stringResource(if(original==null) R.string.add_rule else R.string.edit),onBack=onClose) {
        ContextHelp(stringResource(R.string.edit),stringResource(R.string.ux_rule_help))
        if(onDelete!=null) IconButton(onClick={deleteConfirm=true}) {Icon(Icons.Outlined.Delete,stringResource(R.string.delete))}
    }}, bottomBar={Surface(color=OpColors.Background) {
        Column(Modifier.navigationBarsPadding().imePadding().fillMaxWidth().padding(16.dp)) {
            if(actionChosen && enabled && !canEnable) Text(stringResource(R.string.ux_save_off),style=MaterialTheme.typography.bodySmall,color=OpColors.Amber,modifier=Modifier.padding(bottom=8.dp))
            Button(enabled=rule!=null && !conflict,onClick={rule?.let { onSave(it.copy(enabled=enabled && canEnable)) }},
                colors=ButtonDefaults.buttonColors(containerColor=androidx.compose.ui.graphics.Color(0xFF1458C0),contentColor=androidx.compose.ui.graphics.Color.White),
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(stringResource(R.string.save))}
        }
    }}) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(),contentAlignment=Alignment.TopCenter) {
            Column(Modifier.widthIn(max=720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                OpCard {
                    OpRow(stringResource(R.string.nd_trigger),trigger?.let { triggerTitle(it) },glyph="link",onClick={buttonMenu=true})
                    HorizontalDivider(color=OpColors.Border)
                    OpRow(stringResource(R.string.ux_action),if(actionChosen && action!=null) actionSummary(action) else stringResource(R.string.ux_choose_action),glyph=if(actionChosen) kind.glyph() else "apps",onClick={actionMenu=true})
                }
                if(actionChosen && kind in setOf(ActionKind.TORCH_TOGGLE,ActionKind.TORCH_ON,ActionKind.TORCH_OFF)) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(ActionKind.TORCH_TOGGLE,ActionKind.TORCH_ON,ActionKind.TORCH_OFF).forEach { k -> FilterChip(kind==k,onClick={kind=k},label={Text(stringResource(k.label()))}) }
                }
                if(actionChosen && kind==ActionKind.RECORD_TOGGLE) {
                    Text(stringResource(R.string.autostop_seconds,duration.toInt()),style=MaterialTheme.typography.bodyMedium)
                    Slider(duration,{duration=it},valueRange=10f..3600f)
                }
                if(actionChosen && actionCapability?.status!=Support.SUPPORTED) actionCapability?.let { Notice(stringResource(it.reason)) }
                if(triggerCapability?.status!=Support.SUPPORTED && trigger?.let { !Capabilities.configurableTrigger(config.tier,it) }==true) triggerCapability?.let { Notice(stringResource(it.reason)) }
                if(actionChosen) OutlinedButton(enabled=action!=null && (actionCapability?.status==Support.SUPPORTED || kind==ActionKind.ACTIVITY),onClick={
                    if(kind==ActionKind.RECORD_TOGGLE) confirmTest=true else if(action!=null) scope.launch { app.actions.execute(action,true,config.tier==Tier.ROOT);validationRevision++;app.bridge.refresh() }
                }) {Text(stringResource(R.string.test_action))}
                TextButton(onClick={details=!details}) { Text(stringResource(R.string.advanced)) }
                if(details) {
                    Text(stringResource(R.string.priority_value,priority.toInt()),style=MaterialTheme.typography.bodyMedium)
                    Slider(priority,{priority=it},valueRange=0f..100f,steps=19)
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(R.string.enable_rule),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    Switch(enabled,{enabled=it},enabled=canEnable || enabled,colors=SwitchDefaults.colors(checkedTrackColor=androidx.compose.ui.graphics.Color(0xFF247DFF),checkedThumbColor=androidx.compose.ui.graphics.Color.White))
                }
                if(conflict) Notice(stringResource(R.string.conflict))
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
