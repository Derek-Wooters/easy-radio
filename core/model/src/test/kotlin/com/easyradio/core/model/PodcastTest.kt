package com.easyradio.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PodcastTest {

    @Test
    fun `valid podcast constructs successfully`() {
        val podcast = Podcast(
            id = "https://example.com/feed.xml",
            title = "Test Show",
            author = "Test Author",
            artworkUrl = "https://example.com/art.png",
            feedUrl = "https://example.com/feed.xml",
        )

        assertThat(podcast.title).isEqualTo("Test Show")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank id is rejected`() {
        Podcast(id = "", title = "Test", author = "Author", artworkUrl = null, feedUrl = "https://example.com/feed.xml")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank title is rejected`() {
        Podcast(id = "id", title = "", author = "Author", artworkUrl = null, feedUrl = "https://example.com/feed.xml")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-https feed url is rejected`() {
        Podcast(id = "id", title = "Test", author = "Author", artworkUrl = null, feedUrl = "http://example.com/feed.xml")
    }
}
