package com.easyradio.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAtEpochMillis DESC")
    fun observeByPodcast(podcastId: String): Flow<List<EpisodeEntity>>

    // Inserts genuinely new episodes; a conflicting id (already stored) is left completely
    // untouched -- see updateMetadata for refreshing an already-stored episode's feed-sourced
    // fields without disturbing its locally-tracked positionMs/localFilePath.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(episodes: List<EpisodeEntity>)

    @Update(entity = EpisodeEntity::class)
    suspend fun updateMetadata(updates: List<EpisodeMetadata>)

    @Query("UPDATE episodes SET positionMs = :positionMs WHERE id = :episodeId")
    suspend fun updatePosition(episodeId: String, positionMs: Long)

    @Query("SELECT positionMs FROM episodes WHERE id = :episodeId")
    suspend fun getPosition(episodeId: String): Long?

    @Query("UPDATE episodes SET localFilePath = :localFilePath WHERE id = :episodeId")
    suspend fun updateLocalFilePath(episodeId: String, localFilePath: String?)

    @Query("SELECT * FROM episodes WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE localFilePath IS NOT NULL ORDER BY publishedAtEpochMillis DESC")
    fun observeDownloaded(): Flow<List<EpisodeEntity>>
}
