package com.easyradio.core.model

/** How a [SmartPlaylist] filters by an episode's listen progress. */
enum class SmartPlaylistStatus { ANY, UNPLAYED, IN_PROGRESS, FINISHED }

/**
 * A user-defined filter over every episode across subscribed podcasts, distinct from the app's
 * fixed smart lists (Starred/In Progress/New Episodes): the user names it and picks the rules
 * themselves (which podcasts, listen status, downloaded-only, starred-only).
 */
data class SmartPlaylist(
    val id: String,
    val name: String,
    /** Which subscribed podcasts to include; empty means every podcast. */
    val podcastIds: Set<String> = emptySet(),
    val status: SmartPlaylistStatus = SmartPlaylistStatus.ANY,
    val downloadedOnly: Boolean = false,
    val starredOnly: Boolean = false,
    val createdAtEpochMillis: Long,
) {
    init {
        require(id.isNotBlank()) { "SmartPlaylist.id must not be blank" }
        require(name.isNotBlank()) { "SmartPlaylist.name must not be blank" }
    }
}
