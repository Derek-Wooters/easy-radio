package com.easyradio.core.media

object SeekMath {

    fun clampSeek(currentMs: Long, deltaMs: Long, durationMs: Long): Long {
        val target = currentMs + deltaMs
        val upperBound = if (durationMs > 0) durationMs else Long.MAX_VALUE
        return target.coerceIn(0, upperBound)
    }
}
