package com.easyradio.app.playback

import android.content.ComponentName
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.easyradio.core.media.SeekMath
import com.easyradio.core.model.CuratedRadioStations
import com.easyradio.core.model.wear.WearCommand
import com.easyradio.core.model.wear.WearSync
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking

private const val SKIP_FORWARD_MS = 30_000L
private const val SKIP_BACK_MS = 15_000L

/**
 * Receives [WearCommand]s the watch sends over the Wearable Data Layer and
 * applies them to the phone's playback session via a short-lived
 * [MediaController]. The session (owned by [EasyRadioPlaybackService]) keeps
 * playing after the controller is released.
 */
class PhoneWearListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearSync.COMMAND_PATH) return
        val command = runCatching { WearSync.decodeCommand(event.data) }.getOrNull() ?: return

        val token = SessionToken(this, ComponentName(this, EasyRadioPlaybackService::class.java))
        // MediaController is thread-confined to the app main looper; connect and
        // drive it there, blocking this background callback until it completes.
        // Guard the whole exchange so a controller/connection failure can't crash.
        runCatching {
            runBlocking(Dispatchers.Main) {
                val controller = MediaController.Builder(this@PhoneWearListenerService, token).buildAsync().await()
                try {
                    apply(controller, command)
                } finally {
                    controller.release()
                }
            }
        }
    }

    private fun apply(controller: MediaController, command: WearCommand) {
        when (command) {
            WearCommand.Play -> controller.play()
            WearCommand.Pause -> controller.pause()
            WearCommand.SkipForward -> controller.seekTo(skipTarget(controller, SKIP_FORWARD_MS))
            WearCommand.SkipBack -> controller.seekTo(skipTarget(controller, -SKIP_BACK_MS))
            is WearCommand.PlayStation -> {
                val station = CuratedRadioStations.ALL.firstOrNull { it.id == command.stationId } ?: return
                controller.setMediaItem(
                    MediaItem.Builder()
                        .setUri(station.streamUrl)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(station.name)
                                .setSubtitle(station.tagline)
                                .build(),
                        )
                        .build(),
                )
                controller.prepare()
                controller.play()
            }
        }
    }

    private fun skipTarget(controller: MediaController, deltaMs: Long): Long =
        SeekMath.clampSeek(
            currentMs = controller.currentPosition,
            deltaMs = deltaMs,
            durationMs = controller.duration.coerceAtLeast(0),
        )
}
