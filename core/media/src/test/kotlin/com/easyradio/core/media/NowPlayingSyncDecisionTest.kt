package com.easyradio.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NowPlayingSyncDecisionTest {

    @Test
    fun `no mediaId means nothing to sync`() {
        val result = NowPlayingSyncDecision.resolve(mediaId = null, lastSyncedMediaId = null)

        assertThat(result).isNull()
    }

    @Test
    fun `a mediaId that isn't a station or episode is ignored`() {
        val result = NowPlayingSyncDecision.resolve(mediaId = MediaBrowseTree.PODCASTS_ID, lastSyncedMediaId = null)

        assertThat(result).isNull()
    }

    @Test
    fun `an already-adopted episode mediaId is treated as an echo, not a new sync`() {
        val mediaId = MediaBrowseTree.EPISODE_PREFIX + "ep1"

        val result = NowPlayingSyncDecision.resolve(mediaId = mediaId, lastSyncedMediaId = mediaId)

        assertThat(result).isNull()
    }

    @Test
    fun `a new episode mediaId resolves to an Episode target`() {
        val result = NowPlayingSyncDecision.resolve(
            mediaId = MediaBrowseTree.EPISODE_PREFIX + "ep2",
            lastSyncedMediaId = MediaBrowseTree.EPISODE_PREFIX + "ep1",
        )

        assertThat(result).isEqualTo(NowPlayingSyncTarget.Episode("ep2"))
    }

    @Test
    fun `an already-adopted station mediaId is treated as an echo, not a new sync`() {
        val mediaId = MediaBrowseTree.STATION_PREFIX + "kfan"

        val result = NowPlayingSyncDecision.resolve(mediaId = mediaId, lastSyncedMediaId = mediaId)

        assertThat(result).isNull()
    }

    @Test
    fun `a new station mediaId resolves to a Station target -- the bug this was built to fix`() {
        val result = NowPlayingSyncDecision.resolve(
            mediaId = MediaBrowseTree.STATION_PREFIX + "kfan",
            lastSyncedMediaId = null,
        )

        assertThat(result).isEqualTo(NowPlayingSyncTarget.Station("kfan"))
    }
}
