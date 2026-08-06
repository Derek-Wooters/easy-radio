package com.easyradio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.easyradio.core.model.RadioStation
import com.easyradio.core.network.radiobrowser.RadioStationRepository
import com.easyradio.app.ui.theme.AvatarTint
import com.easyradio.app.ui.theme.LocalEasyRadioColors
import kotlinx.coroutines.delay

private const val SEARCH_DEBOUNCE_MS = 400L

@Composable
fun RadioBrowseScreen(
    repository: RadioStationRepository,
    onStationSelected: (RadioStation) -> Unit,
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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Live Radio",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.Search, contentDescription = "Search")
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search stations") },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )

        if (query.isNotBlank() && searchResults.isEmpty()) {
            Text(
                text = "No stations found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
            items(stationsToShow, key = { it.id }) { station ->
                StationRow(
                    station = station,
                    onClick = { onStationSelected(station) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun StationRow(station: RadioStation, onClick: () -> Unit) {
    val tint = avatarTintFor(station.id)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Avatar(
            imageUrl = station.imageUrl,
            letter = station.name.firstOrNull()?.uppercase() ?: "?",
            tint = tint,
            modifier = Modifier.size(56.dp),
        )

        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(text = station.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = station.tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Transparent),
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Play ${station.name}",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun avatarTintFor(stationId: String): AvatarTint {
    val tints = LocalEasyRadioColors.current.avatarTints
    val index = (stationId.hashCode().mod(tints.size))
    return tints[index]
}

