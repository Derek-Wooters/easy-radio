package com.easyradio.wear

import android.content.Context
import com.easyradio.core.model.wear.NowPlayingState
import com.easyradio.core.model.wear.WearCommand
import com.easyradio.core.model.wear.WearSync
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watch-side bridge to the phone over the Wearable Data Layer. Sends
 * [WearCommand]s to every connected node and surfaces the latest
 * [NowPlayingState] pushed back by the phone as a [StateFlow] the UI observes.
 */
class WearMediaClient(private val context: Context) {

    private val messageClient: MessageClient = Wearable.getMessageClient(context)

    private val _nowPlaying = MutableStateFlow<NowPlayingState?>(null)
    val nowPlaying: StateFlow<NowPlayingState?> = _nowPlaying.asStateFlow()

    private val listener = MessageClient.OnMessageReceivedListener { event ->
        if (event.path == WearSync.STATE_PATH) {
            runCatching { WearSync.decodeState(event.data) }.getOrNull()?.let { _nowPlaying.value = it }
        }
    }

    /** Begin listening for now-playing updates; call while the UI is visible. */
    fun start() {
        messageClient.addListener(listener)
    }

    fun stop() {
        messageClient.removeListener(listener)
    }

    /** Fire a command to the phone. No-op if no node is currently connected. */
    fun send(command: WearCommand) {
        val payload = WearSync.encodeCommand(command)
        Wearable.getNodeClient(context).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                messageClient.sendMessage(node.id, WearSync.COMMAND_PATH, payload)
            }
        }
    }
}
