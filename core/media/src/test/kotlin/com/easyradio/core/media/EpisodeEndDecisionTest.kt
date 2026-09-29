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

    private fun episode(id: String) = Episode(
        id = id,
        podcastId = "p1",
        title = "Episode $id",
        audioUrl = "https://example.com/$id.mp3",
        publishedAtEpochMillis = null,
        durationSeconds = 1_800,
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
}
