package com.easyradio.core.database

import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast
import com.easyradio.core.network.podcast.ItunesSearchApi
import com.easyradio.core.network.podcast.PodcastFeedParser
import com.easyradio.core.network.podcast.toPodcastOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
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

    suspend fun refreshEpisodes(podcast: Podcast) {
        val xml = try {
            fetchFeed(podcast.feedUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
        if (xml.isBlank()) return

        val episodes = PodcastFeedParser.parse(xml, podcastId = podcast.id)
        if (episodes.isNotEmpty()) {
            episodeDao.upsertAll(episodes.map { it.toEntity() })
        }
    }

    fun episodesFor(podcastId: String): Flow<List<Episode>> =
        episodeDao.observeByPodcast(podcastId).map { list -> list.map { it.toEpisode() } }

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
