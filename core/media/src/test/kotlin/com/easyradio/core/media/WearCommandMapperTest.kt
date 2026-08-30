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
        val action = WearCommandMapper.map(WearCommand.Play, currentPositionMs = 0, durationMs = 0)

        assertThat(action).isEqualTo(WearPlayerAction.Play)
    }

    @Test
    fun `Pause maps to Pause`() {
        val action = WearCommandMapper.map(WearCommand.Pause, currentPositionMs = 0, durationMs = 0)

        assertThat(action).isEqualTo(WearPlayerAction.Pause)
    }

    @Test
    fun `SkipForward seeks 30 seconds ahead clamped to duration`() {
        val action = WearCommandMapper.map(WearCommand.SkipForward, currentPositionMs = 50_000, durationMs = 60_000)

        assertThat(action).isEqualTo(WearPlayerAction.SeekTo(60_000))
    }

    @Test
    fun `SkipBack seeks 15 seconds back clamped to zero`() {
        val action = WearCommandMapper.map(WearCommand.SkipBack, currentPositionMs = 10_000, durationMs = 60_000)

        assertThat(action).isEqualTo(WearPlayerAction.SeekTo(0))
    }

    @Test
    fun `PlayStation for a known station id maps to PlayStream with its stream details`() {
        val action = WearCommandMapper.map(
            WearCommand.PlayStation("kfan"),
            currentPositionMs = 0,
            durationMs = 0,
            stations = listOf(station),
        )

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
        val action = WearCommandMapper.map(
            WearCommand.PlayStation("unknown-station"),
            currentPositionMs = 0,
            durationMs = 0,
            stations = listOf(station),
        )

        assertThat(action).isEqualTo(WearPlayerAction.Ignore)
    }
}
