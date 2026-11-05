package com.easyradio.core.media

import com.easyradio.core.model.AdSegment

object AdSkipDecision {

    /** Returns the position to seek to if [positionMs] has landed inside an ad segment, else null. */
    fun resolve(positionMs: Long, segments: List<AdSegment>): Long? =
        segments.firstOrNull { positionMs in it.startMs until it.endMs }?.endMs
}
