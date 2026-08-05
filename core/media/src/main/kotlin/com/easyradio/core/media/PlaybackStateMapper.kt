package com.easyradio.core.media

import androidx.media3.common.Player

object PlaybackStateMapper {

    fun map(playbackState: Int, playWhenReady: Boolean, hasError: Boolean): PlaybackUiState {
        if (hasError) return PlaybackUiState.ERROR

        return when (playbackState) {
            Player.STATE_BUFFERING -> PlaybackUiState.BUFFERING
            Player.STATE_READY -> if (playWhenReady) PlaybackUiState.PLAYING else PlaybackUiState.PAUSED
            Player.STATE_IDLE, Player.STATE_ENDED -> PlaybackUiState.IDLE
            else -> PlaybackUiState.IDLE
        }
    }
}
