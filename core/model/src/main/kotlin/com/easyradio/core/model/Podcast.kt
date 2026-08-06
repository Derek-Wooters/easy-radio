package com.easyradio.core.model

data class Podcast(
    val id: String,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val feedUrl: String,
) {
    init {
        require(id.isNotBlank()) { "Podcast.id must not be blank" }
        require(title.isNotBlank()) { "Podcast.title must not be blank" }
        require(feedUrl.startsWith("https://")) {
            "Podcast.feedUrl must be an https URL, was: $feedUrl"
        }
    }
}
