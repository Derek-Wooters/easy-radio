package com.easyradio.core.media

import com.easyradio.core.model.CuratedRadioStations
import com.easyradio.core.model.RadioStation
import com.easyradio.core.model.wear.WearCommand

/** What a [WearCommand] should do to the phone's player, decoupled from any Player implementation. */
sealed interface WearPlayerAction {
    data object Play : WearPlayerAction
    data object Pause : WearPlayerAction

    // Deliberately relative (Player.seekForward()/seekBack()), not an absolute position computed
    // here from a client-supplied currentPosition/duration snapshot: PhoneWearListenerService
    // connects a brand-new, short-lived MediaController per command, and that controller's
    // reported position/duration right after connecting isn't guaranteed to reflect the actual,
    // live player state. Player.seekForward()/seekBack() are resolved by the player itself
    // against its own current position and configured seek increment, so there's no snapshot to
    // go stale.
    data object SeekForward : WearPlayerAction
    data object SeekBack : WearPlayerAction

    data class PlayStream(val streamUrl: String, val title: String, val subtitle: String) : WearPlayerAction

    /** The command referenced a station id that isn't in the curated list; nothing to do. */
    data object Ignore : WearPlayerAction
}

/**
 * Pure decision logic for `PhoneWearListenerService`: given a command from the watch, decide what
 * should happen. Kept separate from the actual `androidx.media3.session.MediaController` calls so
 * it's unit-testable without a real player.
 */
object WearCommandMapper {

    fun map(
        command: WearCommand,
        stations: List<RadioStation> = CuratedRadioStations.ALL,
    ): WearPlayerAction = when (command) {
        WearCommand.Play -> WearPlayerAction.Play
        WearCommand.Pause -> WearPlayerAction.Pause
        WearCommand.SkipForward -> WearPlayerAction.SeekForward
        WearCommand.SkipBack -> WearPlayerAction.SeekBack
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
