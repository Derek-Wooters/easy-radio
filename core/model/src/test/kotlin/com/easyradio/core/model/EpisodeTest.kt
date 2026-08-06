package com.easyradio.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EpisodeTest {

    @Test
    fun `valid episode constructs successfully`() {
        val episode = Episode(
            id = "guid-1",
            podcastId = "podcast-1",
            title = "Episode One",
            audioUrl = "https://example.com/ep1.mp3",
            publishedAtEpochMillis = 1_700_000_000_000L,
            durationSeconds = 3600,
            description = "First episode",
        )

        assertThat(episode.title).isEqualTo("Episode One")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank id is rejected`() {
        Episode(
            id = "",
            podcastId = "podcast-1",
            title = "Episode One",
            audioUrl = "https://example.com/ep1.mp3",
            publishedAtEpochMillis = null,
            durationSeconds = null,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-https audio url is rejected`() {
        Episode(
            id = "guid-1",
            podcastId = "podcast-1",
            title = "Episode One",
            audioUrl = "http://example.com/ep1.mp3",
            publishedAtEpochMillis = null,
            durationSeconds = null,
        )
    }
}
