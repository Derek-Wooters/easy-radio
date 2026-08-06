package com.easyradio.core.network.podcast

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ItunesPodcastMapperTest {

    @Test
    fun `valid dto maps to a Podcast`() {
        val dto = ItunesPodcastDto(
            collectionName = "Test Show",
            artistName = "Test Author",
            artworkUrl600 = "https://example.com/art.png",
            feedUrl = "https://example.com/feed.xml",
        )

        val podcast = dto.toPodcastOrNull()

        assertThat(podcast).isNotNull()
        assertThat(podcast!!.id).isEqualTo("https://example.com/feed.xml")
        assertThat(podcast.title).isEqualTo("Test Show")
        assertThat(podcast.artworkUrl).isEqualTo("https://example.com/art.png")
    }

    @Test
    fun `blank collection name is filtered out`() {
        val dto = ItunesPodcastDto(collectionName = "", feedUrl = "https://example.com/feed.xml")

        assertThat(dto.toPodcastOrNull()).isNull()
    }

    @Test
    fun `non-https feed url is filtered out`() {
        val dto = ItunesPodcastDto(collectionName = "Test Show", feedUrl = "http://example.com/feed.xml")

        assertThat(dto.toPodcastOrNull()).isNull()
    }

    @Test
    fun `blank artwork url becomes null`() {
        val dto = ItunesPodcastDto(
            collectionName = "Test Show",
            feedUrl = "https://example.com/feed.xml",
            artworkUrl600 = "",
        )

        assertThat(dto.toPodcastOrNull()!!.artworkUrl).isNull()
    }
}
