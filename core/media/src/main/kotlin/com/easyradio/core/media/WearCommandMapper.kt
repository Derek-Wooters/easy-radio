package com.easyradio.core.media

import com.easyradio.core.model.CuratedRadioStations
import com.easyradio.core.model.RadioStation
import com.easyradio.core.model.wear.WearCommand

private const val SKIP_FORWARD_MS = 30_000L
private const val SKIP_BACK_MS = 15_000L

/** What a [WearCommand] should do to the phone's player, decoupled from any Player implementation. */
sealed interface WearPlayerAction {
    data object Play : WearPlayerAction
    data object Pause : WearPlayerAction
    data class SeekTo(val positionMs: Long) : WearPlayerAction
    data class PlayStream(val streamUrl: String, val title: String, val subtitle: String) : WearPlayerAction

    /** The command referenced a station id that isn't in the curated list; nothing to do. */
    data object Ignore : WearPlayerAction
}

/**
 * Pure decision logic for `PhoneWearListenerService`: given a command from the watch and the
 * player's current position/duration, decide what should happen. Kept separate from the actual
 * `androidx.media3.session.MediaController` calls so it's unit-testable without a real player.
 */
object WearCommandMapper {

    fun map(
        command: WearCommand,
        currentPositionMs: Long,
        durationMs: Long,
        stations: List<RadioStation> = CuratedRadioStations.ALL,
    ): WearPlayerAction = when (command) {
        WearCommand.Play -> WearPlayerAction.Play
        WearCommand.Pause -> WearPlayerAction.Pause
        WearCommand.SkipForward ->
            WearPlayerAction.SeekTo(SeekMath.clampSeek(currentPositionMs, SKIP_FORWARD_MS, durationMs))
        WearCommand.SkipBack ->
            WearPlayerAction.SeekTo(SeekMath.clampSeek(currentPositionMs, -SKIP_BACK_MS, durationMs))
        is WearCommand.PlayStation -> {
            val station = stations.firstOrNull { it.id == command.stationId }
            if (station != null) {
                WearPlayerAction.PlayStream(station.streamUrl, station.name, station.tagline)
            } else {
                WearPlayerAction.Ignore
            }
        }
    }
}
