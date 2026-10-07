package com.easyradio.core.database

import androidx.room.ColumnInfo
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
    val isPreset: Boolean = false,
    val lastPlayedAtEpochMillis: Long? = null,
    // defaultValue must match MIGRATION_8_9's ALTER TABLE exactly, or Room's post-migration
    // schema validation fails on next launch (expected schema vs. actual migrated schema).
    @ColumnInfo(defaultValue = "0") val notifyNewEpisodes: Boolean = false,
    @ColumnInfo(defaultValue = "0") val autoDownloadNewEpisodes: Boolean = false,
)
