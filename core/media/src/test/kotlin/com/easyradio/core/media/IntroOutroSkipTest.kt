package com.easyradio.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure decisions for a podcast's per-show "skip first"/"skip last" seconds (Options tab):
 * where a fresh play should actually start, and whether the player has reached the point where
 * the configured outro should be cut instead of played.
 */
class IntroOutroSkipTest {

    @Test
    fun `a fresh play with no skip-first configured starts at the top`() {
        val startMs = IntroOutroSkip.resolveStartPositionMs(resumePositionMs = 0L, skipFirstSeconds = 0)

        assertThat(startMs).isEqualTo(0L)
    }

    @Test
    fun `a fresh play jumps past the configured intro`() {
        val startMs = IntroOutroSkip.resolveStartPositionMs(resumePositionMs = 0L, skipFirstSeconds = 30)

        assertThat(startMs).isEqualTo(30_000L)
    }

    @Test
    fun `resuming a partially-played episode ignores skip-first -- it already got past the intro`() {
        val startMs = IntroOutroSkip.resolveStartPositionMs(resumePositionMs = 120_000L, skipFirstSeconds = 30)

        assertThat(startMs).isEqualTo(120_000L)
    }

    @Test
    fun `shouldEndEarly is false with no skip-last configured`() {
        val ended = IntroOutroSkip.shouldEndEarly(positionMs = 590_000L, durationMs = 600_000L, skipLastSeconds = 0)

        assertThat(ended).isFalse()
    }

    @Test
    fun `shouldEndEarly is false while still short of the configured outro`() {
        val ended = IntroOutroSkip.shouldEndEarly(positionMs = 560_000L, durationMs = 600_000L, skipLastSeconds = 30)

        assertThat(ended).isFalse()
    }

    @Test
    fun `shouldEndEarly is true once playback reaches the configured outro`() {
        val ended = IntroOutroSkip.shouldEndEarly(positionMs = 570_000L, durationMs = 600_000L, skipLastSeconds = 30)

        assertThat(ended).isTrue()
    }

    @Test
    fun `shouldEndEarly is false while the duration isn't known yet`() {
        // ExoPlayer reports 0 (or C.TIME_UNSET, mapped to 0 by callers) until it's buffered enough
        // of the stream to know the real duration -- treating that as "already past the outro"
        // would end every episode the instant it started.
        val ended = IntroOutroSkip.shouldEndEarly(positionMs = 5_000L, durationMs = 0L, skipLastSeconds = 30)

        assertThat(ended).isFalse()
    }

    @Test
    fun `a skip-last longer than the episode itself ends it immediately`() {
        // The whole episode falls inside the configured outro window in this case -- there's no
        // "middle" left to play, so ending right away (rather than never, or crashing on a
        // negative threshold) is the correct behavior, not a bug to guard against.
        val ended = IntroOutroSkip.shouldEndEarly(positionMs = 1_000L, durationMs = 10_000L, skipLastSeconds = 600)

        assertThat(ended).isTrue()
    }
}
