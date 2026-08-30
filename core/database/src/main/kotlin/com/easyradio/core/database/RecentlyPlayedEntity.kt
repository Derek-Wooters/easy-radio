package com.easyradio.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recently_played")
data class RecentlyPlayedEntity(
    @PrimaryKey val contentId: String,
    val type: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val playedAtEpochMillis: Long,
    val stationStreamUrl: String? = null,
    val podcastId: String? = null,
)
