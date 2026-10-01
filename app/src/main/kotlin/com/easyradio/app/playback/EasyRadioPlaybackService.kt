package com.easyradio.app.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.easyradio.app.EasyRadioGraph
import com.easyradio.app.MainActivity
import com.easyradio.core.media.BrowseNode
import com.easyradio.core.media.MediaBrowseTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** LoudnessEnhancer gain, in millibels (100 mB = 1 dB), applied when "voice boost" is on. */
private const val VOICE_BOOST_GAIN_MILLIBELS = 1000
private const val NOTIFICATION_ID = 1001
private const val NOTIFICATION_CHANNEL_ID = "playback"

/** Extras key [MainActivity] uses to pass pre-resolved metadata alongside `playFromUri`. */
const val EXTRA_TITLE = "com.easyradio.app.EXTRA_TITLE"
const val EXTRA_ARTIST = "com.easyradio.app.EXTRA_ARTIST"
const val EXTRA_ARTWORK_URL = "com.easyradio.app.EXTRA_ARTWORK_URL"
const val EXTRA_RESUME_POSITION_MS = "com.easyradio.app.EXTRA_RESUME_POSITION_MS"

/** Extras key identifying which podcast episode is being played, for auto-advance/end-of-episode. */
const val EXTRA_EPISODE_ID = "com.easyradio.app.EXTRA_EPISODE_ID"

/**
 * Extras key identifying which radio station is being played, published back out via the
 * session's own media id (see PlaybackSessionController.publishMetadata()) so MainActivity can
 * resync currentStation if it's recreated while the service keeps a station playing -- without
 * this, reconnecting after e.g. the user backing out of the app left the mini-player/Now Playing
 * bar missing entirely despite audio still genuinely playing.
 */
const val EXTRA_STATION_ID = "com.easyradio.app.EXTRA_STATION_ID"

/**
 * Custom [MediaSessionCompat.Callback.onCustomAction] sent by [MainActivity]'s sleep-timer picker
 * to arm a one-shot "stop instead of advancing to the next queued episode" for the episode
 * currently playing, rather than the persisted minutes-based default.
 */
const val ACTION_SLEEP_AT_END_OF_EPISODE = "com.easyradio.app.ACTION_SLEEP_AT_END_OF_EPISODE"

/**
 * Hand-built [MediaSessionCompat]/[PlaybackStateCompat] session backing all playback surfaces,
 * replacing an earlier Media3 [androidx.media3.session.MediaLibraryService]-based
 * implementation. Media3's automatic translation into the legacy platform `PlaybackState` gave
 * no control over exactly which standard actions get advertised, which -- confirmed via a
 * side-by-side comparison against Pocket Casts on the same phone/watch, and by reading Pocket
 * Casts' own (open source) session code -- is what made Wear OS's system media card hardcode a
 * generic, non-functional "previous" icon into the back slot regardless of what button we
 * assigned there. Building the session by hand, matching Pocket Casts' actual bit-for-bit
 * action configuration, removes that ceiling entirely.
 *
 * ExoPlayer remains the actual playback engine; only the session/control layer around it
 * changed. Beyond serving the session the phone UI controls, this also exposes a browse tree
 * (root -> Radio / Podcasts -> stations / episodes) so Android Auto and Android Automotive OS
 * can browse and play without the phone screen.
 *
 * This class itself only owns genuinely Android-only concerns: building the real ExoPlayer and
 * MediaSessionCompat, the foreground-service/notification lifecycle, and forwarding
 * Player.Listener/MediaSessionCompat.Callback events into [PlaybackSessionController], which owns
 * the actual playback/session-state orchestration in a plain, unit-testable class.
 */
