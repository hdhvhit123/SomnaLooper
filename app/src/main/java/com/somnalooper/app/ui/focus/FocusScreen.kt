package com.somnalooper.app.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.somnalooper.app.R
import com.somnalooper.app.focus.FocusPhase
import com.somnalooper.app.focus.FocusPlan
import com.somnalooper.app.ui.LocalContainer
import com.somnalooper.app.ui.formatClock
import com.somnalooper.app.ui.timer.OptionCard
import com.somnalooper.app.ui.timer.ProgressRing
import kotlinx.coroutines.launch

@Composable
fun FocusScreen(onZen: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val focus by container.focus.state.collectAsStateWithLifecycle()
    val settings by container.settings.collectAsStateWithLifecycle()
    val isPro by container.isPro.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.focus_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(R.string.plan_classic, R.string.plan_deep).forEachIndexed { i, label ->
                SegmentedButton(
                    selected = focus.plan == FocusPlan.of(i),
                    enabled = !focus.active,
                    onClick = {
                        container.focus.setPlan(FocusPlan.of(i))
                        scope.launch { container.settingsRepo.setPomodoroPlan(i) }
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(stringResource(label)) }
            }
        }
        Spacer(Modifier.height(20.dp))
        ProgressRing(focus.progress, Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(
                        when (focus.phase) {
                            FocusPhase.WORK -> R.string.focus_phase_work
                            FocusPhase.BREAK -> R.string.focus_phase_break
                            FocusPhase.LONG_BREAK -> R.string.focus_phase_long_break
                        }
                    ),
                    color = if (focus.phase == FocusPhase.WORK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(formatClock(focus.remainingMs), style = MaterialTheme.typography.displayMedium)
                Text(
                    stringResource(R.string.focus_completed, focus.completedWork),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedIconButton(onClick = { container.focus.stop() }, enabled = focus.active) {
                Icon(Icons.Outlined.Stop, contentDescription = stringResource(R.string.stop))
            }
            val running = focus.active && !focus.paused
            FilledIconButton(
                onClick = { if (running) container.focus.pause() else container.focus.start() },
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
            ) {
                Icon(
                    if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (running) R.string.pause else R.string.play),
                    modifier = Modifier.size(40.dp),
                )
            }
            OutlinedIconButton(onClick = { container.focus.skip() }, enabled = focus.active) {
                Icon(Icons.Outlined.SkipNext, contentDescription = stringResource(R.string.skip))
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onZen) {
            Icon(Icons.Outlined.Fullscreen, contentDescription = null)
            Spacer(Modifier.padding(start = 8.dp))
            Text(stringResource(R.string.zen_open))
        }
        Spacer(Modifier.height(20.dp))
        OptionCard {
            Text(stringResource(R.string.focus_sounds_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(if (isPro) R.string.focus_sounds_work_pro else R.string.focus_sounds_work),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.focus_sounds_break), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }

    // keep plan in sync with the stored preference
    LaunchedEffect(settings.pomodoroPlan) {
        if (!container.focus.state.value.active) container.focus.setPlan(FocusPlan.of(settings.pomodoroPlan))
    }
}
