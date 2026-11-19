package com.somnalooper.app.ui.zen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.somnalooper.app.R
import com.somnalooper.app.focus.FocusPhase
import com.somnalooper.app.ui.LocalContainer
import com.somnalooper.app.ui.theme.Somna
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.sin

private val DimText = Somna.Moon.copy(alpha = 0.55f)

/**
 * Zen Ambient Display: minimal OLED-black screen with a dim retro flip clock or hourglass
 * and slow rain / water ripple motion. Double-tap to unlock and leave.
 */
@Composable
fun ZenScreen(onExit: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.collectAsStateWithLifecycle()
    val focus by container.focus.state.collectAsStateWithLifecycle()
    val engine by container.engine.state.collectAsStateWithLifecycle()
    var frameMs by remember { mutableLongStateOf(0L) }
    var showHint by remember { mutableStateOf(true) }

    BackHandler(onBack = onExit)

    DisposableEffect(settings.zenKeepScreenOn) {
        val activity = context.findActivity()
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        val oldBrightness = window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window?.let { w ->
            w.attributes = w.attributes.apply { screenBrightness = 0.08f }
            if (settings.zenKeepScreenOn) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.let { w ->
                w.attributes = w.attributes.apply { screenBrightness = oldBrightness }
                w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) withFrameMillis { frameMs = it }
    }
    LaunchedEffect(showHint) {
        if (showHint) { delay(4000); showHint = false }
    }

    val nowElapsed = android.os.SystemClock.elapsedRealtime() + frameMs * 0 // recomposed every frame
    val timer = engine.timer
    val label: String?
    val display: String
    val progress: Float
    when {
        focus.active -> {
            display = mmss(focus.remainingMs)
            label = stringResource(if (focus.phase == FocusPhase.WORK) R.string.focus_phase_work else R.string.focus_phase_break)
            progress = focus.progress
        }
        timer != null -> {
            val rem = timer.remainingMs(nowElapsed)
            display = mmss(rem)
            label = stringResource(R.string.sleep_timer)
            progress = if (timer.totalMs > 0) 1f - rem.toFloat() / timer.totalMs else 0f
        }
        else -> {
            val cal = Calendar.getInstance()
            display = String.format(java.util.Locale.ROOT, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            label = null
            progress = cal.get(Calendar.SECOND) / 60f
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onExit() },
                    onTap = { showHint = true },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val t = frameMs / 1000f
        if (settings.zenEffect == 0) RainEffect(t) else RippleEffect(t)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            label?.let { Text(it.uppercase(), color = DimText, style = MaterialTheme.typography.labelLarge, letterSpacing = 3.sp) }
            Spacer(Modifier.height(12.dp))
            if (settings.zenClockStyle == 0) FlipClock(display) else Hourglass(progress, t, display)
        }

        AnimatedVisibility(showHint, enter = fadeIn(), exit = fadeOut(tween(1200)), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 48.dp)) {
                Text(stringResource(R.string.zen_hint), color = DimText, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        showHint = true
                        scope.launch { container.settingsRepo.setZenClockStyle(1 - settings.zenClockStyle) }
                    }) { Text(stringResource(R.string.zen_switch_clock), color = DimText) }
                    TextButton(onClick = {
                        showHint = true
                        scope.launch { container.settingsRepo.setZenEffect(1 - settings.zenEffect) }
                    }) { Text(stringResource(R.string.zen_switch_effect), color = DimText) }
                }
            }
        }
    }
}

private fun mmss(ms: Long): String {
    val s = (ms + 999) / 1000
    val h = s / 3600
    return if (h > 0) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, (s % 3600) / 60, s % 60)
    else String.format(java.util.Locale.ROOT, "%02d:%02d", s / 60, s % 60)
}

/** Retro split-flap style clock: each digit flips when it changes. */
@Composable
private fun FlipClock(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        text.forEach { ch ->
            if (ch == ':') {
                Text(":", color = DimText, fontSize = 48.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 2.dp))
            } else {
                FlipDigit(ch)
            }
        }
    }
}

