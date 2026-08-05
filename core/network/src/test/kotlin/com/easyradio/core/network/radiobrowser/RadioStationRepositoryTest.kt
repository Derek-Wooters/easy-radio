package com.easyradio.core.network.radiobrowser

import com.easyradio.core.model.RadioStation
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakeRadioBrowserApi(
    private val result: List<RadioBrowserStationDto> = emptyList(),
    private val error: Throwable? = null,
) : RadioBrowserApi {
    var lastQuery: String? = null

    override suspend fun searchStations(name: String, limit: Int, hideBroken: Boolean): List<RadioBrowserStationDto> {
        lastQuery = name
        error?.let { throw it }
        return result
    }
}

class RadioStationRepositoryTest {

    private val curated = listOf(RadioStation.KFAN_TEST_STATION)

    @Test
    fun `curatedStations returns the curated list`() {
        val repository = RadioStationRepository(api = FakeRadioBrowserApi(), curatedStations = curated)

        assertThat(repository.curatedStations()).isEqualTo(curated)
    }

    @Test
    fun `search delegates to the api and maps valid results`() = runTest {
        val dto = RadioBrowserStationDto(
            stationUuid = "abc-123",
            name = "Test Station",
            urlResolved = "https://example.com/stream",
            lastCheckOk = 1,
        )
        val api = FakeRadioBrowserApi(result = listOf(dto))
        val repository = RadioStationRepository(api = api, curatedStations = curated)

        val results = repository.search("Test")

        assertThat(api.lastQuery).isEqualTo("Test")
        assertThat(results).hasSize(1)
        assertThat(results.first().id).isEqualTo("abc-123")
    }

    @Test
    fun `search filters out unmappable dtos`() = runTest {
        val badDto = RadioBrowserStationDto(name = "Bad", urlResolved = "http://not-https.example")
        val api = FakeRadioBrowserApi(result = listOf(badDto))
        val repository = RadioStationRepository(api = api, curatedStations = curated)

        assertThat(repository.search("Bad")).isEmpty()
    }

    @Test
    fun `search returns an empty list for a blank query without calling the api`() = runTest {
        val api = FakeRadioBrowserApi()
        val repository = RadioStationRepository(api = api, curatedStations = curated)

        val results = repository.search("   ")

        assertThat(results).isEmpty()
        assertThat(api.lastQuery).isNull()
    }

    @Test
    fun `search returns an empty list instead of throwing when the api fails`() = runTest {
        val api = FakeRadioBrowserApi(error = java.io.IOException("network down"))
        val repository = RadioStationRepository(api = api, curatedStations = curated)

        assertThat(repository.search("Test")).isEmpty()
    }
}
