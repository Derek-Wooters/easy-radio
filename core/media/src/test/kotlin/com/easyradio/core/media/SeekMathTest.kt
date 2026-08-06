package com.easyradio.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SeekMathTest {

    @Test
    fun `positive delta advances position`() {
        assertThat(SeekMath.clampSeek(currentMs = 10_000, deltaMs = 15_000, durationMs = 60_000)).isEqualTo(25_000)
    }

    @Test
    fun `negative delta rewinds position`() {
        assertThat(SeekMath.clampSeek(currentMs = 20_000, deltaMs = -15_000, durationMs = 60_000)).isEqualTo(5_000)
    }

    @Test
    fun `rewind past zero clamps to zero`() {
        assertThat(SeekMath.clampSeek(currentMs = 5_000, deltaMs = -15_000, durationMs = 60_000)).isEqualTo(0)
    }

    @Test
    fun `advance past duration clamps to duration`() {
        assertThat(SeekMath.clampSeek(currentMs = 55_000, deltaMs = 15_000, durationMs = 60_000)).isEqualTo(60_000)
    }

    @Test
    fun `unknown duration (0 or negative) does not clamp the upper bound`() {
        assertThat(SeekMath.clampSeek(currentMs = 100_000, deltaMs = 15_000, durationMs = 0)).isEqualTo(115_000)
    }
}
