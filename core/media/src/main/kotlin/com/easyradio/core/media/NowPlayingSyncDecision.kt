package com.easyradio.core.media

/**
 * Decides whether a media id reported back by the session (e.g. via
 * MediaControllerCompat.Callback.onMetadataChanged) should make MainActivity resync its
 * now-playing UI state -- the only real case today is the service auto-advancing to the next
 * queued episode on its own, with no call into MainActivity. Kept pure and separate from
 * MainActivity so the two ways this must stay a no-op (nothing to sync, or an echo of a media id
 * MainActivity already adopted itself) are each a directly testable branch, not something only
 * caught by watching for a redundant resync on a real device.
 */
object NowPlayingSyncDecision {

    /**
     * Returns the episode id MainActivity should resync to, or null if [mediaId] doesn't call
     * for a resync: it's absent, isn't an episode, or is already what [lastSyncedMediaId] shows
     * as adopted.
     */
    fun episodeIdToSync(mediaId: String?, lastSyncedMediaId: String?): String? {
        if (mediaId == null || mediaId == lastSyncedMediaId || !mediaId.startsWith(MediaBrowseTree.EPISODE_PREFIX)) {
            return null
        }
        return mediaId.removePrefix(MediaBrowseTree.EPISODE_PREFIX)
    }
}
