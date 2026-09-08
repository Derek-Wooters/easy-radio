package com.easyradio.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.easyradio.core.database.FavoriteStationRepository
import com.easyradio.core.model.RadioStation
import com.easyradio.core.network.radiobrowser.RadioStationRepository
import com.easyradio.app.ui.theme.AvatarTint
import com.easyradio.app.ui.theme.LocalEasyRadioColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_MS = 400L
private const val ALL_CITIES = "All Cities"
private const val ALL_GENRES = "All Genres"
private val CITY_OPTIONS = listOf(ALL_CITIES, "Minneapolis", "New York", "Los Angeles", "Chicago")
private val GENRE_OPTIONS = listOf(ALL_GENRES, "News", "Sports", "Music", "Talk")

@Composable
fun RadioBrowseScreen(
    repository: RadioStationRepository,
    favoriteStationRepository: FavoriteStationRepository,
    onStationSelected: (RadioStation) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var city by remember { mutableStateOf(ALL_CITIES) }
    var genre by remember { mutableStateOf(ALL_GENRES) }
    var searchResults by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    val favoriteIds by remember(favoriteStationRepository) { favoriteStationRepository.favoriteIds() }
        .collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()

    // A free-text query takes precedence; otherwise the city/genre chips drive
    // the search. With no query and both chips at "All", show curated stations.
    val cityTerm = city.takeIf { it != ALL_CITIES }
    val genreTerm = genre.takeIf { it != ALL_GENRES }
    val term = when {
        query.isNotBlank() -> query
        cityTerm != null || genreTerm != null -> listOfNotNull(genreTerm, cityTerm).joinToString(" ")
        else -> null
    }

    LaunchedEffect(term) {
        if (term == null) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        searchResults = repository.search(term)
    }

    val stationsToShow = if (term == null) repository.curatedStations() else searchResults

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        ) {
            Text(
                text = "Live Radio",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search stations") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            FilterDropdownChip(
                selectedLabel = city,
                active = cityTerm != null,
                options = CITY_OPTIONS,
                onSelect = { city = it },
            )
            FilterDropdownChip(
                selectedLabel = genre,
                active = genreTerm != null,
                options = GENRE_OPTIONS,
                onSelect = { genre = it },
            )
        }

        if (term != null && searchResults.isEmpty()) {
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
                    isFollowed = station.id in favoriteIds,
                    onClick = { onStationSelected(station) },
                    onFollowClick = {
                        scope.launch {
                            if (station.id in favoriteIds) {
                                favoriteStationRepository.unfavorite(station.id)
                            } else {
                                favoriteStationRepository.favorite(station)
                            }
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun FilterDropdownChip(
    selectedLabel: String,
    active: Boolean,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = active,
            onClick = { expanded = true },
            label = { Text(selectedLabel) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun StationRow(
    station: RadioStation,
    isFollowed: Boolean,
    onClick: () -> Unit,
    onFollowClick: () -> Unit,
) {
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

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onClick) {
                Icon(
                    Icons.Filled.PlayCircleOutline,
                    contentDescription = "Play ${station.name}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
            }
            val followButtonModifier = Modifier.height(28.dp).width(96.dp)
            val followButtonTextStyle = MaterialTheme.typography.labelSmall
            val followButtonPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            if (isFollowed) {
                OutlinedButton(
                    onClick = onFollowClick,
                    modifier = followButtonModifier,
                    contentPadding = followButtonPadding,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Red,
                    ),
                    border = BorderStroke(1.dp, Color.Red),
                ) { Text("Unsubscribe", style = followButtonTextStyle) }
            } else {
                Button(
                    onClick = onFollowClick,
                    modifier = followButtonModifier,
                    contentPadding = followButtonPadding,
                ) { Text("Subscribe", style = followButtonTextStyle) }
            }
        }
    }
}

@Composable
private fun avatarTintFor(stationId: String): AvatarTint {
    val tints = LocalEasyRadioColors.current.avatarTints
    val index = (stationId.hashCode().mod(tints.size))
    return tints[index]
}
