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
}
