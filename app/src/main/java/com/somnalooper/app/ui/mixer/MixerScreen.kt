package com.somnalooper.app.ui.mixer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.somnalooper.app.R
import com.somnalooper.app.audio.SoundCatalog
import com.somnalooper.app.audio.SoundCategory
import com.somnalooper.app.data.db.TrackEntity
import com.somnalooper.app.ui.LocalContainer
import com.somnalooper.app.ui.LocalUiActions
import com.somnalooper.app.ui.components.SoundTile
import kotlinx.coroutines.launch

@Composable
fun MixerScreen() {
    val container = LocalContainer.current
    val actions = LocalUiActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by container.engine.state.collectAsStateWithLifecycle()
    val isPro by container.isPro.collectAsStateWithLifecycle()
    val tracks by container.tracks.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf(SoundCategory.NATURE) }
    var editing by remember { mutableStateOf<TrackEntity?>(null) }
    val limitText = stringResource(R.string.msg_layer_limit, if (isPro) 8 else 3)

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                container.db.tracks().insert(TrackEntity(uri = uri.toString(), name = displayName(context, uri)))
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(SoundCategory.entries.toList()) { c ->
                val count = when (c) {
                    SoundCategory.MINE -> state.layers.keys.count { SoundCatalog.isUser(it) }
                    else -> state.layers.keys.count { SoundCatalog.get(it)?.category == c }
                }
                FilterChip(
                    selected = c == category,
                    onClick = { category = c },
                    label = { Text(stringResource(c.label) + if (count > 0) "  •$count" else "") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            if (category == SoundCategory.BRAINWAVE) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text(
                            stringResource(R.string.headphones_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
            if (category == SoundCategory.MINE) {
                item {
                    SoundTile(
                        label = stringResource(R.string.import_audio),
                        icon = Icons.Outlined.Add,
                        active = false, volume = 0f, locked = false,
                        onToggle = { importer.launch(arrayOf("audio/*")) },
                        onVolume = {},
                    )
                }
                items(tracks, key = { it.id }) { t ->
                    val key = SoundCatalog.userKey(t.id)
                    SoundTile(
                        label = t.name,
                        icon = Icons.Outlined.AudioFile,
                        active = key in state.layers,
                        volume = state.layers[key] ?: 0f,
                        locked = false,
                        onToggle = { actions.handle(container.engine.toggleLayer(key), limitText) },
                        onVolume = { container.engine.setLayer(key, it) },
                        onLongPress = { editing = t },
                    )
                }
                if (tracks.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.import_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            } else {
                items(SoundCatalog.all.filter { it.category == category }, key = { it.id }) { def ->
                    SoundTile(
                        label = stringResource(def.nameRes),
                        icon = def.icon,
                        active = def.id in state.layers,
                        volume = state.layers[def.id] ?: 0f,
                        locked = def.pro && !isPro,
                        onToggle = { actions.handle(container.engine.toggleLayer(def.id), limitText) },
                        onVolume = { container.engine.setLayer(def.id, it) },
                        onLongPress = def.descRes?.let { res -> { actions.showMessage(context.getString(res)) } },
                    )
                }
            }
        }
    }

    editing?.let { t ->
        TrackDialog(
            track = t,
            onDismiss = { editing = null },
            onRename = { name -> scope.launch { container.db.tracks().rename(t.id, name) }; editing = null },
            onDelete = {
                container.engine.removeLayer(SoundCatalog.userKey(t.id))
                scope.launch {
                    container.db.tracks().delete(t.id)
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(Uri.parse(t.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
                editing = null
            },
        )
    }
}

@Composable
private fun TrackDialog(track: TrackEntity, onDismiss: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(track.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(R.string.edit_track)) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.name)) })
        },
        confirmButton = { TextButton(onClick = { onRename(name.trim().ifEmpty { track.name }) }) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        },
    )
}

private fun displayName(context: Context, uri: Uri): String {
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val n = c.getString(0)
                if (!n.isNullOrBlank()) return n.substringBeforeLast('.')
            }
        }
    }
    return uri.lastPathSegment ?: "Audio"
}
