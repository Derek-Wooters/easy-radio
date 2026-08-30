package com.easyradio.core.model

enum class RecentlyPlayedType { STATION, EPISODE }

/**
 * A denormalized log entry for the Home screen's "Recently Played" row.
 * Stations carry enough fields ([stationStreamUrl]) to resume playback directly;
 * episodes carry [podcastId] so the episode/podcast pair can be looked up again.
 */
data class RecentlyPlayedItem(
    val contentId: String,
    val type: RecentlyPlayedType,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val playedAtEpochMillis: Long,
    val stationStreamUrl: String? = null,
    val podcastId: String? = null,
)
