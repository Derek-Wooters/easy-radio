package com.easyradio.core.media

import com.easyradio.core.model.AdSegment
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdSkipDecisionTest {

    @Test
    fun `position inside a segment skips to its end`() {
        val segments = listOf(AdSegment(startMs = 10_000, endMs = 20_000))
        assertThat(AdSkipDecision.resolve(positionMs = 15_000, segments)).isEqualTo(20_000)
    }

    @Test
    fun `position before any segment does not skip`() {
        val segments = listOf(AdSegment(startMs = 10_000, endMs = 20_000))
        assertThat(AdSkipDecision.resolve(positionMs = 5_000, segments)).isNull()
    }

    @Test
    fun `position after a segment's end does not skip`() {
        val segments = listOf(AdSegment(startMs = 10_000, endMs = 20_000))
        assertThat(AdSkipDecision.resolve(positionMs = 20_000, segments)).isNull()
    }

    @Test
    fun `position exactly at a segment's start skips`() {
        val segments = listOf(AdSegment(startMs = 10_000, endMs = 20_000))
        assertThat(AdSkipDecision.resolve(positionMs = 10_000, segments)).isEqualTo(20_000)
    }

    @Test
    fun `no segments never skips`() {
        assertThat(AdSkipDecision.resolve(positionMs = 15_000, emptyList())).isNull()
    }

    @Test
    fun `picks the segment that actually contains the position among several`() {
        val segments = listOf(
            AdSegment(startMs = 0, endMs = 5_000),
            AdSegment(startMs = 10_000, endMs = 20_000),
            AdSegment(startMs = 30_000, endMs = 40_000),
        )
        assertThat(AdSkipDecision.resolve(positionMs = 35_000, segments)).isEqualTo(40_000)
    }
}
