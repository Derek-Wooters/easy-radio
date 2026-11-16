package com.easyradio.core.media

/**
 * Pure decision for the global "Smart resume" setting (Settings > Playback, default off):
 * rewinding a few seconds when resuming a previously-paused episode so the listener has a moment
 * to catch back up, rather than picking up mid-sentence exactly where they left off.
 */
object SmartResume {

    private const val REWIND_MS = 5_000L

    /** Only ever rewinds an actual resume ([resumePositionMs] > 0) -- a fresh play is untouched. */
    fun resolveStartPositionMs(resumePositionMs: Long, enabled: Boolean): Long =
        if (enabled && resumePositionMs > 0L) (resumePositionMs - REWIND_MS).coerceAtLeast(0L) else resumePositionMs
}
