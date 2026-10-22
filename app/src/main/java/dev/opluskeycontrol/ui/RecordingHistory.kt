package dev.opluskeycontrol.ui

import android.content.ContentUris
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import dev.opluskeycontrol.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun RecordingHistory() {
    val context = LocalContext.current
    val recording by dev.opluskeycontrol.actions.RecorderService.recording.collectAsState()
    var entries by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(refresh, recording) {
        error = false
        loading = true
        entries = withContext(Dispatchers.IO) { runCatching {
            if (Build.VERSION.SDK_INT < 29) emptyList() else buildList {
                context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME),
                    "${MediaStore.Audio.Media.RELATIVE_PATH}=? AND ${MediaStore.Audio.Media.OWNER_PACKAGE_NAME}=? AND ${MediaStore.Audio.Media.IS_PENDING}=0", arrayOf("Music/OPlusKeyControl/", context.packageName), "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { cursor ->
                    while (cursor.moveToNext() && size < 50) add(cursor.getLong(0) to cursor.getString(1))
                }
            }
        }.getOrElse { error = true; emptyList() } }
        loading = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Music/OPlusKeyControl", style = MaterialTheme.typography.bodyMedium, color = OpColors.Muted)
        TextButton(onClick = { refresh++ }) { Text(stringResource(R.string.refresh)) }
        if (error) Text(stringResource(R.string.scan_failed))
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!loading && !error && entries.isEmpty()) OpCard { OpRow(stringResource(R.string.ui_empty_recordings), stringResource(R.string.no_recordings), "file", OpColors.Muted) }
        for ((id, name) in entries) OpCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile("file", OpColors.Blue)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    StatusChip(name.substringAfterLast('.', "audio").uppercase(), OpColors.Blue)
                }
                IconButton(onClick = {
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, context.contentResolver.getType(uri) ?: "audio/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }.onFailure { error = true }
                }) { Icon(Icons.Outlined.PlayArrow, stringResource(R.string.select), tint = OpColors.Blue) }
            }
        }
    }
}
