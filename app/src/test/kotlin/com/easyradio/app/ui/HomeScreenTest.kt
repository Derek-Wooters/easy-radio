package com.easyradio.app.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.easyradio.core.database.EpisodeDao
import com.easyradio.core.database.EpisodeEntity
import com.easyradio.core.database.FavoriteStationDao
import com.easyradio.core.database.FavoriteStationEntity
import com.easyradio.core.database.FavoriteStationRepository
import com.easyradio.core.database.PodcastDao
import com.easyradio.core.database.PodcastEntity
import com.easyradio.core.database.PodcastRepository
import com.easyradio.core.database.RecentlyPlayedDao
import com.easyradio.core.database.RecentlyPlayedEntity
import com.easyradio.core.database.RecentlyPlayedRepository
import com.easyradio.core.model.Podcast
import com.easyradio.core.model.RadioStation
import com.easyradio.core.network.podcast.ItunesSearchApi
import com.easyradio.core.network.podcast.ItunesSearchResponseDto
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class UnusedItunesSearchApi : ItunesSearchApi {
    override suspend fun searchPodcasts(term: String, media: String, limit: Int): ItunesSearchResponseDto {
        throw NotImplementedError("HomeScreen never searches podcasts")
    }
}

private class NoOpEpisodeDao : EpisodeDao {
    override fun observeByPodcast(podcastId: String) = MutableStateFlow<List<EpisodeEntity>>(emptyList())
    override suspend fun upsertAll(episodes: List<EpisodeEntity>) {}
    override suspend fun updatePosition(episodeId: String, positionMs: Long) {}
    override suspend fun getPosition(episodeId: String): Long? = null
    override suspend fun updateLocalFilePath(episodeId: String, localFilePath: String?) {}
    override suspend fun getByIds(ids: List<String>): List<EpisodeEntity> = emptyList()
    override fun observeDownloaded() = MutableStateFlow<List<EpisodeEntity>>(emptyList())
}

private class HomeFakePodcastDao : PodcastDao {
    val state = MutableStateFlow<List<PodcastEntity>>(emptyList())
    override fun observeAll() = state
    override suspend fun upsert(podcast: PodcastEntity) {}
    override suspend fun delete(id: String) {}
    override suspend fun setPreset(id: String, isPreset: Boolean) {}
    override suspend fun updateLastPlayed(id: String, timestamp: Long) {}
}

private class HomeFakeFavoriteStationDao : FavoriteStationDao {
    val state = MutableStateFlow<List<FavoriteStationEntity>>(emptyList())
    override fun observeAll() = state
    override suspend fun upsert(station: FavoriteStationEntity) {}
    override suspend fun delete(id: String) {}
    override suspend fun setPreset(id: String, isPreset: Boolean) {}
    override suspend fun updateLastPlayed(id: String, timestamp: Long) {}
}

