package com.easyradio.core.database

import androidx.room.Entity

@Entity(tableName = "ad_segments", primaryKeys = ["episodeId", "startMs"])
data class AdSegmentEntity(
    val episodeId: String,
    val startMs: Long,
    val endMs: Long,
)
