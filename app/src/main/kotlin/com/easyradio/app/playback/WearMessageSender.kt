package com.easyradio.app.playback

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Thin seam over the Wearable Data Layer's [com.google.android.gms.wearable.MessageClient] /
 * [com.google.android.gms.wearable.NodeClient]. Both are abstract classes tied to Play Services'
 * `GoogleApi` construction, so they can't be faked directly in a unit test; callers depend on
 * this interface instead, which a test can implement trivially.
 */
interface WearMessageSender {
    suspend fun connectedNodeIds(): List<String>
    suspend fun sendMessage(nodeId: String, path: String, payload: ByteArray)
}

class PlayServicesWearMessageSender(private val context: Context) : WearMessageSender {
    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    override suspend fun connectedNodeIds(): List<String> =
        nodeClient.connectedNodes.await().map { it.id }

    override suspend fun sendMessage(nodeId: String, path: String, payload: ByteArray) {
        messageClient.sendMessage(nodeId, path, payload).await()
    }
}
