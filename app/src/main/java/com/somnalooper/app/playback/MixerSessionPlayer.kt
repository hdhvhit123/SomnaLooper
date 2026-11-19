package com.somnalooper.app.playback

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.somnalooper.app.audio.SoundEngine

/**
 * Exposes the mixer to Media3's MediaSession so the system shows the media notification,
 * lock-screen controls, Bluetooth / headset buttons and Android Auto style controllers.
 */
class MixerSessionPlayer(
    looper: Looper,
    private val engine: SoundEngine,
    private val title: () -> String,
    private val subtitle: () -> String?,
) : SimpleBasePlayer(looper) {

    private val commands = Player.Commands.Builder()
        .addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_STOP,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_RELEASE,
        ).build()

    fun refresh() = invalidateState()

    override fun getState(): State {
        val s = engine.state.value
        val metadata = MediaMetadata.Builder()
            .setTitle(title())
            .setArtist(subtitle())
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()
        val item = MediaItemData.Builder("somnalooper-mix")
            .setMediaItem(MediaItem.Builder().setMediaId("somnalooper-mix").setMediaMetadata(metadata).build())
            .setMediaMetadata(metadata)
            .setIsSeekable(false)
            .setIsDynamic(true)
            .setDurationUs(C.TIME_UNSET)
            .build()
        val hasLayers = s.layers.isNotEmpty()
        return State.Builder()
            .setAvailableCommands(commands)
            .setPlaylist(listOf(item))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(0)
            .setPlayWhenReady(s.isPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (hasLayers) Player.STATE_READY else Player.STATE_IDLE)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) engine.play() else engine.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        engine.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
}
