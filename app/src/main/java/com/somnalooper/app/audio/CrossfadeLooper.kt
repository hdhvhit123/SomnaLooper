package com.somnalooper.app.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Seamless looper for recorded audio (imported files or bundled OGG assets).
 *
 * Two ExoPlayer instances play the same file. Shortly before the active one reaches its end,
 * the second one starts from the beginning and the two are blended with an equal-power
 * crossfade, so the loop point never produces a click or a gap.
 *
 * Must be used from the main thread. [tick] is called by the engine roughly every 50 ms.
 */
class CrossfadeLooper(
    context: Context,
    uri: Uri,
    private val crossfadeMs: () -> Long,
    private val onError: (Throwable) -> Unit,
) {
    private val players = Array(2) { buildPlayer(context, uri) }
    private var active = 0
    private var fading = false

    /** Linear 0..1 volume after perceptual mapping, set by the engine. */
    var targetVolume = 0f
    private var currentVolume = 0f
    private var playing = false
    var released = false; private set

    private fun buildPlayer(context: Context, uri: Uri): ExoPlayer {
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        return ExoPlayer.Builder(context)
            .setAudioAttributes(attrs, /* handleAudioFocus = */ false)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_OFF
                volume = 0f
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) = onError(error)
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) handleEnded(this@apply)
                    }
                })
                prepare()
            }
    }

    private fun handleEnded(p: ExoPlayer) {
        if (released) return
        val idx = players.indexOf(p)
        if (idx == active) {
            // Ended without a crossfade (very short file or unknown duration): restart directly.
            if (fading) {
                finishFade()
            } else {
                p.seekTo(0)
                if (playing) p.play()
            }
        } else {
            p.pause(); p.seekTo(0)
        }
    }

    fun play() {
        if (released) return
        playing = true
        players[active].play()
        if (fading) players[1 - active].play()
    }

    fun pause() {
        if (released) return
        playing = false
        players.forEach { it.pause() }
    }

    /**
     * @param master overall gain (play/pause ramp, sleep timer fade, audio-focus ducking).
     */
    fun tick(master: Float) {
        if (released) return
        currentVolume += (targetVolume - currentVolume) * 0.35f
        val a = players[active]
        val b = players[1 - active]
        val dur = a.duration
        var fadeIn = 0f
        var fadeOut = 1f
        if (dur != C.TIME_UNSET && dur > 0 && playing) {
            val cf = crossfadeMs().coerceAtMost(dur / 3)
            val pos = a.currentPosition
            val fadeStart = dur - cf
            if (!fading && cf > 0 && pos >= fadeStart) {
                fading = true
                b.seekTo(0)
                b.volume = 0f
                b.play()
            }
            if (fading) {
                val f = ((pos - fadeStart).toFloat() / cf).coerceIn(0f, 1f)
                // equal-power curves keep perceived loudness constant through the seam
                fadeIn = sin(f * PI / 2).toFloat()
                fadeOut = cos(f * PI / 2).toFloat()
                if (f >= 1f) finishFade()
            }
        }
        val g = currentVolume * master
        if (fading) {
            players[active].volume = g * fadeOut
            players[1 - active].volume = g * fadeIn
        } else {
            players[active].volume = g
        }
    }

    private fun finishFade() {
        val old = players[active]
        old.pause()
        old.seekTo(0)
        old.volume = 0f
        active = 1 - active
        fading = false
    }

    fun release() {
        if (released) return
        released = true
        players.forEach { it.release() }
    }
}
