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
import com.easyradio.core.database.toEpisode
import com.easyradio.core.model.RadioStation
import com.google.common.truth.Truth.assertThat
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
    override suspend fun setSkipFirstSeconds(id: String, seconds: Int) {}
    override suspend fun setSkipLastSeconds(id: String, seconds: Int) {}
    override suspend fun setPlaybackSpeed(id: String, speed: Float) {}
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
    override suspend fun setStarred(episodeId: String, starred: Boolean) {
        state.update { list -> list.map { if (it.id == episodeId) it.copy(isStarred = starred) else it } }
    }
    override fun observeStarred() = state.map { list -> list.filter { it.isStarred } }
}

private class PlaylistsFakeSmartPlaylistDao : com.easyradio.core.database.SmartPlaylistDao {
    val state = MutableStateFlow<List<com.easyradio.core.database.SmartPlaylistEntity>>(emptyList())
    override suspend fun upsert(playlist: com.easyradio.core.database.SmartPlaylistEntity) {
        state.update { list -> list.filterNot { it.id == playlist.id } + playlist }
    }
    override fun observeAll() = state.map { it.sortedBy { p -> p.createdAtEpochMillis } }
    override suspend fun delete(id: String) {
        state.update { list -> list.filterNot { it.id == id } }
    }
}