class EasyRadioPlaybackService : MediaBrowserServiceCompat() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var sessionController: PlaybackSessionController
    private lateinit var wearStatePublisher: WearStatePublisher
    private lateinit var notificationManager: NotificationManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var audioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var voiceBoostEnabled: Boolean = false
    private var isForegroundService = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java)!!

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus= */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(15_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
            .apply {
                // Holds a CPU + WiFi wake lock while playing/buffering so a network
                // handoff (wifi <-> cellular) or screen-off doesn't stall a live
                // stream's reconnect longer than necessary.
                setWakeMode(C.WAKE_MODE_NETWORK)
                // Ignore in-band ICY/ID3 metadata entirely. ExoPlayer otherwise merges it into
                // Player.getMediaMetadata(), overwriting the title we set explicitly -- confirmed
                // as the source of garbled titles on the lock screen and Wear OS's media card
                // (some streams' ad-insertion embeds tracking urls, not a clean title, in these
                // tags). Our own metadata is always accurate, so there is nothing worth reading
                // from the stream itself.
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_METADATA, true)
                    .build()
            }

        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, newAudioSessionId: Int) {
                audioSessionId = newAudioSessionId
                refreshLoudnessEnhancer()
            }
        })

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSessionCompat(this, "EasyRadioSession").apply {
            setSessionActivity(sessionActivity)
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setCallback(SessionCallback())
            isActive = true
        }
        sessionToken = mediaSession.sessionToken

        sessionController = PlaybackSessionController(
            player = player,
            repository = EasyRadioGraph.repository(applicationContext),
            onPlaybackStateChanged = mediaSession::setPlaybackState,
            onMetadataChanged = mediaSession::setMetadata,
            onPlaybackStarting = ::ensureStarted,
        )

        player.addListener(PlayerEventListener())

        wearStatePublisher = WearStatePublisher(this, player).also { it.attach() }

        ensureNotificationChannel()
        sessionController.publishPlaybackState()

        serviceScope.launch {
            EasyRadioGraph.settings(applicationContext).settings.collect { settings ->
                player.setSkipSilenceEnabled(settings.skipSilenceEnabled)
                voiceBoostEnabled = settings.voiceBoostEnabled
                refreshLoudnessEnhancer()
                // Keep the lock-screen/notification/Wear rewind and fast-forward amounts in
                // sync with the app's configurable skip amounts.
                player.setSeekBackIncrementMs(settings.skipBackSeconds * 1_000L)
                player.setSeekForwardIncrementMs(settings.skipForwardSeconds * 1_000L)
            }
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW),
        )
    }

    /**
     * LoudnessEnhancer must be (re)created whenever the audio session id changes (a new
     * track can get a new session) or the "voice boost" setting changes. Recreating rather
     * than reusing avoids holding a stale effect bound to a session id ExoPlayer has moved on
     * from.
     */
    private fun refreshLoudnessEnhancer() {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        loudnessEnhancer?.release()
        loudnessEnhancer = try {
            LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(VOICE_BOOST_GAIN_MILLIBELS)
                enabled = voiceBoostEnabled
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        loudnessEnhancer?.release()
        wearStatePublisher.detach()
        sessionController.release()
        mediaSession.run {
            isActive = false
            release()
        }
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!player.isPlaying) {
            stopSelf()
        }
    }

    // -- Playback state / metadata publishing --------------------------------------------

    private inner class PlayerEventListener : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sessionController.publishPlaybackState()
            sessionController.publishMetadata()
            updateNotification()
        }

        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            // Static title/artist are set explicitly via startPlayback()/currentTitle/
            // currentArtist, not derived from the player's merged metadata (which -- with
            // in-band metadata now disabled -- only ever reflects what we set anyway). This
            // override exists so future callers can't accidentally reintroduce a dependency
            // on stream-derived metadata for the legacy session's title.
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            sessionController.onPlayerPlaybackStateChanged(playbackState)
        }
    }

    private fun updateNotification() {
        val notification = buildNotification()
        if (player.playWhenReady) {
            if (!isForegroundService) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
                isForegroundService = true
            } else {
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } else {
            if (isForegroundService) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                isForegroundService = false
            }
            if (player.playbackState != Player.STATE_IDLE) {
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildNotification(): android.app.Notification {
        val playPauseAction = if (player.playWhenReady) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE),
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY),
            )
        }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle(sessionController.currentTitle ?: "Easy Radio")
            .setContentText(sessionController.currentArtist)
            .setContentIntent(mediaSession.controller.sessionActivity)
            .setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP),
            )
            .setOngoing(player.playWhenReady)
            .addAction(
                android.R.drawable.ic_media_rew,
                "Rewind",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_REWIND),
            )
            .addAction(playPauseAction)
            .addAction(
                android.R.drawable.ic_media_ff,
                "Fast forward",
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_FAST_FORWARD),
            )
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    // -- Starting playback -----------------------------------------------------------------

    /**
     * A service bound only via MediaBrowserCompat.connect() (BIND_AUTO_CREATE, no independent
     * startService()/startForegroundService() call) has no reason to survive once its last
     * client unbinds -- confirmed on a real device: backgrounding the app (MainActivity.onStop()
     * disconnecting its browser) destroyed the service and its session outright, even though the
     * app process itself stayed alive, which is what caused the Now Playing screen to come back
     * showing stale info with a dead controller (no progress bar, Play doing nothing). Explicitly
     * starting the service gives it its own independent lifecycle that a client unbinding can't
     * end; only an explicit onStop() (real ACTION_STOP, see SessionCallback) calls stopSelf().
     */
    private fun ensureStarted() {
        ContextCompat.startForegroundService(this, Intent(this, EasyRadioPlaybackService::class.java))
    }

    private inner class SessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            ensureStarted()
            // onStop() (ACTION_STOP, e.g. the notification's delete intent) calls player.stop(),
            // which transitions to STATE_IDLE -- Media3's contract is that an idle player needs
            // prepare() again before play() has any effect, but this just called play() directly.
            // Confirmed on a real device (and reproduced by sending a STOP media button while the
            // app stayed open): the Play button looked real but silently did nothing at all,
            // since play() on an unprepared, idle player is a no-op.
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }

        override fun onPause() {
            player.pause()
        }

        override fun onStop() {
            player.stop()
            stopSelf()
        }

        override fun onSeekTo(pos: Long) {
            player.seekTo(pos)
        }

        override fun onRewind() {
            player.seekBack()
        }

        override fun onFastForward() {
            player.seekForward()
        }

        // Confirmed on a real Pixel Watch 3: the system media card's rewind/forward-looking
        // buttons dispatch ACTION_SKIP_TO_PREVIOUS/ACTION_SKIP_TO_NEXT, not
        // ACTION_REWIND/ACTION_FAST_FORWARD, despite rendering seek icons rather than
        // previous/next-track icons. There's no real "previous/next track" concept for a
        // single playing item in this app anyway, so treating these the same as rewind/fast
        // forward is the correct behavior here, not just a workaround.
        override fun onSkipToPrevious() {
            player.seekBack()
        }

        override fun onSkipToNext() {
            player.seekForward()
        }

        override fun onSetPlaybackSpeed(speed: Float) {
            player.setPlaybackParameters(androidx.media3.common.PlaybackParameters(speed))
        }

        override fun onPlayFromUri(uri: Uri, extras: Bundle?) {
            sessionController.startPlayback(
                uri = uri,
                title = extras?.getString(EXTRA_TITLE),
                artist = extras?.getString(EXTRA_ARTIST),
                artworkUrl = extras?.getString(EXTRA_ARTWORK_URL),
                resumePositionMs = extras?.getLong(EXTRA_RESUME_POSITION_MS) ?: 0L,
                episodeId = extras?.getString(EXTRA_EPISODE_ID),
                stationId = extras?.getString(EXTRA_STATION_ID),
            )
        }

        override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
            serviceScope.launch { sessionController.startPlaybackFromMediaId(mediaId) }
        }

        override fun onCustomAction(action: String, extras: Bundle?) {
            if (action == ACTION_SLEEP_AT_END_OF_EPISODE) {
                sessionController.armSleepAtEndOfEpisode()
            }
        }
    }

    // -- Browse tree (Android Auto / Automotive) --------------------------------------------

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot =
        BrowserRoot(MediaBrowseTree.ROOT_ID, null)

    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) {
        result.detach()
        serviceScope.launch {
            val items = sessionController.childrenOf(parentId).map { it.toMediaItem() }
            result.sendResult(items)
        }
    }

    private fun BrowseNode.toMediaItem(): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(mediaId)
            .setTitle(title)
            .setSubtitle(subtitle.ifBlank { null })
            .setIconUri(artworkUrl?.let { ArtworkContentProvider.uriFor(it) })
            .build()
        val flags = if (isBrowsable) {
            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        } else {
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        }
        return MediaBrowserCompat.MediaItem(description, flags)
    }
}
