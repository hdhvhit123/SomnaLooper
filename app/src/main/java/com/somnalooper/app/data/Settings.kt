package com.somnalooper.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val fadeMinutes: Int = 10,
    val lastTimerMinutes: Int = 30,
    val motionEnabled: Boolean = false,
    val motionMinutes: Int = 20,
    val motionSensitivity: Int = 1,
    val batteryEnabled: Boolean = true,
    val batteryThreshold: Int = 15,
    val crossfadeMs: Int = 1500,
    val haptics: Boolean = true,
    val lastMix: String = "",
    val pomodoroPlan: Int = 0,
    val zenClockStyle: Int = 0,
    val zenEffect: Int = 0,
    val zenKeepScreenOn: Boolean = true,
    val proCached: Boolean = false,
    val devPro: Boolean = false,
    val onboarded: Boolean = false,
)

class SettingsRepository(private val context: Context) {
    private object K {
        val fade = intPreferencesKey("fade_minutes")
        val lastTimer = intPreferencesKey("last_timer_minutes")
        val motion = booleanPreferencesKey("motion_enabled")
        val motionMinutes = intPreferencesKey("motion_minutes")
        val motionSens = intPreferencesKey("motion_sensitivity")
        val battery = booleanPreferencesKey("battery_enabled")
        val batteryThreshold = intPreferencesKey("battery_threshold")
        val crossfade = intPreferencesKey("crossfade_ms")
        val haptics = booleanPreferencesKey("haptics")
        val lastMix = stringPreferencesKey("last_mix")
        val pomodoro = intPreferencesKey("pomodoro_plan")
        val zenClock = intPreferencesKey("zen_clock")
        val zenEffect = intPreferencesKey("zen_effect")
        val zenScreen = booleanPreferencesKey("zen_keep_screen")
        val pro = booleanPreferencesKey("pro_cached")
        val devPro = booleanPreferencesKey("dev_pro")
        val onboarded = booleanPreferencesKey("onboarded")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            fadeMinutes = p[K.fade] ?: 10,
            lastTimerMinutes = p[K.lastTimer] ?: 30,
            motionEnabled = p[K.motion] ?: false,
            motionMinutes = p[K.motionMinutes] ?: 20,
            motionSensitivity = p[K.motionSens] ?: 1,
            batteryEnabled = p[K.battery] ?: true,
            batteryThreshold = p[K.batteryThreshold] ?: 15,
            crossfadeMs = p[K.crossfade] ?: 1500,
            haptics = p[K.haptics] ?: true,
            lastMix = p[K.lastMix] ?: "",
            pomodoroPlan = p[K.pomodoro] ?: 0,
            zenClockStyle = p[K.zenClock] ?: 0,
            zenEffect = p[K.zenEffect] ?: 0,
            zenKeepScreenOn = p[K.zenScreen] ?: true,
            proCached = p[K.pro] ?: false,
            devPro = p[K.devPro] ?: false,
            onboarded = p[K.onboarded] ?: false,
        )
    }

    suspend fun setFadeMinutes(v: Int) = context.dataStore.edit { it[K.fade] = v }
    suspend fun setLastTimerMinutes(v: Int) = context.dataStore.edit { it[K.lastTimer] = v }
    suspend fun setMotionEnabled(v: Boolean) = context.dataStore.edit { it[K.motion] = v }
    suspend fun setMotionMinutes(v: Int) = context.dataStore.edit { it[K.motionMinutes] = v }
    suspend fun setMotionSensitivity(v: Int) = context.dataStore.edit { it[K.motionSens] = v }
    suspend fun setBatteryEnabled(v: Boolean) = context.dataStore.edit { it[K.battery] = v }
    suspend fun setBatteryThreshold(v: Int) = context.dataStore.edit { it[K.batteryThreshold] = v }
    suspend fun setCrossfadeMs(v: Int) = context.dataStore.edit { it[K.crossfade] = v }
    suspend fun setHaptics(v: Boolean) = context.dataStore.edit { it[K.haptics] = v }
    suspend fun setLastMix(v: String) = context.dataStore.edit { it[K.lastMix] = v }
    suspend fun setPomodoroPlan(v: Int) = context.dataStore.edit { it[K.pomodoro] = v }
    suspend fun setZenClockStyle(v: Int) = context.dataStore.edit { it[K.zenClock] = v }
    suspend fun setZenEffect(v: Int) = context.dataStore.edit { it[K.zenEffect] = v }
    suspend fun setZenKeepScreenOn(v: Boolean) = context.dataStore.edit { it[K.zenScreen] = v }
    suspend fun setProCached(v: Boolean) = context.dataStore.edit { it[K.pro] = v }
    suspend fun setDevPro(v: Boolean) = context.dataStore.edit { it[K.devPro] = v }
    suspend fun setOnboarded(v: Boolean) = context.dataStore.edit { it[K.onboarded] = v }
}
