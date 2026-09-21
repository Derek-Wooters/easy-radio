package com.easyradio.app.ui

import com.easyradio.core.database.EpisodeDao
import com.easyradio.core.database.EpisodeEntity
import com.easyradio.core.database.EpisodeMetadata
import com.easyradio.core.database.PodcastDao
import com.easyradio.core.database.PodcastRepository
import com.easyradio.core.model.Episode
import com.easyradio.core.network.podcast.ItunesSearchApi
import com.easyradio.core.network.podcast.ItunesSearchResponseDto
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class NoOpItunesSearchApi : ItunesSearchApi {
    override suspend fun searchPodcasts(term: String, media: String, limit: Int) = ItunesSearchResponseDto()
}

private class NoOpPodcastDao : PodcastDao {
    override fun observeAll() = MutableStateFlow(emptyList<com.easyradio.core.database.PodcastEntity>())
    override suspend fun upsert(podcast: com.easyradio.core.database.PodcastEntity) {}
    override suspend fun delete(id: String) {}
    override suspend fun setPreset(id: String, isPreset: Boolean) {}
    override suspend fun updateLastPlayed(id: String, timestamp: Long) {}
}

/** Tracks updatePosition calls so replayAwareListen's behavior can be asserted directly. */
private class RecordingEpisodeDao : EpisodeDao {
    val positionUpdates = mutableListOf<Pair<String, Long>>()

    override fun observeByPodcast(podcastId: String) = MutableStateFlow<List<EpisodeEntity>>(emptyList())
    override suspend fun insertIgnore(episodes: List<EpisodeEntity>) {}
    override suspend fun updateMetadata(updates: List<EpisodeMetadata>) {}
    override suspend fun updatePosition(episodeId: String, positionMs: Long) {
        positionUpdates += episodeId to positionMs
    }
    override suspend fun getPosition(episodeId: String): Long? = null
    override suspend fun updateLocalFilePath(episodeId: String, localFilePath: String?) {}
    override suspend fun getByIds(ids: List<String>): List<EpisodeEntity> = emptyList()
    override fun observeDownloaded() = MutableStateFlow<List<EpisodeEntity>>(emptyList())
}

class PodcastsScreenTest {

    private fun episode(positionMs: Long, durationSeconds: Int?) = Episode(
        id = "ep-1",
        podcastId = "p1",
        title = "Episode",
        audioUrl = "https://example.com/ep1.mp3",
        publishedAtEpochMillis = null,
        durationSeconds = durationSeconds,
        positionMs = positionMs,
    )

    @Test
    fun `episodeListenState is LISTEN when nothing has been played`() {
        assertThat(episodeListenState(episode(positionMs = 0, durationSeconds = 1_800)))
            .isEqualTo(EpisodeListenState.LISTEN)
    }

    @Test
    fun `episodeListenState is LISTEN when duration is unknown, regardless of position`() {
        assertThat(episodeListenState(episode(positionMs = 60_000, durationSeconds = null)))
            .isEqualTo(EpisodeListenState.LISTEN)
    }

    @Test
    fun `episodeListenState is RESUME partway through`() {
        // 10 min listened of a 30 min episode -- the exact scenario the user described.
        assertThat(episodeListenState(episode(positionMs = 10 * 60_000L, durationSeconds = 30 * 60)))
            .isEqualTo(EpisodeListenState.RESUME)
    }

    @Test
    fun `episodeListenState is REPLAY once fully played`() {
        assertThat(episodeListenState(episode(positionMs = 30 * 60_000L, durationSeconds = 30 * 60)))
            .isEqualTo(EpisodeListenState.REPLAY)
    }

    @Test
    fun `episodeListenState is REPLAY within the finished tolerance, not just at exact duration`() {
        // Players commonly report a position a few seconds short of the true end at natural
        // completion; 98% through should already read as finished, not stuck on RESUME forever.
        val durationSeconds = 30 * 60
        val positionMs = (durationSeconds * 1000L * 0.98).toLong()

        assertThat(episodeListenState(episode(positionMs = positionMs, durationSeconds = durationSeconds)))
            .isEqualTo(EpisodeListenState.REPLAY)
    }

    @Test
    fun `episodeRemainingLabel is null for an unstarted episode, same as one never listened to`() {
        assertThat(episodeRemainingLabel(episode(positionMs = 0, durationSeconds = 1_800))).isNull()
    }

    @Test
    fun `episodeRemainingLabel is null when duration is unknown`() {
        assertThat(episodeRemainingLabel(episode(positionMs = 60_000, durationSeconds = null))).isNull()
    }

    @Test
    fun `episodeRemainingLabel is null once finished, in the Replay state`() {
        // Explicitly requested: a finished episode still labeled "Replay" should not show a
        // remaining-time label (it would read ~0 min, which isn't useful).
        assertThat(episodeRemainingLabel(episode(positionMs = 30 * 60_000L, durationSeconds = 30 * 60))).isNull()
    }

    @Test
    fun `episodeRemainingLabel shows time left, not time listened`() {
        // 10 min listened of a 30 min episode -- the exact scenario the user described -- should
        // show the 20 min left, not the 10 min already heard.
        val label = episodeRemainingLabel(episode(positionMs = 10 * 60_000L, durationSeconds = 30 * 60))

        assertThat(label).isEqualTo("20 min remaining")
    }

    @Test
    fun `episodeRemainingLabel formats hour-plus durations`() {
        val label = episodeRemainingLabel(episode(positionMs = 30 * 60_000L, durationSeconds = 2 * 3600))

        assertThat(label).isEqualTo("1 hr 30 min remaining")
    }

    @Test
    fun `formatEpisodeDuration formats minutes only, hours only, and hours plus minutes`() {
        assertThat(formatEpisodeDuration(45 * 60)).isEqualTo("45 min")
        assertThat(formatEpisodeDuration(2 * 3600)).isEqualTo("2 hr")
        assertThat(formatEpisodeDuration(2 * 3600 + 15 * 60)).isEqualTo("2 hr 15 min")
    }

    private fun repository(episodeDao: EpisodeDao) =
        PodcastRepository(NoOpItunesSearchApi(), { "" }, NoOpPodcastDao(), episodeDao)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `replayAwareListen resets the saved position to 0 before playing a finished episode`() = runTest {
        val episodeDao = RecordingEpisodeDao()
        val finished = episode(positionMs = 30 * 60_000L, durationSeconds = 30 * 60)
        var played = false

        replayAwareListen(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            repository = repository(episodeDao),
            episode = finished,
            onPlay = { played = true },
        )

        assertThat(episodeDao.positionUpdates).containsExactly("ep-1" to 0L)
        assertThat(played).isTrue()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `replayAwareListen plays directly without touching position for Listen or Resume`() = runTest {
        val episodeDao = RecordingEpisodeDao()
        val inProgress = episode(positionMs = 10 * 60_000L, durationSeconds = 30 * 60)
        var played = false

        replayAwareListen(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            repository = repository(episodeDao),
            episode = inProgress,
            onPlay = { played = true },
        )

        assertThat(episodeDao.positionUpdates).isEmpty()
        assertThat(played).isTrue()
    }
}
