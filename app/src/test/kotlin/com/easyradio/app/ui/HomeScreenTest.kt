package com.easyradio.app.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.easyradio.core.database.FavoriteStationDao
import com.easyradio.core.database.FavoriteStationEntity
import com.easyradio.core.database.FavoriteStationRepository
import com.easyradio.core.database.RecentlyPlayedDao
import com.easyradio.core.database.RecentlyPlayedEntity
import com.easyradio.core.database.RecentlyPlayedRepository
import com.easyradio.core.model.RadioStation
import com.easyradio.core.network.radiobrowser.RadioBrowserApi
import com.easyradio.core.network.radiobrowser.RadioBrowserStationDto
import com.easyradio.core.network.radiobrowser.RadioStationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class UnusedRadioBrowserApi : RadioBrowserApi {
    override suspend fun searchStations(name: String, limit: Int, hideBroken: Boolean): List<RadioBrowserStationDto> {
        throw NotImplementedError("HomeScreen never calls search(); only curatedStations()")
    }
}

private class NoOpFavoriteStationDao : FavoriteStationDao {
    val state = MutableStateFlow<List<FavoriteStationEntity>>(emptyList())
    override fun observeAll() = state
    override suspend fun upsert(station: FavoriteStationEntity) {}
    override suspend fun delete(id: String) {}
    override suspend fun setPreset(id: String, isPreset: Boolean) {}
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

    private val curatedStation = RadioStation(
        id = "kfan-test",
        name = "KFAN FM 100.3",
        streamUrl = "https://example.com/kfan.mp3",
        tagline = "Audio Home For Minnesota Sports",
    )

    private fun setHomeScreen(
        onStationSelected: (RadioStation) -> Unit = {},
        onSettingsClick: () -> Unit = {},
        onNavigateStations: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            HomeScreen(
                radioRepository = RadioStationRepository(
                    api = UnusedRadioBrowserApi(),
                    curatedStations = listOf(curatedStation),
                ),
                favoriteStationRepository = FavoriteStationRepository(NoOpFavoriteStationDao()),
                recentlyPlayedRepository = RecentlyPlayedRepository(NoOpRecentlyPlayedDao()),
                onStationSelected = onStationSelected,
                onRecentlyPlayedSelected = {},
                onSettingsClick = onSettingsClick,
                onNavigateStations = onNavigateStations,
            )
        }
    }

    @Test
    fun `curated station is listed and its play button fires onStationSelected`() {
        var selected: RadioStation? = null
        setHomeScreen(onStationSelected = { selected = it })

        composeTestRule.onNodeWithText("KFAN FM 100.3").assertExists()
        composeTestRule.onNodeWithContentDescription("Play KFAN FM 100.3").performClick()
        composeTestRule.waitForIdle()

        assert(selected?.id == "kfan-test") { "Expected onStationSelected to fire with the curated station" }
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
    fun `All Stations link fires onNavigateStations`() {
        var navigated = false
        setHomeScreen(onNavigateStations = { navigated = true })

        composeTestRule.onNodeWithText("All Stations").performClick()
        composeTestRule.waitForIdle()

        assert(navigated) { "Expected the All Stations link click to fire onNavigateStations" }
    }
}
