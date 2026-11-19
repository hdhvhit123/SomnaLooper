package com.somnalooper.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.somnalooper.app.audio.dsp.ChimeGen
import com.somnalooper.app.audio.dsp.Generator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class StopReason { TIMER, MOTION, BATTERY, FOCUS_LOSS }

data class SleepTimerState(
    val endAtElapsed: Long,
    val totalMs: Long,
    val fadeMs: Long,
    val fromMotion: Boolean = false,
) {
    fun remainingMs(now: Long = SystemClock.elapsedRealtime()) = (endAtElapsed - now).coerceAtLeast(0)
    fun isFading(now: Long = SystemClock.elapsedRealtime()) = remainingMs(now) <= fadeMs
}

data class EngineState(
    /** Layer key to slider volume (0..1), in insertion order. */
    val layers: Map<String, Float> = emptyMap(),
    val isPlaying: Boolean = false,
    val timer: SleepTimerState? = null,
    val mixName: String? = null,
    val lastStopReason: StopReason? = null,
    val errorKey: String? = null,
)

/** What a layer key resolves to. */
sealed interface LayerSource {
    data class Synth(val factory: (Int) -> Generator, val trim: Float) : LayerSource
    data class Stream(val uri: Uri) : LayerSource
}

enum class AddResult { OK, LIMIT, LOCKED, UNKNOWN }

/**
 * The multi-layer mixer.
 *
 * - Synthesized layers are rendered on a dedicated audio thread into a single float
 *   [AudioTrack] (one stream for all layers = low CPU and battery).
 * - Recorded layers (imported files / bundled OGG) use [CrossfadeLooper] (Media3 ExoPlayer).
 * - A sleep timer applies an exponential (linear-in-dB) fade-out before stopping.
 *
 * All public methods must be called on the main thread.
 */
