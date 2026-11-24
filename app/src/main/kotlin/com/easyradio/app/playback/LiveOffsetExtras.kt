package com.easyradio.app.playback

import android.os.Bundle
import com.easyradio.core.media.LiveOffset

/**
 * Carries a radio stream's [LiveOffset] across the legacy session boundary as
 * [android.support.v4.media.session.PlaybackStateCompat] extras (service -> MainActivity).
 * Absent entirely for podcast episodes, which have no live edge.
 */
object LiveOffsetExtras {
    const val KEY_BASE_MS = "com.easyradio.app.LIVE_BASE_MS"
    const val KEY_PAUSED_SINCE = "com.easyradio.app.LIVE_PAUSED_SINCE"
    private const val NOT_PAUSED = -1L

    fun toBundle(offset: LiveOffset): Bundle = Bundle().apply {
        putLong(KEY_BASE_MS, offset.baseMs)
        putLong(KEY_PAUSED_SINCE, offset.pausedSinceMs ?: NOT_PAUSED)
    }

    fun fromBundle(extras: Bundle?): LiveOffset? {
        if (extras == null || !extras.containsKey(KEY_BASE_MS)) return null
        val pausedSince = extras.getLong(KEY_PAUSED_SINCE, NOT_PAUSED)
        return LiveOffset(extras.getLong(KEY_BASE_MS), pausedSince.takeIf { it != NOT_PAUSED })
    }
}