private class NoOpRecentlyPlayedDao : RecentlyPlayedDao {
    val state = MutableStateFlow<List<RecentlyPlayedEntity>>(emptyList())
    override fun observeRecent() = state
    override suspend fun upsert(item: RecentlyPlayedEntity) {}
    override suspend fun trim() {}
}

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun podcastEntity(id: String, title: String, lastPlayed: Long?) = PodcastEntity(
        id = id,
        title = title,
        author = "Author",
        artworkUrl = null,
        feedUrl = "https://example.com/$id.xml",
        subscribedAtEpochMillis = 0L,
        lastPlayedAtEpochMillis = lastPlayed,
    )

    private fun stationEntity(id: String, name: String, lastPlayed: Long?) = FavoriteStationEntity(
        id = id,
        name = name,
        streamUrl = "https://example.com/$id.mp3",
        tagline = "Tagline",
        imageUrl = null,
        favoritedAtEpochMillis = 0L,
        lastPlayedAtEpochMillis = lastPlayed,
    )

    private fun setHomeScreen(
        podcastDao: HomeFakePodcastDao = HomeFakePodcastDao(),
        favoriteStationDao: HomeFakeFavoriteStationDao = HomeFakeFavoriteStationDao(),
        onStationSelected: (RadioStation) -> Unit = {},
        onPodcastSelected: (Podcast) -> Unit = {},
        onSettingsClick: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            HomeScreen(
                podcastRepository = PodcastRepository(
                    itunesApi = UnusedItunesSearchApi(),
                    fetchFeed = { throw NotImplementedError("HomeScreen never fetches feeds") },
                    podcastDao = podcastDao,
                    episodeDao = NoOpEpisodeDao(),
                ),
                favoriteStationRepository = FavoriteStationRepository(favoriteStationDao),
                recentlyPlayedRepository = RecentlyPlayedRepository(NoOpRecentlyPlayedDao()),
                onStationSelected = onStationSelected,
                onPodcastSelected = onPodcastSelected,
                onRecentlyPlayedSelected = {},
                onSettingsClick = onSettingsClick,
            )
        }
    }

    @Test
    fun `settings gear fires onSettingsClick`() {
        var settingsOpened = false
        setHomeScreen(onSettingsClick = { settingsOpened = true })

        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()

        assert(settingsOpened) { "Expected the settings gear click to fire onSettingsClick" }
    }

    @Test
    fun `subscribed channels section is hidden when nothing has been played yet`() {
        val podcastDao = HomeFakePodcastDao()
        podcastDao.state.value = listOf(podcastEntity("p1", "Never Played Show", lastPlayed = null))

        setHomeScreen(podcastDao = podcastDao)

        composeTestRule.onNodeWithText("Subscribed Channels").assertDoesNotExist()
    }

    @Test
    fun `tapping a subscribed podcast channel fires onPodcastSelected`() {
        val podcastDao = HomeFakePodcastDao()
        podcastDao.state.value = listOf(podcastEntity("p1", "Planet Money", lastPlayed = 1_000L))
        var selected: Podcast? = null

        setHomeScreen(podcastDao = podcastDao, onPodcastSelected = { selected = it })

        composeTestRule.onNodeWithText("Subscribed Channels").assertExists()
        composeTestRule.onNodeWithText("Planet Money").performClick()
        composeTestRule.waitForIdle()

        assert(selected?.id == "p1") { "Expected onPodcastSelected to fire with the played podcast" }
    }

    @Test
    fun `tapping a followed station channel fires onStationSelected`() {
        val favoriteStationDao = HomeFakeFavoriteStationDao()
        favoriteStationDao.state.value = listOf(stationEntity("s1", "KFAN FM 100.3", lastPlayed = 2_000L))
        var selected: RadioStation? = null

        setHomeScreen(favoriteStationDao = favoriteStationDao, onStationSelected = { selected = it })

        composeTestRule.onNodeWithText("KFAN FM 100.3").performClick()
        composeTestRule.waitForIdle()

        assert(selected?.id == "s1") { "Expected onStationSelected to fire with the followed station" }
    }

    @Test
    fun `subscribed channels shows only the 4 most recently played items across podcasts and stations`() {
        val podcastDao = HomeFakePodcastDao()
        podcastDao.state.value = listOf(
            podcastEntity("p1", "Dropped Podcast", lastPlayed = 1_000L),
            podcastEntity("p2", "Newest Podcast", lastPlayed = 5_000L),
        )
        val favoriteStationDao = HomeFakeFavoriteStationDao()
        favoriteStationDao.state.value = listOf(
            stationEntity("s1", "Middle Station A", lastPlayed = 3_000L),
            stationEntity("s2", "Middle Station B", lastPlayed = 4_000L),
            stationEntity("s3", "Fourth Station", lastPlayed = 2_000L),
        )

        setHomeScreen(podcastDao = podcastDao, favoriteStationDao = favoriteStationDao)

        composeTestRule.onNodeWithText("Newest Podcast").assertExists()
        composeTestRule.onNodeWithText("Middle Station B").assertExists()
        composeTestRule.onNodeWithText("Middle Station A").assertExists()
        composeTestRule.onNodeWithText("Fourth Station").assertExists()
        composeTestRule.onNodeWithText("Dropped Podcast").assertDoesNotExist()
    }
}
