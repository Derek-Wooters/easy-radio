package com.easyradio.app

import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Regression coverage for a real crash: constructing a placeholder Podcast with title = "" for an
 * episode whose podcast wasn't subscribed -- Podcast's own constructor rejects a blank title, so
 * this threw and took the whole process down. Both MainActivity call sites (the Up Next queue
 * screen, and resyncing now-playing state when the service auto-advances) go through
 * resolvePlayablePodcast now instead of constructing a placeholder inline.
 */
class PlayablePodcastTest {

    private val episode = Episode(
        id = "ep1",
        podcastId = "podcast1",
        title = "An Episode",
        audioUrl = "https://example.com/ep1.mp3",
        publishedAtEpochMillis = null,
        durationSeconds = 1_800,
    )

    @Test
    fun `resolves the real podcast when it's subscribed`() {
        val subscribed = Podcast(
            id = "podcast1",
            title = "Real Show",
            author = "Real Author",
            artworkUrl = "https://example.com/art.png",
            feedUrl = "https://example.com/feed.xml",
        )

        val result = resolvePlayablePodcast(episode, subscribedPodcasts = listOf(subscribed))

        assertThat(result).isEqualTo(subscribed)
    }

    @Test
    fun `falls back to a valid placeholder when the podcast isn't subscribed`() {
        val result = resolvePlayablePodcast(episode, subscribedPodcasts = emptyList())

        // The whole point: this must never throw the way Podcast(title = "") used to.
        assertThat(result.id).isEqualTo(episode.podcastId)
        assertThat(result.title).isNotEmpty()
    }

    @Test
    fun `doesn't match a subscribed podcast with a different id`() {
        val other = Podcast(
            id = "some-other-podcast",
            title = "Unrelated Show",
            author = "Someone Else",
            artworkUrl = null,
            feedUrl = "https://example.com/other.xml",
        )

        val result = resolvePlayablePodcast(episode, subscribedPodcasts = listOf(other))

        assertThat(result.id).isEqualTo(episode.podcastId)
        assertThat(result.title).isNotEmpty()
    }
}
