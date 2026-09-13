package com.easyradio.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cumulative listening time for one calendar day, keyed by ISO date ("2026-09-12"). */
@Entity(tableName = "listening_stats")
data class ListeningStatsEntity(
    @PrimaryKey val date: String,
    val secondsListened: Long,
)
