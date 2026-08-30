package com.easyradio.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_stations")
data class FavoriteStationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
    val tagline: String,
    val imageUrl: String?,
    val favoritedAtEpochMillis: Long,
    val isPreset: Boolean = false,
)
