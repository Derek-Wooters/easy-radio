package com.easyradio.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure sleep-timer math. The app records when the timer started and its
 * duration, then periodically feeds the current elapsed-realtime clock in to
 * decide how long is left and whether to pause playback. Keeping it a pure
 * function makes the countdown and expiry unit-testable without a real clock.
 */
class SleepTimerTest {

    @Test
    fun `remaining counts down from the full duration`() {
        assertThat(SleepTimer.remainingMs(startElapsedMs = 1_000, durationMs = 60_000, nowElapsedMs = 1_000))
            .isEqualTo(60_000)
        assertThat(SleepTimer.remainingMs(startElapsedMs = 1_000, durationMs = 60_000, nowElapsedMs = 21_000))
            .isEqualTo(40_000)
    }

    @Test
    fun `remaining clamps to zero once the duration has elapsed`() {
        assertThat(SleepTimer.remainingMs(startElapsedMs = 1_000, durationMs = 60_000, nowElapsedMs = 61_000))
            .isEqualTo(0)
        assertThat(SleepTimer.remainingMs(startElapsedMs = 1_000, durationMs = 60_000, nowElapsedMs = 999_999))
            .isEqualTo(0)
    }

    @Test
    fun `isExpired is false before the duration and true at or after`() {
        assertThat(SleepTimer.isExpired(startElapsedMs = 1_000, durationMs = 60_000, nowElapsedMs = 60_999)).isFalse()
        assertThat(SleepTimer.isExpired(startElapsedMs = 1_000, durationMs = 60_000, nowElapsedMs = 61_000)).isTrue()
        assertThat(SleepTimer.isExpired(startElapsedMs = 1_000, durationMs = 60_000, nowElapsedMs = 61_001)).isTrue()
    }

    @Test
    fun `a non-positive duration means no timer is set - never expires`() {
        assertThat(SleepTimer.isExpired(startElapsedMs = 1_000, durationMs = 0, nowElapsedMs = 5_000)).isFalse()
        assertThat(SleepTimer.remainingMs(startElapsedMs = 1_000, durationMs = 0, nowElapsedMs = 5_000)).isEqualTo(0)
    }
}
