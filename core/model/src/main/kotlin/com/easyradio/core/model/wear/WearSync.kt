package com.easyradio.core.model.wear

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A control instruction sent from the watch to the phone over the Wearable Data
 * Layer. The phone owns playback; the watch is a remote control (see the Phase 6
 * decision to remote-control the phone rather than play standalone).
 */
@Serializable
sealed interface WearCommand {
    @Serializable @SerialName("play") data object Play : WearCommand
    @Serializable @SerialName("pause") data object Pause : WearCommand
    @Serializable @SerialName("skip_forward") data object SkipForward : WearCommand
    @Serializable @SerialName("skip_back") data object SkipBack : WearCommand
    @Serializable @SerialName("play_station") data class PlayStation(val stationId: String) : WearCommand
}

/** Snapshot of what the phone is playing, pushed to the watch for display. */
@Serializable
data class NowPlayingState(
    val title: String,
    val subtitle: String,
    val isPlaying: Boolean,
    val canSkip: Boolean,
)

/**
 * Single serialization contract shared by the phone and watch builds so the two
 * can never disagree on the wire format. Payloads are compact JSON encoded to
 * bytes, which is what MessageClient/DataClient exchange.
 */
object WearSync {
    const val COMMAND_PATH = "/easyradio/command"
    const val STATE_PATH = "/easyradio/state"

    private val json = Json { classDiscriminator = "type" }

    fun encodeCommand(command: WearCommand): ByteArray =
        json.encodeToString(WearCommand.serializer(), command).encodeToByteArray()

    fun decodeCommand(bytes: ByteArray): WearCommand =
        json.decodeFromString(WearCommand.serializer(), bytes.decodeToString())

    fun encodeState(state: NowPlayingState): ByteArray =
        json.encodeToString(NowPlayingState.serializer(), state).encodeToByteArray()

    fun decodeState(bytes: ByteArray): NowPlayingState =
        json.decodeFromString(NowPlayingState.serializer(), bytes.decodeToString())
}
