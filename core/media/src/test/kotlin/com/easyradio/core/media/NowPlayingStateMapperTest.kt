package com.easyradio.core.media

import com.easyradio.core.model.wear.NowPlayingState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NowPlayingStateMapperTest {

    @Test
    fun `uses title and subtitle when both are present`() {
        val state = NowPlayingStateMapper.map(
            title = "Episode One",
            subtitle = "Planet Money",
            artist = "Ignored",
            isPlaying = true,
            isSeekable = true,
        )

        assertThat(state).isEqualTo(NowPlayingState("Episode One", "Planet Money", isPlaying = true, canSkip = true))
    }

    @Test
    fun `subtitle falls back to artist when subtitle is null`() {
        val state = NowPlayingStateMapper.map(
            title = "KFAN FM 100.3",
            subtitle = null,
            artist = "Audio Home For Minnesota Sports",
            isPlaying = true,
            isSeekable = false,
        )

        assertThat(state.subtitle).isEqualTo("Audio Home For Minnesota Sports")
    }

    @Test
    fun `title and subtitle fall back to empty strings when both metadata fields are null`() {
        val state = NowPlayingStateMapper.map(
            title = null,
            subtitle = null,
            artist = null,
            isPlaying = false,
            isSeekable = false,
        )

        assertThat(state).isEqualTo(NowPlayingState("", "", isPlaying = false, canSkip = false))
    }
}
