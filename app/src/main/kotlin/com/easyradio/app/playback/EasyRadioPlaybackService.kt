package com.easyradio.app.playback

import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.easyradio.app.EasyRadioGraph
import com.easyradio.core.database.PodcastRepository
import com.easyradio.core.media.BrowseNode
import com.easyradio.core.media.MediaBrowseTree
import com.easyradio.core.model.AppSettings
import com.easyradio.core.model.CuratedRadioStations
import com.easyradio.core.model.Episode
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

/** LoudnessEnhancer gain, in millibels (100 mB = 1 dB), applied when "voice boost" is on. */
private const val VOICE_BOOST_GAIN_MILLIBELS = 1000

/**
 * Media3 [MediaLibraryService] backing all playback surfaces. Beyond serving the
 * [MediaSession] the phone UI controls, it exposes a browse tree (root -> Radio /
 * Podcasts -> stations / episodes) so Android Auto and Android Automotive OS can
 * browse and play without the phone screen. The tree structure comes from the
 * pure [MediaBrowseTree]; this class only adapts [BrowseNode]s to media3
 * [MediaItem]s and resolves a tapped item's uri for ExoPlayer.
 */
class EasyRadioPlaybackService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var repository: PodcastRepository
    private lateinit var wearStatePublisher: WearStatePublisher
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var audioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var voiceBoostEnabled: Boolean = false

    override fun onCreate() {
        super.onCreate()
        repository = EasyRadioGraph.repository(applicationContext)

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
            }

        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, newAudioSessionId: Int) {
                audioSessionId = newAudioSessionId
                refreshLoudnessEnhancer()
            }
        })

        mediaSession = MediaLibrarySession.Builder(this, SeekButtonPlayer(player), LibraryCallback())
            .setMediaButtonPreferences(seekMediaButtons(AppSettings().skipBackSeconds, AppSettings().skipForwardSeconds))
            .build()

        wearStatePublisher = WearStatePublisher(this, player).also { it.attach() }

        serviceScope.launch {
            EasyRadioGraph.settings(applicationContext).settings.collect { settings ->
                player.setSkipSilenceEnabled(settings.skipSilenceEnabled)
                voiceBoostEnabled = settings.voiceBoostEnabled
                refreshLoudnessEnhancer()
                // Keep the lock-screen/notification rewind and fast-forward buttons in sync
                // with the app's configurable skip amounts.
                player.setSeekBackIncrementMs(settings.skipBackSeconds * 1_000L)
                player.setSeekForwardIncrementMs(settings.skipForwardSeconds * 1_000L)
                mediaSession.setMediaButtonPreferences(
                    seekMediaButtons(settings.skipBackSeconds, settings.skipForwardSeconds),
                )
            }
        }
    }

    /**
     * The media notification and lock screen place their rewind/fast-forward icons from
     * [MediaSession.setMediaButtonPreferences], not from the player's raw available commands
     * -- without this, hiding COMMAND_SEEK_TO_PREVIOUS/NEXT (see [SeekButtonPlayer]) leaves no
     * button in their place at all. [Player.COMMAND_SEEK_BACK]/[Player.COMMAND_SEEK_FORWARD]
     * ties each button to the player's native seekBack()/seekForward(), so no custom command
     * handling is needed; the system automatically disables/hides a button when the command
     * isn't currently available (e.g. seeking on a non-seekable live radio stream).
     */
    private fun seekMediaButtons(skipBackSeconds: Int, skipForwardSeconds: Int): List<CommandButton> = listOf(
        CommandButton.Builder(seekBackIcon(skipBackSeconds))
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setDisplayName("Rewind $skipBackSeconds seconds")
            .setSlots(CommandButton.SLOT_BACK)
            .build(),
        CommandButton.Builder(seekForwardIcon(skipForwardSeconds))
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setDisplayName("Fast forward $skipForwardSeconds seconds")
            .setSlots(CommandButton.SLOT_FORWARD)
            .build(),
    )

    private fun seekBackIcon(seconds: Int): Int = when (seconds) {
        5 -> CommandButton.ICON_SKIP_BACK_5
        10 -> CommandButton.ICON_SKIP_BACK_10
        15 -> CommandButton.ICON_SKIP_BACK_15
        30 -> CommandButton.ICON_SKIP_BACK_30
        else -> CommandButton.ICON_SKIP_BACK
    }

    private fun seekForwardIcon(seconds: Int): Int = when (seconds) {
        5 -> CommandButton.ICON_SKIP_FORWARD_5
        10 -> CommandButton.ICON_SKIP_FORWARD_10
        15 -> CommandButton.ICON_SKIP_FORWARD_15
        30 -> CommandButton.ICON_SKIP_FORWARD_30
        else -> CommandButton.ICON_SKIP_FORWARD
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = mediaSession

    override fun onDestroy() {
        loudnessEnhancer?.release()
        wearStatePublisher.detach()
        mediaSession.run {
            player.release()
            release()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    // The media notification / lock screen build their buttons from the session's player's
    // own Player.getAvailableCommands() -- NOT from MediaSession.Callback.onConnect's granted
    // commands, which only gate what a given remote controller (Auto, Bluetooth, etc.) is
    // permitted to invoke. A radio stream or podcast episode is played one at a time, so
    // there's no real "previous/next item" to seek between, but ExoPlayer reports
    // COMMAND_SEEK_TO_PREVIOUS as available anyway (it just restarts the current item),
    // which is what renders as a "restart" button. Wrapping the player to hide that command
    // (and the equally meaningless seek-to-next) lets the system fall back to rendering
    // seek-back/seek-forward instead, which stay available since the player still reports
    // them for seekable content.
    private class SeekButtonPlayer(player: ExoPlayer) : ForwardingPlayer(player) {
        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon()
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_NEXT)
                .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .build()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(rootItem(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            val items = childrenOf(parentId).map { it.toMediaItem() }
            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        }

        // Resolve each tapped item to a playable MediaItem with a uri. Auto's
        // legacy onPlayFromMediaId path delivers an item with only a mediaId (no
        // RequestMetadata), so we fall back to resolving the uri from the browse
        // tree -- without a uri ExoPlayer's DefaultMediaSourceFactory throws.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            // Stations (and items already carrying a uri) resolve from memory, so
            // return them synchronously -- the player then prepares within the same
            // play command instead of waiting on a Main-dispatched future that can
            // land after play() has already evaluated an empty timeline. Only
            // episode ids need the async database lookup.
            val needsEpisodeLookup = mediaItems.any {
                it.requestMetadata.mediaUri == null &&
                    it.mediaId.startsWith(MediaBrowseTree.EPISODE_PREFIX)
            }
            return if (needsEpisodeLookup) {
                serviceScope.future { mediaItems.map { resolvePlayableItem(it) }.toMutableList() }
            } else {
                Futures.immediateFuture(mediaItems.map { resolveSync(it) }.toMutableList())
            }
        }
    }

    private fun resolveSync(item: MediaItem): MediaItem {
        val uri = item.requestMetadata.mediaUri?.toString()
            ?: MediaBrowseTree.playbackUri(item.mediaId, CuratedRadioStations.ALL, emptyList())
        return if (uri != null) item.buildUpon().setUri(uri).build() else item
    }

    private suspend fun resolvePlayableItem(item: MediaItem): MediaItem {
        val uri = item.requestMetadata.mediaUri?.toString() ?: resolveUri(item.mediaId)
        return if (uri != null) item.buildUpon().setUri(uri).build() else item
    }

    private suspend fun resolveUri(mediaId: String): String? = when {
        mediaId.startsWith(MediaBrowseTree.STATION_PREFIX) ->
            MediaBrowseTree.playbackUri(mediaId, CuratedRadioStations.ALL, emptyList())
        mediaId.startsWith(MediaBrowseTree.EPISODE_PREFIX) ->
            MediaBrowseTree.playbackUri(mediaId, emptyList(), allSubscribedEpisodes())
        else -> null
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

    private fun rootItem(): MediaItem = MediaItem.Builder()
        .setMediaId(MediaBrowseTree.ROOT_ID)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("Easy Radio")
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build(),
        )
        .build()

    private fun BrowseNode.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle.ifBlank { null })
            .setArtworkUri(artworkUrl?.let { ArtworkContentProvider.uriFor(it) })
            .setIsBrowsable(isBrowsable)
            .setIsPlayable(isPlayable)
            .setMediaType(
                if (isBrowsable) MediaMetadata.MEDIA_TYPE_FOLDER_MIXED else MediaMetadata.MEDIA_TYPE_MUSIC,
            )
            .build()

        val builder = MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadata)

        playbackUri?.let { uri ->
            builder.setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(uri)).build(),
            )
        }
        return builder.build()
    }
}
