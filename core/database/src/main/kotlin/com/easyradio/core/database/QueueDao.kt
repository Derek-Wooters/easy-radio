package com.easyradio.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {

    @Query("SELECT * FROM queue ORDER BY position ASC")
    fun observeAll(): Flow<List<QueueItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: QueueItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<QueueItemEntity>)

    @Query("DELETE FROM queue WHERE episodeId = :episodeId")
    suspend fun remove(episodeId: String)

    @Query("SELECT COALESCE(MAX(position), -1) FROM queue")
    suspend fun maxPosition(): Int
}
