package com.easyradio.core.media

import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast
import com.easyradio.core.model.RadioStation
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The Android Auto browse tree is built from a pure [BrowseNode] representation
 * so all of its structure -- node ids, browsable/playable flags, and the
 * offline-vs-remote playback uri choice -- is unit-testable without pulling in
 * android.net.Uri / MediaItem. The service converts BrowseNode -> MediaItem.
 */
class MediaBrowseTreeTest {

    private val station = RadioStation(
        id = "kfan-100.3",
        name = "KFAN FM 100.3",
        streamUrl = "https://stream.example.com/kfan",
        tagline = "Audio Home For Minnesota Sports",
        imageUrl = "https://img.example.com/kfan.png",
    )

    private val podcast = Podcast(
        id = "planet-money",
        title = "Planet Money",
        author = "NPR",
        artworkUrl = "https://img.example.com/pm.png",
        feedUrl = "https://feeds.example.com/pm.xml",
    )

    private val remoteEpisode = Episode(
        id = "ep-1",
        podcastId = "planet-money",
        title = "Piles of cash",
        audioUrl = "https://audio.example.com/ep-1.mp3",
        publishedAtEpochMillis = 1_700_000_000_000L,
        durationSeconds = 1500,
    )

    @Test
    fun `root exposes Radio and Podcasts as browsable, non-playable nodes`() {
        val children = MediaBrowseTree.rootChildren()

        assertThat(children.map { it.mediaId })
            .containsExactly(MediaBrowseTree.RADIO_ID, MediaBrowseTree.PODCASTS_ID)
            .inOrder()
        assertThat(children.all { it.isBrowsable }).isTrue()
        assertThat(children.none { it.isPlayable }).isTrue()
    }

    @Test
    fun `station maps to a playable node carrying the stream uri and artwork`() {
        val node = MediaBrowseTree.stationNodes(listOf(station)).single()

        assertThat(node.mediaId).isEqualTo("station/kfan-100.3")
        assertThat(node.title).isEqualTo("KFAN FM 100.3")
        assertThat(node.subtitle).isEqualTo("Audio Home For Minnesota Sports")
        assertThat(node.artworkUrl).isEqualTo("https://img.example.com/kfan.png")
        assertThat(node.playbackUri).isEqualTo("https://stream.example.com/kfan")
        assertThat(node.isPlayable).isTrue()
        assertThat(node.isBrowsable).isFalse()
    }

    @Test
    fun `podcast maps to a browsable, non-playable node`() {
        val node = MediaBrowseTree.podcastNodes(listOf(podcast)).single()

        assertThat(node.mediaId).isEqualTo("podcast/planet-money")
        assertThat(node.title).isEqualTo("Planet Money")
        assertThat(node.artworkUrl).isEqualTo("https://img.example.com/pm.png")
        assertThat(node.isBrowsable).isTrue()
        assertThat(node.isPlayable).isFalse()
    }

    @Test
    fun `episode maps to a playable node using the remote audio url by default`() {
        val node = MediaBrowseTree.episodeNodes(listOf(remoteEpisode)).single()

        assertThat(node.mediaId).isEqualTo("episode/ep-1")
        assertThat(node.title).isEqualTo("Piles of cash")
        assertThat(node.playbackUri).isEqualTo("https://audio.example.com/ep-1.mp3")
        assertThat(node.isPlayable).isTrue()
        assertThat(node.isBrowsable).isFalse()
    }

    @Test
    fun `downloaded episode prefers the local file path for offline playback`() {
        val downloaded = remoteEpisode.copy(localFilePath = "/data/downloads/ep-1.audio")

        val node = MediaBrowseTree.episodeNodes(listOf(downloaded)).single()

        assertThat(node.playbackUri).isEqualTo("/data/downloads/ep-1.audio")
    }

    @Test
    fun `playbackUri resolves station and episode media ids back to their source`() {
        assertThat(
            MediaBrowseTree.playbackUri("station/kfan-100.3", listOf(station), listOf(remoteEpisode)),
        ).isEqualTo("https://stream.example.com/kfan")

        assertThat(
            MediaBrowseTree.playbackUri("episode/ep-1", listOf(station), listOf(remoteEpisode)),
        ).isEqualTo("https://audio.example.com/ep-1.mp3")
    }

    @Test
    fun `playbackUri returns null for unknown or browsable media ids`() {
        assertThat(
            MediaBrowseTree.playbackUri("podcast/planet-money", listOf(station), listOf(remoteEpisode)),
        ).isNull()
        assertThat(
            MediaBrowseTree.playbackUri("station/does-not-exist", listOf(station), listOf(remoteEpisode)),
        ).isNull()
    }
}
