package com.easyradio.core.model.wear

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The watch and phone exchange commands and now-playing state as bytes over the
 * Wearable Data Layer. [WearSync] is the single serialization contract both
 * sides use; these tests pin the round-trip so a watch build and a phone build
 * can never silently disagree on the wire format.
 */
class WearSyncTest {

    @Test
    fun `every command round-trips through encode then decode`() {
        val commands = listOf(
            WearCommand.Play,
            WearCommand.Pause,
            WearCommand.SkipForward,
            WearCommand.SkipBack,
            WearCommand.PlayStation("kfan-100.3"),
        )

        for (command in commands) {
            val decoded = WearSync.decodeCommand(WearSync.encodeCommand(command))
            assertThat(decoded).isEqualTo(command)
        }
    }

    @Test
    fun `play-station command preserves the station id`() {
        val decoded = WearSync.decodeCommand(
            WearSync.encodeCommand(WearCommand.PlayStation("bbc-world-service")),
        )

        assertThat(decoded).isInstanceOf(WearCommand.PlayStation::class.java)
        assertThat((decoded as WearCommand.PlayStation).stationId).isEqualTo("bbc-world-service")
    }

    @Test
    fun `now-playing state round-trips every field`() {
        val state = NowPlayingState(
            title = "KFAN FM 100.3",
            subtitle = "Audio Home For Minnesota Sports",
            isPlaying = true,
            canSkip = false,
        )

        val decoded = WearSync.decodeState(WearSync.encodeState(state))

        assertThat(decoded).isEqualTo(state)
    }

    @Test
    fun `command and state paths are distinct`() {
        assertThat(WearSync.COMMAND_PATH).isNotEqualTo(WearSync.STATE_PATH)
    }
}
