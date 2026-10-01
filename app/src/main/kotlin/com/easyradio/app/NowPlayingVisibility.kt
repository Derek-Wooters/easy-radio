package com.easyradio.app

import com.easyradio.core.media.PlaybackUiState

/**
 * Whether the mini-player/Now Playing bar should show at all. Deliberately keyed on [uiState]
 * rather than solely on whether [hasStation]/[hasEpisode] have resolved: those only become true
 * once MainActivity's resync (a DB round-trip over the session's reported media id) completes, but
 * [uiState] is synced immediately when the session attaches. Gating visibility on the resolved
 * metadata alone left a real window -- a slow resync, or one that silently never finds the
 * episode/station -- where the session already knew something was playing, yet the mini-player
 * (its pause/play control included) rendered nothing at all, leaving the user with no way to nudge
 * it out of that state. Showing a working control the moment [uiState] says something's active,
 * even before the title/artwork round-trip finishes, means there's always a command available.
 */
internal fun hasNowPlayingContent(uiState: PlaybackUiState, hasStation: Boolean, hasEpisode: Boolean): Boolean =
    uiState != PlaybackUiState.IDLE || hasStation || hasEpisode
