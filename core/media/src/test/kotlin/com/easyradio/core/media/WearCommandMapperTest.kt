package com.easyradio.core.media

import com.easyradio.core.model.RadioStation
import com.easyradio.core.model.wear.WearCommand
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WearCommandMapperTest {

    private val station = RadioStation(
        id = "kfan",
        name = "KFAN FM 100.3",
        streamUrl = "https://example.com/kfan.mp3",
        tagline = "Audio Home For Minnesota Sports",
    )

    @Test
    fun `Play maps to Play`() {
        val action = WearCommandMapper.map(WearCommand.Play)

        assertThat(action).isEqualTo(WearPlayerAction.Play)
    }

    @Test
    fun `Pause maps to Pause`() {
        val action = WearCommandMapper.map(WearCommand.Pause)

        assertThat(action).isEqualTo(WearPlayerAction.Pause)
    }

    @Test
    fun `SkipForward maps to SeekForward`() {
        // Deliberately NOT a client-computed absolute position: PhoneWearListenerService connects
        // a brand-new, throwaway MediaController per command, whose currentPosition/duration can
        // be stale/default right after connecting -- an absolute seekTo() computed from that
        // snapshot was the original bug (skip taps did nothing, or jumped to the wrong spot).
        // SeekForward instead maps to Player.seekForward(), which the actual player resolves
        // against its own live position using its already-configured seek increment.
        val action = WearCommandMapper.map(WearCommand.SkipForward)

        assertThat(action).isEqualTo(WearPlayerAction.SeekForward)
    }

    @Test
    fun `SkipBack maps to SeekBack`() {
        val action = WearCommandMapper.map(WearCommand.SkipBack)

        assertThat(action).isEqualTo(WearPlayerAction.SeekBack)
    }

    @Test
    fun `PlayStation for a known station id maps to PlayStream with its stream details`() {
        val action = WearCommandMapper.map(WearCommand.PlayStation("kfan"), stations = listOf(station))

        assertThat(action).isEqualTo(
            WearPlayerAction.PlayStream(
                streamUrl = "https://example.com/kfan.mp3",
                title = "KFAN FM 100.3",
                subtitle = "Audio Home For Minnesota Sports",
            ),
        )
    }

    @Test
    fun `PlayStation for an unknown station id maps to Ignore`() {
        val action = WearCommandMapper.map(WearCommand.PlayStation("unknown-station"), stations = listOf(station))

        assertThat(action).isEqualTo(WearPlayerAction.Ignore)
    }
}
