package com.easyradio.core.media

import com.easyradio.core.model.Episode

/** What [EpisodeEndDecision.resolve] says to do when a podcast episode reaches its natural end. */
sealed interface EpisodeEndAction {
    /** Leave playback stopped -- either nothing is queued, or the sleep timer suppressed advancing. */
    data object Stop : EpisodeEndAction

    /** Advance to [next], the head of the queue. */
    data class Advance(val next: Episode) : EpisodeEndAction
}

/**
 * Pure decision for what happens when an episode finishes: advance to the next queued episode
 * first (an explicit user choice, so it always wins), then fall back to the finished episode's
 * own podcast -- its next unplayed episode, newest first, same order as the Episodes tab's
 * default sort -- so finishing a show's backlog keeps moving without the user having to queue
 * anything. Either path is suppressed, regardless of what's available, when the "End of episode"
 * sleep timer was armed for this episode.
 */
object EpisodeEndDecision {

    fun resolve(
        sleepAtEndOfEpisode: Boolean,
        queue: List<Episode>,
        finishedEpisodeId: String? = null,
        podcastEpisodes: List<Episode> = emptyList(),
    ): EpisodeEndAction {
        if (sleepAtEndOfEpisode) return EpisodeEndAction.Stop
        queue.firstOrNull()?.let { return EpisodeEndAction.Advance(it) }
        val nextUnplayed = podcastEpisodes
            .filter { it.id != finishedEpisodeId && it.positionMs <= 0L }
            .sortedByDescending { it.publishedAtEpochMillis ?: 0L }
            .firstOrNull()
        return nextUnplayed?.let { EpisodeEndAction.Advance(it) } ?: EpisodeEndAction.Stop
    }
}
