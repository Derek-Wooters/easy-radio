package com.easyradio.app.playback

import android.net.Uri
import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast
import com.easyradio.core.model.RadioStation
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PlaybackRequestsTest {

    private val podcast = Podcast(
        id = "p1",
        title = "The Daily",
        author = "The New York Times",
        artworkUrl = "https://example.com/art.png",
        feedUrl = "https://example.com/feed",
    )

    private fun episode(localFilePath: String? = null) = Episode(
        id = "ep1",
        podcastId = "p1",
        title = "Episode 1",
        audioUrl = "https://example.com/ep1.mp3",
        publishedAtEpochMillis = null,
        durationSeconds = 600,
        localFilePath = localFilePath,
    )

    @Test
    fun `forStation builds the stream uri and station extras`() {
        val station = RadioStation(
            id = "kfan",
            name = "KFAN FM 100.3",
            streamUrl = "https://example.com/stream.mp3",
            tagline = "Audio Home For Minnesota Sports",
            imageUrl = "https://example.com/kfan.png",
        )

        val request = PlaybackRequests.forStation(station)

        assertThat(request.uri.toString()).isEqualTo("https://example.com/stream.mp3")
        assertThat(request.extras.getString(EXTRA_TITLE)).isEqualTo("KFAN FM 100.3")
        assertThat(request.extras.getString(EXTRA_ARTIST)).isEqualTo("Audio Home For Minnesota Sports")
        assertThat(request.extras.getString(EXTRA_ARTWORK_URL)).isEqualTo("https://example.com/kfan.png")
    }

    @Test
    fun `forEpisode streams from the network when there is no local file`() {
        val request = PlaybackRequests.forEpisode(podcast, episode(localFilePath = null), resumePositionMs = 0L)

        assertThat(request.uri.toString()).isEqualTo("https://example.com/ep1.mp3")
    }

    @Test
    fun `forEpisode falls back to streaming when the saved local file is gone`() {
        val request = PlaybackRequests.forEpisode(
            podcast,
            episode(localFilePath = "/data/downloads/ep1.mp3"),
            resumePositionMs = 0L,
            localFileExists = { false },
        )

        assertThat(request.uri.toString()).isEqualTo("https://example.com/ep1.mp3")
    }

    @Test
    fun `forEpisode plays the local file when it still exists`() {
        val localPath = "/data/downloads/ep1.mp3"

        val request = PlaybackRequests.forEpisode(
            podcast,
            episode(localFilePath = localPath),
            resumePositionMs = 0L,
            localFileExists = { true },
        )

        // Uri.fromFile(File(path)) resolves relative to the platform's own filesystem rules (e.g.
        // Windows anchors a leading-slash path to the current drive), so compare against the same
        // construction rather than a hardcoded Unix-style uri string.
        assertThat(request.uri).isEqualTo(Uri.fromFile(File(localPath)))
    }

    @Test
    fun `forEpisode falls back to the podcast title when the author is blank`() {
        val blankAuthorPodcast = podcast.copy(author = "")

        val request = PlaybackRequests.forEpisode(blankAuthorPodcast, episode(), resumePositionMs = 0L)

        assertThat(request.extras.getString(EXTRA_ARTIST)).isEqualTo("The Daily")
    }

    @Test
    fun `forEpisode includes the resume position, episode id, and podcast id`() {
        val request = PlaybackRequests.forEpisode(podcast, episode(), resumePositionMs = 42_000L)

        assertThat(request.extras.getLong(EXTRA_RESUME_POSITION_MS)).isEqualTo(42_000L)
        assertThat(request.extras.getString(EXTRA_EPISODE_ID)).isEqualTo("ep1")
        assertThat(request.extras.getString(EXTRA_PODCAST_ID)).isEqualTo("p1")
    }

    @Test
    fun `forEpisode jumps a fresh play past the podcast's configured intro`() {
        val request = PlaybackRequests.forEpisode(
            podcast.copy(skipFirstSeconds = 30),
            episode(),
            resumePositionMs = 0L,
        )

        assertThat(request.extras.getLong(EXTRA_RESUME_POSITION_MS)).isEqualTo(30_000L)
    }

    @Test
    fun `forEpisode leaves a resumed play's position alone despite skip-first`() {
        val request = PlaybackRequests.forEpisode(
            podcast.copy(skipFirstSeconds = 30),
            episode(),
            resumePositionMs = 120_000L,
        )

        assertThat(request.extras.getLong(EXTRA_RESUME_POSITION_MS)).isEqualTo(120_000L)
    }

    @Test
    fun `forEpisode includes the podcast's configured skip-last seconds`() {
        val request = PlaybackRequests.forEpisode(
            podcast.copy(skipLastSeconds = 15),
            episode(),
            resumePositionMs = 0L,
        )

        assertThat(request.extras.getInt(EXTRA_SKIP_LAST_SECONDS)).isEqualTo(15)
    }

    @Test
    fun `forEpisode rewinds a resumed play when smart resume is enabled`() {
        val request = PlaybackRequests.forEpisode(
            podcast,
            episode(),
            resumePositionMs = 60_000L,
            smartResumeEnabled = true,
        )

        assertThat(request.extras.getLong(EXTRA_RESUME_POSITION_MS)).isEqualTo(55_000L)
    }

    @Test
    fun `forEpisode leaves a resumed play alone when smart resume is disabled`() {
        val request = PlaybackRequests.forEpisode(
            podcast,
            episode(),
            resumePositionMs = 60_000L,
            smartResumeEnabled = false,
        )

        assertThat(request.extras.getLong(EXTRA_RESUME_POSITION_MS)).isEqualTo(60_000L)
    }

    @Test
    fun `forEpisode never applies smart resume to a fresh play, even when enabled`() {
        val request = PlaybackRequests.forEpisode(
            podcast,
            episode(),
            resumePositionMs = 0L,
            smartResumeEnabled = true,
        )

        assertThat(request.extras.getLong(EXTRA_RESUME_POSITION_MS)).isEqualTo(0L)
    }

    @Test
    fun `forEpisode includes the podcast's playback speed`() {
        val request = PlaybackRequests.forEpisode(
            podcast.copy(playbackSpeed = 1.5f),
            episode(),
            resumePositionMs = 0L,
        )

        assertThat(request.extras.getFloat(EXTRA_PLAYBACK_SPEED)).isEqualTo(1.5f)
    }

    @Test
    fun `forEpisode defaults to 1x for a podcast with no custom speed set`() {
        val request = PlaybackRequests.forEpisode(podcast, episode(), resumePositionMs = 0L)

        assertThat(request.extras.getFloat(EXTRA_PLAYBACK_SPEED)).isEqualTo(1.0f)
    }
}
