package com.easyradio.core.network.podcast

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ItunesSearchResponseDto(
    val resultCount: Int = 0,
    val results: List<ItunesPodcastDto> = emptyList(),
)

@Serializable
data class ItunesPodcastDto(
    @SerialName("collectionName") val collectionName: String = "",
    @SerialName("artistName") val artistName: String = "",
    @SerialName("artworkUrl600") val artworkUrl600: String = "",
    @SerialName("feedUrl") val feedUrl: String = "",
)
