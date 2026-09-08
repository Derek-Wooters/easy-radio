package com.easyradio.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteStationDao {

    @Query("SELECT * FROM favorite_stations ORDER BY favoritedAtEpochMillis DESC")
    fun observeAll(): Flow<List<FavoriteStationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(station: FavoriteStationEntity)

    @Query("DELETE FROM favorite_stations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE favorite_stations SET isPreset = :isPreset WHERE id = :id")
    suspend fun setPreset(id: String, isPreset: Boolean)

    @Query("UPDATE favorite_stations SET lastPlayedAtEpochMillis = :timestamp WHERE id = :id")
    suspend fun updateLastPlayed(id: String, timestamp: Long)
}
