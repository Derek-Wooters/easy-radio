package com.easyradio.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure decision for "Smart resume" (Settings > Playback): rewinding a few seconds when resuming
 * a previously-paused episode, so the listener gets a moment to catch back up instead of picking
 * up mid-sentence exactly where they left off.
 */
class SmartResumeTest {

    @Test
    fun `rewinds a fresh resume when enabled`() {
        val startMs = SmartResume.resolveStartPositionMs(resumePositionMs = 60_000L, enabled = true)

        assertThat(startMs).isEqualTo(55_000L)
    }

    @Test
    fun `does nothing when disabled`() {
        val startMs = SmartResume.resolveStartPositionMs(resumePositionMs = 60_000L, enabled = false)

        assertThat(startMs).isEqualTo(60_000L)
    }

    @Test
    fun `does nothing for a fresh play with nothing saved yet, even when enabled`() {
        val startMs = SmartResume.resolveStartPositionMs(resumePositionMs = 0L, enabled = true)

        assertThat(startMs).isEqualTo(0L)
    }

    @Test
    fun `never rewinds past the start`() {
        val startMs = SmartResume.resolveStartPositionMs(resumePositionMs = 2_000L, enabled = true)

        assertThat(startMs).isEqualTo(0L)
    }
}
