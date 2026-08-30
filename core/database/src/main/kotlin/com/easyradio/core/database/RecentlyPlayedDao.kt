package com.easyradio.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

private const val RECENTLY_PLAYED_LIMIT = 20

@Dao
interface RecentlyPlayedDao {

    @Query("SELECT * FROM recently_played ORDER BY playedAtEpochMillis DESC LIMIT $RECENTLY_PLAYED_LIMIT")
    fun observeRecent(): Flow<List<RecentlyPlayedEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RecentlyPlayedEntity)

    @Query(
        "DELETE FROM recently_played WHERE contentId NOT IN " +
            "(SELECT contentId FROM recently_played ORDER BY playedAtEpochMillis DESC LIMIT $RECENTLY_PLAYED_LIMIT)",
    )
    suspend fun trim()
}
