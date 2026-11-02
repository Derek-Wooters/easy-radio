package com.easyradio.core.media

import com.easyradio.core.model.Episode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure decision for what EasyRadioPlaybackService.handleEpisodeEnded() should do when a podcast
 * episode reaches its natural end. Keeping it a pure function makes the auto-advance / "End of
 * episode" suppression branching unit-testable without a real ExoPlayer or MediaSession.
 */
class EpisodeEndDecisionTest {

    private fun episode(id: String, publishedAtEpochMillis: Long? = null, positionMs: Long = 0L) = Episode(
        id = id,
        podcastId = "p1",
        title = "Episode $id",
        audioUrl = "https://example.com/$id.mp3",
        publishedAtEpochMillis = publishedAtEpochMillis,
        durationSeconds = 1_800,
        positionMs = positionMs,
    )

    @Test
    fun `stops when nothing is queued and the sleep timer wasn't armed`() {
        val action = EpisodeEndDecision.resolve(sleepAtEndOfEpisode = false, queue = emptyList())

        assertThat(action).isEqualTo(EpisodeEndAction.Stop)
    }

    @Test
    fun `advances to the head of the queue when it isn't empty`() {
        val head = episode("first")
        val action = EpisodeEndDecision.resolve(sleepAtEndOfEpisode = false, queue = listOf(head, episode("second")))

        assertThat(action).isEqualTo(EpisodeEndAction.Advance(head))
    }

    @Test
    fun `stops when the sleep timer was armed, even with nothing queued`() {
        val action = EpisodeEndDecision.resolve(sleepAtEndOfEpisode = true, queue = emptyList())

        assertThat(action).isEqualTo(EpisodeEndAction.Stop)
    }

    @Test
    fun `an armed sleep timer suppresses advancing even when something is queued`() {
        val action = EpisodeEndDecision.resolve(sleepAtEndOfEpisode = true, queue = listOf(episode("queued")))

        assertThat(action).isEqualTo(EpisodeEndAction.Stop)
    }

    @Test
    fun `falls back to the podcast's newest unplayed episode when nothing is queued`() {
        val older = episode("older", publishedAtEpochMillis = 1_000L)
        val newer = episode("newer", publishedAtEpochMillis = 2_000L)

        val action = EpisodeEndDecision.resolve(
            sleepAtEndOfEpisode = false,
            queue = emptyList(),
            finishedEpisodeId = "just-finished",
            podcastEpisodes = listOf(older, newer),
        )

        assertThat(action).isEqualTo(EpisodeEndAction.Advance(newer))
    }

    @Test
    fun `the queue wins over the podcast fallback when both have something`() {
        val queued = episode("queued")
        val podcastNext = episode("podcast-next", publishedAtEpochMillis = 2_000L)

        val action = EpisodeEndDecision.resolve(
            sleepAtEndOfEpisode = false,
            queue = listOf(queued),
            finishedEpisodeId = "just-finished",
            podcastEpisodes = listOf(podcastNext),
        )

        assertThat(action).isEqualTo(EpisodeEndAction.Advance(queued))
    }

    @Test
    fun `the podcast fallback skips episodes already started or finished`() {
        val inProgress = episode("in-progress", publishedAtEpochMillis = 3_000L, positionMs = 60_000L)
        val unplayed = episode("unplayed", publishedAtEpochMillis = 1_000L)

        val action = EpisodeEndDecision.resolve(
            sleepAtEndOfEpisode = false,
            queue = emptyList(),
            finishedEpisodeId = "just-finished",
            podcastEpisodes = listOf(inProgress, unplayed),
        )

        assertThat(action).isEqualTo(EpisodeEndAction.Advance(unplayed))
    }

    @Test
    fun `the podcast fallback never re-selects the episode that just finished`() {
        // positionMs defaults to 0 here deliberately -- the DB write for the finished episode's
        // final position may not have landed yet by the time this resolves, so the fallback must
        // not rely on positionMs alone to exclude it.
        val justFinished = episode("just-finished", publishedAtEpochMillis = 5_000L)
        val olderUnplayed = episode("older-unplayed", publishedAtEpochMillis = 1_000L)

        val action = EpisodeEndDecision.resolve(
            sleepAtEndOfEpisode = false,
            queue = emptyList(),
            finishedEpisodeId = "just-finished",
            podcastEpisodes = listOf(justFinished, olderUnplayed),
        )

        assertThat(action).isEqualTo(EpisodeEndAction.Advance(olderUnplayed))
    }

    @Test
    fun `stops when the whole podcast has already been heard and nothing is queued`() {
        val finished = episode("finished", publishedAtEpochMillis = 1_000L, positionMs = 1_800_000L)

        val action = EpisodeEndDecision.resolve(
            sleepAtEndOfEpisode = false,
            queue = emptyList(),
            finishedEpisodeId = "just-finished",
            podcastEpisodes = listOf(finished),
        )

        assertThat(action).isEqualTo(EpisodeEndAction.Stop)
    }

    @Test
    fun `an armed sleep timer suppresses the podcast fallback too`() {
        val unplayed = episode("unplayed", publishedAtEpochMillis = 1_000L)

        val action = EpisodeEndDecision.resolve(
            sleepAtEndOfEpisode = true,
            queue = emptyList(),
            finishedEpisodeId = "just-finished",
            podcastEpisodes = listOf(unplayed),
        )

        assertThat(action).isEqualTo(EpisodeEndAction.Stop)
    }
}
