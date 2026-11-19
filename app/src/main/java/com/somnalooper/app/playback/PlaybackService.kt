package com.somnalooper.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.somnalooper.app.MainActivity
import com.somnalooper.app.R
import com.somnalooper.app.container
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground media playback service (FOREGROUND_SERVICE_MEDIA_PLAYBACK). Media3 promotes it to
 * the foreground with a media notification while sound is playing, so playback continues with
 * the screen off, and demotes it when paused (Android 14/15/16 compliant).
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: MixerSessionPlayer
    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        val c = container
        player = MixerSessionPlayer(
            looper = mainLooper,
            engine = c.engine,
            title = { c.mixTitle(this) },
            subtitle = { subtitle() },
        )
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setId("somnalooper")
            .setSessionActivity(openApp)
            .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) }
        )
        scope.launch {
            combine(c.engine.state, c.tracks) { s, _ -> s }.collect { player.refresh() }
        }
        // Refresh the remaining-time text in the notification once a minute.
        scope.launch {
            while (isActive) {
                delay(30_000)
                if (c.engine.state.value.timer != null) player.refresh()
            }
        }
    }

    private fun subtitle(): String? {
        val t = container.engine.state.value.timer ?: return getString(R.string.notif_subtitle_playing)
        val min = ((t.endAtElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0) + 59_999) / 60_000
        return getString(R.string.notif_timer_left, min.toInt())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!container.engine.state.value.isPlaying) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
