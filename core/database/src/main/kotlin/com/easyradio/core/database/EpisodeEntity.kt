package com.easyradio.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "episodes")
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val podcastId: String,
    val title: String,
    val audioUrl: String,
    val publishedAtEpochMillis: Long?,
    val durationSeconds: Int?,
    val description: String,
    val positionMs: Long = 0,
    val localFilePath: String? = null,
)
