package com.easyradio.app.playback

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.easyradio.app.MainActivity
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumented coverage for the real MediaSessionCompat/MediaControllerCompat pipeline
 * PlaybackSessionControllerTest can't reach with a mocked Player: this connects to the actual
 * EasyRadioPlaybackService the same way MainActivity does (MediaBrowserCompat.connect()), plays a
 * bundled local test file (a tiny silent WAV, so this never depends on network access), and
 * asserts the real session's PlaybackStateCompat transitions the way it's been manually verified
 * to on a real device after every playback-layer refactor this session (#42, #43) via
 * `adb shell dumpsys media_session`.
 *
 * MediaBrowserCompat/MediaControllerCompat create internal Handlers bound to whatever thread
 * constructs/calls them, which requires a prepared Looper -- the bare instrumentation thread
 * doesn't have one (confirmed by a real
 * "Can't create handler inside thread ... that has not called Looper.prepare()" crash), so every
 * interaction with either class runs via [Instrumentation.runOnMainSync].
 *
 * Also launches a real MainActivity via [ActivityScenario]: without a visible foreground
 * activity, the emulator's audio focus hardening denied ExoPlayer's focus request outright
 * (confirmed via a real "Focus request DENIED ... procState:4" logcat line), which left playback
 * stuck at PAUSED forever since ExoPlayer never proceeds to PLAYING without focus. A real device
 * manually tested throughout this session never hit this because the app was always foregrounded.
 */
@RunWith(AndroidJUnit4::class)
class EasyRadioPlaybackServiceInstrumentedTest {

    private lateinit var instrumentation: Instrumentation
    private lateinit var activityScenario: ActivityScenario<MainActivity>
    private lateinit var mediaBrowser: MediaBrowserCompat
    private lateinit var controller: MediaControllerCompat

    @Before
    fun connectToService() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connected = CountDownLatch(1)

        // connect() itself is async -- onConnected() fires later, as a separate message on the
        // main looper, so this block just kicks it off rather than waiting inline (which would
        // deadlock: the callback needs the same looper this block is running on to ever fire).
        instrumentation.runOnMainSync {
            mediaBrowser = MediaBrowserCompat(
                context,
                ComponentName(context, EasyRadioPlaybackService::class.java),
                object : MediaBrowserCompat.ConnectionCallback() {
                    override fun onConnected() {
                        controller = MediaControllerCompat(context, mediaBrowser.sessionToken)
                        connected.countDown()
                    }
                },
                null,
            )
            mediaBrowser.connect()
        }
        assertThat(connected.await(10, TimeUnit.SECONDS)).isTrue()
    }

    @After
    fun disconnect() {
        instrumentation.runOnMainSync {
            controller.transportControls.stop()
            mediaBrowser.disconnect()
        }
        activityScenario.close()
    }

    /**
     * The session's onPlaybackStateChanged callback only fires on a *change*, so a state that's
     * already true when we start observing (a race against a fast local-file transition) would
     * otherwise never signal -- checking the already-current state first closes that window.
     * Registering the callback (like all MediaControllerCompat interaction) happens on the main
     * thread, but the actual wait happens on the instrumentation thread so the main looper stays
     * free to keep delivering the callback as real state changes arrive.
     */
    private fun awaitState(timeoutSeconds: Long = 10, predicate: (Int) -> Boolean): PlaybackStateCompat {
        val latch = CountDownLatch(1)
        var result: PlaybackStateCompat? = null
        val callback = object : MediaControllerCompat.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                if (state != null && predicate(state.state)) {
                    result = state
                    latch.countDown()
                }
            }
        }
        instrumentation.runOnMainSync {
            controller.playbackState?.let { current ->
                if (predicate(current.state)) {
                    result = current
                    latch.countDown()
                }
            }
            controller.registerCallback(callback)
        }
        try {
            assertThat(latch.await(timeoutSeconds, TimeUnit.SECONDS)).isTrue()
        } finally {
            instrumentation.runOnMainSync { controller.unregisterCallback(callback) }
        }
        return result!!
    }

    private fun localTestAudioUri(): Uri {
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val outFile = File(appContext.cacheDir, "instrumented_test_audio.wav")
        instrumentationContext.assets.open("test_audio.wav").use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(outFile)
    }

    @Test
    fun playThenPauseTransitionsTheRealSessionState() {
        val uri = localTestAudioUri()
        instrumentation.runOnMainSync { controller.transportControls.playFromUri(uri, null) }

        val playing = awaitState { it == PlaybackStateCompat.STATE_PLAYING }
        assertThat(playing.state).isEqualTo(PlaybackStateCompat.STATE_PLAYING)

        instrumentation.runOnMainSync { controller.transportControls.pause() }

        val paused = awaitState { it == PlaybackStateCompat.STATE_PAUSED }
        assertThat(paused.state).isEqualTo(PlaybackStateCompat.STATE_PAUSED)
    }

    @Test
    fun playingAgainAfterStopActuallyResumesPlayback() {
        // Regression test for a real bug report: ACTION_STOP (e.g. the notification's delete
        // intent) calls player.stop(), which transitions ExoPlayer to STATE_IDLE -- an idle
        // player needs prepare() again before play() has any effect. onPlay() previously called
        // player.play() directly with no such check, so the Play button looked real (and showed
        // the right icon) but silently did nothing at all, confirmed on a real device and
        // reproduced by sending a STOP media button while the app stayed open.
        val uri = localTestAudioUri()
        instrumentation.runOnMainSync { controller.transportControls.playFromUri(uri, null) }
        awaitState { it == PlaybackStateCompat.STATE_PLAYING }

        instrumentation.runOnMainSync { controller.transportControls.stop() }
        awaitState { it == PlaybackStateCompat.STATE_NONE || it == PlaybackStateCompat.STATE_STOPPED }

        instrumentation.runOnMainSync { controller.transportControls.play() }

        val playing = awaitState { it == PlaybackStateCompat.STATE_PLAYING }
        assertThat(playing.state).isEqualTo(PlaybackStateCompat.STATE_PLAYING)
    }
}
