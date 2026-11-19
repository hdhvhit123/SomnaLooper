package com.somnalooper.app.ui.settings

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.somnalooper.app.BuildConfig
import com.somnalooper.app.R
import com.somnalooper.app.ui.LocalContainer
import com.somnalooper.app.ui.components.SectionTitle
import com.somnalooper.app.ui.timer.OptionCard
import com.somnalooper.app.ui.timer.ToggleRow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Languages shipped in res/values-xx. Names are shown in their own language. Empty tag = follow the phone. */
private val LANGUAGES = listOf(
    "" to null,
    "en" to "English",
    "vi" to "Tiếng Việt",
    "es" to "Español",
    "fr" to "Français",
    "de" to "Deutsch",
    "pt-BR" to "Português (Brasil)",
    "ru" to "Русский",
    "ja" to "日本語",
    "ko" to "한국어",
    "zh-CN" to "简体中文",
    "id" to "Bahasa Indonesia",
    "th" to "ไทย",
)

@Composable
fun SettingsScreen(activity: Activity, onHistory: () -> Unit, onPro: () -> Unit, onZen: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.collectAsStateWithLifecycle()
    val isPro by container.isPro.collectAsStateWithLifecycle()
    var showLanguage by remember { mutableStateOf(false) }
    val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',')
    val currentName = LANGUAGES.firstOrNull { it.first.equals(currentTag, ignoreCase = true) && it.first.isNotEmpty() }?.second
        ?: stringResource(R.string.language_system)

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) {
        item { Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium) }

        item { SectionTitle(stringResource(R.string.section_general)) }
        item {
            OptionCard {
                NavRow(stringResource(R.string.language), currentName) { showLanguage = true }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ToggleRow(stringResource(R.string.haptics), stringResource(R.string.haptics_desc), settings.haptics) { v ->
                    scope.launch { container.settingsRepo.setHaptics(v) }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.section_playback)) }
        item {
            OptionCard {
                Text(stringResource(R.string.crossfade_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.crossfade_desc, settings.crossfadeMs / 1000f),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = settings.crossfadeMs.toFloat(), valueRange = 500f..2000f, steps = 5,
                    onValueChange = { v -> scope.launch { container.settingsRepo.setCrossfadeMs(((v / 250f).roundToInt() * 250)) } },
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(R.string.fade_out_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    pluralStringResource(R.plurals.fade_minutes, settings.fadeMinutes, settings.fadeMinutes),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = settings.fadeMinutes.toFloat(), valueRange = 1f..15f, steps = 13,
                    onValueChange = { v -> scope.launch { container.settingsRepo.setFadeMinutes(v.roundToInt()) } },
                )
            }
        }

        item { SectionTitle(stringResource(R.string.section_sleep_safety)) }
        item {
            OptionCard {
                ToggleRow(
                    stringResource(R.string.motion_title),
                    pluralStringResource(R.plurals.motion_desc, settings.motionMinutes, settings.motionMinutes),
                    isPro && settings.motionEnabled, pro = !isPro,
                ) { v -> if (!isPro) onPro() else scope.launch { container.settingsRepo.setMotionEnabled(v) } }
                if (isPro && settings.motionEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.motion_sensitivity), style = MaterialTheme.typography.bodyMedium)
                    Row {
                        listOf(R.string.sens_low, R.string.sens_medium, R.string.sens_high).forEachIndexed { i, res ->
                            Row(
                                Modifier.weight(1f).clickable { scope.launch { container.settingsRepo.setMotionSensitivity(i) } },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = settings.motionSensitivity == i, onClick = { scope.launch { container.settingsRepo.setMotionSensitivity(i) } })
                                Text(stringResource(res), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ToggleRow(
                    stringResource(R.string.battery_title),
                    stringResource(R.string.battery_desc, settings.batteryThreshold),
                    isPro && settings.batteryEnabled, pro = !isPro,
                ) { v -> if (!isPro) onPro() else scope.launch { container.settingsRepo.setBatteryEnabled(v) } }
            }
        }

        item { SectionTitle(stringResource(R.string.section_zen)) }
        item {
            OptionCard {
                ToggleRow(stringResource(R.string.zen_keep_screen), stringResource(R.string.zen_keep_screen_desc), settings.zenKeepScreenOn) { v ->
                    scope.launch { container.settingsRepo.setZenKeepScreenOn(v) }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                NavRow(stringResource(R.string.zen_open), null, onZen)
            }
        }

        item { SectionTitle(stringResource(R.string.section_pro)) }
        item {
            OptionCard {
                NavRow(
                    stringResource(if (isPro) R.string.pro_active_title else R.string.pro_title),
                    stringResource(if (isPro) R.string.pro_active_desc else R.string.pro_desc),
                    onPro,
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                NavRow(stringResource(R.string.restore_purchases), null) { container.billing.refreshPurchases() }
                if (BuildConfig.DEBUG) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    ToggleRow(stringResource(R.string.dev_pro), stringResource(R.string.dev_pro_desc), settings.devPro) { v ->
                        scope.launch { container.settingsRepo.setDevPro(v) }
                    }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.section_data)) }
        item {
            OptionCard {
                NavRow(stringResource(R.string.history_title), stringResource(R.string.history_desc), onHistory)
            }
        }

        item { SectionTitle(stringResource(R.string.section_about)) }
        item {
            OptionCard {
                Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.privacy_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showLanguage) {
        AlertDialog(
            onDismissRequest = { showLanguage = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.language)) },
            text = {
                LazyColumn {
                    items(LANGUAGES) { (tag, name) ->
                        val selected = if (tag.isEmpty()) currentTag.isEmpty() else tag.equals(currentTag, ignoreCase = true)
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                showLanguage = false
                                AppCompatDelegate.setApplicationLocales(
                                    if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
                                )
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(Modifier.padding(start = 8.dp))
                            Text(name ?: stringResource(R.string.language_system))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguage = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun NavRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val sessionsFlow = remember { container.db.sessions().observeRecent() }
    val sessions by sessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
    val weekMinutes = sessions.filter { it.startedAt >= weekAgo }.sumOf { (it.endedAt - it.startedAt) / 60_000 }
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back)) }
                Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (sessions.isNotEmpty()) {
                    TextButton(onClick = { scope.launch { container.db.sessions().clear() } }) { Text(stringResource(R.string.clear_all)) }
                }
            }
        }
        item {
            OptionCard {
                Text(stringResource(R.string.history_week), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    String.format(Locale.ROOT, "%d:%02d", weekMinutes / 60, weekMinutes % 60),
                    style = MaterialTheme.typography.displaySmall,
                )
                Text(stringResource(R.string.hours_minutes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (sessions.isEmpty()) {
            item { Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(sessions, key = { it.id }) { s ->
            val mins = ((s.endedAt - s.startedAt) / 60_000).toInt()
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(s.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    df.format(Date(s.startedAt)) + " · " + pluralStringResource(R.plurals.minutes, mins, mins) +
                        when (s.stopReason) {
                            "MOTION" -> " · " + stringResource(R.string.reason_motion)
                            "BATTERY" -> " · " + stringResource(R.string.reason_battery)
                            "TIMER" -> " · " + stringResource(R.string.reason_timer)
                            else -> ""
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
