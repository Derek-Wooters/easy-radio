package com.easyradio.core.model

data class Episode(
    val id: String,
    val podcastId: String,
    val title: String,
    val audioUrl: String,
    val publishedAtEpochMillis: Long?,
    val durationSeconds: Int?,
    val description: String = "",
    val localFilePath: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Episode.id must not be blank" }
        require(podcastId.isNotBlank()) { "Episode.podcastId must not be blank" }
        require(title.isNotBlank()) { "Episode.title must not be blank" }
        require(audioUrl.startsWith("https://")) {
            "Episode.audioUrl must be an https URL, was: $audioUrl"
        }
    }
}
