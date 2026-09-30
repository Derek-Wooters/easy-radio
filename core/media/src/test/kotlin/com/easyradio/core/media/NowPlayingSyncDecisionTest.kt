package com.easyradio.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NowPlayingSyncDecisionTest {

    @Test
    fun `no mediaId means nothing to sync`() {
        val result = NowPlayingSyncDecision.episodeIdToSync(mediaId = null, lastSyncedMediaId = null)

        assertThat(result).isNull()
    }

    @Test
    fun `a non-episode mediaId is ignored`() {
        val result = NowPlayingSyncDecision.episodeIdToSync(
            mediaId = MediaBrowseTree.STATION_PREFIX + "kfan",
            lastSyncedMediaId = null,
        )

        assertThat(result).isNull()
    }

    @Test
    fun `an already-adopted mediaId is treated as an echo, not a new sync`() {
        val mediaId = MediaBrowseTree.EPISODE_PREFIX + "ep1"

        val result = NowPlayingSyncDecision.episodeIdToSync(mediaId = mediaId, lastSyncedMediaId = mediaId)

        assertThat(result).isNull()
    }

    @Test
    fun `a new episode mediaId resolves to its episode id`() {
        val result = NowPlayingSyncDecision.episodeIdToSync(
            mediaId = MediaBrowseTree.EPISODE_PREFIX + "ep2",
            lastSyncedMediaId = MediaBrowseTree.EPISODE_PREFIX + "ep1",
        )

        assertThat(result).isEqualTo("ep2")
    }
}
