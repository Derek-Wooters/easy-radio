package com.easyradio.core.database

import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast
import com.easyradio.core.network.podcast.ItunesSearchApi
import com.easyradio.core.network.podcast.PodcastFeedParser
import com.easyradio.core.network.podcast.toPodcastOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PodcastRepository(
    private val itunesApi: ItunesSearchApi,
    private val fetchFeed: suspend (feedUrl: String) -> String,
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val downloadFile: suspend (id: String, audioUrl: String) -> String? = { _, _ -> null },
    private val deleteFile: (String) -> Unit = {},
    private val queueDao: QueueDao = NoOpQueueDao,
) {
    companion object {
        const val EPISODE_PAGE_SIZE = 50
    }

    /**
     * Parsed episode lists, keyed by podcast id, cached after the first successful fetch of a
     * feed so scroll-triggered [loadMoreEpisodes] calls slice the next page locally instead of
     * re-downloading and re-parsing the whole (possibly multi-thousand-item) RSS document.
     */
    private val feedCache = mutableMapOf<String, List<Episode>>()

    suspend fun search(query: String): List<Podcast> {
        if (query.isBlank()) return emptyList()

        return try {
            itunesApi.searchPodcasts(term = query).results.mapNotNull { it.toPodcastOrNull() }.distinctBy { it.id }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun subscribedPodcasts(): Flow<List<Podcast>> =
        podcastDao.observeAll().map { list -> list.map { it.toPodcast() } }

    suspend fun subscribe(podcast: Podcast) {
        podcastDao.upsert(podcast.toEntity(subscribedAtEpochMillis = System.currentTimeMillis()))
        refreshEpisodes(podcast)
    }

    suspend fun unsubscribe(podcastId: String) {
        podcastDao.delete(podcastId)
    }

    suspend fun setPreset(podcastId: String, isPreset: Boolean) {
        podcastDao.setPreset(podcastId, isPreset)
    }

    /**
     * Fetches the feed and stores its [EPISODE_PAGE_SIZE] most recent episodes. Long-running
     * shows can have thousands of episodes in their RSS feed; storing all of them up front would
     * mean downloading/parsing a multi-megabyte XML document and bulk-inserting thousands of rows
     * on every subscribe. [loadMoreEpisodes] extends this window as the user scrolls.
     */
    suspend fun refreshEpisodes(podcast: Podcast) {
        feedCache.remove(podcast.id)
        loadEpisodePage(podcast, upToCount = EPISODE_PAGE_SIZE)
    }

    /**
     * Loads the next page of episodes beyond what's currently stored. Returns true if the feed
     * has still more episodes beyond this page (so the caller can keep offering to load more),
     * or if the underlying fetch failed transiently -- a network blip should prompt a retry on
     * the next scroll, not be mistaken for having reached the true end of the feed.
     */
    suspend fun loadMoreEpisodes(podcast: Podcast): Boolean {
        val alreadyStored = episodeDao.observeByPodcast(podcast.id).first().size
        val targetCount = alreadyStored + EPISODE_PAGE_SIZE
        val result = loadEpisodePage(podcast, upToCount = targetCount)
        if (result == null) return true
        return result > targetCount
    }

    /**
     * Fetches (or reuses the cached parse of) the feed, stores the [upToCount] most recent
     * episodes, and returns the feed's total episode count -- or null if the fetch failed.
     */
    private suspend fun loadEpisodePage(podcast: Podcast, upToCount: Int): Int? {
        val allEpisodes = feedCache[podcast.id] ?: run {
            val xml = try {
                fetchFeed(podcast.feedUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (xml.isNullOrBlank()) return null

            PodcastFeedParser.parse(xml, podcastId = podcast.id)
                .sortedByDescending { it.publishedAtEpochMillis ?: 0L }
                .also { feedCache[podcast.id] = it }
        }

        val page = allEpisodes.take(upToCount)
        if (page.isNotEmpty()) {
            episodeDao.upsertAll(page.map { it.toEntity() })
        }
        return allEpisodes.size
    }

    fun episodesFor(podcastId: String): Flow<List<Episode>> =
        episodeDao.observeByPodcast(podcastId).map { list -> list.map { it.toEpisode() } }

    fun downloadedEpisodes(): Flow<List<Episode>> =
        episodeDao.observeDownloaded().map { list -> list.map { it.toEpisode() } }

    suspend fun savePosition(episodeId: String, positionMs: Long) {
        episodeDao.updatePosition(episodeId, positionMs)
    }

    suspend fun lastPosition(episodeId: String): Long = episodeDao.getPosition(episodeId) ?: 0L

    suspend fun downloadEpisode(episode: Episode): Boolean {
        val path = downloadFile(episode.id, episode.audioUrl) ?: return false
        episodeDao.updateLocalFilePath(episode.id, path)
        return true
    }

    suspend fun deleteDownload(episode: Episode) {
        episode.localFilePath?.let { deleteFile(it) }
        episodeDao.updateLocalFilePath(episode.id, null)
    }

    suspend fun enqueue(episode: Episode) {
        val nextPosition = queueDao.maxPosition() + 1
        queueDao.upsert(QueueItemEntity(episodeId = episode.id, position = nextPosition))
    }

    suspend fun removeFromQueue(episodeId: String) {
        queueDao.remove(episodeId)
    }

    suspend fun reorderQueue(orderedEpisodeIds: List<String>) {
        val items = orderedEpisodeIds.mapIndexed { index, id -> QueueItemEntity(episodeId = id, position = index) }
        queueDao.upsertAll(items)
    }

    fun queue(): Flow<List<Episode>> = queueDao.observeAll().map { items ->
        val episodesById = episodeDao.getByIds(items.map { it.episodeId }).associateBy { it.id }
        items.mapNotNull { item -> episodesById[item.episodeId]?.toEpisode() }
    }
}

private val NoOpQueueDao = object : QueueDao {
    override fun observeAll(): Flow<List<QueueItemEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun upsert(item: QueueItemEntity) {}
    override suspend fun upsertAll(items: List<QueueItemEntity>) {}
    override suspend fun remove(episodeId: String) {}
    override suspend fun maxPosition(): Int = -1
}
