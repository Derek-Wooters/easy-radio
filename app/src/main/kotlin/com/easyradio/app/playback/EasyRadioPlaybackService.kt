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
import android.support.v4.media.MediaMetadataCompat
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.easyradio.app.EasyRadioGraph
import com.easyradio.app.MainActivity
import com.easyradio.core.database.PodcastRepository
import com.easyradio.core.media.BrowseNode
import com.easyradio.core.media.MediaBrowseTree
import com.easyradio.core.model.CuratedRadioStations
import com.easyradio.core.model.Episode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
 * can browse and play without the phone screen. The tree structure comes from the pure
 * [MediaBrowseTree]; this class only adapts [BrowseNode]s to [MediaBrowserCompat.MediaItem]s and
 * resolves a tapped item's uri for ExoPlayer.
 */
class EasyRadioPlaybackService : MediaBrowserServiceCompat() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var repository: PodcastRepository
    private lateinit var wearStatePublisher: WearStatePublisher
    private lateinit var notificationManager: NotificationManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var audioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var voiceBoostEnabled: Boolean = false
    private var isForegroundService = false
    private var currentTitle: String? = null
    private var currentArtist: String? = null
    private var currentArtworkUrl: String? = null

    override fun onCreate() {
        super.onCreate()
        repository = EasyRadioGraph.repository(applicationContext)
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

        player.addListener(PlayerEventListener())

        wearStatePublisher = WearStatePublisher(this, player).also { it.attach() }

        ensureNotificationChannel()
        publishPlaybackState()

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
            publishPlaybackState()
            publishMetadata()
            updateNotification()
        }

        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            // Static title/artist are set explicitly via startPlayback()/currentTitle/
            // currentArtist, not derived from the player's merged metadata (which -- with
            // in-band metadata now disabled -- only ever reflects what we set anyway). This
            // override exists so future callers can't accidentally reintroduce a dependency
            // on stream-derived metadata for the legacy session's title.
        }
    }

    /**
     * Rebuilds and publishes [PlaybackStateCompat] on every relevant player event. Matches
     * Pocket Casts' own advertised action set exactly (verified against their open-source
     * MediaSessionManager.kt): ACTION_REWIND/ACTION_FAST_FORWARD alongside the standard
     * ACTION_SKIP_TO_PREVIOUS/ACTION_SKIP_TO_NEXT, rather than hiding the latter pair the way
     * the prior Media3-based session did -- that hiding is what triggered Wear OS's card to
     * hardcode a generic previous icon into the back slot instead of respecting our button.
     */
    private fun publishPlaybackState() {
        val state = when {
            player.playerError != null -> PlaybackStateCompat.STATE_ERROR
            player.playbackState == Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            player.playbackState == Player.STATE_IDLE -> PlaybackStateCompat.STATE_NONE
            player.playbackState == Player.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
            player.playWhenReady -> PlaybackStateCompat.STATE_PLAYING
            else -> PlaybackStateCompat.STATE_PAUSED
        }
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_REWIND or
            PlaybackStateCompat.ACTION_FAST_FORWARD or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
            PlaybackStateCompat.ACTION_PLAY_FROM_URI

        val playbackSpeed = if (state == PlaybackStateCompat.STATE_PLAYING) player.playbackParameters.speed else 0f
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, player.currentPosition, playbackSpeed)
                .setBufferedPosition(player.bufferedPosition)
                .setActions(actions)
                .build(),
        )
    }

    /**
     * Republishes [MediaMetadataCompat] including the player's current duration. ExoPlayer
     * doesn't know an item's duration until it's loaded enough of the stream/file to determine
     * it, so this has to be re-published as events arrive (not just once in startPlayback()) --
     * omitting METADATA_KEY_DURATION entirely (the original bug here) left
     * MainActivity.controllerDurationMs() always reading 0, which hid the seek/progress
     * indicator on the Now Playing screen since its progress fraction is only shown when a
     * duration is known.
     */
    private fun publishMetadata() {
        val durationMs = player.duration.takeIf { it != C.TIME_UNSET }
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
                .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, currentArtworkUrl)
                .apply { durationMs?.let { putLong(MediaMetadataCompat.METADATA_KEY_DURATION, it) } }
                .build(),
        )
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
            .setContentTitle(currentTitle ?: "Easy Radio")
            .setContentText(currentArtist)
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

    private fun startPlayback(uri: Uri, title: String?, artist: String?, artworkUrl: String?, resumePositionMs: Long) {
        ensureStarted()
        currentTitle = title
        currentArtist = artist
        currentArtworkUrl = artworkUrl
        publishMetadata()
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(artworkUrl?.let { Uri.parse(it) })
                    .build(),
            )
            .build()
        player.setMediaItem(mediaItem, resumePositionMs)
        player.prepare()
        player.play()
    }

    /** Resolves a browse-tree media id (used by Android Auto's tap-to-play) to a playable uri. */
    private suspend fun startPlaybackFromMediaId(mediaId: String) {
        when {
            mediaId.startsWith(MediaBrowseTree.STATION_PREFIX) -> {
                val station = CuratedRadioStations.ALL.firstOrNull {
                    MediaBrowseTree.STATION_PREFIX + it.id == mediaId
                } ?: return
                startPlayback(Uri.parse(station.streamUrl), station.name, station.tagline, station.imageUrl, 0L)
            }
            mediaId.startsWith(MediaBrowseTree.EPISODE_PREFIX) -> {
                val episode = allSubscribedEpisodes().firstOrNull {
                    MediaBrowseTree.EPISODE_PREFIX + it.id == mediaId
                } ?: return
                val podcast = repository.subscribedPodcasts().first().firstOrNull { it.id == episode.podcastId }
                val uri = episode.localFilePath?.let { Uri.fromFile(java.io.File(it)) } ?: Uri.parse(episode.audioUrl)
                val resumeMs = repository.lastPosition(episode.id)
                startPlayback(uri, episode.title, podcast?.author?.ifBlank { podcast.title }, podcast?.artworkUrl, resumeMs)
            }
        }
    }

    private inner class SessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            ensureStarted()
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
            startPlayback(
                uri = uri,
                title = extras?.getString(EXTRA_TITLE),
                artist = extras?.getString(EXTRA_ARTIST),
                artworkUrl = extras?.getString(EXTRA_ARTWORK_URL),
                resumePositionMs = extras?.getLong(EXTRA_RESUME_POSITION_MS) ?: 0L,
            )
        }

        override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
            serviceScope.launch { startPlaybackFromMediaId(mediaId) }
        }
    }

    // -- Browse tree (Android Auto / Automotive) --------------------------------------------

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot =
        BrowserRoot(MediaBrowseTree.ROOT_ID, null)

    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) {
        result.detach()
        serviceScope.launch {
            val items = childrenOf(parentId).map { it.toMediaItem() }
            result.sendResult(items)
        }
    }

    private suspend fun allSubscribedEpisodes(): List<Episode> =
        repository.subscribedPodcasts().first().flatMap { repository.episodesFor(it.id).first() }

    private suspend fun childrenOf(parentId: String): List<BrowseNode> = when {
        parentId == MediaBrowseTree.ROOT_ID -> MediaBrowseTree.rootChildren()
        parentId == MediaBrowseTree.RADIO_ID -> MediaBrowseTree.stationNodes(CuratedRadioStations.ALL)
        parentId == MediaBrowseTree.PODCASTS_ID ->
            MediaBrowseTree.podcastNodes(repository.subscribedPodcasts().first())
        parentId.startsWith(MediaBrowseTree.PODCAST_PREFIX) ->
            MediaBrowseTree.episodeNodes(
                repository.episodesFor(parentId.removePrefix(MediaBrowseTree.PODCAST_PREFIX)).first(),
            )
        else -> emptyList()
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
