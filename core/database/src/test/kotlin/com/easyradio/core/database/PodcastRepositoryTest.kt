package com.easyradio.core.database

import com.easyradio.core.model.Podcast
import com.easyradio.core.network.podcast.ItunesPodcastDto
import com.easyradio.core.network.podcast.ItunesSearchApi
import com.easyradio.core.network.podcast.ItunesSearchResponseDto
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakeItunesSearchApi(
    private val result: ItunesSearchResponseDto = ItunesSearchResponseDto(),
    private val error: Throwable? = null,
) : ItunesSearchApi {
    var lastTerm: String? = null

    override suspend fun searchPodcasts(term: String, media: String, limit: Int): ItunesSearchResponseDto {
        lastTerm = term
        error?.let { throw it }
        return result
    }
}

private class FakePodcastDao : PodcastDao {
    val state = MutableStateFlow<List<PodcastEntity>>(emptyList())

    override fun observeAll() = state.map { it.sortedByDescending { p -> p.subscribedAtEpochMillis } }

    override suspend fun upsert(podcast: PodcastEntity) {
        state.update { list -> list.filterNot { it.id == podcast.id } + podcast }
    }

    override suspend fun delete(id: String) {
        state.update { list -> list.filterNot { it.id == id } }
    }
}

private class FakeEpisodeDao : EpisodeDao {
    val state = MutableStateFlow<List<EpisodeEntity>>(emptyList())
    private val positions = mutableMapOf<String, Long>()

    override fun observeByPodcast(podcastId: String) = state.map { list -> list.filter { it.podcastId == podcastId } }

    override suspend fun upsertAll(episodes: List<EpisodeEntity>) {
        val ids = episodes.map { it.id }.toSet()
        state.update { list -> list.filterNot { it.id in ids } + episodes }
    }

    override suspend fun updatePosition(episodeId: String, positionMs: Long) {
        positions[episodeId] = positionMs
    }

    override suspend fun getPosition(episodeId: String): Long? = positions[episodeId]

    override suspend fun updateLocalFilePath(episodeId: String, localFilePath: String?) {
        state.update { list -> list.map { if (it.id == episodeId) it.copy(localFilePath = localFilePath) else it } }
    }

    override suspend fun getByIds(ids: List<String>): List<EpisodeEntity> =
        state.value.filter { it.id in ids }
}

private class FakeQueueDao : QueueDao {
    val state = MutableStateFlow<List<QueueItemEntity>>(emptyList())

    override fun observeAll() = state.map { it.sortedBy { item -> item.position } }

    override suspend fun upsert(item: QueueItemEntity) {
        state.update { list -> list.filterNot { it.episodeId == item.episodeId } + item }
    }

    override suspend fun upsertAll(items: List<QueueItemEntity>) {
        val ids = items.map { it.episodeId }.toSet()
        state.update { list -> list.filterNot { it.episodeId in ids } + items }
    }

    override suspend fun remove(episodeId: String) {
        state.update { list -> list.filterNot { it.episodeId == episodeId } }
    }

    override suspend fun maxPosition(): Int = state.value.maxOfOrNull { it.position } ?: -1
}

class PodcastRepositoryTest {

    private val testPodcast = Podcast(
        id = "https://example.com/feed.xml",
        title = "Test Show",
        author = "Author",
        artworkUrl = null,
        feedUrl = "https://example.com/feed.xml",
    )

