package com.easyradio.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartPlaylistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(playlist: SmartPlaylistEntity)

    @Query("SELECT * FROM smart_playlists ORDER BY createdAtEpochMillis ASC")
    fun observeAll(): Flow<List<SmartPlaylistEntity>>

    @Query("DELETE FROM smart_playlists WHERE id = :id")
    suspend fun delete(id: String)
}
