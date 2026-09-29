package com.easyradio.app

import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast

/**
 * Resolves [episode]'s real podcast from [subscribedPodcasts] if it's subscribed there, or a
 * minimal placeholder otherwise. Queuing (and auto-advancing to) an episode never required
 * subscribing to its podcast, so this must never throw the way constructing
 * `Podcast(title = "")` once did here -- [Podcast]'s own constructor rejects a blank title,
 * which crashed the whole process the moment either call site hit an unsubscribed show's
 * episode (tapping it in the Up Next queue, or the service auto-advancing to it).
 */
internal fun resolvePlayablePodcast(episode: Episode, subscribedPodcasts: List<Podcast>): Podcast =
    subscribedPodcasts.firstOrNull { it.id == episode.podcastId }
        ?: Podcast(
            id = episode.podcastId,
            title = "Podcast",
            author = "",
            artworkUrl = null,
            feedUrl = "https://placeholder.invalid/",
        )
