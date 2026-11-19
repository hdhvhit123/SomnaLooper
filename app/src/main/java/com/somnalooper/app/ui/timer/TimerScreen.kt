package com.somnalooper.app.ui.timer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.somnalooper.app.R
import com.somnalooper.app.ui.LocalContainer
import com.somnalooper.app.ui.LocalUiActions
import com.somnalooper.app.ui.components.ProPill
import com.somnalooper.app.ui.formatClock
import com.somnalooper.app.ui.theme.Somna
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MAX_MINUTES = 600

@Composable
fun TimerScreen() {
    val container = LocalContainer.current
    val actions = LocalUiActions.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.collectAsStateWithLifecycle()
    val engineState by container.engine.state.collectAsStateWithLifecycle()
    val isPro by container.isPro.collectAsStateWithLifecycle()
    var minutes by remember { mutableFloatStateOf(settings.lastTimerMinutes.toFloat()) }
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    val timer = engineState.timer

    LaunchedEffect(timer) {
        while (timer != null) {
            now = android.os.SystemClock.elapsedRealtime()
            delay(500)
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.sleep_timer), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))

        if (timer != null) {
            val remaining = timer.remainingMs(now)
            val progress = if (timer.totalMs > 0) remaining.toFloat() / timer.totalMs else 0f
            ProgressRing(progress, Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatClock(remaining), style = MaterialTheme.typography.displayMedium)
                    Text(
                        stringResource(
                            when {
                                timer.fromMotion -> R.string.timer_motion_fading
                                timer.isFading(now) -> R.string.timer_fading
                                else -> R.string.timer_running
                            }
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    val left = (timer.remainingMs(now) / 60_000).toInt() + 15
                    container.engine.startTimer(left, settings.fadeMinutes)
                }) { Text(stringResource(R.string.add_15)) }
                Button(onClick = { container.engine.cancelTimer() }) { Text(stringResource(R.string.cancel_timer)) }
            }
        } else {
            RotaryDial(
                minutes = minutes,
                haptics = settings.haptics,
                onChange = { minutes = it },
                modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(15, 30, 45, 60, 120).forEach { m ->
                    FilterChip(
                        selected = minutes.roundToInt() == m,
                        onClick = { minutes = m.toFloat() },
                        label = { Text(if (m < 60) "${m}′" else "${m / 60}h") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val m = minutes.roundToInt().coerceIn(1, MAX_MINUTES)
                    container.engine.startTimer(m, settings.fadeMinutes)
                    if (!engineState.isPlaying) container.engine.play()
                    scope.launch { container.settingsRepo.setLastTimerMinutes(m) }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) { Text(stringResource(R.string.start_timer), style = MaterialTheme.typography.titleMedium) }
            if (engineState.layers.isEmpty()) {
                Text(stringResource(R.string.timer_no_sound_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }

        Spacer(Modifier.height(20.dp))
        OptionCard {
            Text(stringResource(R.string.fade_out_title), style = MaterialTheme.typography.titleMedium)
            Text(
                pluralStringResource(R.plurals.fade_minutes, settings.fadeMinutes, settings.fadeMinutes),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = settings.fadeMinutes.toFloat(), valueRange = 1f..15f, steps = 13,
                onValueChange = { v -> scope.launch { container.settingsRepo.setFadeMinutes(v.roundToInt()) } },
            )
        }
        Spacer(Modifier.height(12.dp))
        OptionCard {
            ToggleRow(
                title = stringResource(R.string.motion_title),
                subtitle = pluralStringResource(R.plurals.motion_desc, settings.motionMinutes, settings.motionMinutes),
                checked = isPro && settings.motionEnabled,
                pro = !isPro,
                onChange = { v -> if (!isPro) actions.showPro() else scope.launch { container.settingsRepo.setMotionEnabled(v) } },
            )
            if (isPro && settings.motionEnabled) {
                Slider(
                    value = settings.motionMinutes.toFloat(), valueRange = 5f..60f, steps = 10,
                    onValueChange = { v -> scope.launch { container.settingsRepo.setMotionMinutes((v / 5).roundToInt() * 5) } },
                )
                Text(stringResource(R.string.motion_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        OptionCard {
            ToggleRow(
                title = stringResource(R.string.battery_title),
                subtitle = stringResource(R.string.battery_desc, settings.batteryThreshold),
                checked = isPro && settings.batteryEnabled,
                pro = !isPro,
                onChange = { v -> if (!isPro) actions.showPro() else scope.launch { container.settingsRepo.setBatteryEnabled(v) } },
            )
            if (isPro && settings.batteryEnabled) {
                Slider(
                    value = settings.batteryThreshold.toFloat(), valueRange = 5f..30f, steps = 4,
                    onValueChange = { v -> scope.launch { container.settingsRepo.setBatteryThreshold((v / 5).roundToInt() * 5) } },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun OptionCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, pro: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                if (pro) { Spacer(Modifier.padding(start = 8.dp)); ProPill() }
            }
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Mechanical-style rotary knob: one full turn = 60 minutes, keep turning for more hours.
 * Emits a light haptic tick on each minute.
 */
@Composable
fun RotaryDial(minutes: Float, haptics: Boolean, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val tick = MaterialTheme.colorScheme.outline
    var value by remember { mutableFloatStateOf(minutes) }
    LaunchedEffect(minutes) { if (minutes.roundToInt() != value.roundToInt()) value = minutes }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(haptics) {
                var lastAngle = 0f
                detectDragGestures(
                    onDragStart = { p -> lastAngle = angleOf(p, size.width / 2f, size.height / 2f) },
                ) { change, _ ->
                    val a = angleOf(change.position, size.width / 2f, size.height / 2f)
                    var d = a - lastAngle
                    if (d > 180f) d -= 360f
                    if (d < -180f) d += 360f
                    lastAngle = a
                    val before = value.roundToInt()
                    value = (value + d / 6f).coerceIn(1f, MAX_MINUTES.toFloat())
                    val after = value.roundToInt()
                    if (after != before) {
                        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onChange(after.toFloat())
                    }
                    change.consume()
                }
            }
        ) {
            val r = size.minDimension / 2f
            val c = center
            val ringW = 10.dp.toPx()
            // knob body
            drawCircle(Brush.radialGradient(listOf(Somna.MidnightHigher, Somna.Midnight, Color.Black), c, r), r * 0.98f, c)
            // minute ticks
            for (i in 0 until 60) {
                val ang = Math.toRadians(i * 6.0 - 90.0)
                val long = i % 5 == 0
                val outer = r * 0.93f
                val inner = if (long) r * 0.83f else r * 0.88f
                drawLine(
                    if (long) tick else track,
                    Offset(c.x + cos(ang).toFloat() * inner, c.y + sin(ang).toFloat() * inner),
                    Offset(c.x + cos(ang).toFloat() * outer, c.y + sin(ang).toFloat() * outer),
                    strokeWidth = if (long) 3f else 2f,
                    cap = StrokeCap.Round,
                )
            }
            val arcR = r * 0.72f
            val tl = Offset(c.x - arcR, c.y - arcR)
            val sz = Size(arcR * 2, arcR * 2)
            drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(ringW))
            val hours = (value / 60f).toInt()
            val frac = (value % 60f) / 60f
            if (hours > 0) drawArc(primary.copy(alpha = 0.35f), 0f, 360f, false, tl, sz, style = Stroke(ringW))
            drawArc(primary, -90f, 360f * frac, false, tl, sz, style = Stroke(ringW, cap = StrokeCap.Round))
            // pointer
            rotate(frac * 360f, c) {
                drawCircle(primary, 9.dp.toPx(), Offset(c.x, c.y - arcR))
                drawCircle(Color.Black, 4.dp.toPx(), Offset(c.x, c.y - arcR))
            }
        }
        val m = value.roundToInt()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (m >= 60) String.format(java.util.Locale.ROOT, "%d:%02d", m / 60, m % 60) else "$m",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
            )
            Text(
                stringResource(if (m >= 60) R.string.hours_minutes else R.string.minutes_short),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.dial_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun angleOf(p: Offset, cx: Float, cy: Float): Float =
    Math.toDegrees(atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())).toFloat()

@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 10.dp.toPx()
            val r = size.minDimension / 2f - w
            val tl = Offset(center.x - r, center.y - r)
            drawCircle(Brush.radialGradient(listOf(Somna.Midnight, Color.Black), center, r), r, center)
            drawArc(track, 0f, 360f, false, tl, Size(r * 2, r * 2), style = Stroke(w))
            drawArc(primary, -90f, 360f * progress.coerceIn(0f, 1f), false, tl, Size(r * 2, r * 2), style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}
