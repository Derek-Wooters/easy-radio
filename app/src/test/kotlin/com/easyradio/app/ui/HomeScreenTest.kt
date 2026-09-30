package com.easyradio.app.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.easyradio.core.database.EpisodeDao
import com.easyradio.core.database.EpisodeEntity
import com.easyradio.core.database.EpisodeMetadata
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
import com.easyradio.core.network.podcast.ItunesPodcastDto
import com.easyradio.core.network.podcast.ItunesSearchApi
import com.easyradio.core.network.podcast.ItunesSearchResponseDto
import com.easyradio.core.network.radiobrowser.RadioBrowserApi
import com.easyradio.core.network.radiobrowser.RadioBrowserStationDto
import com.easyradio.core.network.radiobrowser.RadioStationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FakeItunesSearchApi(private val results: Map<String, List<ItunesPodcastDto>> = emptyMap()) :
    ItunesSearchApi {
    override suspend fun searchPodcasts(term: String, media: String, limit: Int): ItunesSearchResponseDto =
        ItunesSearchResponseDto(results = results[term] ?: emptyList())
}

private class FakeRadioBrowserApi(private val results: Map<String, List<RadioBrowserStationDto>> = emptyMap()) :
    RadioBrowserApi {
    override suspend fun searchStations(name: String, limit: Int, hideBroken: Boolean): List<RadioBrowserStationDto> =
        results[name] ?: emptyList()
}

private class HomeFakeEpisodeDao : EpisodeDao {
    val state = MutableStateFlow<List<EpisodeEntity>>(emptyList())
    override fun observeByPodcast(podcastId: String) =
        MutableStateFlow(state.value.filter { it.podcastId == podcastId })
    override suspend fun insertIgnore(episodes: List<EpisodeEntity>) {}
    override suspend fun updateMetadata(updates: List<EpisodeMetadata>) {}
    override suspend fun updatePosition(episodeId: String, positionMs: Long) {}
    override suspend fun getPosition(episodeId: String): Long? = null
    override suspend fun updateLocalFilePath(episodeId: String, localFilePath: String?) {}
    override suspend fun getByIds(ids: List<String>): List<EpisodeEntity> = emptyList()
    override fun observeDownloaded() = MutableStateFlow<List<EpisodeEntity>>(emptyList())
    override fun observeAll() = state
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

    private fun podcastEntity(id: String, title: String, subscribedAt: Long, lastPlayed: Long? = null) = PodcastEntity(
        id = id,
        title = title,
        author = "Author",
        artworkUrl = null,
        feedUrl = "https://example.com/$id.xml",
        subscribedAtEpochMillis = subscribedAt,
        lastPlayedAtEpochMillis = lastPlayed,
    )

    private fun stationEntity(id: String, name: String, favoritedAt: Long, lastPlayed: Long? = null) = FavoriteStationEntity(
        id = id,
        name = name,
        streamUrl = "https://example.com/$id.mp3",
        tagline = "Tagline",
        imageUrl = null,
        favoritedAtEpochMillis = favoritedAt,
        lastPlayedAtEpochMillis = lastPlayed,
    )

    private fun episodeEntity(id: String, podcastId: String, positionMs: Long = 0L) = EpisodeEntity(
        id = id,
        podcastId = podcastId,
        title = "Episode $id",
        audioUrl = "https://example.com/$id.mp3",
        publishedAtEpochMillis = null,
        durationSeconds = 600,
        description = "",
        positionMs = positionMs,
    )

