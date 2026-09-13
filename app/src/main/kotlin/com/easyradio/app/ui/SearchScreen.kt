package com.easyradio.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.easyradio.app.ui.theme.LocalEasyRadioColors
import com.easyradio.core.database.FavoriteStationRepository
import com.easyradio.core.database.PodcastRepository
import com.easyradio.core.model.Podcast
import com.easyradio.core.model.RadioStation
import com.easyradio.core.network.radiobrowser.RadioStationRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_MS = 400L

private enum class SearchFilter(val label: String) { ALL("All"), STATIONS("Stations"), PODCASTS("Podcasts") }

private sealed interface SearchResult {
    val id: String
    val title: String
    val imageUrl: String?
    val typeLabel: String

    data class Station(val station: RadioStation) : SearchResult {
        override val id get() = station.id
        override val title get() = station.name
        override val imageUrl get() = station.imageUrl
        override val typeLabel get() = "STATION"
    }

    data class PodcastMatch(val podcast: Podcast) : SearchResult {
        override val id get() = podcast.id
        override val title get() = podcast.title
        override val imageUrl get() = podcast.artworkUrl
        override val typeLabel get() = "PODCAST"
    }
}

/** Matches docs/designs/7b-unified-search.png. */
@Composable
fun SearchScreen(
    radioRepository: RadioStationRepository,
    podcastRepository: PodcastRepository,
    favoriteStationRepository: FavoriteStationRepository,
    onStationSelected: (RadioStation) -> Unit,
    onPodcastSelected: (Podcast) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var stationResults by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var podcastResults by remember { mutableStateOf<List<Podcast>>(emptyList()) }
    var filter by remember { mutableStateOf(SearchFilter.ALL) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val favoriteStationIds by remember(favoriteStationRepository) { favoriteStationRepository.favoriteIds() }
        .collectAsState(initial = emptySet())
    val subscribedPodcasts by remember(podcastRepository) { podcastRepository.subscribedPodcasts() }
        .collectAsState(initial = emptyList())
    val subscribedPodcastIds = subscribedPodcasts.map { it.id }.toSet()

    LaunchedEffect(query) {
        if (query.isBlank()) {
            stationResults = emptyList()
            podcastResults = emptyList()
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        coroutineScope {
            val stationsJob = launch { stationResults = radioRepository.search(query) }
            val podcastsJob = launch { podcastResults = podcastRepository.search(query) }
            stationsJob.join()
            podcastsJob.join()
        }
    }

    val results: List<SearchResult> = when (filter) {
        SearchFilter.ALL -> stationResults.map { SearchResult.Station(it) } +
            podcastResults.map { SearchResult.PodcastMatch(it) }
        SearchFilter.STATIONS -> stationResults.map { SearchResult.Station(it) }
        SearchFilter.PODCASTS -> podcastResults.map { SearchResult.PodcastMatch(it) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search stations and podcasts") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        )

        SecondaryTabRow(selectedTabIndex = filter.ordinal) {
            SearchFilter.entries.forEach { tab ->
                Tab(
                    selected = filter == tab,
                    onClick = { filter = tab },
                    text = { Text(tab.label) },
                )
            }
        }

        LazyColumn {
            items(results, key = { "${it.typeLabel}:${it.id}" }) { result ->
                val tints = LocalEasyRadioColors.current.avatarTints
                val tint = tints[result.id.hashCode().mod(tints.size)]
                val onClick = {
                    when (result) {
                        is SearchResult.Station -> onStationSelected(result.station)
                        is SearchResult.PodcastMatch -> onPodcastSelected(result.podcast)
                    }
                }
                val isSubscribed = when (result) {
                    is SearchResult.Station -> result.station.id in favoriteStationIds
                    is SearchResult.PodcastMatch -> result.podcast.id in subscribedPodcastIds
                }
                val onSubscribeToggle = {
                    scope.launch {
                        when (result) {
                            is SearchResult.Station -> if (isSubscribed) {
                                favoriteStationRepository.unfavorite(result.station.id)
                            } else {
                                favoriteStationRepository.favorite(result.station)
                            }
                            is SearchResult.PodcastMatch -> if (isSubscribed) {
                                podcastRepository.unsubscribe(result.podcast.id)
                            } else {
                                podcastRepository.subscribe(result.podcast)
                            }
                        }
                    }
                    Unit
                }

                ListItem(
                    modifier = Modifier.clickable(onClick = onClick),
                    leadingContent = {
                        Avatar(
                            imageUrl = result.imageUrl,
                            letter = result.title.firstOrNull()?.uppercase() ?: "?",
                            tint = tint,
                            modifier = Modifier.size(48.dp),
                        )
                    },
                    headlineContent = { Text(result.title) },
                    supportingContent = {
                        Text(
                            text = result.typeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = onClick) {
                                Icon(
                                    Icons.Filled.PlayCircleOutline,
                                    contentDescription = "Play ${result.title}",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            val subscribeButtonModifier = Modifier.height(28.dp).width(96.dp)
                            val subscribeButtonTextStyle = MaterialTheme.typography.labelSmall
                            val subscribeButtonPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            if (isSubscribed) {
                                OutlinedButton(
                                    onClick = onSubscribeToggle,
                                    modifier = subscribeButtonModifier,
                                    contentPadding = subscribeButtonPadding,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Red,
                                    ),
                                    border = BorderStroke(1.dp, Color.Red),
                                ) { Text("Unsubscribe", style = subscribeButtonTextStyle) }
                            } else {
                                Button(
                                    onClick = onSubscribeToggle,
                                    modifier = subscribeButtonModifier,
                                    contentPadding = subscribeButtonPadding,
                                ) { Text("Subscribe", style = subscribeButtonTextStyle) }
                            }
                        }
                    },
                )
            }
        }
    }
}
