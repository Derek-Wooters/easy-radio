package com.easyradio.core.database

import com.easyradio.core.model.Podcast
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PodcastSearchRankingTest {

    private fun podcast(id: String, title: String, author: String = "Author") = Podcast(
        id = id,
        title = title,
        author = author,
        artworkUrl = null,
        feedUrl = "https://example.com/$id.xml",
    )

    @Test
    fun `a partial query promotes the title it's a prefix of, even if Apple ranked it last`() {
        val results = listOf(
            podcast("p1", "Popular Sports Show"),
            podcast("p2", "Some Other Podcast"),
            // The actual example this was built for: Apple's own relevance order buried this
            // one last even though "Dan Barrei" is an exact prefix of its title.
            podcast("p3", "Dan Barreiro"),
        )

        val ranked = PodcastSearchRanking.rank(results, "Dan Barrei")

        assertThat(ranked.first().id).isEqualTo("p3")
    }

    @Test
    fun `a title-prefix match beats an author-prefix match`() {
        val titleMatch = podcast("p1", "The Daily", author = "Someone Else")
        val authorMatch = podcast("p2", "News Roundup", author = "The Daily Team")

        val ranked = PodcastSearchRanking.rank(listOf(authorMatch, titleMatch), "The Daily")

        assertThat(ranked.first().id).isEqualTo("p1")
    }

    @Test
    fun `a prefix match beats a contains-only match`() {
        val containsOnly = podcast("p1", "My Favorite Daily Show")
        val prefixMatch = podcast("p2", "Daily Show Recap")

        val ranked = PodcastSearchRanking.rank(listOf(containsOnly, prefixMatch), "Daily")

        assertThat(ranked.first().id).isEqualTo("p2")
    }

    @Test
    fun `matching is case-insensitive`() {
        val results = listOf(podcast("p1", "dan barreiro"))

        val ranked = PodcastSearchRanking.rank(results, "DAN BARREIRO")

        assertThat(ranked.first().id).isEqualTo("p1")
    }

    @Test
    fun `results tied on match tier keep Apple's original relative order`() {
        val results = listOf(
            podcast("p1", "Sports Talk A"),
            podcast("p2", "Sports Talk B"),
            podcast("p3", "Sports Talk C"),
        )

        val ranked = PodcastSearchRanking.rank(results, "Sports Talk")

        assertThat(ranked.map { it.id }).containsExactly("p1", "p2", "p3").inOrder()
    }

    @Test
    fun `a blank query leaves the original order untouched`() {
        val results = listOf(podcast("p1", "B Show"), podcast("p2", "A Show"))

        val ranked = PodcastSearchRanking.rank(results, "")

        assertThat(ranked).isEqualTo(results)
    }
}
