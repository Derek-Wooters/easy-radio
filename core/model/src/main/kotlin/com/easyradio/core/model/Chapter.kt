package com.easyradio.core.model

/** One chapter marker from a Podcasting 2.0 `podcast:chapters` JSON document. */
data class Chapter(
    val startTimeMs: Long,
    val title: String,
    val url: String? = null,
    val imageUrl: String? = null,
)
