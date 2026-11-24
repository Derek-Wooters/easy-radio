package com.easyradio.app.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import com.easyradio.core.media.LiveOffset
import kotlinx.coroutines.delay

/**
 * How far behind live the current station is right now, in ms. Re-evaluated every second only
 * while the stream is actually paused (the offset is growing); otherwise it's a constant read
 * straight off [offset], so a playing station costs nothing.
 */
@Composable
fun rememberBehindLiveMs(offset: LiveOffset?): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val ticking = offset?.pausedSinceMs != null
    LaunchedEffect(ticking, offset) {
        now = SystemClock.elapsedRealtime()
        while (ticking) {
            delay(1_000L)
            now = SystemClock.elapsedRealtime()
        }
    }
    return offset?.behindMs(now) ?: 0L
}