    private val feedXml = """
        <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" version="2.0">
          <channel>
            <item>
              <title>Episode One</title>
              <guid>guid-1</guid>
              <enclosure url="https://example.com/ep1.mp3"/>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun `search delegates to the api and maps valid results`() = runTest {
        val dto = ItunesPodcastDto(collectionName = "Found Show", feedUrl = "https://example.com/found.xml")
        val api = FakeItunesSearchApi(result = ItunesSearchResponseDto(results = listOf(dto)))
        val repository = PodcastRepository(api, { "" }, FakePodcastDao(), FakeEpisodeDao())

        val results = repository.search("found")

        assertThat(api.lastTerm).isEqualTo("found")
        assertThat(results).hasSize(1)
        assertThat(results.first().title).isEqualTo("Found Show")
    }

    @Test
    fun `search removes duplicate results with the same feed url`() = runTest {
        val dto = ItunesPodcastDto(collectionName = "Planet Money", feedUrl = "https://feeds.npr.org/510289/podcast.xml")
        val api = FakeItunesSearchApi(result = ItunesSearchResponseDto(results = listOf(dto, dto)))
        val repository = PodcastRepository(api, { "" }, FakePodcastDao(), FakeEpisodeDao())

        val results = repository.search("planet money")

        assertThat(results).hasSize(1)
    }

    @Test
    fun `search returns an empty list for a blank query without calling the api`() = runTest {
        val api = FakeItunesSearchApi()
        val repository = PodcastRepository(api, { "" }, FakePodcastDao(), FakeEpisodeDao())

        assertThat(repository.search("   ")).isEmpty()
        assertThat(api.lastTerm).isNull()
    }

    @Test
    fun `search returns an empty list instead of throwing when the api fails`() = runTest {
        val api = FakeItunesSearchApi(error = java.io.IOException("network down"))
        val repository = PodcastRepository(api, { "" }, FakePodcastDao(), FakeEpisodeDao())

        assertThat(repository.search("test")).isEmpty()
    }

    @Test
    fun `subscribe stores the podcast and fetches its episodes`() = runTest {
        val podcastDao = FakePodcastDao()
        val episodeDao = FakeEpisodeDao()
        val repository = PodcastRepository(FakeItunesSearchApi(), { feedXml }, podcastDao, episodeDao)

        repository.subscribe(testPodcast)

        assertThat(repository.subscribedPodcasts().first()).hasSize(1)
        assertThat(episodeDao.state.value).hasSize(1)
        assertThat(episodeDao.state.value.first().title).isEqualTo("Episode One")
    }

    @Test
    fun `unsubscribe removes the podcast`() = runTest {
        val podcastDao = FakePodcastDao()
        val repository = PodcastRepository(FakeItunesSearchApi(), { feedXml }, podcastDao, FakeEpisodeDao())
        repository.subscribe(testPodcast)

        repository.unsubscribe(testPodcast.id)

        assertThat(repository.subscribedPodcasts().first()).isEmpty()
    }

    @Test
    fun `episodesFor reflects episodes stored for that podcast`() = runTest {
        val episodeDao = FakeEpisodeDao()
        val repository = PodcastRepository(FakeItunesSearchApi(), { feedXml }, FakePodcastDao(), episodeDao)

        repository.refreshEpisodes(testPodcast)

        val episodes = repository.episodesFor(testPodcast.id).first()
        assertThat(episodes).hasSize(1)
        assertThat(episodes.first().podcastId).isEqualTo(testPodcast.id)
    }

    @Test
    fun `savePosition then lastPosition returns the saved value`() = runTest {
        val repository = PodcastRepository(FakeItunesSearchApi(), { "" }, FakePodcastDao(), FakeEpisodeDao())

        repository.savePosition("ep-1", 45_000L)

        assertThat(repository.lastPosition("ep-1")).isEqualTo(45_000L)
    }

    @Test
    fun `downloadEpisode saves the local file path when the download succeeds`() = runTest {
        val episodeDao = FakeEpisodeDao()
        episodeDao.state.value = listOf(
            EpisodeEntity(
                id = "ep-1", podcastId = "p1", title = "Ep 1", audioUrl = "https://example.com/ep1.mp3",
                publishedAtEpochMillis = null, durationSeconds = null, description = "",
            ),
        )
        val repository = PodcastRepository(
            itunesApi = FakeItunesSearchApi(),
            fetchFeed = { "" },
            podcastDao = FakePodcastDao(),
            episodeDao = episodeDao,
            downloadFile = { _, _ -> "/local/path/ep1.audio" },
        )
        val episode = episodeDao.state.value.first().toEpisode()

        val result = repository.downloadEpisode(episode)

        assertThat(result).isTrue()
        assertThat(episodeDao.state.value.first().localFilePath).isEqualTo("/local/path/ep1.audio")
    }

    @Test
    fun `downloadEpisode returns false and does not update on failure`() = runTest {
        val episodeDao = FakeEpisodeDao()
        episodeDao.state.value = listOf(
            EpisodeEntity(
                id = "ep-1", podcastId = "p1", title = "Ep 1", audioUrl = "https://example.com/ep1.mp3",
                publishedAtEpochMillis = null, durationSeconds = null, description = "",
            ),
        )
        val repository = PodcastRepository(
            itunesApi = FakeItunesSearchApi(),
            fetchFeed = { "" },
            podcastDao = FakePodcastDao(),
            episodeDao = episodeDao,
            downloadFile = { _, _ -> null },
        )
        val episode = episodeDao.state.value.first().toEpisode()

        val result = repository.downloadEpisode(episode)

        assertThat(result).isFalse()
        assertThat(episodeDao.state.value.first().localFilePath).isNull()
    }

    @Test
    fun `deleteDownload clears the local file path and deletes the file`() = runTest {
        val episodeDao = FakeEpisodeDao()
        episodeDao.state.value = listOf(
            EpisodeEntity(
                id = "ep-1", podcastId = "p1", title = "Ep 1", audioUrl = "https://example.com/ep1.mp3",
                publishedAtEpochMillis = null, durationSeconds = null, description = "",
                localFilePath = "/local/path/ep1.audio",
            ),
        )
        var deletedPath: String? = null
        val repository = PodcastRepository(
            itunesApi = FakeItunesSearchApi(),
            fetchFeed = { "" },
            podcastDao = FakePodcastDao(),
            episodeDao = episodeDao,
            deleteFile = { deletedPath = it },
        )
        val episode = episodeDao.state.value.first().toEpisode()

        repository.deleteDownload(episode)

        assertThat(deletedPath).isEqualTo("/local/path/ep1.audio")
        assertThat(episodeDao.state.value.first().localFilePath).isNull()
    }

    @Test
    fun `enqueue adds the episode at the next position`() = runTest {
        val episodeDao = FakeEpisodeDao()
        episodeDao.state.value = listOf(
            EpisodeEntity(id = "e1", podcastId = "p1", title = "E1", audioUrl = "https://example.com/e1.mp3", publishedAtEpochMillis = null, durationSeconds = null, description = ""),
            EpisodeEntity(id = "e2", podcastId = "p1", title = "E2", audioUrl = "https://example.com/e2.mp3", publishedAtEpochMillis = null, durationSeconds = null, description = ""),
        )
        val queueDao = FakeQueueDao()
        val repository = PodcastRepository(FakeItunesSearchApi(), { "" }, FakePodcastDao(), episodeDao, queueDao = queueDao)

        repository.enqueue(episodeDao.state.value[0].toEpisode())
        repository.enqueue(episodeDao.state.value[1].toEpisode())

        val queued = repository.queue().first()
        assertThat(queued.map { it.id }).containsExactly("e1", "e2").inOrder()
    }

    @Test
    fun `removeFromQueue removes the episode`() = runTest {
        val episodeDao = FakeEpisodeDao()
        episodeDao.state.value = listOf(
            EpisodeEntity(id = "e1", podcastId = "p1", title = "E1", audioUrl = "https://example.com/e1.mp3", publishedAtEpochMillis = null, durationSeconds = null, description = ""),
        )
        val queueDao = FakeQueueDao()
        val repository = PodcastRepository(FakeItunesSearchApi(), { "" }, FakePodcastDao(), episodeDao, queueDao = queueDao)
        repository.enqueue(episodeDao.state.value[0].toEpisode())

        repository.removeFromQueue("e1")

        assertThat(repository.queue().first()).isEmpty()
    }

    @Test
    fun `reorderQueue changes playback order`() = runTest {
        val episodeDao = FakeEpisodeDao()
        episodeDao.state.value = listOf(
            EpisodeEntity(id = "e1", podcastId = "p1", title = "E1", audioUrl = "https://example.com/e1.mp3", publishedAtEpochMillis = null, durationSeconds = null, description = ""),
            EpisodeEntity(id = "e2", podcastId = "p1", title = "E2", audioUrl = "https://example.com/e2.mp3", publishedAtEpochMillis = null, durationSeconds = null, description = ""),
        )
        val queueDao = FakeQueueDao()
        val repository = PodcastRepository(FakeItunesSearchApi(), { "" }, FakePodcastDao(), episodeDao, queueDao = queueDao)
        repository.enqueue(episodeDao.state.value[0].toEpisode())
        repository.enqueue(episodeDao.state.value[1].toEpisode())

        repository.reorderQueue(listOf("e2", "e1"))

        val queued = repository.queue().first()
        assertThat(queued.map { it.id }).containsExactly("e2", "e1").inOrder()
    }

    @Test
    fun `lastPosition returns zero when nothing was saved`() = runTest {
        val repository = PodcastRepository(FakeItunesSearchApi(), { "" }, FakePodcastDao(), FakeEpisodeDao())

        assertThat(repository.lastPosition("unknown")).isEqualTo(0L)
    }
}
