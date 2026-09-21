package com.easyradio.core.database

import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast
import com.easyradio.core.model.Chapter
import com.easyradio.core.network.podcast.ChaptersParser
import com.easyradio.core.network.podcast.ItunesSearchApi
import com.easyradio.core.network.podcast.OpmlSupport
import com.easyradio.core.network.podcast.PodcastFeedParser
import com.easyradio.core.network.podcast.TranscriptParser
import com.easyradio.core.network.podcast.toPodcastOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

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

    suspend fun markPlayed(podcastId: String) {
        podcastDao.updateLastPlayed(podcastId, System.currentTimeMillis())
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
     * Loads the first page of episodes for [podcast] if none are stored yet -- deliberately
     * independent of whether the user has subscribed, so browsing a podcast's episode list works
     * before committing to follow it. A no-op once any episodes exist for this podcast, so it's
     * safe to call every time the episode list screen is shown.
     */
    suspend fun ensureEpisodesLoaded(podcast: Podcast) {
        if (episodeDao.observeByPodcast(podcast.id).first().isNotEmpty()) return
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

            // Parsing + sorting a multi-thousand-item feed is real CPU work; keep it off
            // whatever dispatcher called us (often Compose's main-thread scope) so a huge
            // feed doesn't freeze the UI while it parses.
            withContext(Dispatchers.Default) {
                PodcastFeedParser.parse(xml, podcastId = podcast.id)
                    .sortedByDescending { it.publishedAtEpochMillis ?: 0L }
            }.also { feedCache[podcast.id] = it }
        }

        val page = allEpisodes.take(upToCount)
        if (page.isNotEmpty()) {
            // insertIgnore adds genuinely new episodes only, leaving any already-stored row (and
            // its locally-tracked positionMs/localFilePath) completely alone; updateMetadata then
            // refreshes feed-sourced fields for the whole page -- new and already-stored alike --
            // without touching those two columns. A single blanket upsert here would otherwise
            // silently reset every already-in-progress episode's saved position back to 0 on
            // every re-sync (e.g. whenever the user scrolls to load more episodes).
            episodeDao.insertIgnore(page.map { it.toEntity() })
            episodeDao.updateMetadata(page.map { it.toMetadata() })
        }
        return allEpisodes.size
    }

    /**
     * Fetches a podcast's feed fresh (bypassing the parse cache, since a background
     * refresh needs to see episodes published since the last check) and returns just
     * the episodes that weren't already stored -- empty if the fetch fails or nothing
     * is new. Used by the background new-episode check for notifications/auto-download.
     */
    suspend fun checkForNewEpisodes(podcast: Podcast): List<Episode> {
        val before = episodeDao.observeByPodcast(podcast.id).first().map { it.id }.toSet()
        feedCache.remove(podcast.id)
        loadEpisodePage(podcast, upToCount = EPISODE_PAGE_SIZE) ?: return emptyList()
        return episodeDao.observeByPodcast(podcast.id).first()
            .filter { it.id !in before }
            .map { it.toEpisode() }
    }

    /** Serializes current subscriptions to OPML, the standard podcast-app migration format. */
    suspend fun exportOpml(): String = OpmlSupport.write(subscribedPodcasts().first())

    /**
     * Subscribes to every feed in [xml] not already followed, using the OPML entry's
     * title directly (no iTunes lookup) since the feed URL is already known. Returns
     * how many new subscriptions were added.
     */
    suspend fun importOpml(xml: String): Int {
        val existingFeedUrls = subscribedPodcasts().first().map { it.feedUrl }.toSet()
        var imported = 0
        for (entry in OpmlSupport.parse(xml)) {
            if (entry.feedUrl in existingFeedUrls) continue
            subscribe(Podcast(id = entry.feedUrl, title = entry.title, author = "", artworkUrl = null, feedUrl = entry.feedUrl))
            imported++
        }
        return imported
    }

    /**
     * Fetches and parses an episode's Podcasting 2.0 chapters, if it published a
     * `<podcast:chapters>` tag -- empty if it didn't, or if the fetch/parse fails.
     */
    suspend fun loadChapters(episode: Episode): List<Chapter> {
        val url = episode.chaptersUrl ?: return emptyList()
        val json = try {
            fetchFeed(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        return ChaptersParser.parse(json)
    }

    /**
     * Fetches and converts an episode's `<podcast:transcript>` document to plain reading text,
     * or null if it didn't publish one, the fetch failed, or the result was blank.
     */
    suspend fun loadTranscript(episode: Episode): String? {
        val url = episode.transcriptUrl ?: return null
        val raw = try {
            fetchFeed(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        return TranscriptParser.parse(raw, episode.transcriptType).takeIf { it.isNotBlank() }
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
