package com.easyradio.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.easyradio.core.database.EpisodeDao
import com.easyradio.core.database.EpisodeEntity
import com.easyradio.core.database.EpisodeMetadata
import com.easyradio.core.database.PodcastDao
import com.easyradio.core.database.PodcastEntity
import com.easyradio.core.database.PodcastRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class DownloadsFakePodcastDao : PodcastDao {
    val state = MutableStateFlow<List<PodcastEntity>>(emptyList())
    override fun observeAll() = state
    override suspend fun upsert(podcast: PodcastEntity) {}
    override suspend fun delete(id: String) {}
    override suspend fun setPreset(id: String, isPreset: Boolean) {}
    override suspend fun updateLastPlayed(id: String, timestamp: Long) {}
}

private class DownloadsFakeEpisodeDao : EpisodeDao {
    val state = MutableStateFlow<List<EpisodeEntity>>(emptyList())
    override fun observeByPodcast(podcastId: String) = state.map { list -> list.filter { it.podcastId == podcastId } }
    override suspend fun insertIgnore(episodes: List<EpisodeEntity>) {}
    override suspend fun updateMetadata(updates: List<EpisodeMetadata>) {}
    override suspend fun updatePosition(episodeId: String, positionMs: Long) {}
    override suspend fun getPosition(episodeId: String): Long? = null
    override suspend fun updateLocalFilePath(episodeId: String, localFilePath: String?) {
        state.update { list -> list.map { if (it.id == episodeId) it.copy(localFilePath = localFilePath) else it } }
    }
    override suspend fun getByIds(ids: List<String>): List<EpisodeEntity> = state.value.filter { it.id in ids }
    override fun observeDownloaded() = state.map { list -> list.filter { it.localFilePath != null } }
    override fun observeAll() = state
}

private fun fakePodcastRepository(podcastDao: DownloadsFakePodcastDao, episodeDao: DownloadsFakeEpisodeDao) =
    PodcastRepository(
        itunesApi = object : com.easyradio.core.network.podcast.ItunesSearchApi {
            override suspend fun searchPodcasts(term: String, media: String, limit: Int) =
                com.easyradio.core.network.podcast.ItunesSearchResponseDto()
        },
        fetchFeed = { "" },
        podcastDao = podcastDao,
        episodeDao = episodeDao,
    )

@RunWith(RobolectricTestRunner::class)
class DownloadsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun episode(id: String, podcastId: String, title: String, localFilePath: String?) = EpisodeEntity(
        id = id,
        podcastId = podcastId,
        title = title,
        audioUrl = "https://example.com/$id.mp3",
        publishedAtEpochMillis = 0L,
        durationSeconds = 600,
        description = "",
        localFilePath = localFilePath,
    )

    @Test
    fun `downloaded episodes are grouped under their own podcast's header`() {
        val podcastDao = DownloadsFakePodcastDao()
        podcastDao.state.value = listOf(
            PodcastEntity(id = "p1", title = "The Daily", author = "NYT", artworkUrl = null, feedUrl = "https://example.com/daily.xml", subscribedAtEpochMillis = 0L),
            PodcastEntity(id = "p2", title = "Radiolab", author = "WNYC", artworkUrl = null, feedUrl = "https://example.com/radiolab.xml", subscribedAtEpochMillis = 0L),
        )
        val episodeDao = DownloadsFakeEpisodeDao()
        episodeDao.state.value = listOf(
            episode("d1", "p1", "Daily Episode 1", "/fake/d1.mp3"),
            episode("d2", "p1", "Daily Episode 2", "/fake/d2.mp3"),
            episode("r1", "p2", "Radiolab Episode 1", "/fake/r1.mp3"),
            episode("not-downloaded", "p1", "Not Downloaded", localFilePath = null),
        )

        composeTestRule.setContent {
            DownloadsScreen(repository = fakePodcastRepository(podcastDao, episodeDao), onBack = {})
        }

        composeTestRule.onNodeWithText("The Daily").assertExists()
        composeTestRule.onNodeWithText("Radiolab").assertExists()
        composeTestRule.onNodeWithText("Daily Episode 1").assertExists()
        composeTestRule.onNodeWithText("Daily Episode 2").assertExists()
        composeTestRule.onNodeWithText("Radiolab Episode 1").assertExists()
        composeTestRule.onNodeWithText("Not Downloaded").assertDoesNotExist()
        composeTestRule.onNodeWithText("2 episodes · 0 KB").assertExists()
        composeTestRule.onNodeWithText("1 episode · 0 KB").assertExists()
    }

    @Test
    fun `delete all removes every downloaded episode for that podcast after confirming`() {
        val podcastDao = DownloadsFakePodcastDao()
        podcastDao.state.value = listOf(
            PodcastEntity(id = "p1", title = "The Daily", author = "NYT", artworkUrl = null, feedUrl = "https://example.com/daily.xml", subscribedAtEpochMillis = 0L),
        )
        val episodeDao = DownloadsFakeEpisodeDao()
        episodeDao.state.value = listOf(
            episode("d1", "p1", "Daily Episode 1", "/fake/d1.mp3"),
            episode("d2", "p1", "Daily Episode 2", "/fake/d2.mp3"),
        )

        composeTestRule.setContent {
            DownloadsScreen(repository = fakePodcastRepository(podcastDao, episodeDao), onBack = {})
        }

        composeTestRule.onNodeWithContentDescription("Delete all downloads for The Daily").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Delete all").performClick()
        composeTestRule.waitForIdle()

        assertThat(episodeDao.state.value.all { it.localFilePath == null }).isTrue()
        composeTestRule.onAllNodesWithText("The Daily").assertCountEquals(0)
    }
}
