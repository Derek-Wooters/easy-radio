package com.easyradio.app.playback

import android.net.Uri
import android.os.Bundle
import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast
import com.easyradio.core.model.RadioStation
import java.io.File

/** What MainActivity sends via `MediaControllerCompat.transportControls.playFromUri(uri, extras)`. */
data class PlaybackRequest(val uri: Uri, val extras: Bundle)

/**
 * Builds the uri + extras MainActivity sends to start playback, kept separate from MainActivity
 * itself so the two real decisions in here -- whether an episode's saved local file still exists
 * (a stale path must fall back to streaming, not silently fail) and the podcast-author-blank ->
 * title fallback -- are unit-testable without a real MediaControllerCompat.
 */
object PlaybackRequests {

    fun forStation(station: RadioStation): PlaybackRequest = PlaybackRequest(
        uri = Uri.parse(station.streamUrl),
        extras = Bundle().apply {
            putString(EXTRA_TITLE, station.name)
            putString(EXTRA_ARTIST, station.tagline)
            putString(EXTRA_ARTWORK_URL, station.imageUrl)
        },
    )

    fun forEpisode(
        podcast: Podcast,
        episode: Episode,
        resumePositionMs: Long,
        localFileExists: (String) -> Boolean = { File(it).exists() },
    ): PlaybackRequest {
        val localPath = episode.localFilePath
        val uri = if (localPath != null && localFileExists(localPath)) {
            Uri.fromFile(File(localPath))
        } else {
            Uri.parse(episode.audioUrl)
        }
        return PlaybackRequest(
            uri = uri,
            extras = Bundle().apply {
                putString(EXTRA_TITLE, episode.title)
                putString(EXTRA_ARTIST, podcast.author.ifBlank { podcast.title })
                putString(EXTRA_ARTWORK_URL, podcast.artworkUrl)
                putLong(EXTRA_RESUME_POSITION_MS, resumePositionMs)
                putString(EXTRA_EPISODE_ID, episode.id)
            },
        )
    }
}
