package com.easyradio.app.playback

import android.net.Uri
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.easyradio.core.database.EpisodeDao
import com.easyradio.core.database.EpisodeEntity
import com.easyradio.core.database.EpisodeMetadata
import com.easyradio.core.database.PodcastDao
import com.easyradio.core.database.PodcastEntity
import com.easyradio.core.database.PodcastRepository
import com.easyradio.core.database.QueueDao
import com.easyradio.core.database.QueueItemEntity
import com.easyradio.core.database.toEntity
import com.easyradio.core.media.MediaBrowseTree
import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast
import com.easyradio.core.network.podcast.ItunesSearchApi
import com.easyradio.core.network.podcast.ItunesSearchResponseDto
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FakeItunesSearchApi : ItunesSearchApi {
    override suspend fun searchPodcasts(term: String, media: String, limit: Int) = ItunesSearchResponseDto()
}

private class FakePodcastDao : PodcastDao {
    val state = MutableStateFlow<List<PodcastEntity>>(emptyList())
    override fun observeAll() = state
    override suspend fun upsert(podcast: PodcastEntity) {
        state.update { list -> list.filterNot { it.id == podcast.id } + podcast }
    }
    override suspend fun delete(id: String) {
        state.update { list -> list.filterNot { it.id == id } }
    }
    override suspend fun setPreset(id: String, isPreset: Boolean) {}
    override suspend fun updateLastPlayed(id: String, timestamp: Long) {}
}

private class FakeEpisodeDao : EpisodeDao {
    val state = MutableStateFlow<List<EpisodeEntity>>(emptyList())
    override fun observeByPodcast(podcastId: String) = state.map { list -> list.filter { it.podcastId == podcastId } }
    override suspend fun insertIgnore(episodes: List<EpisodeEntity>) {}
    override suspend fun updateMetadata(updates: List<EpisodeMetadata>) {}
    override suspend fun updatePosition(episodeId: String, positionMs: Long) {
        state.update { list -> list.map { if (it.id == episodeId) it.copy(positionMs = positionMs) else it } }
    }
    override suspend fun getPosition(episodeId: String): Long? = state.value.find { it.id == episodeId }?.positionMs
    override suspend fun updateLocalFilePath(episodeId: String, localFilePath: String?) {}
    override suspend fun getByIds(ids: List<String>) = state.value.filter { it.id in ids }
    override fun observeDownloaded() = state.map { list -> list.filter { it.localFilePath != null } }
    override fun observeAll() = state
}

private class FakeQueueDao : QueueDao {
    val state = MutableStateFlow<List<QueueItemEntity>>(emptyList())
    override fun observeAll() = state
    override suspend fun upsert(item: QueueItemEntity) {
        state.update { it + item }
    }
    override suspend fun upsertAll(items: List<QueueItemEntity>) {
        state.update { it + items }
    }
    override suspend fun remove(episodeId: String) {
        state.update { list -> list.filterNot { it.episodeId == episodeId } }
    }
    override suspend fun maxPosition(): Int = state.value.maxOfOrNull { it.position } ?: -1
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackSessionControllerTest {

    private val dispatcher = StandardTestDispatcher()
    private val podcastDao = FakePodcastDao()
    private val episodeDao = FakeEpisodeDao()
    private val queueDao = FakeQueueDao()

    private val repository = PodcastRepository(
        itunesApi = FakeItunesSearchApi(),
        fetchFeed = { "" },
        podcastDao = podcastDao,
        episodeDao = episodeDao,
        queueDao = queueDao,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakePlayer(
        hasError: Boolean = false,
        playbackState: Int = Player.STATE_READY,
        playWhenReady: Boolean = true,
        isPlaying: Boolean = true,
        positionMs: Long = 0L,
        bufferedPositionMs: Long = 0L,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.playerError } returns
            if (hasError) PlaybackException("boom", null, PlaybackException.ERROR_CODE_UNSPECIFIED) else null
        every { player.playbackState } returns playbackState
        every { player.playWhenReady } returns playWhenReady
        every { player.isPlaying } returns isPlaying
        every { player.playbackParameters } returns PlaybackParameters(1.0f)
        every { player.currentPosition } returns positionMs
        every { player.bufferedPosition } returns bufferedPositionMs
        return player
    }

    private fun controller(
        player: Player,
        onPlaybackStateChanged: (PlaybackStateCompat) -> Unit = {},
        onMetadataChanged: (MediaMetadataCompat) -> Unit = {},
        onPlaybackStarting: () -> Unit = {},
    ) = PlaybackSessionController(
        player = player,
        repository = repository,
        onPlaybackStateChanged = onPlaybackStateChanged,
        onMetadataChanged = onMetadataChanged,
        onPlaybackStarting = onPlaybackStarting,
    )

    @Test
    fun `publishPlaybackState reports playing with position and buffered position`() {
        val player = fakePlayer(positionMs = 5_000, bufferedPositionMs = 8_000)
        var published: PlaybackStateCompat? = null
        val sut = controller(player, onPlaybackStateChanged = { published = it })

        sut.publishPlaybackState()

        assertThat(published!!.state).isEqualTo(PlaybackStateCompat.STATE_PLAYING)
        assertThat(published!!.position).isEqualTo(5_000)
        assertThat(published!!.bufferedPosition).isEqualTo(8_000)
    }

    @Test
    fun `publishPlaybackState reports error ahead of everything else`() {
        val player = fakePlayer(hasError = true, playbackState = Player.STATE_READY, playWhenReady = true)
        var published: PlaybackStateCompat? = null
        val sut = controller(player, onPlaybackStateChanged = { published = it })

        sut.publishPlaybackState()

        assertThat(published!!.state).isEqualTo(PlaybackStateCompat.STATE_ERROR)
    }

    @Test
    fun `startPlayback calls onPlaybackStarting before touching the player`() {
        val player = fakePlayer()
        var started = false
        val sut = controller(player, onPlaybackStarting = { started = true })

        sut.startPlayback(Uri.parse("https://example.com/stream"), "Title", "Artist", null, 0L)

        assertThat(started).isTrue()
        verify { player.setMediaItem(any(), 0L) }
        verify { player.prepare() }
        verify { player.play() }
    }

    @Test
    fun `startPlayback immediately publishes the new title and artist as metadata`() {
        val player = fakePlayer()
        var metadata: MediaMetadataCompat? = null
        val sut = controller(player, onMetadataChanged = { metadata = it })

        sut.startPlayback(Uri.parse("https://example.com/stream"), "KFAN FM", "Sports Talk", null, 0L)

        assertThat(metadata!!.getString(MediaMetadataCompat.METADATA_KEY_TITLE)).isEqualTo("KFAN FM")
        assertThat(metadata!!.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)).isEqualTo("Sports Talk")
    }

