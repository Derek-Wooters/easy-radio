package com.easyradio.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val episodeId: String,
    val positionMs: Long,
    val note: String,
    val createdAtEpochMillis: Long,
)
