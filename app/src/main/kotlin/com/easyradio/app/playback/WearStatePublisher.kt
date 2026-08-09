package com.easyradio.app.playback

import android.content.Context
import androidx.media3.common.Player
import com.easyradio.core.model.wear.NowPlayingState
import com.easyradio.core.model.wear.WearSync
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Pushes the phone's now-playing state to the paired watch over the Wearable
 * Data Layer whenever playback changes, so the watch's remote-control UI stays
 * in sync. Reads the [Player]'s combined media metadata for the title/subtitle
 * and derives `canSkip` from whether the current item is seekable (live radio
 * is not; podcasts are).
 */
class WearStatePublisher(
    private val context: Context,
    private val player: Player,
) {
    private val messageClient = Wearable.getMessageClient(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
    }

    fun attach() {
        player.addListener(listener)
        publish()
    }

    fun detach() {
        player.removeListener(listener)
        scope.cancel()
    }

    private fun publish() {
        val metadata = player.mediaMetadata
        val state = NowPlayingState(
            title = metadata.title?.toString() ?: "",
            subtitle = metadata.subtitle?.toString() ?: metadata.artist?.toString() ?: "",
            isPlaying = player.isPlaying,
            canSkip = player.isCurrentMediaItemSeekable,
        )
        val payload = WearSync.encodeState(state)
        scope.launch {
            // No watch paired, or no Wear support on this device (the Wearable API
            // is unavailable on non-Wear-enabled builds) -> silently skip. This
            // must never crash normal phone playback.
            runCatching {
                val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                for (node in nodes) {
                    messageClient.sendMessage(node.id, WearSync.STATE_PATH, payload)
                }
            }
        }
    }
}
