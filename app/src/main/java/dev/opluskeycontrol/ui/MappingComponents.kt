@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.opluskeycontrol.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import dev.opluskeycontrol.R
import dev.opluskeycontrol.capabilities.Capabilities
import dev.opluskeycontrol.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun UtilityTile(title: String, glyph: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = OpColors.Surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OpGlyph(glyph, OpColors.Accent, 28.dp)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable fun ContextHelp(title: String, detail: String) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Outlined.Info, stringResource(R.string.ux_help, title), tint = OpColors.Muted) }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) }, text = { Text(detail) },
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.ux_close)) } })
}

@Composable fun actionSummary(action: Action): String {
    val context = LocalContext.current
    val packageName = action.packageName
    val appName by produceState(packageName.orEmpty(), packageName) {
        if (packageName != null) value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString() }.getOrDefault(packageName)
        }
    }
    return when (action.kind) {
        ActionKind.APP -> appName
        ActionKind.ACTIVITY -> appName + " · " + action.activity.orEmpty().substringAfterLast('.').removeSuffix("Activity")
        else -> stringResource(action.kind.label())
    }
}

fun configurableTriggers(tier: Tier): List<Trigger> {
    val singles = listOf(Button.PLUS, Button.POWER, Button.VOLUME_UP, Button.VOLUME_DOWN).flatMap { key ->
        (if (key == Button.POWER) listOf(Gesture.DOUBLE, Gesture.TRIPLE) else listOf(Gesture.SINGLE, Gesture.DOUBLE, Gesture.TRIPLE, Gesture.LONG)).map { Trigger(key, it) }
    }
    val chords = listOf(Button.VOLUME_UP, Button.VOLUME_DOWN, Button.POWER).map { Trigger(Button.PLUS, Gesture.CHORD, it) }
    return (singles + chords).filter { Capabilities.configurableTrigger(tier, it) }
}

@Composable fun TriggerPicker(tier: Tier, group: Int? = null, onDismiss: () -> Unit, onSelect: (Trigger) -> Unit) {
    val choices = configurableTriggers(tier).filter { when (group) {
        0 -> it.button == Button.PLUS && it.gesture != Gesture.CHORD
        1 -> it.button == Button.POWER && it.gesture != Gesture.CHORD
        2 -> it.button in setOf(Button.VOLUME_UP, Button.VOLUME_DOWN) && it.gesture != Gesture.CHORD
        3 -> it.gesture == Gesture.CHORD
        else -> true
    } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = OpColors.Surface) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(16.dp)) {
            item { Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ux_choose_trigger), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                ContextHelp(stringResource(R.string.ux_choose_trigger), stringResource(R.string.ux_supported_only))
            } }
            if (choices.isEmpty()) item { Text(stringResource(R.string.ux_no_trigger), Modifier.padding(vertical = 24.dp), color = OpColors.Muted) }
            items(choices) { trigger ->
                OpRow(triggerTitle(trigger), glyph = if (trigger.gesture == Gesture.CHORD) "link" else if (trigger.button == Button.POWER) "power" else "plus", onClick = { onSelect(trigger) })
            }
        }
    }
}

@Composable fun ActionPicker(recent: List<Action>, onDismiss: () -> Unit, onSelect: (Action) -> Unit, onApps: () -> Unit, onActivities: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = OpColors.Surface) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(16.dp)) {
            item { Text(stringResource(R.string.ux_choose_action), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp)) }
            val used = recent.distinct().filter { it.packageName != null }.take(4)
            if (used.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.ux_recent)) }
                items(used) { action -> OpRow(actionSummary(action), glyph = "apps", onClick = { onSelect(action) }) }
            }
            item { SectionTitle(stringResource(R.string.ux_device)) }
            items(listOf(ActionKind.TORCH_TOGGLE, ActionKind.RECORD_TOGGLE, ActionKind.STOCK_CAMERA_CAPTURE)) { kind ->
                OpRow(stringResource(kind.label()), glyph = kind.glyph(), onClick = { onSelect(Action(kind)) })
            }
            item { SectionTitle(stringResource(R.string.ux_apps)) }
            item { OpRow(stringResource(R.string.open_app), stringResource(R.string.open_app_hint), glyph = "apps", onClick = onApps) }
            item { HorizontalDivider(color = OpColors.Border) }
            item { OpRow(stringResource(R.string.open_activity), stringResource(R.string.open_activity_hint), glyph = "android", onClick = onActivities) }
        }
    }
}
