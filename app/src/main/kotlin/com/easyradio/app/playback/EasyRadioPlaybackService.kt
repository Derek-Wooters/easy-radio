package com.easyradio.app.playback

import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.easyradio.app.EasyRadioGraph
import com.easyradio.core.database.PodcastRepository
import com.easyradio.core.media.BrowseNode
import com.easyradio.core.media.MediaBrowseTree
import com.easyradio.core.model.CuratedRadioStations
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future

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
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
            .build()

        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = mediaSession

    override fun onDestroy() {
        mediaSession.run {
            player.release()
            release()
        }
        serviceScope.cancel()
        super.onDestroy()
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

        // A browse item carries its playback uri in RequestMetadata; when the user
        // taps it, rebuild the MediaItem with that uri so ExoPlayer can stream it.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                val uri = item.requestMetadata.mediaUri
                if (uri != null) item.buildUpon().setUri(uri).build() else item
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }

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
            .setArtworkUri(artworkUrl?.let(Uri::parse))
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
