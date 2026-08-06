package com.easyradio.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queue")
data class QueueItemEntity(
    @PrimaryKey val episodeId: String,
    val position: Int,
)
