package com.easyradio.app.playback

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.easyradio.core.media.PlaybackUiState

/**
 * Owns the reactive state MainActivity derives from its MediaControllerCompat connection --
 * [uiState] and the connected [controller] itself -- plus the connection-lifecycle logic around
 * it: syncing immediately on [attach]/onSessionReady rather than waiting for the next callback
 * (a fresh connection's cached state can be stale), and recovering when the session is destroyed
 * out from under the app. This is exactly the logic that's caused real bugs before (stale uiState
 * after backgrounding, a dangling controller after the service process was reclaimed), and it was
 * previously inline in MainActivity with no test coverage at all.
 *
 * [onNowPlayingMediaIdChanged] is MainActivity's own `syncNowPlayingFromMediaId` -- this class
 * only detects that the now-playing media id changed, not what to do about it (resolving and
 * adopting the episode needs podcastRepository/lifecycleScope, which don't belong here).
 *
 * Kept as a plain class around the real MediaControllerCompat (rather than behind a further
 * interface) since MediaControllerCompat.Callback is itself a concrete, instantiable class with
 * no-op default methods -- a test can register a fake controller (mockk) and invoke the captured
 * callback's methods directly to simulate connection events, the same seam
 * WearStatePublisher/PlaybackSessionController use Player for on the service side.
 */
class SessionConnection(
    private val onNowPlayingMediaIdChanged: (String?) -> Unit,
    private val onSessionDestroyed: () -> Unit = {},
) {
    var controller: MediaControllerCompat? by mutableStateOf(null)
        private set

    var uiState: PlaybackUiState by mutableStateOf(PlaybackUiState.IDLE)
        private set

    private val callback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            uiState = LegacyPlaybackStateMapper.toUiState(state?.state)
        }

        // The service can advance to the next queued episode on its own (auto-advance when one
        // episode ends) without MainActivity ever calling playEpisode() -- reporting this lets
        // MainActivity notice and resync currentEpisode/currentPodcast to match.
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            onNowPlayingMediaIdChanged(metadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID))
        }

        // A real connection isn't synchronous the way Media3's MediaController is -- the
        // controller's cached playbackState/metadata right after connecting can still be stale.
        // onSessionReady fires once the real, current state has actually landed; without this,
        // returning to the app after another session became the system's active one (or any
        // other state change while disconnected) could leave the UI showing whatever stale state
        // happened to be cached at connect time.
        override fun onSessionReady() {
            uiState = LegacyPlaybackStateMapper.toUiState(controller?.playbackState?.state)
            onNowPlayingMediaIdChanged(controller?.metadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID))
        }

        // The session can be destroyed out from under us -- the service stops itself
        // (onTaskRemoved while paused, or an explicit ACTION_STOP) -- without ever going through
        // another onStop()/onStart() cycle if MainActivity stayed in the foreground the whole
        // time. The old assumption that "the next attach() (e.g. onStart's reconnect) recovers"
        // only held when something actually called attach() again; confirmed on a real device
        // (and reproduced by sending a STOP media button while the app stayed open) that it
        // otherwise left a dead controller and stale now-playing info on screen forever -- the
        // Play button looked real but called transportControls on a null controller, doing
        // nothing. onSessionDestroyed lets MainActivity notice and reconnect proactively instead
        // of only reacting to its own lifecycle.
        override fun onSessionDestroyed() {
            controller = null
            uiState = PlaybackUiState.IDLE
            this@SessionConnection.onSessionDestroyed()
        }
    }

    /** Call once a MediaBrowserCompat connection resolves a fresh MediaControllerCompat. */
    fun attach(newController: MediaControllerCompat) {
        controller = newController
        newController.registerCallback(callback)
        // A fresh controller only reports playback state/metadata via the callbacks above on the
        // NEXT change -- attaching here after the session already settled to whatever it's
        // currently showing means no change ever fires. For uiState that leaves it stale (e.g.
        // still "Playing" with no audio, toggling the wrong direction); for the now-playing media
        // id, onSessionReady turned out not to fire at all once the session was already stable
        // (confirmed on a real device: a station kept playing correctly in the background, but
        // reopening the app after it was recreated showed no mini-player at all, since nothing
        // ever told MainActivity what was playing). Sync both immediately instead of waiting.
        uiState = LegacyPlaybackStateMapper.toUiState(newController.playbackState?.state)
        onNowPlayingMediaIdChanged(newController.metadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID))
    }

    fun detach() {
        controller?.unregisterCallback(callback)
        controller = null
    }
}
