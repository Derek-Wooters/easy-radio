package com.easyradio.core.network.radiobrowser

import com.easyradio.core.model.CuratedRadioStations
import com.easyradio.core.model.RadioStation
import kotlinx.coroutines.CancellationException

class RadioStationRepository(
    private val api: RadioBrowserApi,
    private val curatedStations: List<RadioStation> = CuratedRadioStations.ALL,
) {

    fun curatedStations(): List<RadioStation> = curatedStations

    suspend fun search(query: String): List<RadioStation> {
        if (query.isBlank()) return emptyList()

        return try {
            api.searchStations(name = query).mapNotNull { it.toRadioStationOrNull() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
}
