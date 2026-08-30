package com.easyradio.core.database

import com.easyradio.core.model.RecentlyPlayedItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecentlyPlayedRepository(private val dao: RecentlyPlayedDao) {

    fun recent(): Flow<List<RecentlyPlayedItem>> = dao.observeRecent().map { list -> list.map { it.toItem() } }

    suspend fun record(item: RecentlyPlayedItem) {
        dao.upsert(item.toEntity())
        dao.trim()
    }
}
