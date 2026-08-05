package com.easyradio.core.network.radiobrowser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RadioBrowserStationDto(
    @SerialName("stationuuid") val stationUuid: String = "",
    val name: String = "",
    @SerialName("url_resolved") val urlResolved: String = "",
    val homepage: String = "",
    val favicon: String = "",
    @SerialName("lastcheckok") val lastCheckOk: Int = 0,
)
