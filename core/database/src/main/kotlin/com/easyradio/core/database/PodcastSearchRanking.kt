package com.easyradio.core.database

import com.easyradio.core.model.Podcast

/**
 * Re-ranks iTunes search results by how closely they match what the user actually typed, rather
 * than trusting Apple's own relevance order as-is. That order weighs popularity/quality signals
 * we have no visibility into, which can bury an exact or near-exact title match well past the
 * first page for a partial or slightly misspelled query (e.g. "Dan Barrei" for "Dan Barreiro") --
 * pairs with PodcastRepository.search() requesting a larger raw pool (50) precisely so a buried
 * match has a chance to be in the pool this re-ranks in the first place.
 *
 * Kotlin's sortedBy is a stable sort, so results tied on match tier keep Apple's own relative
 * order -- their ranking signal still breaks ties, it just no longer overrides an exact match.
 */
object PodcastSearchRanking {

    fun rank(results: List<Podcast>, query: String): List<Podcast> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) return results
        return results.sortedBy { matchTier(it, normalizedQuery) }
    }

    private fun matchTier(podcast: Podcast, normalizedQuery: String): Int {
        val title = podcast.title.lowercase()
        val author = podcast.author.lowercase()
        return when {
            title.startsWith(normalizedQuery) -> 0
            author.startsWith(normalizedQuery) -> 1
            title.contains(normalizedQuery) -> 2
            author.contains(normalizedQuery) -> 3
            else -> 4
        }
    }
}
