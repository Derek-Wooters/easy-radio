package com.easyradio.core.media

/**
 * Pure sleep-timer math. Callers record the elapsed-realtime clock reading when
 * the timer started ([startElapsedMs]) and its [durationMs], then feed the
 * current clock ([nowElapsedMs]) in to poll. A non-positive duration means "no
 * timer set": nothing counts down and it never expires.
 */
object SleepTimer {

    fun remainingMs(startElapsedMs: Long, durationMs: Long, nowElapsedMs: Long): Long {
        if (durationMs <= 0) return 0
        val elapsed = nowElapsedMs - startElapsedMs
        return (durationMs - elapsed).coerceIn(0, durationMs)
    }

    fun isExpired(startElapsedMs: Long, durationMs: Long, nowElapsedMs: Long): Boolean {
        if (durationMs <= 0) return false
        return nowElapsedMs - startElapsedMs >= durationMs
    }
}
