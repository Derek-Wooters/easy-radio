package com.easyradio.app

import com.easyradio.core.media.PlaybackUiState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Regression coverage for a real bug report: the mini-player (pause/play control included) could
 * render nothing at all while a resync was slow or silently failed to resolve which station/
 * episode was playing, even though the session already knew something was active. See
 * [hasNowPlayingContent]'s doc for the full story.
 */
class NowPlayingVisibilityTest {

    @Test
    fun `nothing shows when idle and no station or episode is known`() {
        assertThat(hasNowPlayingContent(PlaybackUiState.IDLE, hasStation = false, hasEpisode = false)).isFalse()
    }

    @Test
    fun `shows once a station or episode is known, even if idle`() {
        assertThat(hasNowPlayingContent(PlaybackUiState.IDLE, hasStation = true, hasEpisode = false)).isTrue()
        assertThat(hasNowPlayingContent(PlaybackUiState.IDLE, hasStation = false, hasEpisode = true)).isTrue()
    }

    @Test
    fun `shows as soon as uiState is active, even before the station or episode resolves`() {
        // The actual bug this guards against: a resync in flight (or one that never resolves)
        // left currentStation/currentEpisode null, but the session already reported PLAYING.
        assertThat(hasNowPlayingContent(PlaybackUiState.PLAYING, hasStation = false, hasEpisode = false)).isTrue()
        assertThat(hasNowPlayingContent(PlaybackUiState.PAUSED, hasStation = false, hasEpisode = false)).isTrue()
        assertThat(hasNowPlayingContent(PlaybackUiState.BUFFERING, hasStation = false, hasEpisode = false)).isTrue()
        assertThat(hasNowPlayingContent(PlaybackUiState.ERROR, hasStation = false, hasEpisode = false)).isTrue()
    }
}
