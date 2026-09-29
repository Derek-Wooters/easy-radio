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
 * (Easy Radio's only auto-advance path), unless the "End of episode" sleep timer was armed for
 * this episode, in which case it's consumed here regardless of what's queued.
 */
object EpisodeEndDecision {

    fun resolve(sleepAtEndOfEpisode: Boolean, queue: List<Episode>): EpisodeEndAction {
        if (sleepAtEndOfEpisode) return EpisodeEndAction.Stop
        val next = queue.firstOrNull() ?: return EpisodeEndAction.Stop
        return EpisodeEndAction.Advance(next)
    }
}
