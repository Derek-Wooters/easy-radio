package com.easyradio.core.database

import com.easyradio.core.model.RadioStation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FavoriteStationRepository(private val dao: FavoriteStationDao) {

    fun favorites(): Flow<List<RadioStation>> = dao.observeAll().map { list -> list.map { it.toRadioStation() } }

    fun favoriteIds(): Flow<Set<String>> = dao.observeAll().map { list -> list.map { it.id }.toSet() }

    fun presetIds(): Flow<Set<String>> =
        dao.observeAll().map { list -> list.filter { it.isPreset }.map { it.id }.toSet() }

    fun presets(): Flow<List<RadioStation>> =
        dao.observeAll().map { list -> list.filter { it.isPreset }.map { it.toRadioStation() } }

    suspend fun favorite(station: RadioStation) {
        dao.upsert(station.toEntity(favoritedAtEpochMillis = System.currentTimeMillis()))
    }

    suspend fun unfavorite(stationId: String) {
        dao.delete(stationId)
    }

    suspend fun setPreset(stationId: String, isPreset: Boolean) {
        dao.setPreset(stationId, isPreset)
    }
}
