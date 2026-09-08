package com.easyradio.core.database

import com.easyradio.core.model.RadioStation

fun RadioStation.toEntity(favoritedAtEpochMillis: Long, isPreset: Boolean = false): FavoriteStationEntity =
    FavoriteStationEntity(
        id = id,
        name = name,
        streamUrl = streamUrl,
        tagline = tagline,
        imageUrl = imageUrl,
        favoritedAtEpochMillis = favoritedAtEpochMillis,
        isPreset = isPreset,
        lastPlayedAtEpochMillis = lastPlayedAtEpochMillis,
    )

fun FavoriteStationEntity.toRadioStation(): RadioStation = RadioStation(
    id = id,
    name = name,
    streamUrl = streamUrl,
    tagline = tagline,
    imageUrl = imageUrl,
    lastPlayedAtEpochMillis = lastPlayedAtEpochMillis,
)
