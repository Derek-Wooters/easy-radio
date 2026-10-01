package com.easyradio.app.playback

import android.net.Uri
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.easyradio.core.database.PodcastRepository
import com.easyradio.core.media.BrowseNode
import com.easyradio.core.media.EpisodeEndAction
import com.easyradio.core.media.EpisodeEndDecision
import com.easyradio.core.media.MediaBrowseTree
import com.easyradio.core.model.CuratedRadioStations
import com.easyradio.core.model.Episode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

internal const val POSITION_SAVE_INTERVAL_MS = 5_000L

/**
 * Owns the playback/session-state orchestration [EasyRadioPlaybackService] used to do entirely
 * inline: publishing [PlaybackStateCompat]/[MediaMetadataCompat] off [player]'s own state,
 * starting playback (including auto-advance and "End of episode" sleep-timer suppression via
 * [EpisodeEndDecision]), and resolving the Android Auto/Automotive browse tree.
 *
 * Kept as a plain class with no Android base class, and [player] taken as the Media3 [Player]
 * interface rather than a concrete ExoPlayer, so this is unit-testable with a mocked player and a
 * fake repository instead of only verifiable on a real device -- the same seam
 * [WearStatePublisher] already uses for the same reason. [EasyRadioPlaybackService] keeps
 * everything genuinely Android-only that this doesn't need to know about: building the real
 * ExoPlayer/MediaSessionCompat, the foreground-service/notification lifecycle, and forwarding
 * Player.Listener/MediaSessionCompat.Callback events in here.
 */
