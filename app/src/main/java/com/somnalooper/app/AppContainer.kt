package com.somnalooper.app

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.somnalooper.app.audio.BatteryGuard
import com.somnalooper.app.audio.LayerSource
import com.somnalooper.app.audio.MotionDetector
import com.somnalooper.app.audio.SoundCatalog
import com.somnalooper.app.audio.SoundEngine
import com.somnalooper.app.audio.StopReason
import com.somnalooper.app.billing.BillingManager
import com.somnalooper.app.data.AppSettings
import com.somnalooper.app.data.MixCodec
import com.somnalooper.app.data.SettingsRepository
import com.somnalooper.app.data.db.AppDatabase
import com.somnalooper.app.data.db.PresetEntity
import com.somnalooper.app.data.db.SessionEntity
import com.somnalooper.app.data.db.TrackEntity
import com.somnalooper.app.focus.FocusController
import com.somnalooper.app.focus.FocusPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Simple manual dependency container living for the whole process. */
@OptIn(FlowPreview::class)
class AppContainer(private val app: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val db = AppDatabase.build(app)
    val settingsRepo = SettingsRepository(app)

    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())
    val tracks: StateFlow<List<TrackEntity>> = db.tracks().observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val presets: StateFlow<List<PresetEntity>> = db.presets().observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    val isPro: StateFlow<Boolean> = settings
        .map { it.proCached || (BuildConfig.DEBUG && it.devPro) }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Recorded replacements bundled in assets/sounds/<id>.ogg (optional). */
    private val assetOverrides: Set<String> = runCatching {
        app.assets.list("sounds")?.filter { it.endsWith(".ogg") }?.map { it.removeSuffix(".ogg") }?.toSet()
    }.getOrNull() ?: emptySet()

    val engine = SoundEngine(
        context = app,
        resolver = ::resolve,
        maxLayers = { if (isPro.value) MAX_LAYERS_PRO else MAX_LAYERS_FREE },
        isLocked = { key -> SoundCatalog.isPro(key) && !isPro.value },
        crossfadeMs = { settings.value.crossfadeMs.toLong() },
    )

    val focus = FocusController(
        engine = engine,
        scope = scope,
        workMix = {
            if (isPro.value) linkedMapOf("brown_noise" to 0.6f, "beta" to 0.35f) else linkedMapOf("brown_noise" to 0.65f)
        },
        breakMix = { linkedMapOf("stream" to 0.55f, "birds" to 0.5f) },
        phaseLabel = { phase ->
            app.getString(if (phase == FocusPhase.WORK) R.string.focus_phase_work else R.string.focus_phase_break)
        },
    )

    val billing = BillingManager(app) { owned -> scope.launch { settingsRepo.setProCached(owned) } }

    private val motion = MotionDetector(app) {
        engine.fadeOutNow(settings.value.fadeMinutes * 60_000L, fromMotion = true)
    }

    private val battery = BatteryGuard(app) { level ->
        engine.stop(StopReason.BATTERY)
        notifyBatteryStop(level)
    }

    private var sessionStart = 0L
    private var sessionStartWall = 0L

    fun resolve(key: String): LayerSource? {
        if (SoundCatalog.isUser(key)) {
            val id = SoundCatalog.userId(key) ?: return null
            val t = tracks.value.firstOrNull { it.id == id } ?: return null
            return LayerSource.Stream(Uri.parse(t.uri))
        }
        val def = SoundCatalog.get(key) ?: return null
        if (key in assetOverrides) return LayerSource.Stream(Uri.parse("asset:///sounds/$key.ogg"))
        return LayerSource.Synth(def.factory, def.trim)
    }

    /** Human readable label for a layer key. */
    fun layerLabel(context: Context, key: String): String {
        if (SoundCatalog.isUser(key)) {
            val id = SoundCatalog.userId(key)
            return tracks.value.firstOrNull { it.id == id }?.name ?: context.getString(R.string.cat_mine)
        }
        return SoundCatalog.get(key)?.let { context.getString(it.nameRes) } ?: key
    }

    fun mixTitle(context: Context): String {
        val s = engine.state.value
        s.mixName?.let { return it }
        if (s.layers.isEmpty()) return context.getString(R.string.app_name)
        return s.layers.keys.take(3).joinToString(" · ") { layerLabel(context, it) } +
            if (s.layers.size > 3) " +${s.layers.size - 3}" else ""
    }

    init {
        createChannels()
        billing.connect()

        // Restore the last mix once imported tracks are known (paused; never auto-plays).
        scope.launch {
            val saved = settingsRepo.settings.first().lastMix
            db.tracks().observeAll().first()
            val mix = MixCodec.decode(saved)
            if (mix.isNotEmpty() && engine.state.value.layers.isEmpty()) engine.setMix(mix, null, rampMs = 50)
        }

        // Persist the current mix.
        scope.launch {
            engine.state.map { it.layers }.distinctUntilChanged().drop(1).debounce(800).collect {
                settingsRepo.setLastMix(MixCodec.encode(it))
            }
        }

        // Motion + battery safeguards only run while playing.
        scope.launch {
            combine(engine.state.map { it.isPlaying }.distinctUntilChanged(), settings, isPro) { playing, s, pro ->
                Triple(playing, s, pro)
            }.collect { (playing, s, pro) ->
                if (playing && pro && s.motionEnabled) motion.start(s.motionMinutes, s.motionSensitivity) else motion.stop()
                if (playing && pro && s.batteryEnabled) battery.start(s.batteryThreshold) else battery.stop()
            }
        }

        // Listening history.
        scope.launch {
            engine.state.map { it.isPlaying }.distinctUntilChanged().collect { playing ->
                if (playing) {
                    sessionStart = SystemClock.elapsedRealtime()
                    sessionStartWall = System.currentTimeMillis()
                } else if (sessionStart > 0) {
                    val dur = SystemClock.elapsedRealtime() - sessionStart
                    sessionStart = 0
                    if (dur >= 60_000) {
                        val label = mixTitle(app)
                        val reason = engine.state.value.lastStopReason?.name
                        db.sessions().insert(SessionEntity(startedAt = sessionStartWall, endedAt = sessionStartWall + dur, label = label, stopReason = reason))
                    }
                }
            }
        }
    }

    private fun createChannels() {
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, app.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { setSound(null, null) }
        )
    }

    @SuppressLint("MissingPermission")
    private fun notifyBatteryStop(level: Int) {
        val pi = PendingIntent.getActivity(
            app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(app, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(app.getString(R.string.battery_stop_title))
            .setContentText(app.getString(R.string.battery_stop_text, level))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify(NOTIF_BATTERY, n) }
    }

    companion object {
        const val MAX_LAYERS_FREE = 3
        const val MAX_LAYERS_PRO = 8
        const val CHANNEL_ALERTS = "alerts"
        private const val NOTIF_BATTERY = 42
    }
}