    private fun setHomeScreen(
        podcastDao: HomeFakePodcastDao = HomeFakePodcastDao(),
        favoriteStationDao: HomeFakeFavoriteStationDao = HomeFakeFavoriteStationDao(),
        episodeDao: HomeFakeEpisodeDao = HomeFakeEpisodeDao(),
        itunesResults: Map<String, List<ItunesPodcastDto>> = emptyMap(),
        radioResults: Map<String, List<RadioBrowserStationDto>> = emptyMap(),
        favoriteGenres: Set<String> = emptySet(),
        onStationSelected: (RadioStation) -> Unit = {},
        onPodcastSelected: (Podcast) -> Unit = {},
        onSettingsClick: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            HomeScreen(
                podcastRepository = PodcastRepository(
                    itunesApi = FakeItunesSearchApi(itunesResults),
                    fetchFeed = { throw NotImplementedError("HomeScreen never fetches feeds") },
                    podcastDao = podcastDao,
                    episodeDao = episodeDao,
                ),
                favoriteStationRepository = FavoriteStationRepository(favoriteStationDao),
                recentlyPlayedRepository = RecentlyPlayedRepository(NoOpRecentlyPlayedDao()),
                radioRepository = RadioStationRepository(
                    api = FakeRadioBrowserApi(radioResults),
                    curatedStations = emptyList(),
                ),
                favoriteGenres = favoriteGenres,
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
    fun `subscribed channels shows an empty-state prompt when nothing is subscribed or followed`() {
        setHomeScreen()

        composeTestRule.onNodeWithText("Subscribed Channels").assertExists()
        composeTestRule.onNodeWithText("Go add a radio station or podcast").assertExists()
    }

    @Test
    fun `a never-played subscription shows up immediately, not just after its first play`() {
        val podcastDao = HomeFakePodcastDao()
        podcastDao.state.value = listOf(podcastEntity("p1", "Never Played Show", subscribedAt = 1_000L))

        setHomeScreen(podcastDao = podcastDao)

        composeTestRule.onNodeWithText("Never Played Show").assertExists()
        composeTestRule.onNodeWithText("Go add a radio station or podcast").assertDoesNotExist()
    }

    @Test
    fun `tapping a subscribed podcast channel fires onPodcastSelected`() {
        val podcastDao = HomeFakePodcastDao()
        podcastDao.state.value = listOf(podcastEntity("p1", "Planet Money", subscribedAt = 1_000L))
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
        favoriteStationDao.state.value = listOf(stationEntity("s1", "KFAN FM 100.3", favoritedAt = 2_000L))
        var selected: RadioStation? = null

        setHomeScreen(favoriteStationDao = favoriteStationDao, onStationSelected = { selected = it })

        composeTestRule.onNodeWithText("KFAN FM 100.3").performClick()
        composeTestRule.waitForIdle()

        assert(selected?.id == "s1") { "Expected onStationSelected to fire with the followed station" }
    }

    @Test
    fun `subscribed channels shows only the 4 most recently subscribed items across podcasts and stations`() {
        val podcastDao = HomeFakePodcastDao()
        podcastDao.state.value = listOf(
            podcastEntity("p1", "Dropped Podcast", subscribedAt = 1_000L),
            podcastEntity("p2", "Newest Podcast", subscribedAt = 5_000L),
        )
        val favoriteStationDao = HomeFakeFavoriteStationDao()
        favoriteStationDao.state.value = listOf(
            stationEntity("s1", "Middle Station A", favoritedAt = 3_000L),
            stationEntity("s2", "Middle Station B", favoritedAt = 4_000L),
            stationEntity("s3", "Fourth Station", favoritedAt = 2_000L),
        )

        setHomeScreen(podcastDao = podcastDao, favoriteStationDao = favoriteStationDao)

        composeTestRule.onNodeWithText("Newest Podcast").assertExists()
        composeTestRule.onNodeWithText("Middle Station B").assertExists()
        composeTestRule.onNodeWithText("Middle Station A").assertExists()
        composeTestRule.onNodeWithText("Fourth Station").assertExists()
        composeTestRule.onNodeWithText("Dropped Podcast").assertDoesNotExist()
    }

    @Test
    fun `a podcast with a fresh unplayed episode is pulled ahead of a more recently subscribed show without one`() {
        val podcastDao = HomeFakePodcastDao()
        podcastDao.state.value = listOf(
            // Subscribed to more recently, but nothing new to listen to.
            podcastEntity("p1", "Caught Up Show", subscribedAt = 5_000L),
            // Subscribed to longer ago, but has a fresh episode -- should still show first.
            podcastEntity("p2", "Show With New Episode", subscribedAt = 1_000L),
        )
        val episodeDao = HomeFakeEpisodeDao()
        episodeDao.state.value = listOf(
            episodeEntity("ep1", podcastId = "p1", positionMs = 300_000L),
            episodeEntity("ep2", podcastId = "p2", positionMs = 0L),
        )

        setHomeScreen(podcastDao = podcastDao, episodeDao = episodeDao)

        // A LazyRow renders in list order left-to-right, so the fresh-episode show having a
        // smaller left offset than the more-recently-subscribed-but-caught-up show proves the
        // "jump ahead" ordering actually happened -- a pure subscribedAt sort would have put
        // Caught Up Show (subscribedAt=5_000) first, ahead of Show With New Episode
        // (subscribedAt=1_000).
        val newEpisodeShowLeft = composeTestRule.onNodeWithText("Show With New Episode")
            .fetchSemanticsNode().boundsInRoot.left
        val caughtUpShowLeft = composeTestRule.onNodeWithText("Caught Up Show")
            .fetchSemanticsNode().boundsInRoot.left

        assert(newEpisodeShowLeft < caughtUpShowLeft) {
            "Expected the show with a fresh episode to render before the more recently " +
                "subscribed but caught-up show"
        }
    }

    @Test
    fun `favorite genre gets a header and search results from both podcasts and stations`() {
        val podcastDto = ItunesPodcastDto(collectionName = "Sports Daily", feedUrl = "https://example.com/sports.xml")
        val stationDto = RadioBrowserStationDto(
            stationUuid = "sports-fm",
            name = "Sports FM",
            urlResolved = "https://example.com/sports.mp3",
            favicon = "",
            lastCheckOk = 1,
        )
        var selectedPodcast: Podcast? = null

        setHomeScreen(
            itunesResults = mapOf("Sports" to listOf(podcastDto)),
            radioResults = mapOf("Sports" to listOf(stationDto)),
            favoriteGenres = setOf("Sports"),
            onPodcastSelected = { selectedPodcast = it },
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Sports").assertExists()
        composeTestRule.onNodeWithText("Sports Daily").assertExists()
        composeTestRule.onNodeWithText("Sports FM").assertExists()

        composeTestRule.onNodeWithText("Sports Daily").performClick()
        composeTestRule.waitForIdle()

        assert(selectedPodcast?.title == "Sports Daily") {
            "Expected onPodcastSelected to fire with the discovered podcast"
        }
    }

    @Test
    fun `favorite genre with no search results shows no section`() {
        setHomeScreen(favoriteGenres = setOf("Obscure Topic"))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Obscure Topic").assertDoesNotExist()
    }
}
