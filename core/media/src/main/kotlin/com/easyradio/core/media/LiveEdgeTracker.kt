package com.easyradio.core.media

/** Offsets under this are noise (a brief rebuffer), not "you're on a delayed stream". */
const val BEHIND_LIVE_THRESHOLD_MS = 5_000L

/**
 * A snapshot of how far behind the live edge a radio stream is: [baseMs] already accumulated
 * from earlier pauses/stalls, plus -- only while currently not playing -- the time since
 * [pausedSinceMs] (an arbitrary monotonic clock, the same one passed to [behindMs]).
 */
data class LiveOffset(val baseMs: Long, val pausedSinceMs: Long?) {
    fun behindMs(nowMs: Long): Long = baseMs + (pausedSinceMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L)
}

/** True once [behindMs] is large enough to be worth telling the listener about. */
fun isBehindLive(behindMs: Long): Boolean = behindMs >= BEHIND_LIVE_THRESHOLD_MS

/** "-1:23" / "-1:02:03": how far behind live, for the badge. */
fun formatBehindLive(behindMs: Long): String {
    val totalSeconds = behindMs / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "-%d:%02d:%02d".format(hours, minutes, seconds) else "-%d:%02d".format(minutes, seconds)
}

/**
 * Tracks how far behind live a radio stream is. A live stream keeps moving while the listener
 * is paused (or stalled), and resuming plays from the buffered point rather than the live edge,
 * so the offset is exactly the time spent not playing since the stream was last joined live.
 * Pure and clock-agnostic (callers pass `nowMs`) so it's unit-testable without Android.
 *
 * This is an estimate: if the connection drops and reconnects during a long pause the player
 * rejoins closer to live than this reports; going live is always available to resync.
 */
class LiveEdgeTracker {
    private var started = false
    private var accumulatedMs = 0L
    private var pausedSinceMs: Long? = null

    /** A new stream was just started, or the listener went live: the offset is zero again. */
    fun reset() {
        started = false
        accumulatedMs = 0L
        pausedSinceMs = null
    }

    /**
     * Report whether audio is currently actually playing. Time before the first playing report
     * (initial buffering) is not counted; repeated identical reports are harmless.
     */
    fun onPlayingChanged(isPlaying: Boolean, nowMs: Long) {
        if (isPlaying) {
            started = true
            pausedSinceMs?.let { accumulatedMs += (nowMs - it).coerceAtLeast(0L) }
            pausedSinceMs = null
        } else if (started && pausedSinceMs == null) {
            pausedSinceMs = nowMs
        }
    }

    fun snapshot(): LiveOffset = LiveOffset(accumulatedMs, pausedSinceMs)
}
