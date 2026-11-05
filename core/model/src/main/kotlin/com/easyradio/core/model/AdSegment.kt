package com.easyradio.core.model

/** A detected ad break within an episode's real downloaded audio, in playback-time milliseconds. */
data class AdSegment(
    val startMs: Long,
    val endMs: Long,
)
