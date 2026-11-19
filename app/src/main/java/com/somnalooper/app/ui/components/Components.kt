package com.somnalooper.app.ui.components

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.somnalooper.app.R
import com.somnalooper.app.ui.LocalContainer
import com.somnalooper.app.ui.formatClock
import com.somnalooper.app.ui.theme.Somna
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt

private const val ARC_START = 135f
private const val ARC_SWEEP = 270f

/**
 * Sound icon with a glowing circular volume ring. Tap toggles the sound; when active,
 * dragging along the ring with one finger sets its volume.
 */
@Composable
fun SoundTile(
    label: String,
    icon: ImageVector,
    active: Boolean,
    volume: Float,
    locked: Boolean,
    onToggle: () -> Unit,
    onVolume: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalContainer.current.settings.collectAsStateWithLifecycle().value.haptics
    val currentVolume by rememberUpdatedState(volume)
    val onVolumeState by rememberUpdatedState(onVolume)
    val onToggleState by rememberUpdatedState(onToggle)
    val onLongPressState by rememberUpdatedState(onLongPress)
    var dragging by remember { mutableStateOf(false) }
    val glow = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val stateText = if (active) stringResource(R.string.a11y_volume, (volume * 100).roundToInt()) else stringResource(R.string.a11y_off)

    Column(
        modifier.padding(vertical = 6.dp).semantics(mergeDescendants = true) { stateDescription = stateText },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(84.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onToggleState() },
                        onLongPress = { onLongPressState?.invoke() },
                    )
                }
                .pointerInput(active) {
                    if (!active) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = min(size.width, size.height) / 2f
                        if ((down.position - center).getDistance() < radius * 0.45f) return@awaitEachGesture
                        var started = false
                        var lastStep = (currentVolume * 20).roundToInt()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (!started && (change.position - down.position).getDistance() > viewConfiguration.touchSlop / 3) {
                                started = true
                                dragging = true
                            }
                            if (started) {
                                val v = angleToVolume(change.position - center)
                                onVolumeState(v)
                                val step = (v * 20).roundToInt()
                                if (step != lastStep && hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastStep = step
                                change.consume()
                            }
                        }
                        dragging = false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = 4.dp.toPx()
                val inset = stroke / 2 + 2.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                if (active) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(glow.copy(alpha = 0.28f + 0.2f * volume), Color.Transparent),
                            center = center, radius = size.minDimension / 2,
                        )
                    )
                }
                drawArc(track, ARC_START, ARC_SWEEP, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (active) {
                    drawArc(glow, ARC_START, ARC_SWEEP * volume.coerceIn(0.01f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            if (dragging) {
                Text("${(volume * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            } else {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp).alpha(if (locked) 0.45f else 1f),
                )
            }
            if (locked) {
                Icon(
                    Icons.Outlined.Lock, contentDescription = stringResource(R.string.pro_badge),
                    tint = Somna.Amber,
                    modifier = Modifier.align(Alignment.TopEnd).size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(96.dp),
        )
    }
}

/** Maps a touch position (relative to the dial centre) to 0..1 along the 270° ring. */
private fun angleToVolume(d: Offset): Float {
    val deg = (Math.toDegrees(atan2(d.y.toDouble(), d.x.toDouble())).toFloat() + 360f) % 360f
    var rel = (deg - ARC_START + 360f) % 360f
    if (rel > ARC_SWEEP) rel = if (rel > ARC_SWEEP + (360f - ARC_SWEEP) / 2) 0f else ARC_SWEEP
    return (rel / ARC_SWEEP).coerceIn(0f, 1f)
}

/** Bottom mini-player: mix name, live countdown and a large play/pause button easy to hit in the dark. */
@Composable
fun MiniPlayer(onOpenTimer: () -> Unit, onSave: () -> Unit, onOpenLayers: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val state by container.engine.state.collectAsStateWithLifecycle()
    container.tracks.collectAsStateWithLifecycle().value
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.timer) {
        while (state.timer != null) {
            now = android.os.SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val hasLayers = state.layers.isNotEmpty()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(enabled = hasLayers, onClick = onOpenLayers)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    if (hasLayers) container.mixTitle(context) else stringResource(R.string.mini_empty),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val timer = state.timer
                Text(
                    when {
                        timer != null -> stringResource(R.string.mini_timer, formatClock(timer.remainingMs(now)))
                        hasLayers -> stringResource(R.string.mini_layers, state.layers.size)
                        else -> stringResource(R.string.mini_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (timer != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onOpenLayers, enabled = hasLayers) {
                Icon(Icons.Outlined.Layers, contentDescription = stringResource(R.string.active_layers))
            }
            IconButton(onClick = onOpenTimer) {
                Icon(Icons.Outlined.Timer, contentDescription = stringResource(R.string.tab_timer))
            }
            IconButton(onClick = onSave, enabled = hasLayers) {
                Icon(Icons.Outlined.BookmarkAdd, contentDescription = stringResource(R.string.save_preset))
            }
            Spacer(Modifier.width(4.dp))
            FilledIconButton(
                onClick = { container.engine.togglePlay() },
                enabled = hasLayers,
                modifier = Modifier.size(60.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

/** Sheet listing every active layer with a classic slider (accessible alternative to the ring). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveLayersSheet(onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val state by container.engine.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.layers.isEmpty()) { if (state.layers.isEmpty()) onDismiss() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.active_layers), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { container.engine.clear(); onDismiss() }) { Text(stringResource(R.string.clear_all)) }
            }
            LazyColumn {
                items(state.layers.entries.toList(), key = { it.key }) { (key, vol) ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(container.layerLabel(context, key), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text("${(vol * 100).roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(onClick = { container.engine.removeLayer(key) }) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.remove))
                            }
                        }
                        Slider(value = vol, onValueChange = { container.engine.setLayer(key, it) })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Pro upsell / purchase dialog. */
@Composable
fun ProDialog(activity: Activity, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val isPro by container.isPro.collectAsStateWithLifecycle()
    val price by container.billing.price.collectAsStateWithLifecycle()
    var unavailable by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(stringResource(if (isPro) R.string.pro_active_title else R.string.pro_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.pro_benefit_sounds, R.string.pro_benefit_layers, R.string.pro_benefit_sensors, R.string.pro_benefit_support).forEach {
                    Row {
                        Text("✦ ", color = Somna.Amber)
                        Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (unavailable) {
                    Text(stringResource(R.string.pro_unavailable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (!isPro) {
                TextButton(onClick = {
                    if (!container.billing.launchPurchase(activity)) unavailable = true
                }) {
                    Text(price?.let { stringResource(R.string.pro_buy_price, it) } ?: stringResource(R.string.pro_buy))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/** Small section header. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

/** Small pill shown on Pro-only items. */
@Composable
fun ProPill() {
    Text(
        stringResource(R.string.pro_badge),
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF2E1800),
        modifier = Modifier
            .background(Somna.Amber, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

