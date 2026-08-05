package com.easyradio.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.easyradio.core.media.PlaybackUiState
import com.easyradio.core.model.RadioStation
import com.easyradio.core.network.radiobrowser.RadioStationRepository
import kotlinx.coroutines.delay

private const val SEARCH_DEBOUNCE_MS = 400L

@Composable
fun RadioBrowseScreen(
    repository: RadioStationRepository,
    currentStation: RadioStation?,
    playbackState: PlaybackUiState,
    onStationSelected: (RadioStation) -> Unit,
    onPauseClick: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<RadioStation>>(emptyList()) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        searchResults = repository.search(query)
    }

    val stationsToShow = if (query.isBlank()) repository.curatedStations() else searchResults

    Scaffold(
        bottomBar = {
            if (currentStation != null) {
                MiniPlayer(
                    station = currentStation,
                    playbackState = playbackState,
                    onPlayClick = { onStationSelected(currentStation) },
                    onPauseClick = onPauseClick,
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search stations") },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )

            if (query.isNotBlank() && searchResults.isEmpty()) {
                Text(
                    text = "No stations found",
                    modifier = Modifier.padding(16.dp),
                )
            }

            LazyColumn {
                items(stationsToShow, key = { it.id }) { station ->
                    ListItem(
                        headlineContent = { Text(station.name) },
                        supportingContent = { Text(station.tagline) },
                        modifier = Modifier.clickable { onStationSelected(station) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    station: RadioStation,
    playbackState: PlaybackUiState,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = station.name, style = MaterialTheme.typography.titleMedium)
        Text(text = "State: $playbackState")
        if (playbackState == PlaybackUiState.PLAYING || playbackState == PlaybackUiState.BUFFERING) {
            Button(onClick = onPauseClick) { Text("Pause") }
        } else {
            Button(onClick = onPlayClick) { Text("Play") }
        }
    }
}
