package com.easyradio.app.playback

import android.net.Uri
import android.os.Bundle
import com.easyradio.core.media.IntroOutroSkip
import com.easyradio.core.media.SmartResume
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
            putString(EXTRA_STATION_ID, station.id)
        },
    )

    fun forEpisode(
        podcast: Podcast,
        episode: Episode,
        resumePositionMs: Long,
        smartResumeEnabled: Boolean = false,
        localFileExists: (String) -> Boolean = { File(it).exists() },
    ): PlaybackRequest {
        val localPath = episode.localFilePath
        val uri = if (localPath != null && localFileExists(localPath)) {
            Uri.fromFile(File(localPath))
        } else {
            Uri.parse(episode.audioUrl)
        }
        // These two apply to opposite cases of resumePositionMs (<=0 vs. >0) and must each see the
        // real, original value -- chaining one's output into the other would feed skip-first's
        // now-positive result (e.g. 30s) into Smart resume, which would then wrongly treat a fresh,
        // intro-skipped play as an actual resume and rewind it.
        val startPositionMs = if (resumePositionMs <= 0L) {
            IntroOutroSkip.resolveStartPositionMs(resumePositionMs, podcast.skipFirstSeconds)
        } else {
            SmartResume.resolveStartPositionMs(resumePositionMs, smartResumeEnabled)
        }
        return PlaybackRequest(
            uri = uri,
            extras = Bundle().apply {
                putString(EXTRA_TITLE, episode.title)
                putString(EXTRA_ARTIST, podcast.author.ifBlank { podcast.title })
                putString(EXTRA_ARTWORK_URL, podcast.artworkUrl)
                putLong(EXTRA_RESUME_POSITION_MS, startPositionMs)
                putString(EXTRA_EPISODE_ID, episode.id)
                putString(EXTRA_PODCAST_ID, episode.podcastId)
                putInt(EXTRA_SKIP_LAST_SECONDS, podcast.skipLastSeconds)
                putFloat(EXTRA_PLAYBACK_SPEED, podcast.playbackSpeed)
            },
        )
    }
}
