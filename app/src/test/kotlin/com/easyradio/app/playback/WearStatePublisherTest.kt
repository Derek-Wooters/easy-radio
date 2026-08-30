package com.easyradio.app.playback

import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.easyradio.core.model.wear.WearSync
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private class FakeWearMessageSender(
    private val nodeIds: List<String> = emptyList(),
    private val failure: Throwable? = null,
) : WearMessageSender {
    val sentMessages = mutableListOf<Pair<String, ByteArray>>()

    override suspend fun connectedNodeIds(): List<String> {
        failure?.let { throw it }
        return nodeIds
    }

    override suspend fun sendMessage(nodeId: String, path: String, payload: ByteArray) {
        sentMessages.add(nodeId to payload)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WearStatePublisherTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakePlayer(
        title: String? = "KFAN FM 100.3",
        subtitle: String? = "Audio Home For Minnesota Sports",
        isPlaying: Boolean = true,
        isSeekable: Boolean = false,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.mediaMetadata } returns MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()
        every { player.isPlaying } returns isPlaying
        every { player.isCurrentMediaItemSeekable } returns isSeekable
        return player
    }

    @Test
    fun `attach publishes the current state to every connected node`() = runTest(dispatcher) {
        val sender = FakeWearMessageSender(nodeIds = listOf("node-1", "node-2"))
        val publisher = WearStatePublisher(fakePlayer(), sender)

        publisher.attach()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(sender.sentMessages.map { it.first }).containsExactly("node-1", "node-2")
        val state = WearSync.decodeState(sender.sentMessages.first().second)
        assertThat(state.title).isEqualTo("KFAN FM 100.3")
        assertThat(state.subtitle).isEqualTo("Audio Home For Minnesota Sports")
        assertThat(state.isPlaying).isTrue()
    }

    @Test
    fun `a failure fetching connected nodes does not crash publish`() = runTest(dispatcher) {
        val sender = FakeWearMessageSender(failure = RuntimeException("no wear api"))
        val publisher = WearStatePublisher(fakePlayer(), sender)

        publisher.attach()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(sender.sentMessages).isEmpty()
    }

    @Test
    fun `no connected nodes means no messages are sent`() = runTest(dispatcher) {
        val sender = FakeWearMessageSender(nodeIds = emptyList())
        val publisher = WearStatePublisher(fakePlayer(), sender)

        publisher.attach()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(sender.sentMessages).isEmpty()
    }
}
