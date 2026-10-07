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
import com.easyradio.core.model.RadioStation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class PlaylistsFakePodcastDao : PodcastDao {
    val state = MutableStateFlow<List<PodcastEntity>>(emptyList())
    override fun observeAll() = state
    override suspend fun upsert(podcast: PodcastEntity) {
        state.update { list -> list.filterNot { it.id == podcast.id } + podcast }
    }
    override suspend fun delete(id: String) {}
    override suspend fun setPreset(id: String, isPreset: Boolean) {}
    override suspend fun updateLastPlayed(id: String, timestamp: Long) {}
    override suspend fun setNotifyNewEpisodes(id: String, enabled: Boolean) {}
    override suspend fun setAutoDownloadNewEpisodes(id: String, enabled: Boolean) {}
}

private class PlaylistsFakeEpisodeDao : EpisodeDao {
    val state = MutableStateFlow<List<EpisodeEntity>>(emptyList())
    override fun observeByPodcast(podcastId: String) = state.map { list -> list.filter { it.podcastId == podcastId } }
    override suspend fun insertIgnore(episodes: List<EpisodeEntity>) {
        state.update { list -> list + episodes }
    }
    override suspend fun updateMetadata(updates: List<EpisodeMetadata>) {}
    override suspend fun updatePosition(episodeId: String, positionMs: Long) {}
    override suspend fun getPosition(episodeId: String): Long? = null
    override suspend fun updateLocalFilePath(episodeId: String, localFilePath: String?) {}
    override suspend fun getByIds(ids: List<String>): List<EpisodeEntity> = state.value.filter { it.id in ids }
    override fun observeDownloaded() = MutableStateFlow<List<EpisodeEntity>>(emptyList())
    override fun observeAll() = state
}

private fun fakePodcastRepository(
    podcastDao: PlaylistsFakePodcastDao = PlaylistsFakePodcastDao(),
    episodeDao: PlaylistsFakeEpisodeDao = PlaylistsFakeEpisodeDao(),
) = PodcastRepository(
    itunesApi = object : com.easyradio.core.network.podcast.ItunesSearchApi {
        override suspend fun searchPodcasts(term: String, media: String, limit: Int) =
            com.easyradio.core.network.podcast.ItunesSearchResponseDto()
    },
    fetchFeed = { "" },
    podcastDao = podcastDao,
    episodeDao = episodeDao,
)

private class FakeFavoriteStationDao : FavoriteStationDao {
    val state = MutableStateFlow<List<FavoriteStationEntity>>(emptyList())
    var deletedId: String? = null

    override fun observeAll() = state.map { it.sortedByDescending { s -> s.favoritedAtEpochMillis } }

    override suspend fun upsert(station: FavoriteStationEntity) {
        state.update { list -> list.filterNot { it.id == station.id } + station }
    }

    override suspend fun delete(id: String) {
        deletedId = id
        state.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun setPreset(id: String, isPreset: Boolean) {
        state.update { list -> list.map { if (it.id == id) it.copy(isPreset = isPreset) else it } }
    }

    override suspend fun updateLastPlayed(id: String, timestamp: Long) {
        state.update { list -> list.map { if (it.id == id) it.copy(lastPlayedAtEpochMillis = timestamp) else it } }
    }
}

@RunWith(RobolectricTestRunner::class)
class PlaylistsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `favorited station is listed and unfavorite button removes it`() {
        val dao = FakeFavoriteStationDao()
        dao.state.value = listOf(
            FavoriteStationEntity(
                id = "kfan",
                name = "KFAN FM 100.3",
                streamUrl = "https://example.com/kfan.mp3",
                tagline = "Audio Home For Minnesota Sports",
                imageUrl = null,
                favoritedAtEpochMillis = 100L,
            ),
        )
        val repository = FavoriteStationRepository(dao)
        var selectedStation: RadioStation? = null

        composeTestRule.setContent {
            PlaylistsScreen(
                repository = repository,
                podcastRepository = fakePodcastRepository(),
                onStationSelected = { selectedStation = it },
                onEpisodeSelected = { _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("KFAN FM 100.3").assertExists()

        composeTestRule.onNodeWithContentDescription("Remove from favorites").performClick()
        composeTestRule.waitForIdle()

        assert(dao.deletedId == "kfan") { "Expected unfavorite to delete 'kfan', deleted was ${dao.deletedId}" }
        assert(selectedStation == null) { "Removing a favorite should not trigger playback" }
    }

    @Test
    fun `in-progress and new episodes are sorted into their own smart lists`() {
        val podcastDao = PlaylistsFakePodcastDao()
        podcastDao.state.value = listOf(
            PodcastEntity(
                id = "p1",
                title = "Radiolab",
                author = "WNYC",
                artworkUrl = null,
                feedUrl = "https://example.com/radiolab.xml",
                subscribedAtEpochMillis = 0L,
            ),
        )
        val episodeDao = PlaylistsFakeEpisodeDao()
        episodeDao.state.value = listOf(
            EpisodeEntity(
                id = "resumed",
                podcastId = "p1",
                title = "Partway Through",
                audioUrl = "https://example.com/resumed.mp3",
                publishedAtEpochMillis = 200L,
                durationSeconds = 1_800,
                description = "",
                positionMs = 60_000L,
            ),
            EpisodeEntity(
                id = "fresh",
                podcastId = "p1",
                title = "Never Started",
                audioUrl = "https://example.com/fresh.mp3",
                publishedAtEpochMillis = 300L,
                durationSeconds = 1_800,
                description = "",
                positionMs = 0L,
            ),
        )
        val favoriteRepository = FavoriteStationRepository(FakeFavoriteStationDao())
        var selected: Pair<String, String>? = null

        composeTestRule.setContent {
            PlaylistsScreen(
                repository = favoriteRepository,
                podcastRepository = fakePodcastRepository(podcastDao, episodeDao),
                onStationSelected = {},
                onEpisodeSelected = { podcast, episode -> selected = podcast.id to episode.id },
            )
        }

        composeTestRule.onNodeWithText("Partway Through").assertExists()
        composeTestRule.onNodeWithText("Never Started").assertExists()

        composeTestRule.onNodeWithContentDescription("Play Never Started").performClick()
        composeTestRule.waitForIdle()

        assert(selected == ("p1" to "fresh")) { "Expected tapping the new episode to select podcast p1 / episode fresh, got $selected" }
    }
}