class SoundEngine(
    private val context: Context,
    private val resolver: (String) -> LayerSource?,
    private val maxLayers: () -> Int,
    private val isLocked: (String) -> Boolean,
    private val crossfadeMs: () -> Long,
) {
    companion object {
        const val SAMPLE_RATE = 44100
        private const val BLOCK = 1024
        private const val TAG = "SoundEngine"
        const val DEFAULT_VOLUME = 0.6f
        private const val TICK_MS = 50L
    }

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val renderer = Renderer()
    private val streams = HashMap<String, CrossfadeLooper>()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SomnaLooper:playback")
        .apply { setReferenceCounted(false) }

    /** Ramp used for play/pause so starting and stopping never clicks. 0..1 */
    private var playRamp = 0f
    private var duck = 1f
    private var resumeOnFocusGain = false
    private var ticking = false

    /** Listeners notified when playback stops on its own (timer, motion, battery). */
    var onAutoStop: ((StopReason) -> Unit)? = null

    init {
        renderer.start()
    }

    // ------------------------------------------------------------------ public API

    fun toggleLayer(key: String): AddResult =
        if (_state.value.layers.containsKey(key)) { removeLayer(key); AddResult.OK } else setLayer(key, DEFAULT_VOLUME)

    /** Adds the layer if needed and sets its volume. */
    fun setLayer(key: String, volume: Float, rampMs: Int = 60): AddResult {
        val current = _state.value.layers
        if (!current.containsKey(key)) {
            if (isLocked(key)) return AddResult.LOCKED
            if (current.size >= maxLayers()) return AddResult.LIMIT
            val src = resolver(key) ?: return AddResult.UNKNOWN
            when (src) {
                is LayerSource.Synth -> renderer.post(Cmd.Add(key, src.factory(SAMPLE_RATE), src.trim, perceptual(volume), rampMs))
                is LayerSource.Stream -> {
                    val looper = CrossfadeLooper(context, src.uri, crossfadeMs) { e ->
                        Log.w(TAG, "stream error $key", e)
                        main.post { removeLayer(key); _state.update { it.copy(errorKey = key) } }
                    }
                    looper.targetVolume = perceptual(volume)
                    streams[key] = looper
                    if (_state.value.isPlaying) looper.play()
                }
            }
            _state.update { it.copy(layers = it.layers + (key to volume), mixName = null) }
            ensureTicking()
        } else {
            renderer.post(Cmd.Volume(key, perceptual(volume), rampMs))
            streams[key]?.targetVolume = perceptual(volume)
            _state.update { it.copy(layers = it.layers + (key to volume)) }
        }
        return AddResult.OK
    }

    fun removeLayer(key: String, rampMs: Int = 300, autoPause: Boolean = true) {
        renderer.post(Cmd.Remove(key, rampMs))
        streams.remove(key)?.let { looper ->
            looper.targetVolume = 0f
            main.postDelayed({ looper.release() }, rampMs.toLong() + 200)
        }
        _state.update { it.copy(layers = it.layers - key, mixName = null) }
        if (autoPause && _state.value.layers.isEmpty()) pause()
    }

    /** Replaces the whole mix with a crossfade (used by presets and Pomodoro). */
    fun setMix(mix: Map<String, Float>, name: String?, rampMs: Int = 1500) {
        val allowed = mix.filterKeys { !isLocked(it) && resolver(it) != null }.entries.take(maxLayers())
        val keep = allowed.map { it.key }.toSet()
        _state.value.layers.keys.filter { it !in keep }.forEach { removeLayer(it, rampMs, autoPause = false) }
        allowed.forEach { (k, v) -> setLayer(k, v, rampMs) }
        _state.update { it.copy(mixName = name) }
        if (_state.value.layers.isEmpty()) pause()
    }

    fun clear() {
        _state.value.layers.keys.toList().forEach { removeLayer(it) }
        _state.update { it.copy(mixName = null) }
    }

    fun togglePlay() = if (_state.value.isPlaying) pause() else play()

    fun play() {
        if (_state.value.layers.isEmpty()) return
        if (!requestFocus()) return
        _state.value.timer?.let { if (it.remainingMs() <= 0) cancelTimer() }
        _state.update { it.copy(isPlaying = true, lastStopReason = null, errorKey = null) }
        streams.values.forEach { it.play() }
        wakeLock.acquire(12 * 60 * 60 * 1000L)
        ensureTicking()
    }

    fun pause() {
        if (!_state.value.isPlaying) return
        _state.update { it.copy(isPlaying = false) }
        resumeOnFocusGain = false
        abandonFocus()
        ensureTicking() // finishes the fade-out ramp, then pauses streams and releases the wake lock
    }

    /** Pauses and clears the timer. */
    fun stop(reason: StopReason? = null) {
        _state.update { it.copy(lastStopReason = reason) }
        pause()
        _state.update { it.copy(timer = null) }
    }

    fun startTimer(minutes: Int, fadeMinutes: Int) {
        val total = minutes * 60_000L
        val fade = min(fadeMinutes * 60_000L, total)
        _state.update { it.copy(timer = SleepTimerState(SystemClock.elapsedRealtime() + total, total, fade)) }
        ensureTicking()
    }

    fun cancelTimer() = _state.update { it.copy(timer = null) }

    /** Starts the fade-out right now (motion detector), unless a timer would end sooner. */
    fun fadeOutNow(fadeMs: Long, fromMotion: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val existing = _state.value.timer
        if (existing != null && existing.endAtElapsed <= now + fadeMs) return
        _state.update { it.copy(timer = SleepTimerState(now + fadeMs, fadeMs, fadeMs, fromMotion)) }
        ensureTicking()
    }

    fun playChime() = renderer.post(Cmd.Chime)

    fun clearError() = _state.update { it.copy(errorKey = null) }

    fun release() {
        renderer.quit()
        streams.values.forEach { it.release() }
        streams.clear()
        if (wakeLock.isHeld) wakeLock.release()
    }

    // ------------------------------------------------------------------ internals

    /** Slider position to amplitude: squared curve feels linear to the ear. */
    private fun perceptual(v: Float) = v.coerceIn(0f, 1f).pow(2)

    /**
     * Exponential fade: -60 dB across the fade window (linear in decibels, which the ear
     * perceives as a smooth, even decrease), reaching true silence at the end.
     */
    private fun timerGain(now: Long): Float {
        val t = _state.value.timer ?: return 1f
        val remaining = t.remainingMs(now)
        if (remaining >= t.fadeMs || t.fadeMs <= 0) return if (remaining > 0) 1f else 0f
        val p = 1f - remaining.toFloat() / t.fadeMs
        return ((10f.pow(-3f * p) - 0.001f) / 0.999f).coerceIn(0f, 1f)
    }

    private fun ensureTicking() {
        if (ticking) return
        ticking = true
        main.post(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val s = _state.value
            val timer = s.timer
            if (timer != null && s.isPlaying && timer.remainingMs(now) <= 0) {
                val reason = if (timer.fromMotion) StopReason.MOTION else StopReason.TIMER
                stop(reason)
                onAutoStop?.invoke(reason)
            }
            val target = if (_state.value.isPlaying) 1f else 0f
            val step = TICK_MS / 300f
            playRamp = if (playRamp < target) min(target, playRamp + step) else max(target, playRamp - step)
            val master = playRamp * duck * timerGain(now)
            renderer.masterTarget = master
            renderer.wake()
            streams.values.forEach { it.tick(master) }

            val idle = !_state.value.isPlaying && playRamp == 0f
            if (idle) {
                streams.values.forEach { it.pause() }
                if (wakeLock.isHeld) wakeLock.release()
                ticking = false
            } else {
                main.postDelayed(this, TICK_MS)
            }
        }
    }

    // ------------------------------------------------------------------ audio focus

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        main.post {
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    resumeOnFocusGain = false
                    if (_state.value.isPlaying) { pause(); _state.update { it.copy(lastStopReason = StopReason.FOCUS_LOSS) } }
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    if (_state.value.isPlaying) {
                        _state.update { it.copy(isPlaying = false) }
                        resumeOnFocusGain = true
                        ensureTicking()
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { duck = 0.3f }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    duck = 1f
                    if (resumeOnFocusGain) {
                        resumeOnFocusGain = false
                        _state.update { it.copy(isPlaying = true) }
                        streams.values.forEach { it.play() }
                        ensureTicking()
                    }
                }
            }
        }
    }

    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setOnAudioFocusChangeListener(focusListener, main)
        .setWillPauseWhenDucked(false)
        .build()

    private fun requestFocus(): Boolean =
        audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_FAILED

    private fun abandonFocus() {
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    // ------------------------------------------------------------------ render thread

    private sealed interface Cmd {
        data class Add(val key: String, val gen: Generator, val trim: Float, val amp: Float, val rampMs: Int) : Cmd
        data class Volume(val key: String, val amp: Float, val rampMs: Int) : Cmd
        data class Remove(val key: String, val rampMs: Int) : Cmd
        data object Chime : Cmd
        data object Wake : Cmd
        data object Quit : Cmd
    }

    private class Voice(val key: String, val gen: Generator, val trim: Float) {
        var cur = 0f
        var target = 0f
        var step = 0f
        var remaining = 0
        var removing = false

        fun rampTo(amp: Float, samples: Int) {
            target = amp * trim
            remaining = max(1, samples)
            step = (target - cur) / remaining
        }
    }

    private inner class Renderer : Thread("SomnaMixer") {
        private val queue = LinkedBlockingQueue<Cmd>()
        @Volatile var masterTarget = 0f

        fun post(c: Cmd) { queue.offer(c) }
        fun wake() { if (queue.isEmpty()) queue.offer(Cmd.Wake) }
        fun quit() { queue.offer(Cmd.Quit) }

        private fun buildTrack(): AudioTrack {
            val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
            val bufBytes = max(minBuf * 2, (SAMPLE_RATE * 0.3f).toInt() * 2 * 4)
            return AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
                .build()
        }

        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            var track: AudioTrack? = null
            val voices = ArrayList<Voice>()
            val l = FloatArray(BLOCK)
            val r = FloatArray(BLOCK)
            val tl = FloatArray(BLOCK)
            val tr = FloatArray(BLOCK)
            val out = FloatArray(BLOCK * 2)
            var master = 0f
            var trackPlaying = false
            val sr = SAMPLE_RATE

            fun handle(c: Cmd): Boolean {
                when (c) {
                    is Cmd.Add -> {
                        val existing = voices.firstOrNull { it.key == c.key }
                        if (existing != null) {
                            existing.removing = false
                            existing.rampTo(c.amp, c.rampMs * sr / 1000)
                        } else {
                            voices += Voice(c.key, c.gen, c.trim).also { it.rampTo(c.amp, c.rampMs * sr / 1000) }
                        }
                    }
                    is Cmd.Volume -> voices.firstOrNull { it.key == c.key }?.rampTo(c.amp, c.rampMs * sr / 1000)
                    is Cmd.Remove -> voices.firstOrNull { it.key == c.key }?.let { it.removing = true; it.rampTo(0f, c.rampMs * sr / 1000) }
                    Cmd.Chime -> voices += Voice("__chime", ChimeGen(sr), 1f).also { it.cur = 1f; it.target = 1f }
                    Cmd.Wake -> Unit
                    Cmd.Quit -> return false
                }
                return true
            }

            try {
                loop@ while (true) {
                    while (true) {
                        val c = queue.poll() ?: break
                        if (!handle(c)) break@loop
                    }
                    val want = masterTarget
                    val audible = voices.isNotEmpty() && (want > 0.0001f || master > 0.0001f)
                    if (!audible) {
                        if (trackPlaying) {
                            track?.pause(); track?.flush(); trackPlaying = false
                        }
                        master = 0f
                        val c = queue.poll(1, TimeUnit.SECONDS) ?: continue@loop
                        if (!handle(c)) break@loop
                        continue@loop
                    }
                    if (track == null) track = buildTrack()
                    if (!trackPlaying) { track.play(); trackPlaying = true }

                    l.fill(0f); r.fill(0f)
                    val iter = voices.iterator()
                    while (iter.hasNext()) {
                        val v = iter.next()
                        tl.fill(0f); tr.fill(0f)
                        v.gen.render(tl, tr, BLOCK)
                        for (i in 0 until BLOCK) {
                            if (v.remaining > 0) { v.cur += v.step; v.remaining-- } else v.cur = v.target
                            l[i] += tl[i] * v.cur
                            r[i] += tr[i] * v.cur
                        }
                        val gen = v.gen
                        if ((v.removing && v.remaining == 0) || (gen is ChimeGen && gen.finished)) iter.remove()
                    }
                    // master gain ramps across the block; at most a full swing in 250 ms
                    val maxDelta = BLOCK / (0.25f * sr)
                    val end = master + (want - master).coerceIn(-maxDelta, maxDelta)
                    for (i in 0 until BLOCK) {
                        val g = master + (end - master) * (i.toFloat() / BLOCK)
                        out[2 * i] = softClip(l[i] * g)
                        out[2 * i + 1] = softClip(r[i] * g)
                    }
                    master = end
                    val written = track.write(out, 0, out.size, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) {
                        Log.w(TAG, "AudioTrack write error $written, rebuilding")
                        track.release(); track = null; trackPlaying = false
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "renderer crashed", t)
            } finally {
                track?.release()
            }
        }

        /** Gentle saturation instead of hard clipping when many layers are loud. */
        private fun softClip(x: Float): Float {
            val a = abs(x)
            if (a < 0.6f) return x
            val s = if (x < 0) -1f else 1f
            // smooth curve from 0.6 to an asymptote of 1.0
            val over = a - 0.6f
            return s * (0.6f + 0.4f * over / (over + 0.4f))
        }
    }
}
