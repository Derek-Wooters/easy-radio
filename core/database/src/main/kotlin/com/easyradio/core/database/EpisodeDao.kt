package com.easyradio.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAtEpochMillis DESC")
    fun observeByPodcast(podcastId: String): Flow<List<EpisodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(episodes: List<EpisodeEntity>)

    @Query("UPDATE episodes SET positionMs = :positionMs WHERE id = :episodeId")
    suspend fun updatePosition(episodeId: String, positionMs: Long)

    @Query("SELECT positionMs FROM episodes WHERE id = :episodeId")
    suspend fun getPosition(episodeId: String): Long?
}
