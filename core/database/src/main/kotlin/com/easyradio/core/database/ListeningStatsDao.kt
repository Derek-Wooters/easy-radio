package com.easyradio.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ListeningStatsDao {

    @Query("SELECT * FROM listening_stats WHERE date = :date")
    suspend fun get(date: String): ListeningStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ListeningStatsEntity)

    @Query("SELECT * FROM listening_stats WHERE date >= :sinceDate ORDER BY date DESC")
    fun observeSince(sinceDate: String): Flow<List<ListeningStatsEntity>>
}
