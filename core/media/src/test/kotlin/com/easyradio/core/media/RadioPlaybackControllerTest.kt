package com.easyradio.core.media

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.easyradio.core.model.RadioStation
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Before
import org.junit.Test

class RadioPlaybackControllerTest {

    private val player: Player = mockk(relaxed = true)
    private val listenerSlot = slot<Player.Listener>()
    private val fakeMediaItem = MediaItem.Builder().setMediaId("fake-media-item").build()

    private lateinit var controller: RadioPlaybackController

    @Before
    fun setUp() {
        every { player.addListener(capture(listenerSlot)) } returns Unit
        controller = RadioPlaybackController(
            player = player,
            mediaItemFactory = { fakeMediaItem },
        )
    }

    @Test
    fun `play sets the station media item, prepares, and starts playback`() {
        controller.play(RadioStation.KFAN_TEST_STATION)

        verifyOrder {
            player.setMediaItem(fakeMediaItem)
            player.prepare()
            player.play()
        }
    }

    @Test
    fun `pause delegates to player pause`() {
        controller.pause()

        verify { player.pause() }
    }

    @Test
    fun `stop delegates to player stop`() {
        controller.stop()

        verify { player.stop() }
    }

    @Test
    fun `state reflects the player becoming ready and playing`() {
        every { player.playbackState } returns Player.STATE_READY
        every { player.playWhenReady } returns true
        every { player.playerError } returns null

        listenerSlot.captured.onPlaybackStateChanged(Player.STATE_READY)

        assertThat(controller.state.value).isEqualTo(PlaybackUiState.PLAYING)
    }

    @Test
    fun `state reflects a player error`() {
        every { player.playbackState } returns Player.STATE_IDLE
        every { player.playWhenReady } returns false
        every { player.playerError } returns mockk(relaxed = true)

        listenerSlot.captured.onPlayerErrorChanged(mockk(relaxed = true))

        assertThat(controller.state.value).isEqualTo(PlaybackUiState.ERROR)
    }

    @Test
    fun `release removes the listener and releases the player`() {
        controller.release()

        verify {
            player.removeListener(any())
            player.release()
        }
    }
}
