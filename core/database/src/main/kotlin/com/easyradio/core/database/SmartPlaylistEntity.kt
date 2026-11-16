package com.easyradio.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * [podcastIds] is stored comma-joined rather than via a Room TypeConverter -- this is the only
 * collection-valued column in the schema, so a converter would be more machinery than the one
 * field needs. [status] stores [com.easyradio.core.model.SmartPlaylistStatus]'s enum name.
 */
@Entity(tableName = "smart_playlists")
data class SmartPlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val podcastIds: String,
    val status: String,
    val downloadedOnly: Boolean,
    val starredOnly: Boolean,
    val createdAtEpochMillis: Long,
)
