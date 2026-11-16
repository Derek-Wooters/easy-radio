package com.easyradio.core.media

/**
 * Pure decisions for a podcast's per-show "skip first"/"skip last" seconds (set from that show's
 * Options tab, defaulting to 0/off): where a fresh play should actually start, and whether
 * playback has reached the point where the configured outro should be cut short instead of
 * played. Kept separate from [EpisodeEndDecision] -- that one decides what to play *next* once an
 * episode has genuinely ended; this one decides whether *this* episode's end should come early.
 */
object IntroOutroSkip {

    /**
     * A fresh play (nothing saved yet) jumps past [skipFirstSeconds]; resuming a partially-played
     * episode is left alone -- it's already past whatever intro there was, so skip-first would
     * otherwise jump it forward a second time on every resume.
     */
    fun resolveStartPositionMs(resumePositionMs: Long, skipFirstSeconds: Int): Long =
        if (resumePositionMs <= 0L && skipFirstSeconds > 0) skipFirstSeconds * 1_000L else resumePositionMs

    /**
     * True once [positionMs] has reached the point [skipLastSeconds] short of [durationMs] --
     * the caller should treat this exactly like the player reaching its natural end. Always false
     * while [durationMs] isn't known yet (ExoPlayer reports 0/unset until it's buffered enough of
     * the stream to tell), so a just-started episode is never mistaken for one already at its
     * outro.
     */
    fun shouldEndEarly(positionMs: Long, durationMs: Long, skipLastSeconds: Int): Boolean =
        skipLastSeconds > 0 && durationMs > 0 && positionMs >= durationMs - skipLastSeconds * 1_000L
}
