package com.easyradio.core.media

import com.easyradio.core.model.wear.NowPlayingState

/**
 * Pure mapping from a player's current metadata/state to the [NowPlayingState] pushed to the
 * watch, decoupled from any Player implementation so it's unit-testable without a real player.
 * `subtitle` falls back to `artist` when the item has no explicit subtitle (e.g. live radio
 * streams that only carry an artist tag), then to an empty string.
 */
object NowPlayingStateMapper {

    fun map(
        title: String?,
        subtitle: String?,
        artist: String?,
        isPlaying: Boolean,
        isSeekable: Boolean,
    ): NowPlayingState = NowPlayingState(
        title = title ?: "",
        subtitle = subtitle ?: artist ?: "",
        isPlaying = isPlaying,
        canSkip = isSeekable,
    )
}
