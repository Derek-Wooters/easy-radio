package com.easyradio.app.playback

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.easyradio.core.media.PlaybackUiState
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// MediaMetadataCompat stores its fields in a real android.os.Bundle internally, which needs
// Robolectric's shadow to actually store/retrieve values -- under the plain unit-test stub jar
// (isReturnDefaultValues = true), putString()/getString() silently no-op/return null.
@RunWith(RobolectricTestRunner::class)
class SessionConnectionTest {

    private fun stateOf(compatState: Int) = PlaybackStateCompat.Builder().setState(compatState, 0, 1f).build()

    private fun metadataWithMediaId(mediaId: String?) = MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId)
        .build()

    /** Captures the callback SessionConnection registers, so tests can invoke it like a fake bus. */
    private fun fakeController(playbackState: PlaybackStateCompat? = null, metadata: MediaMetadataCompat? = null):
        Pair<MediaControllerCompat, () -> MediaControllerCompat.Callback> {
        val controller = mockk<MediaControllerCompat>(relaxed = true)
        every { controller.playbackState } returns playbackState
        every { controller.metadata } returns metadata
        val callbackSlot = slot<MediaControllerCompat.Callback>()
        every { controller.registerCallback(capture(callbackSlot)) } just Runs
        return controller to { callbackSlot.captured }
    }

    @Test
    fun `attach immediately syncs uiState from the controller's already-current state`() {
        val (controller, _) = fakeController(playbackState = stateOf(PlaybackStateCompat.STATE_PLAYING))
        var reportedMediaId: String? = "unset"
        val sut = SessionConnection(onNowPlayingMediaIdChanged = { reportedMediaId = it })

        sut.attach(controller)

        assertThat(sut.uiState).isEqualTo(PlaybackUiState.PLAYING)
        assertThat(sut.controller).isSameInstanceAs(controller)
        // attach() alone reports uiState, not now-playing -- that only ever comes from a real
        // metadata change or onSessionReady.
        assertThat(reportedMediaId).isEqualTo("unset")
    }

    @Test
    fun `a playback state change updates uiState`() {
        val (controller, callback) = fakeController(playbackState = stateOf(PlaybackStateCompat.STATE_NONE))
        val sut = SessionConnection(onNowPlayingMediaIdChanged = {})
        sut.attach(controller)

        callback().onPlaybackStateChanged(stateOf(PlaybackStateCompat.STATE_PAUSED))

        assertThat(sut.uiState).isEqualTo(PlaybackUiState.PAUSED)
    }

    @Test
    fun `a metadata change reports the new media id`() {
        val (controller, callback) = fakeController()
        var reportedMediaId: String? = null
        val sut = SessionConnection(onNowPlayingMediaIdChanged = { reportedMediaId = it })
        sut.attach(controller)

        callback().onMetadataChanged(metadataWithMediaId("episode/ep1"))

        assertThat(reportedMediaId).isEqualTo("episode/ep1")
    }

    @Test
    fun `onSessionReady re-syncs both uiState and the now-playing media id from the controller`() {
        val (controller, callback) = fakeController(
            playbackState = stateOf(PlaybackStateCompat.STATE_PLAYING),
            metadata = metadataWithMediaId("episode/ep2"),
        )
        var reportedMediaId: String? = null
        val sut = SessionConnection(onNowPlayingMediaIdChanged = { reportedMediaId = it })
        sut.attach(controller)

        callback().onSessionReady()

        assertThat(sut.uiState).isEqualTo(PlaybackUiState.PLAYING)
        assertThat(reportedMediaId).isEqualTo("episode/ep2")
    }

    @Test
    fun `onSessionDestroyed drops the controller and resets uiState to idle`() {
        val (controller, callback) = fakeController(playbackState = stateOf(PlaybackStateCompat.STATE_PLAYING))
        val sut = SessionConnection(onNowPlayingMediaIdChanged = {})
        sut.attach(controller)

        callback().onSessionDestroyed()

        assertThat(sut.controller).isNull()
        assertThat(sut.uiState).isEqualTo(PlaybackUiState.IDLE)
    }

    @Test
    fun `detach unregisters the callback and clears the controller`() {
        val (controller, _) = fakeController()
        val sut = SessionConnection(onNowPlayingMediaIdChanged = {})
        sut.attach(controller)

        sut.detach()

        assertThat(sut.controller).isNull()
        verify { controller.unregisterCallback(any()) }
    }
}
