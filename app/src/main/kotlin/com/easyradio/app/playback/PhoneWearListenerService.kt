package com.easyradio.app.playback

import android.content.ComponentName
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.easyradio.core.media.WearCommandMapper
import com.easyradio.core.media.WearPlayerAction
import com.easyradio.core.model.wear.WearCommand
import com.easyradio.core.model.wear.WearSync
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking

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
        val action = WearCommandMapper.map(
            command = command,
            currentPositionMs = controller.currentPosition,
            durationMs = controller.duration.coerceAtLeast(0),
        )
        when (action) {
            WearPlayerAction.Play -> controller.play()
            WearPlayerAction.Pause -> controller.pause()
            is WearPlayerAction.SeekTo -> controller.seekTo(action.positionMs)
            is WearPlayerAction.PlayStream -> {
                controller.setMediaItem(
                    MediaItem.Builder()
                        .setUri(action.streamUrl)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(action.title)
                                .setSubtitle(action.subtitle)
                                .build(),
                        )
                        .build(),
                )
                controller.prepare()
                controller.play()
            }
            WearPlayerAction.Ignore -> Unit
        }
    }
}
