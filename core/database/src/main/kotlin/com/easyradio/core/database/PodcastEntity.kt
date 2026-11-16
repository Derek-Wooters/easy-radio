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
    // defaultValue must match MIGRATION_9_10's ALTER TABLE exactly, same reason as above.
    @ColumnInfo(defaultValue = "0") val skipFirstSeconds: Int = 0,
    @ColumnInfo(defaultValue = "0") val skipLastSeconds: Int = 0,
    // defaultValue must match MIGRATION_10_11's ALTER TABLE exactly, same reason as above.
    @ColumnInfo(defaultValue = "1.0") val playbackSpeed: Float = 1.0f,
)
