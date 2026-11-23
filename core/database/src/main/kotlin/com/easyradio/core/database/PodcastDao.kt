package com.easyradio.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {

    @Query("SELECT * FROM podcasts ORDER BY subscribedAtEpochMillis DESC")
    fun observeAll(): Flow<List<PodcastEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(podcast: PodcastEntity)

    @Query("DELETE FROM podcasts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE podcasts SET isPreset = :isPreset WHERE id = :id")
    suspend fun setPreset(id: String, isPreset: Boolean)

    @Query("UPDATE podcasts SET lastPlayedAtEpochMillis = :timestamp WHERE id = :id")
    suspend fun updateLastPlayed(id: String, timestamp: Long)

    @Query("UPDATE podcasts SET notifyNewEpisodes = :enabled WHERE id = :id")
    suspend fun setNotifyNewEpisodes(id: String, enabled: Boolean)

    @Query("UPDATE podcasts SET autoDownloadNewEpisodes = :enabled WHERE id = :id")
    suspend fun setAutoDownloadNewEpisodes(id: String, enabled: Boolean)

    @Query("UPDATE podcasts SET skipFirstSeconds = :seconds WHERE id = :id")
    suspend fun setSkipFirstSeconds(id: String, seconds: Int)

    @Query("UPDATE podcasts SET skipLastSeconds = :seconds WHERE id = :id")
    suspend fun setSkipLastSeconds(id: String, seconds: Int)

    @Query("UPDATE podcasts SET playbackSpeed = :speed WHERE id = :id")
    suspend fun setPlaybackSpeed(id: String, speed: Float)
}
