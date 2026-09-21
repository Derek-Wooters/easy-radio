package com.easyradio.app.ui

import com.easyradio.core.model.Episode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PodcastsScreenTest {

    private fun episode(positionMs: Long, durationSeconds: Int?) = Episode(
        id = "ep-1",
        podcastId = "p1",
        title = "Episode",
        audioUrl = "https://example.com/ep1.mp3",
        publishedAtEpochMillis = null,
        durationSeconds = durationSeconds,
        positionMs = positionMs,
    )

    @Test
    fun `episodeListenState is LISTEN when nothing has been played`() {
        assertThat(episodeListenState(episode(positionMs = 0, durationSeconds = 1_800)))
            .isEqualTo(EpisodeListenState.LISTEN)
    }

    @Test
    fun `episodeListenState is LISTEN when duration is unknown, regardless of position`() {
        assertThat(episodeListenState(episode(positionMs = 60_000, durationSeconds = null)))
            .isEqualTo(EpisodeListenState.LISTEN)
    }

    @Test
    fun `episodeListenState is RESUME partway through`() {
        // 10 min listened of a 30 min episode -- the exact scenario the user described.
        assertThat(episodeListenState(episode(positionMs = 10 * 60_000L, durationSeconds = 30 * 60)))
            .isEqualTo(EpisodeListenState.RESUME)
    }

    @Test
    fun `episodeListenState is REPLAY once fully played`() {
        assertThat(episodeListenState(episode(positionMs = 30 * 60_000L, durationSeconds = 30 * 60)))
            .isEqualTo(EpisodeListenState.REPLAY)
    }

    @Test
    fun `episodeListenState is REPLAY within the finished tolerance, not just at exact duration`() {
        // Players commonly report a position a few seconds short of the true end at natural
        // completion; 98% through should already read as finished, not stuck on RESUME forever.
        val durationSeconds = 30 * 60
        val positionMs = (durationSeconds * 1000L * 0.98).toLong()

        assertThat(episodeListenState(episode(positionMs = positionMs, durationSeconds = durationSeconds)))
            .isEqualTo(EpisodeListenState.REPLAY)
    }

    @Test
    fun `episodeProgressLabel is null when nothing has been played`() {
        assertThat(episodeProgressLabel(episode(positionMs = 0, durationSeconds = 1_800))).isNull()
    }

    @Test
    fun `episodeProgressLabel is null when duration is unknown`() {
        assertThat(episodeProgressLabel(episode(positionMs = 60_000, durationSeconds = null))).isNull()
    }

    @Test
    fun `episodeProgressLabel shows listened versus total time`() {
        val label = episodeProgressLabel(episode(positionMs = 10 * 60_000L, durationSeconds = 30 * 60))

        assertThat(label).isEqualTo("10 min of 30 min")
    }

    @Test
    fun `episodeProgressLabel formats hour-plus durations`() {
        val label = episodeProgressLabel(episode(positionMs = 90 * 60_000L, durationSeconds = 2 * 3600))

        assertThat(label).isEqualTo("1 hr 30 min of 2 hr")
    }

    @Test
    fun `formatEpisodeDuration formats minutes only, hours only, and hours plus minutes`() {
        assertThat(formatEpisodeDuration(45 * 60)).isEqualTo("45 min")
        assertThat(formatEpisodeDuration(2 * 3600)).isEqualTo("2 hr")
        assertThat(formatEpisodeDuration(2 * 3600 + 15 * 60)).isEqualTo("2 hr 15 min")
    }
}
