package com.easyradio.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.easyradio.core.model.CuratedRadioStations
import com.easyradio.core.model.RadioStation
import com.easyradio.core.model.wear.NowPlayingState
import com.easyradio.core.model.wear.WearCommand

class MainActivity : ComponentActivity() {

    private lateinit var client: WearMediaClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        client = WearMediaClient(applicationContext)
        setContent {
            val nowPlaying by client.nowPlaying.collectAsState()
            WearApp(
                nowPlaying = nowPlaying,
                stations = CuratedRadioStations.ALL,
                onCommand = client::send,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        client.start()
    }

    override fun onPause() {
        client.stop()
        super.onPause()
    }
}

@Composable
private fun WearApp(
    nowPlaying: NowPlayingState?,
    stations: List<RadioStation>,
    onCommand: (WearCommand) -> Unit,
) {
    MaterialTheme {
        Scaffold(timeText = { TimeText() }) {
            ScalingLazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    Text(
                        text = nowPlaying?.title ?: "Easy Radio",
                        style = MaterialTheme.typography.title3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
                item {
                    Text(
                        text = nowPlaying?.subtitle ?: "Pick a station",
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
                item {
                    val isPlaying = nowPlaying?.isPlaying == true
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    ) {
                        if (nowPlaying?.canSkip == true) {
                            CompactButton(onClick = { onCommand(WearCommand.SkipBack) }) {
                                Icon(Icons.Filled.Replay, contentDescription = "Skip back")
                            }
                        }
                        CompactButton(
                            onClick = { onCommand(if (isPlaying) WearCommand.Pause else WearCommand.Play) },
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                            )
                        }
                        if (nowPlaying?.canSkip == true) {
                            CompactButton(onClick = { onCommand(WearCommand.SkipForward) }) {
                                Icon(Icons.Filled.Forward30, contentDescription = "Skip forward")
                            }
                        }
                    }
                }
                items(stations, key = { it.id }) { station ->
                    Chip(
                        label = { Text(station.name, maxLines = 1) },
                        onClick = { onCommand(WearCommand.PlayStation(station.id)) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
