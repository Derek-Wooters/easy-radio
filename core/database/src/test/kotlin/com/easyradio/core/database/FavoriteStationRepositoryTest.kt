package com.easyradio.core.database

import com.easyradio.core.model.RadioStation
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakeFavoriteStationDao : FavoriteStationDao {
    val state = MutableStateFlow<List<FavoriteStationEntity>>(emptyList())

    override fun observeAll() = state.map { it.sortedByDescending { s -> s.favoritedAtEpochMillis } }

    override suspend fun upsert(station: FavoriteStationEntity) {
        state.update { list -> list.filterNot { it.id == station.id } + station }
    }

    override suspend fun delete(id: String) {
        state.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun setPreset(id: String, isPreset: Boolean) {
        state.update { list -> list.map { if (it.id == id) it.copy(isPreset = isPreset) else it } }
    }
}

class FavoriteStationRepositoryTest {

    private fun station(id: String, name: String = "Station $id") = RadioStation(
        id = id,
        name = name,
        streamUrl = "https://example.com/$id.mp3",
        tagline = "Tagline",
    )

    @Test
    fun `favorite then favorites returns the station`() = runTest {
        val dao = FakeFavoriteStationDao()
        val repository = FavoriteStationRepository(dao)

        repository.favorite(station("s1"))

        assertThat(repository.favorites().first().map { it.id }).containsExactly("s1")
    }

    @Test
    fun `unfavorite removes the station`() = runTest {
        val dao = FakeFavoriteStationDao()
        val repository = FavoriteStationRepository(dao)
        repository.favorite(station("s1"))

        repository.unfavorite("s1")

        assertThat(repository.favorites().first()).isEmpty()
    }

    @Test
    fun `favoriteIds reflects the current favorited set`() = runTest {
        val dao = FakeFavoriteStationDao()
        val repository = FavoriteStationRepository(dao)
        repository.favorite(station("s1"))
        repository.favorite(station("s2"))

        assertThat(repository.favoriteIds().first()).containsExactly("s1", "s2")
    }

    @Test
    fun `presets only includes stations flagged as preset`() = runTest {
        val dao = FakeFavoriteStationDao()
        val repository = FavoriteStationRepository(dao)
        repository.favorite(station("s1"))
        repository.favorite(station("s2"))

        repository.setPreset("s1", true)

        assertThat(repository.presets().first().map { it.id }).containsExactly("s1")
        assertThat(repository.presetIds().first()).containsExactly("s1")
    }
}
