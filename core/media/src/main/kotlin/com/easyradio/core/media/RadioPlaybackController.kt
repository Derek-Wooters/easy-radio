package com.easyradio.core.media

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.easyradio.core.model.RadioStation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RadioPlaybackController(
    private val player: Player,
    private val mediaItemFactory: (RadioStation) -> MediaItem = { MediaItem.fromUri(it.streamUrl) },
) {

    private val _state = MutableStateFlow(PlaybackUiState.IDLE)
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = updateState()
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = updateState()
        override fun onPlayerErrorChanged(error: PlaybackException?) = updateState()
    }

    init {
        player.addListener(listener)
    }

    fun play(station: RadioStation) {
        player.setMediaItem(mediaItemFactory(station))
        player.prepare()
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun stop() {
        player.stop()
    }

    fun release() {
        player.removeListener(listener)
        player.release()
    }

    private fun updateState() {
        _state.value = PlaybackStateMapper.map(
            playbackState = player.playbackState,
            playWhenReady = player.playWhenReady,
            hasError = player.playerError != null,
        )
    }
}
