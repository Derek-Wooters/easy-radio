package com.easyradio.core.network.radiobrowser

import com.easyradio.core.model.RadioStation

fun RadioBrowserStationDto.toRadioStationOrNull(): RadioStation? {
    if (name.isBlank()) return null
    if (!urlResolved.startsWith("https://")) return null
    if (lastCheckOk != 1) return null

    return RadioStation(
        id = stationUuid.ifBlank { urlResolved },
        name = name,
        streamUrl = urlResolved,
        imageUrl = favicon.ifBlank { null },
    )
}
