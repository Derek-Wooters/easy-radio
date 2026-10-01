package com.easyradio.app.playback

import android.support.v4.media.session.PlaybackStateCompat
import androidx.media3.common.Player
import com.easyradio.core.media.PlaybackUiState

/**
 * Pure mapping across both legs of the hand-rolled legacy session pipeline: [toCompatState] is
 * what [EasyRadioPlaybackService] publishes from ExoPlayer's own state, and [toUiState] is what
 * MainActivity derives from the [PlaybackStateCompat] it reads back off a
 * [android.support.v4.media.session.MediaControllerCompat]. Kept pure and out of both classes so
 * a state-precedence mistake (e.g. checking playWhenReady before an error) is a failing unit test
 * instead of something only caught on a real device.
 */
object LegacyPlaybackStateMapper {

    // isPlaying, not playWhenReady: ExoPlayer keeps playWhenReady=true during a transient audio
    // focus loss it expects to auto-recover from (e.g. another app briefly taking focus), only
    // setting Player.playbackSuppressionReason instead -- confirmed on-device (a real bug report)
    // that playWhenReady alone reported STATE_PLAYING, pause icon and all, while the stream was
    // genuinely silent and the user had no way to tell short of tapping the (wrongly labeled)
    // button. isPlaying is Media3's own definitive "is this actually audible right now" signal:
    // true only when playbackState == STATE_READY, playWhenReady == true, AND suppressionReason
    // == NONE.
    fun toCompatState(hasError: Boolean, playbackState: Int, isPlaying: Boolean): Int = when {
        hasError -> PlaybackStateCompat.STATE_ERROR
        playbackState == Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
        playbackState == Player.STATE_IDLE -> PlaybackStateCompat.STATE_NONE
        playbackState == Player.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
        isPlaying -> PlaybackStateCompat.STATE_PLAYING
        else -> PlaybackStateCompat.STATE_PAUSED
    }

    fun toUiState(compatState: Int?): PlaybackUiState = when (compatState) {
        null -> PlaybackUiState.IDLE
        PlaybackStateCompat.STATE_ERROR -> PlaybackUiState.ERROR
        PlaybackStateCompat.STATE_BUFFERING -> PlaybackUiState.BUFFERING
        PlaybackStateCompat.STATE_PLAYING -> PlaybackUiState.PLAYING
        PlaybackStateCompat.STATE_PAUSED -> PlaybackUiState.PAUSED
        else -> PlaybackUiState.IDLE
    }
}
