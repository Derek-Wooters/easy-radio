package com.easyradio.app.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.easyradio.core.database.FavoriteStationDao
import com.easyradio.core.database.FavoriteStationEntity
import com.easyradio.core.database.FavoriteStationRepository
import com.easyradio.core.model.RadioStation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
            PlaylistsScreen(repository = repository, onStationSelected = { selectedStation = it })
        }

        composeTestRule.onNodeWithText("KFAN FM 100.3").assertExists()

        composeTestRule.onNodeWithContentDescription("Remove from favorites").performClick()
        composeTestRule.waitForIdle()

        assert(dao.deletedId == "kfan") { "Expected unfavorite to delete 'kfan', deleted was ${dao.deletedId}" }
        assert(selectedStation == null) { "Removing a favorite should not trigger playback" }
    }
}