class PlaybackSessionController(
    private val player: Player,
    private val repository: PodcastRepository,
    private val onPlaybackStateChanged: (PlaybackStateCompat) -> Unit,
    private val onMetadataChanged: (MediaMetadataCompat) -> Unit,
    private val onPlaybackStarting: () -> Unit,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var currentTitle: String? = null
        private set
    var currentArtist: String? = null
        private set
    private var currentArtworkUrl: String? = null

    // Null whenever radio (or nothing) is playing -- auto-advance/end-of-episode only apply to
    // podcast episodes, which is exactly what a non-null id here means.
    private var currentEpisodeId: String? = null

    // Null whenever a podcast episode (or nothing) is playing -- mutually exclusive with
    // currentEpisodeId. Published as the session's own media id the same way currentEpisodeId is,
    // so MainActivity can resync currentStation if it's recreated while a station keeps playing --
    // without this, reconnecting left the mini-player/Now Playing bar missing entirely despite
    // audio still genuinely playing (the bug this field exists to fix).
    private var currentStationId: String? = null

    // Armed by the "End of episode" sleep-timer option (ACTION_SLEEP_AT_END_OF_EPISODE) for the
    // *current* episode only; reset whenever any new item starts playing so it never leaks onto
    // whatever plays next.
    private var sleepAtEndOfEpisode: Boolean = false

    private var positionSaveJob: Job? = null

    fun release() {
        scope.cancel()
    }

    /**
     * Periodically persists the current episode's playback position for as long as this
     * controller (i.e. the service) is alive, independent of whether any Activity is bound.
     * MainActivity previously owned this on its own lifecycleScope, saving only while it was
     * started, plus one final flush in onStop() -- but onTaskRemoved() deliberately keeps this
     * service (and playback) alive after the app is swiped away from Recents while playing, and
     * reopening the app later while the service kept playing meant no Activity existed to do that
     * onStop() flush at all. Confirmed as the cause of a real resume-to-the-wrong-spot bug report:
     * ~30 minutes of unattended background listening with zero saves, so "Resume" started the
     * episode over from a position minutes old instead of where it had actually gotten to.
     */
    private fun restartPositionSaving(episodeId: String?) {
        positionSaveJob?.cancel()
        if (episodeId == null) return
        positionSaveJob = scope.launch {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                if (player.isPlaying) {
                    repository.savePosition(episodeId, player.currentPosition)
                }
            }
        }
    }

    // -- Playback state / metadata publishing --------------------------------------------

    /** Call on every Player.Listener.onEvents -- rebuilds and republishes both. */
    fun publishPlaybackState() {
        val state = LegacyPlaybackStateMapper.toCompatState(
            hasError = player.playerError != null,
            playbackState = player.playbackState,
            isPlaying = player.isPlaying,
        )
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_REWIND or
            PlaybackStateCompat.ACTION_FAST_FORWARD or
            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
            PlaybackStateCompat.ACTION_PLAY_FROM_URI

        val playbackSpeed = if (state == PlaybackStateCompat.STATE_PLAYING) player.playbackParameters.speed else 0f
        onPlaybackStateChanged(
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
     * it, so this has to be re-published as events arrive (not just once in startPlayback()).
     */
    fun publishMetadata() {
        val durationMs = player.duration.takeIf { it != C.TIME_UNSET }
        // MEDIA_ID lets MainActivity notice a change it didn't itself initiate -- either the
        // service auto-advancing to the next queued episode, or MainActivity itself being
        // recreated while this station/episode keeps playing -- and resync its own now-playing UI
        // state to match, rather than going stale (or, for a station recreated mid-playback,
        // showing nothing at all: see currentStationId's doc).
        val mediaId = currentEpisodeId?.let { MediaBrowseTree.EPISODE_PREFIX + it }
            ?: currentStationId?.let { MediaBrowseTree.STATION_PREFIX + it }
        onMetadataChanged(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
                .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, currentArtworkUrl)
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId)
                .apply { durationMs?.let { putLong(MediaMetadataCompat.METADATA_KEY_DURATION, it) } }
                .build(),
        )
    }

    /** Call from Player.Listener.onPlaybackStateChanged. */
    fun onPlayerPlaybackStateChanged(playbackState: Int) {
        // Fires once per genuine transition, so this is the correct edge to react to the player
        // reaching the true end of an item exactly once.
        if (playbackState == Player.STATE_ENDED && currentEpisodeId != null) {
            scope.launch { handleEpisodeEnded() }
        }
    }

    /**
     * Called exactly once when a podcast episode reaches its natural end. Advances to the next
     * queued episode (Easy Radio has no other auto-advance path -- finishing an episode with
     * nothing queued just stops, same as before this existed), unless the "End of episode" sleep
     * timer was armed for this episode, in which case it's consumed here and playback is left
     * stopped instead.
     */
    private suspend fun handleEpisodeEnded() {
        currentEpisodeId = null
        restartPositionSaving(null)
        val wasArmed = sleepAtEndOfEpisode
        sleepAtEndOfEpisode = false
        when (val action = EpisodeEndDecision.resolve(wasArmed, repository.queue().first())) {
            is EpisodeEndAction.Advance -> {
                repository.removeFromQueue(action.next.id)
                playEpisode(action.next)
            }
            EpisodeEndAction.Stop -> Unit
        }
    }

    // -- Starting playback -----------------------------------------------------------------

    fun startPlayback(
        uri: Uri,
        title: String?,
        artist: String?,
        artworkUrl: String?,
        resumePositionMs: Long,
        episodeId: String? = null,
        stationId: String? = null,
    ) {
        onPlaybackStarting()
        currentTitle = title
        currentArtist = artist
        currentArtworkUrl = artworkUrl
        currentEpisodeId = episodeId
        currentStationId = stationId
        sleepAtEndOfEpisode = false
        restartPositionSaving(episodeId)
        publishMetadata()
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
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
    suspend fun startPlaybackFromMediaId(mediaId: String) {
        when {
            mediaId.startsWith(MediaBrowseTree.STATION_PREFIX) -> {
                val station = CuratedRadioStations.ALL.firstOrNull {
                    MediaBrowseTree.STATION_PREFIX + it.id == mediaId
                } ?: return
                startPlayback(
                    uri = Uri.parse(station.streamUrl),
                    title = station.name,
                    artist = station.tagline,
                    artworkUrl = station.imageUrl,
                    resumePositionMs = 0L,
                    stationId = station.id,
                )
            }
            mediaId.startsWith(MediaBrowseTree.EPISODE_PREFIX) -> {
                val episode = allSubscribedEpisodes().firstOrNull {
                    MediaBrowseTree.EPISODE_PREFIX + it.id == mediaId
                } ?: return
                playEpisode(episode)
            }
        }
    }

    /**
     * Starts playback of [episode], resolving its podcast (for artist/artwork) and saved position
     * the same way a media-id tap-to-play does. Shared by [startPlaybackFromMediaId] and
     * [handleEpisodeEnded]'s auto-advance-to-next-queued-episode.
     */
    private suspend fun playEpisode(episode: Episode) {
        val podcast = repository.subscribedPodcasts().first().firstOrNull { it.id == episode.podcastId }
        val uri = episode.localFilePath?.let { Uri.fromFile(File(it)) } ?: Uri.parse(episode.audioUrl)
        val resumeMs = repository.lastPosition(episode.id)
        startPlayback(
            uri = uri,
            title = episode.title,
            artist = podcast?.author?.ifBlank { podcast.title },
            artworkUrl = podcast?.artworkUrl,
            resumePositionMs = resumeMs,
            episodeId = episode.id,
        )
    }

    fun armSleepAtEndOfEpisode() {
        sleepAtEndOfEpisode = true
    }

    // -- Browse tree (Android Auto / Automotive) --------------------------------------------

    suspend fun childrenOf(parentId: String): List<BrowseNode> = when {
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

    private suspend fun allSubscribedEpisodes(): List<Episode> =
        repository.subscribedPodcasts().first().flatMap { repository.episodesFor(it.id).first() }
}
