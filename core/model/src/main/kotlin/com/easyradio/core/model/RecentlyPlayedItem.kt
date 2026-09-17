package com.easyradio.core.model

enum class RecentlyPlayedType { STATION, EPISODE }

/**
 * A denormalized log entry for the Home screen's "Recently Played" row. Both types are
 * recorded and displayed at the "channel" level, not the specific thing last played:
 * stations carry enough fields ([stationStreamUrl]) to resume playback directly, while
 * episode entries use the podcast's own [title]/[subtitle]/[imageUrl] (keyed by
 * [contentId] = the podcast's id, so replaying any episode of the same show updates one
 * row) and [podcastId] to look the podcast back up -- tapping one opens the podcast's
 * page rather than resuming the specific episode that was last played.
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
