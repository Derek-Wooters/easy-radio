package com.easyradio.app.playback

import android.support.v4.media.session.PlaybackStateCompat
import androidx.media3.common.Player
import com.easyradio.core.media.PlaybackUiState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LegacyPlaybackStateMapperTest {

    @Test
    fun `toCompatState reports error regardless of buffering or playWhenReady`() {
        val state = LegacyPlaybackStateMapper.toCompatState(
            hasError = true,
            playbackState = Player.STATE_BUFFERING,
            playWhenReady = true,
        )

        assertThat(state).isEqualTo(PlaybackStateCompat.STATE_ERROR)
    }

    @Test
    fun `toCompatState reports buffering ahead of playWhenReady`() {
        val state = LegacyPlaybackStateMapper.toCompatState(
            hasError = false,
            playbackState = Player.STATE_BUFFERING,
            playWhenReady = true,
        )

        assertThat(state).isEqualTo(PlaybackStateCompat.STATE_BUFFERING)
    }

    @Test
    fun `toCompatState maps idle to none, distinct from ended`() {
        val idle = LegacyPlaybackStateMapper.toCompatState(false, Player.STATE_IDLE, playWhenReady = false)
        val ended = LegacyPlaybackStateMapper.toCompatState(false, Player.STATE_ENDED, playWhenReady = false)

        assertThat(idle).isEqualTo(PlaybackStateCompat.STATE_NONE)
        assertThat(ended).isEqualTo(PlaybackStateCompat.STATE_STOPPED)
    }

    @Test
    fun `toCompatState maps ready state by playWhenReady`() {
        val playing = LegacyPlaybackStateMapper.toCompatState(false, Player.STATE_READY, playWhenReady = true)
        val paused = LegacyPlaybackStateMapper.toCompatState(false, Player.STATE_READY, playWhenReady = false)

        assertThat(playing).isEqualTo(PlaybackStateCompat.STATE_PLAYING)
        assertThat(paused).isEqualTo(PlaybackStateCompat.STATE_PAUSED)
    }

    @Test
    fun `toUiState maps a null state to idle`() {
        assertThat(LegacyPlaybackStateMapper.toUiState(null)).isEqualTo(PlaybackUiState.IDLE)
    }

    @Test
    fun `toUiState maps each known compat state`() {
        assertThat(LegacyPlaybackStateMapper.toUiState(PlaybackStateCompat.STATE_ERROR))
            .isEqualTo(PlaybackUiState.ERROR)
        assertThat(LegacyPlaybackStateMapper.toUiState(PlaybackStateCompat.STATE_BUFFERING))
            .isEqualTo(PlaybackUiState.BUFFERING)
        assertThat(LegacyPlaybackStateMapper.toUiState(PlaybackStateCompat.STATE_PLAYING))
            .isEqualTo(PlaybackUiState.PLAYING)
        assertThat(LegacyPlaybackStateMapper.toUiState(PlaybackStateCompat.STATE_PAUSED))
            .isEqualTo(PlaybackUiState.PAUSED)
    }

    @Test
    fun `toUiState falls back to idle for an unhandled compat state`() {
        assertThat(LegacyPlaybackStateMapper.toUiState(PlaybackStateCompat.STATE_NONE))
            .isEqualTo(PlaybackUiState.IDLE)
        assertThat(LegacyPlaybackStateMapper.toUiState(PlaybackStateCompat.STATE_STOPPED))
            .isEqualTo(PlaybackUiState.IDLE)
    }
}
