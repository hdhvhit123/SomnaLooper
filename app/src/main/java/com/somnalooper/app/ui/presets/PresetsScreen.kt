package com.somnalooper.app.ui.presets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.somnalooper.app.R
import com.somnalooper.app.audio.SoundCatalog
import com.somnalooper.app.data.BuiltInPresets
import com.somnalooper.app.data.MixCodec
import com.somnalooper.app.data.db.PresetEntity
import com.somnalooper.app.ui.LocalContainer
import com.somnalooper.app.ui.components.ProPill
import com.somnalooper.app.ui.components.SectionTitle
import kotlinx.coroutines.launch

@Composable
fun PresetsScreen(onSaveCurrent: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presets by container.presets.collectAsStateWithLifecycle()
    val isPro by container.isPro.collectAsStateWithLifecycle()
    val engineState by container.engine.state.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<PresetEntity?>(null) }
    var deleting by remember { mutableStateOf<PresetEntity?>(null) }

    fun apply(mix: Map<String, Float>, name: String) {
        container.engine.setMix(mix, name)
        container.engine.play()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text(stringResource(R.string.tab_presets), style = MaterialTheme.typography.headlineMedium) }
            item { SectionTitle(stringResource(R.string.my_presets)) }
            if (presets.isEmpty()) {
                item {
                    Text(stringResource(R.string.no_presets), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(presets, key = { it.id }) { p ->
                val mix = remember(p.layers) { MixCodec.decode(p.layers) }
                PresetCard(
                    title = p.name,
                    subtitle = mix.keys.joinToString(" · ") { container.layerLabel(context, it) },
                    needsPro = !isPro && mix.keys.any { SoundCatalog.isPro(it) },
                    current = engineState.mixName == p.name,
                    onPlay = {
                        apply(mix, p.name)
                        scope.launch { container.db.presets().touch(p.id) }
                    },
                    onRename = { renaming = p },
                    onDelete = { deleting = p },
                )
            }
            item { SectionTitle(stringResource(R.string.curated_presets)) }
            items(BuiltInPresets.all, key = { it.id }) { b ->
                val name = stringResource(b.nameRes)
                PresetCard(
                    title = name,
                    subtitle = b.mix.keys.joinToString(" · ") { container.layerLabel(context, it) },
                    needsPro = !isPro && b.mix.keys.any { SoundCatalog.isPro(it) },
                    current = engineState.mixName == name,
                    onPlay = { apply(b.mix, name) },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = onSaveCurrent,
            icon = { Icon(Icons.Outlined.BookmarkAdd, contentDescription = null) },
            text = { Text(stringResource(R.string.save_current_mix)) },
            expanded = true,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    renaming?.let { p ->
        NameDialog(
            title = stringResource(R.string.rename),
            initial = p.name,
            onDismiss = { renaming = null },
            onConfirm = { name -> scope.launch { container.db.presets().update(p.copy(name = name)) }; renaming = null },
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.delete_preset_q, p.name)) },
            confirmButton = {
                TextButton(onClick = { scope.launch { container.db.presets().delete(p.id) }; deleting = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PresetCard(
    title: String,
    subtitle: String,
    needsPro: Boolean,
    current: Boolean,
    onPlay: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Card(
        onClick = onPlay,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (needsPro) { Spacer(Modifier.width(8.dp)); ProPill() }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            onRename?.let { IconButton(onClick = it) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.rename)) } }
            onDelete?.let { IconButton(onClick = it) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete)) } }
            Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.play), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp).clickable(onClick = onPlay))
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                label = { Text(stringResource(R.string.name)) },
                placeholder = { Text(stringResource(R.string.preset_name_hint)) },
            )
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Saves the current mix as a named "favourite recipe". */
@Composable
fun SavePresetDialog(onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val layers = container.engine.state.value.layers
    if (layers.isEmpty()) { onDismiss(); return }
    NameDialog(
        title = stringResource(R.string.save_preset),
        initial = "",
        onDismiss = onDismiss,
        onConfirm = { name ->
            container.scope.launch { container.db.presets().insert(PresetEntity(name = name, layers = MixCodec.encode(layers))) }
            onDismiss()
        },
    )
}