    @Test
    fun `startPlayback with a stationId publishes it as the session's media id`() {
        // The actual bug this guards against: without a published station media id, MainActivity
        // being recreated while a station keeps playing had nothing to resync currentStation
        // from, leaving the mini-player/Now Playing bar missing despite audio still playing.
        val player = fakePlayer()
        var metadata: MediaMetadataCompat? = null
        val sut = controller(player, onMetadataChanged = { metadata = it })

        sut.startPlayback(Uri.parse("https://example.com/stream"), "KFAN FM", "Sports Talk", null, 0L, stationId = "kfan")

        assertThat(metadata!!.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID))
            .isEqualTo(MediaBrowseTree.STATION_PREFIX + "kfan")
    }

    @Test
    fun `startPlayback with an episodeId publishes it as the session's media id`() {
        val player = fakePlayer()
        var metadata: MediaMetadataCompat? = null
        val sut = controller(player, onMetadataChanged = { metadata = it })

        sut.startPlayback(
            Uri.parse("https://example.com/ep1.mp3"),
            "Ep1",
            "Podcast",
            null,
            0L,
            episodeId = "ep1",
        )

        assertThat(metadata!!.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID))
            .isEqualTo(MediaBrowseTree.EPISODE_PREFIX + "ep1")
    }

    @Test
    fun `an ended episode with nothing queued just stops`() = runTest(dispatcher) {
        val player = fakePlayer()
        val sut = controller(player)
        sut.startPlayback(Uri.parse("https://example.com/ep1.mp3"), "Ep1", "Podcast", null, 0L, episodeId = "ep1")

        sut.onPlayerPlaybackStateChanged(Player.STATE_ENDED)
        dispatcher.scheduler.runCurrent()

        // Only the original startPlayback call -- nothing queued to advance to.
        verify(exactly = 1) { player.setMediaItem(any(), any<Long>()) }
    }

    @Test
    fun `an ended episode with something queued advances to it`() = runTest(dispatcher) {
        val podcast = Podcast(id = "p1", title = "The Daily", author = "NYT", artworkUrl = null, feedUrl = "https://example.com/feed")
        podcastDao.state.value = listOf(podcast.toEntity(subscribedAtEpochMillis = 0))
        val next = Episode(
            id = "ep2",
            podcastId = "p1",
            title = "Episode 2",
            audioUrl = "https://example.com/ep2.mp3",
            publishedAtEpochMillis = null,
            durationSeconds = 600,
        )
        episodeDao.state.value = listOf(next.toEntity())
        queueDao.state.value = listOf(QueueItemEntity(episodeId = "ep2", position = 0))

        val player = fakePlayer()
        val sut = controller(player)
        sut.startPlayback(Uri.parse("https://example.com/ep1.mp3"), "Ep1", "Podcast", null, 0L, episodeId = "ep1")

        sut.onPlayerPlaybackStateChanged(Player.STATE_ENDED)
        dispatcher.scheduler.runCurrent()

        // startPlayback for ep1, then again for the auto-advanced ep2.
        verify(exactly = 2) { player.setMediaItem(any(), any<Long>()) }
        assertThat(queueDao.state.value).isEmpty()
        sut.release() // ep2's position-save loop is still running; runs forever otherwise
    }

    @Test
    fun `arming the end-of-episode sleep timer suppresses auto-advance`() = runTest(dispatcher) {
        val podcast = Podcast(id = "p1", title = "The Daily", author = "NYT", artworkUrl = null, feedUrl = "https://example.com/feed")
        podcastDao.state.value = listOf(podcast.toEntity(subscribedAtEpochMillis = 0))
        val next = Episode(
            id = "ep2",
            podcastId = "p1",
            title = "Episode 2",
            audioUrl = "https://example.com/ep2.mp3",
            publishedAtEpochMillis = null,
            durationSeconds = 600,
        )
        episodeDao.state.value = listOf(next.toEntity())
        queueDao.state.value = listOf(QueueItemEntity(episodeId = "ep2", position = 0))

        val player = fakePlayer()
        val sut = controller(player)
        sut.startPlayback(Uri.parse("https://example.com/ep1.mp3"), "Ep1", "Podcast", null, 0L, episodeId = "ep1")
        sut.armSleepAtEndOfEpisode()

        sut.onPlayerPlaybackStateChanged(Player.STATE_ENDED)
        dispatcher.scheduler.runCurrent()

        // Only the original startPlayback -- no auto-advance despite something queued.
        verify(exactly = 1) { player.setMediaItem(any(), any<Long>()) }
        assertThat(queueDao.state.value).hasSize(1)
    }

    @Test
    fun `periodically saves the playing episode's position for as long as the controller is alive`() = runTest(dispatcher) {
        // Regression test for a real bug report: MainActivity previously owned this on its own
        // lifecycleScope (saving only while the Activity was started, plus one flush in onStop()),
        // but onTaskRemoved() deliberately keeps this service -- and playback -- alive after the
        // app is swiped away while playing. ~30 minutes of unattended background listening got
        // zero saves, so reopening the app and tapping "Resume" started the episode over from a
        // position minutes old instead of where it had actually gotten to. Saving here instead,
        // on the controller's own scope, ties it to the service's lifetime, which already spans
        // exactly this case.
        val episode = Episode(
            id = "ep1",
            podcastId = "p1",
            title = "Episode 1",
            audioUrl = "https://example.com/ep1.mp3",
            publishedAtEpochMillis = null,
            durationSeconds = 3_600,
        )
        episodeDao.state.value = listOf(episode.toEntity())

        val player = fakePlayer(isPlaying = true, positionMs = 42_000L)
        val sut = controller(player)
        sut.startPlayback(Uri.parse("https://example.com/ep1.mp3"), "Ep1", "Podcast", null, 0L, episodeId = "ep1")

        dispatcher.scheduler.advanceTimeBy(POSITION_SAVE_INTERVAL_MS + 100)
        dispatcher.scheduler.runCurrent()

        assertThat(episodeDao.state.value.find { it.id == "ep1" }?.positionMs).isEqualTo(42_000L)
        sut.release() // the save loop runs forever otherwise, hanging runTest's cleanup
    }

    @Test
    fun `does not save position while paused`() = runTest(dispatcher) {
        val episode = Episode(
            id = "ep1",
            podcastId = "p1",
            title = "Episode 1",
            audioUrl = "https://example.com/ep1.mp3",
            publishedAtEpochMillis = null,
            durationSeconds = 3_600,
        )
        episodeDao.state.value = listOf(episode.toEntity())

        val player = fakePlayer(isPlaying = false, positionMs = 42_000L)
        val sut = controller(player)
        sut.startPlayback(Uri.parse("https://example.com/ep1.mp3"), "Ep1", "Podcast", null, 0L, episodeId = "ep1")

        dispatcher.scheduler.advanceTimeBy(POSITION_SAVE_INTERVAL_MS + 100)
        dispatcher.scheduler.runCurrent()

        assertThat(episodeDao.state.value.find { it.id == "ep1" }?.positionMs).isEqualTo(0L)
        sut.release() // the save loop runs forever otherwise, hanging runTest's cleanup
    }
}
