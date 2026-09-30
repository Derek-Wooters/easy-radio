package com.easyradio.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MainActivityFormattingTest {

    @Test
    fun `formatDuration omits hours when under an hour`() {
        assertThat(formatDuration(65_000)).isEqualTo("1:05")
    }

    @Test
    fun `formatDuration includes hours when an hour or more`() {
        assertThat(formatDuration(3_665_000)).isEqualTo("1:01:05")
    }

    @Test
    fun `formatDuration handles zero`() {
        assertThat(formatDuration(0)).isEqualTo("0:00")
    }

    @Test
    fun `episodeProgressFraction is null until a duration is known`() {
        assertThat(episodeProgressFraction(positionMs = 0, durationMs = 0)).isNull()
    }

    @Test
    fun `episodeProgressFraction is the position over duration ratio`() {
        assertThat(episodeProgressFraction(positionMs = 30_000, durationMs = 60_000)).isEqualTo(0.5f)
    }

    @Test
    fun `episodeMiniPlayerTagline falls back to just the podcast title before a duration is known`() {
        val tagline = episodeMiniPlayerTagline(podcastTitle = "The Daily", positionMs = 0, durationMs = 0)

        assertThat(tagline).isEqualTo("The Daily")
    }

    @Test
    fun `episodeMiniPlayerTagline combines the podcast title and time left once duration is known`() {
        val tagline = episodeMiniPlayerTagline(podcastTitle = "The Daily", positionMs = 30_000, durationMs = 90_000)

        assertThat(tagline).isEqualTo("The Daily · 1:00 left")
    }

    @Test
    fun `episodeMiniPlayerTagline omits the separator when there's no podcast title`() {
        val tagline = episodeMiniPlayerTagline(podcastTitle = "", positionMs = 30_000, durationMs = 90_000)

        assertThat(tagline).isEqualTo("1:00 left")
    }

    @Test
    fun `episodeMiniPlayerTagline never reports negative time left past the end`() {
        val tagline = episodeMiniPlayerTagline(podcastTitle = "The Daily", positionMs = 100_000, durationMs = 90_000)

        assertThat(tagline).isEqualTo("The Daily · 0:00 left")
    }
}
