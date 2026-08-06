package com.easyradio.core.network.podcast

import com.easyradio.core.model.Podcast

fun ItunesPodcastDto.toPodcastOrNull(): Podcast? {
    if (collectionName.isBlank()) return null
    if (!feedUrl.startsWith("https://")) return null

    return Podcast(
        id = feedUrl,
        title = collectionName,
        author = artistName,
        artworkUrl = artworkUrl600.ifBlank { null },
        feedUrl = feedUrl,
    )
}
