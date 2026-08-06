package com.easyradio.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "podcasts")
data class PodcastEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val feedUrl: String,
    val subscribedAtEpochMillis: Long,
)
