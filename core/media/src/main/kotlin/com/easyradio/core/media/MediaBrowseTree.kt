package com.easyradio.core.media

import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast
import com.easyradio.core.model.RadioStation

/**
 * Media-system-agnostic description of a single Android Auto browse item. The
 * playback service converts each node into an androidx.media3 MediaItem; keeping
 * the tree in this pure form lets all of its structure be unit-tested without
 * android.net.Uri.
 */
data class BrowseNode(
    val mediaId: String,
    val title: String,
    val subtitle: String = "",
    val artworkUrl: String? = null,
    val isBrowsable: Boolean,
    val isPlayable: Boolean,
    val playbackUri: String? = null,
)

/**
 * Builds the Android Auto browse tree shown by [EasyRadioPlaybackService]'s
 * MediaLibrarySession. The tree is two levels under the root: "Radio" lists the
 * curated stations (playable) and "Podcasts" lists subscribed shows (browsable),
 * each expanding to its episodes (playable). Media ids are prefixed by type so a
 * tapped item can be resolved back to a playback uri via [playbackUri].
 */
object MediaBrowseTree {
    const val ROOT_ID = "root"
    const val RADIO_ID = "radio"
    const val PODCASTS_ID = "podcasts"

    const val STATION_PREFIX = "station/"
    const val PODCAST_PREFIX = "podcast/"
    const val EPISODE_PREFIX = "episode/"

    fun rootChildren(): List<BrowseNode> = listOf(
        BrowseNode(mediaId = RADIO_ID, title = "Radio", isBrowsable = true, isPlayable = false),
        BrowseNode(mediaId = PODCASTS_ID, title = "Podcasts", isBrowsable = true, isPlayable = false),
    )

    fun stationNodes(stations: List<RadioStation>): List<BrowseNode> = stations.map { station ->
        BrowseNode(
            mediaId = STATION_PREFIX + station.id,
            title = station.name,
            subtitle = station.tagline,
            artworkUrl = station.imageUrl,
            isBrowsable = false,
            isPlayable = true,
            playbackUri = station.streamUrl,
        )
    }

    fun podcastNodes(podcasts: List<Podcast>): List<BrowseNode> = podcasts.map { podcast ->
        BrowseNode(
            mediaId = PODCAST_PREFIX + podcast.id,
            title = podcast.title,
            subtitle = podcast.author,
            artworkUrl = podcast.artworkUrl,
            isBrowsable = true,
            isPlayable = false,
        )
    }

    fun episodeNodes(episodes: List<Episode>): List<BrowseNode> = episodes.map { episode ->
        BrowseNode(
            mediaId = EPISODE_PREFIX + episode.id,
            title = episode.title,
            isBrowsable = false,
            isPlayable = true,
            playbackUri = episode.localFilePath ?: episode.audioUrl,
        )
    }

    /** Resolves a playable media id back to the uri ExoPlayer should stream. */
    fun playbackUri(
        mediaId: String,
        stations: List<RadioStation>,
        episodes: List<Episode>,
    ): String? = when {
        mediaId.startsWith(STATION_PREFIX) ->
            stations.firstOrNull { it.id == mediaId.removePrefix(STATION_PREFIX) }?.streamUrl
        mediaId.startsWith(EPISODE_PREFIX) ->
            episodes.firstOrNull { it.id == mediaId.removePrefix(EPISODE_PREFIX) }
                ?.let { it.localFilePath ?: it.audioUrl }
        else -> null
    }
}
