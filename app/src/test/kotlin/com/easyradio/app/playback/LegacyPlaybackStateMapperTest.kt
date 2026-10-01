package com.easyradio.app.playback

import android.support.v4.media.session.PlaybackStateCompat
import androidx.media3.common.Player
import com.easyradio.core.media.PlaybackUiState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LegacyPlaybackStateMapperTest {

    @Test
    fun `toCompatState reports error regardless of buffering or isPlaying`() {
        val state = LegacyPlaybackStateMapper.toCompatState(
            hasError = true,
            playbackState = Player.STATE_BUFFERING,
            isPlaying = true,
        )

        assertThat(state).isEqualTo(PlaybackStateCompat.STATE_ERROR)
    }

    @Test
    fun `toCompatState reports buffering ahead of isPlaying`() {
        val state = LegacyPlaybackStateMapper.toCompatState(
            hasError = false,
            playbackState = Player.STATE_BUFFERING,
            isPlaying = true,
        )

        assertThat(state).isEqualTo(PlaybackStateCompat.STATE_BUFFERING)
    }

    @Test
    fun `toCompatState maps idle to none, distinct from ended`() {
        val idle = LegacyPlaybackStateMapper.toCompatState(false, Player.STATE_IDLE, isPlaying = false)
        val ended = LegacyPlaybackStateMapper.toCompatState(false, Player.STATE_ENDED, isPlaying = false)

        assertThat(idle).isEqualTo(PlaybackStateCompat.STATE_NONE)
        assertThat(ended).isEqualTo(PlaybackStateCompat.STATE_STOPPED)
    }

    @Test
    fun `toCompatState maps ready state by isPlaying`() {
        val playing = LegacyPlaybackStateMapper.toCompatState(false, Player.STATE_READY, isPlaying = true)
        val paused = LegacyPlaybackStateMapper.toCompatState(false, Player.STATE_READY, isPlaying = false)

        assertThat(playing).isEqualTo(PlaybackStateCompat.STATE_PLAYING)
        assertThat(paused).isEqualTo(PlaybackStateCompat.STATE_PAUSED)
    }

    @Test
    fun `toCompatState reports paused during a transient audio-focus suppression despite playWhenReady staying true`() {
        // Regression test for a real bug report: ExoPlayer keeps playWhenReady=true during a
        // transient focus loss it expects to auto-recover from (another app briefly taking
        // audio focus), only setting playbackSuppressionReason -- so isPlaying (which already
        // accounts for suppression) is what must drive this, not playWhenReady. Confirmed
        // on-device: another app taking focus left the pause icon showing while the stream was
        // genuinely silent.
        val state = LegacyPlaybackStateMapper.toCompatState(
            hasError = false,
            playbackState = Player.STATE_READY,
            isPlaying = false, // player.isPlaying is false even though playWhenReady stayed true
        )

        assertThat(state).isEqualTo(PlaybackStateCompat.STATE_PAUSED)
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
