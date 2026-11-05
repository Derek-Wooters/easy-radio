package com.easyradio.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AdSegmentDao {

    @Query("SELECT * FROM ad_segments WHERE episodeId = :episodeId ORDER BY startMs ASC")
    fun observeForEpisode(episodeId: String): Flow<List<AdSegmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(segments: List<AdSegmentEntity>)

    @Query("DELETE FROM ad_segments WHERE episodeId = :episodeId")
    suspend fun deleteForEpisode(episodeId: String)
}
