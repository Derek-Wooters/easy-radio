package com.easyradio.core.media

/** What [NowPlayingSyncDecision.resolve] found MainActivity should resync to. */
sealed interface NowPlayingSyncTarget {
    data class Episode(val episodeId: String) : NowPlayingSyncTarget
    data class Station(val stationId: String) : NowPlayingSyncTarget

    /**
     * The session genuinely has nothing loaded anymore -- most commonly a fresh service instance
     * recreated after the app was backgrounded and the old one was torn down (onTaskRemoved while
     * paused, or an explicit ACTION_STOP). MainActivity should clear whatever it had adopted
     * before. Confirmed as a real bug report's root cause: without this, the screen kept showing
     * stale episode/station info with a Play button that could never do anything, since there was
     * no media item left on the new, empty player for a bare transportControls.play() to resume.
     */
    data object Nothing : NowPlayingSyncTarget
}

/**
 * Decides whether a media id reported back by the session (e.g. via
 * MediaControllerCompat.Callback.onMetadataChanged/onSessionReady) should make MainActivity
 * resync its now-playing UI state. Two real cases need this: the service auto-advancing to the
 * next queued episode on its own, with no call into MainActivity, and -- the bug this was
 * extended for -- MainActivity itself being recreated (e.g. the user backed out of the app, or
 * its process was reclaimed) while the service keeps a *station* playing independently. Before
 * this only recognized episode media ids, so reconnecting to an already-playing station left
 * MainActivity's currentStation null and the mini-player/Now Playing bar missing entirely, even
 * though audio was genuinely still playing.
 *
 * Kept pure and separate from MainActivity so the ways this must stay a no-op (nothing to sync,
 * an echo of a media id MainActivity already adopted itself, or a media id that isn't a station
 * or episode at all) are each a directly testable branch.
 */
object NowPlayingSyncDecision {

    /**
     * Returns what MainActivity should resync to, or null if [mediaId] doesn't call for a
     * resync: it's already what [lastSyncedMediaId] shows as adopted (including both being null,
     * i.e. nothing was adopted and there's still nothing to sync), or it isn't a station or
     * episode. A [mediaId] that's null while [lastSyncedMediaId] isn't resolves to
     * [NowPlayingSyncTarget.Nothing], not null -- that's a genuine change (something was playing,
     * now nothing is), as opposed to null meaning no actionable change at all.
     */
    fun resolve(mediaId: String?, lastSyncedMediaId: String?): NowPlayingSyncTarget? {
        if (mediaId == lastSyncedMediaId) return null
        if (mediaId == null) return NowPlayingSyncTarget.Nothing
        return when {
            mediaId.startsWith(MediaBrowseTree.EPISODE_PREFIX) ->
                NowPlayingSyncTarget.Episode(mediaId.removePrefix(MediaBrowseTree.EPISODE_PREFIX))
            mediaId.startsWith(MediaBrowseTree.STATION_PREFIX) ->
                NowPlayingSyncTarget.Station(mediaId.removePrefix(MediaBrowseTree.STATION_PREFIX))
            else -> null
        }
    }
}
