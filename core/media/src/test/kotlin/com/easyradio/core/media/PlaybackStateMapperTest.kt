package com.easyradio.core.media

import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackStateMapperTest {

    @Test
    fun `idle state maps to Idle`() {
        val result = PlaybackStateMapper.map(
            playbackState = Player.STATE_IDLE,
            playWhenReady = false,
            hasError = false,
        )

        assertThat(result).isEqualTo(PlaybackUiState.IDLE)
    }

    @Test
    fun `buffering state maps to Buffering`() {
        val result = PlaybackStateMapper.map(
            playbackState = Player.STATE_BUFFERING,
            playWhenReady = true,
            hasError = false,
        )

        assertThat(result).isEqualTo(PlaybackUiState.BUFFERING)
    }

    @Test
    fun `ready and playWhenReady maps to Playing`() {
        val result = PlaybackStateMapper.map(
            playbackState = Player.STATE_READY,
            playWhenReady = true,
            hasError = false,
        )

        assertThat(result).isEqualTo(PlaybackUiState.PLAYING)
    }

    @Test
    fun `ready and not playWhenReady maps to Paused`() {
        val result = PlaybackStateMapper.map(
            playbackState = Player.STATE_READY,
            playWhenReady = false,
            hasError = false,
        )

        assertThat(result).isEqualTo(PlaybackUiState.PAUSED)
    }

    @Test
    fun `ended state maps to Idle`() {
        val result = PlaybackStateMapper.map(
            playbackState = Player.STATE_ENDED,
            playWhenReady = false,
            hasError = false,
        )

        assertThat(result).isEqualTo(PlaybackUiState.IDLE)
    }

    @Test
    fun `error overrides playback state and maps to Error`() {
        val result = PlaybackStateMapper.map(
            playbackState = Player.STATE_READY,
            playWhenReady = true,
            hasError = true,
        )

        assertThat(result).isEqualTo(PlaybackUiState.ERROR)
    }
}
