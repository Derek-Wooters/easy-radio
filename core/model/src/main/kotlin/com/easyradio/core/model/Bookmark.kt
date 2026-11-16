package com.easyradio.core.model

/** A saved timestamp within an episode, created from Now Playing's bookmark button. */
data class Bookmark(
    val id: String,
    val episodeId: String,
    val positionMs: Long,
    val note: String = "",
    val createdAtEpochMillis: Long,
)
