package com.easyradio.core.database

import com.easyradio.core.model.RecentlyPlayedItem
import com.easyradio.core.model.RecentlyPlayedType

fun RecentlyPlayedItem.toEntity(): RecentlyPlayedEntity = RecentlyPlayedEntity(
    contentId = contentId,
    type = type.name,
    title = title,
    subtitle = subtitle,
    imageUrl = imageUrl,
    playedAtEpochMillis = playedAtEpochMillis,
    stationStreamUrl = stationStreamUrl,
    podcastId = podcastId,
)

fun RecentlyPlayedEntity.toItem(): RecentlyPlayedItem = RecentlyPlayedItem(
    contentId = contentId,
    type = RecentlyPlayedType.valueOf(type),
    title = title,
    subtitle = subtitle,
    imageUrl = imageUrl,
    playedAtEpochMillis = playedAtEpochMillis,
    stationStreamUrl = stationStreamUrl,
    podcastId = podcastId,
)
