package com.easyradio.app.playback

import android.content.ComponentName
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import com.easyradio.core.media.WearCommandMapper
import com.easyradio.core.media.WearPlayerAction
import com.easyradio.core.model.wear.WearCommand
import com.easyradio.core.model.wear.WearSync
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Receives [WearCommand]s the watch sends over the Wearable Data Layer and
 * applies them to the phone's playback session via a short-lived
 * [MediaControllerCompat]. The session (owned by [EasyRadioPlaybackService]) keeps
 * playing after the controller connection is released.
 */
class PhoneWearListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearSync.COMMAND_PATH) return
        val command = runCatching { WearSync.decodeCommand(event.data) }.getOrNull() ?: return

        // MediaBrowserCompat/MediaControllerCompat are thread-confined to the app main looper;
        // connect and drive them there, blocking this background callback until it completes.
        // Guard the whole exchange so a connection failure can't crash.
        runCatching {
            runBlocking(Dispatchers.Main) {
                val browser = connectBrowser()
                try {
                    val controller = MediaControllerCompat(this@PhoneWearListenerService, browser.sessionToken)
                    apply(controller, command)
                } finally {
                    browser.disconnect()
                }
            }
        }
    }

    private suspend fun connectBrowser(): MediaBrowserCompat = suspendCancellableCoroutine { continuation ->
        lateinit var browser: MediaBrowserCompat
        browser = MediaBrowserCompat(
            this,
            ComponentName(this, EasyRadioPlaybackService::class.java),
            object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    continuation.resumeWith(Result.success(browser))
                }

                override fun onConnectionFailed() {
                    continuation.resumeWith(Result.failure(IllegalStateException("Wear command: session connection failed")))
                }
            },
            null,
        )
        browser.connect()
        continuation.invokeOnCancellation { browser.disconnect() }
    }

    private fun apply(controller: MediaControllerCompat, command: WearCommand) {
        val action = WearCommandMapper.map(command)
        val transportControls = controller.transportControls
        when (action) {
            WearPlayerAction.Play -> transportControls.play()
            WearPlayerAction.Pause -> transportControls.pause()
            WearPlayerAction.SeekForward -> transportControls.fastForward()
            WearPlayerAction.SeekBack -> transportControls.rewind()
            is WearPlayerAction.PlayStream -> {
                transportControls.playFromUri(
                    android.net.Uri.parse(action.streamUrl),
                    Bundle().apply {
                        putString(EXTRA_TITLE, action.title)
                        putString(EXTRA_ARTIST, action.subtitle)
                    },
                )
            }
            WearPlayerAction.Ignore -> Unit
        }
    }
}