@Composable
private fun FlipDigit(ch: Char) {
    Box(
        Modifier
            .size(width = 58.dp, height = 84.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0B0F1E)),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = ch,
            transitionSpec = {
                (slideInVertically(tween(450)) { -it / 2 } + fadeIn(tween(450))) togetherWith
                    (slideOutVertically(tween(450)) { it / 2 } + fadeOut(tween(300)))
            },
            label = "flip",
        ) { c ->
            Text(c.toString(), color = DimText, fontSize = 56.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light)
        }
        // the split line of a flip card
        Box(Modifier.align(Alignment.Center).width(58.dp).height(2.dp).background(Color.Black))
    }
}

/** Misty hourglass whose sand follows the session progress. */
@Composable
private fun Hourglass(progress: Float, t: Float, text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(width = 140.dp, height = 200.dp)) {
            val w = size.width
            val h = size.height
            val glass = Somna.Glow.copy(alpha = 0.25f)
            val sand = Somna.Amber.copy(alpha = 0.45f)
            val p = progress.coerceIn(0f, 1f)
            val outline = Path().apply {
                moveTo(0f, 0f); lineTo(w, 0f); lineTo(w / 2 + 6f, h / 2); lineTo(w, h); lineTo(0f, h); lineTo(w / 2 - 6f, h / 2); close()
            }
            drawPath(outline, glass, style = Stroke(2.dp.toPx()))
            // top sand: triangle shrinking towards the neck
            val topLevel = h / 2 * p
            val topHalfWidth = (w / 2) * (1 - topLevel / (h / 2))
            if (p < 1f) {
                drawPath(Path().apply {
                    moveTo(w / 2 - topHalfWidth, topLevel); lineTo(w / 2 + topHalfWidth, topLevel); lineTo(w / 2, h / 2); close()
                }, sand)
            }
            // bottom pile grows
            val pileH = h / 2 * p
            if (p > 0f) {
                val halfBase = w / 2 * (pileH / (h / 2)).coerceAtLeast(0.15f)
                drawPath(Path().apply {
                    moveTo(w / 2 - halfBase, h); lineTo(w / 2 + halfBase, h); lineTo(w / 2, h - pileH); close()
                }, sand)
            }
            // falling stream with drifting grains
            if (p < 1f) {
                drawLine(sand, Offset(w / 2, h / 2), Offset(w / 2, h - pileH), strokeWidth = 1.5f)
                for (i in 0 until 4) {
                    val y = h / 2 + ((t * 60f + i * 25f) % (h / 2 - pileH).coerceAtLeast(1f))
                    drawCircle(sand, 2f, Offset(w / 2 + sin(t * 3 + i) * 1.5f, y))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(text, color = DimText, fontSize = 40.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light)
    }
}

/** Slow, faint falling rain. Positions are pure functions of time: no allocations per frame. */
@Composable
private fun RainEffect(t: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val n = 70
        for (i in 0 until n) {
            val seed = i * 9301 + 49297
            val x = ((seed * 233) % 1000) / 1000f * size.width
            val speed = 60f + (seed % 97)
            val len = 12f + (seed % 23)
            val y = ((seed % 1000) / 1000f * size.height + t * speed) % (size.height + len) - len
            val alpha = 0.06f + ((seed / 7) % 10) / 100f
            drawLine(
                Somna.Glow.copy(alpha = alpha),
                Offset(x, y), Offset(x - 2f, y + len),
                strokeWidth = 1.5f, cap = StrokeCap.Round,
            )
        }
    }
}

/** Expanding water ripples that fade out slowly. */
@Composable
private fun RippleEffect(t: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val count = 6
        for (i in 0 until count) {
            val period = 9f + i * 1.7f
            val phase = (t + i * 2.3f) % period
            val cycle = ((t + i * 2.3f) / period).toInt()
            val h = (cycle * 7919 + i * 104729) and 0x7fffffff
            val cx = (h % 1000) / 1000f * size.width
            val cy = ((h / 1000) % 1000) / 1000f * size.height
            val k = phase / period
            val r = k * size.minDimension * 0.45f
            val a = (1f - k) * 0.12f
            drawCircle(Somna.Teal.copy(alpha = a), r, Offset(cx, cy), style = Stroke(1.5f))
            drawCircle(Somna.Teal.copy(alpha = a * 0.6f), r * 0.7f, Offset(cx, cy), style = Stroke(1f))
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