private fun fakePodcastRepository(
    podcastDao: PlaylistsFakePodcastDao = PlaylistsFakePodcastDao(),
    episodeDao: PlaylistsFakeEpisodeDao = PlaylistsFakeEpisodeDao(),
    smartPlaylistDao: PlaylistsFakeSmartPlaylistDao = PlaylistsFakeSmartPlaylistDao(),
) = PodcastRepository(
    itunesApi = object : com.easyradio.core.network.podcast.ItunesSearchApi {
        override suspend fun searchPodcasts(term: String, media: String, limit: Int) =
            com.easyradio.core.network.podcast.ItunesSearchResponseDto()
    },
    fetchFeed = { "" },
    podcastDao = podcastDao,
    episodeDao = episodeDao,
    smartPlaylistDao = smartPlaylistDao,
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

    @Test
    fun `starred episodes show in the Starred section across podcasts`() {
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
                id = "starred-ep",
                podcastId = "p1",
                title = "A Starred Episode",
                audioUrl = "https://example.com/starred.mp3",
                publishedAtEpochMillis = 100L,
                durationSeconds = 1_800,
                description = "",
                // Finished, not just started -- keeps it out of the unrelated In Progress/New
                // Episodes smart lists so it appears exactly once, only in Starred.
                positionMs = 1_800_000L,
                isStarred = true,
            ),
            EpisodeEntity(
                id = "plain-ep",
                podcastId = "p1",
                title = "A Plain Episode",
                audioUrl = "https://example.com/plain.mp3",
                publishedAtEpochMillis = 200L,
                durationSeconds = 1_800,
                description = "",
            ),
        )
        val favoriteRepository = FavoriteStationRepository(FakeFavoriteStationDao())

        composeTestRule.setContent {
            PlaylistsScreen(
                repository = favoriteRepository,
                podcastRepository = fakePodcastRepository(podcastDao, episodeDao),
                onStationSelected = {},
                onEpisodeSelected = { _, _ -> },
            )
        }

        // "A Plain Episode" is deliberately not asserted absent here: being unplayed, it also
        // legitimately shows up in the unrelated "New Episodes" smart list further down this same
        // screen, so a bare text-exists check can't tell the two sections' rows apart.
        composeTestRule.onNodeWithText("A Starred Episode").assertExists()
        composeTestRule.onNodeWithText("Star an episode from any podcast to pin it here.").assertDoesNotExist()
    }

    @Test
    fun `matchesSmartPlaylist filters by podcast, status, downloaded, and starred`() {
        val unplayed = episodeEntity("e1", "p1", positionMs = 0L)
        val inProgress = episodeEntity("e2", "p1", positionMs = 60_000L)
        val finished = episodeEntity("e3", "p1", positionMs = 1_800_000L)
        val downloaded = episodeEntity("e4", "p1", localFilePath = "/local/e4.mp3")
        val starred = episodeEntity("e5", "p1", isStarred = true)
        val otherPodcast = episodeEntity("e6", "p2")

        val onlyP1 = com.easyradio.core.model.SmartPlaylist(id = "sp", name = "P1", podcastIds = setOf("p1"), createdAtEpochMillis = 0L)
        assertThat(matchesSmartPlaylist(unplayed.toEpisode(), onlyP1)).isTrue()
        assertThat(matchesSmartPlaylist(otherPodcast.toEpisode(), onlyP1)).isFalse()

        val unplayedOnly = com.easyradio.core.model.SmartPlaylist(
            id = "sp", name = "Unplayed", status = com.easyradio.core.model.SmartPlaylistStatus.UNPLAYED, createdAtEpochMillis = 0L,
        )
        assertThat(matchesSmartPlaylist(unplayed.toEpisode(), unplayedOnly)).isTrue()
        assertThat(matchesSmartPlaylist(inProgress.toEpisode(), unplayedOnly)).isFalse()

        val inProgressOnly = com.easyradio.core.model.SmartPlaylist(
            id = "sp", name = "In Progress", status = com.easyradio.core.model.SmartPlaylistStatus.IN_PROGRESS, createdAtEpochMillis = 0L,
        )
        assertThat(matchesSmartPlaylist(inProgress.toEpisode(), inProgressOnly)).isTrue()
        assertThat(matchesSmartPlaylist(finished.toEpisode(), inProgressOnly)).isFalse()

        val finishedOnly = com.easyradio.core.model.SmartPlaylist(
            id = "sp", name = "Finished", status = com.easyradio.core.model.SmartPlaylistStatus.FINISHED, createdAtEpochMillis = 0L,
        )
        assertThat(matchesSmartPlaylist(finished.toEpisode(), finishedOnly)).isTrue()
        assertThat(matchesSmartPlaylist(unplayed.toEpisode(), finishedOnly)).isFalse()

        val downloadedOnly = com.easyradio.core.model.SmartPlaylist(id = "sp", name = "DL", downloadedOnly = true, createdAtEpochMillis = 0L)
        assertThat(matchesSmartPlaylist(downloaded.toEpisode(), downloadedOnly)).isTrue()
        assertThat(matchesSmartPlaylist(unplayed.toEpisode(), downloadedOnly)).isFalse()

        val starredOnly = com.easyradio.core.model.SmartPlaylist(id = "sp", name = "Starred", starredOnly = true, createdAtEpochMillis = 0L)
        assertThat(matchesSmartPlaylist(starred.toEpisode(), starredOnly)).isTrue()
        assertThat(matchesSmartPlaylist(unplayed.toEpisode(), starredOnly)).isFalse()
    }

    @Test
    fun `a custom smart playlist shows only its matching episodes and can be deleted`() {
        val podcastDao = PlaylistsFakePodcastDao()
        podcastDao.state.value = listOf(
            PodcastEntity(id = "p1", title = "Radiolab", author = "WNYC", artworkUrl = null, feedUrl = "https://example.com/radiolab.xml", subscribedAtEpochMillis = 0L),
            PodcastEntity(id = "p2", title = "Science Vs", author = "Gimlet", artworkUrl = null, feedUrl = "https://example.com/sciencevs.xml", subscribedAtEpochMillis = 0L),
        )
        val episodeDao = PlaylistsFakeEpisodeDao()
        episodeDao.state.value = listOf(
            // Finished (positionMs >= duration), not just started -- keeps these out of the
            // unrelated In Progress/New Episodes smart lists so each shows up exactly once,
            // only via this custom smart playlist's own section.
            episodeEntity("in-playlist", "p1", title = "Radiolab Episode", positionMs = 1_800_000L),
            episodeEntity("not-in-playlist", "p2", title = "Science Vs Episode", positionMs = 1_800_000L),
        )
        val smartPlaylistDao = PlaylistsFakeSmartPlaylistDao()
        smartPlaylistDao.state.value = listOf(
            com.easyradio.core.database.SmartPlaylistEntity(
                id = "sp1", name = "Radiolab Only", podcastIds = "p1", status = "ANY",
                downloadedOnly = false, starredOnly = false, createdAtEpochMillis = 0L,
            ),
        )
        val favoriteRepository = FavoriteStationRepository(FakeFavoriteStationDao())

        composeTestRule.setContent {
            PlaylistsScreen(
                repository = favoriteRepository,
                podcastRepository = fakePodcastRepository(podcastDao, episodeDao, smartPlaylistDao),
                onStationSelected = {},
                onEpisodeSelected = { _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("Radiolab Only").assertExists()
        composeTestRule.onNodeWithText("Radiolab Episode").assertExists()
        composeTestRule.onNodeWithText("Science Vs Episode").assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription("Delete Radiolab Only").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Radiolab Only").assertDoesNotExist()
    }

    // No automated test drives opening CreateSmartPlaylistDialog from a click: doing so (with or
    // without pausing composeTestRule.mainClock) reliably throws OutOfMemoryError deep inside
    // AndroidParagraph/StaticLayout the moment the dialog is composed under Robolectric -- a
    // real environment limitation with this project's current Compose/Robolectric versions, not
    // a bug in the dialog's logic (same class of confirmed tooling gap as MigrationTest's doc
    // comment on MigrationTestHelper). The dialog's actual effect -- creating a SmartPlaylist and
    // persisting its rules -- is covered directly by PodcastRepositoryTest's
    // `createSmartPlaylist persists its rules and smartPlaylists reflects it`, and the resulting
    // section/filtering is covered by the test above; the create-dialog interaction itself was
    // verified live on-device instead.
}

private fun episodeEntity(
    id: String,
    podcastId: String,
    title: String = "Episode $id",
    positionMs: Long = 0L,
    durationSeconds: Int = 1_800,
    localFilePath: String? = null,
    isStarred: Boolean = false,
) = EpisodeEntity(
    id = id,
    podcastId = podcastId,
    title = title,
    audioUrl = "https://example.com/$id.mp3",
    publishedAtEpochMillis = 100L,
    durationSeconds = durationSeconds,
    description = "",
    positionMs = positionMs,
    localFilePath = localFilePath,
    isStarred = isStarred,
)
