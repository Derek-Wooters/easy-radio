package com.easyradio.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveEdgeTrackerTest {

    @Test
    fun `is live while continuously playing`() {
        val sut = LiveEdgeTracker()
        sut.onPlayingChanged(true, 0L)
        sut.onPlayingChanged(true, 60_000L)

        assertThat(sut.snapshot().behindMs(120_000L)).isEqualTo(0L)
    }

    @Test
    fun `initial buffering before first play is not counted as behind`() {
        val sut = LiveEdgeTracker()
        sut.onPlayingChanged(false, 0L)
        sut.onPlayingChanged(false, 3_000L)
        sut.onPlayingChanged(true, 5_000L)

        assertThat(sut.snapshot().behindMs(9_000L)).isEqualTo(0L)
    }

    @Test
    fun `grows while paused`() {
        val sut = LiveEdgeTracker()
        sut.onPlayingChanged(true, 0L)
        sut.onPlayingChanged(false, 10_000L)

        assertThat(sut.snapshot().behindMs(10_000L)).isEqualTo(0L)
        assertThat(sut.snapshot().behindMs(100_000L)).isEqualTo(90_000L)
    }

    @Test
    fun `freezes on resume and stays behind`() {
        val sut = LiveEdgeTracker()
        sut.onPlayingChanged(true, 0L)
        sut.onPlayingChanged(false, 10_000L)
        sut.onPlayingChanged(true, 70_000L)

        assertThat(sut.snapshot().behindMs(70_000L)).isEqualTo(60_000L)
        assertThat(sut.snapshot().behindMs(500_000L)).isEqualTo(60_000L)
    }

    @Test
    fun `accumulates across multiple pauses`() {
        val sut = LiveEdgeTracker()
        sut.onPlayingChanged(true, 0L)
        sut.onPlayingChanged(false, 10_000L)
        sut.onPlayingChanged(true, 40_000L)
        sut.onPlayingChanged(false, 100_000L)
        sut.onPlayingChanged(true, 120_000L)

        assertThat(sut.snapshot().behindMs(200_000L)).isEqualTo(50_000L)
    }

    @Test
    fun `reset returns to live`() {
        val sut = LiveEdgeTracker()
        sut.onPlayingChanged(true, 0L)
        sut.onPlayingChanged(false, 10_000L)
        sut.reset()

        assertThat(sut.snapshot().behindMs(100_000L)).isEqualTo(0L)
    }

    @Test
    fun `threshold and formatting`() {
        assertThat(isBehindLive(4_999L)).isFalse()
        assertThat(isBehindLive(5_000L)).isTrue()
        assertThat(formatBehindLive(83_000L)).isEqualTo("-1:23")
        assertThat(formatBehindLive(3_723_000L)).isEqualTo("-1:02:03")
    }
}
