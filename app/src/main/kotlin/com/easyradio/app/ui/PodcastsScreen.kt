package com.easyradio.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.easyradio.core.database.PodcastRepository
import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PODCAST_SEARCH_DEBOUNCE_MS = 400L

@Composable
fun PodcastsScreen(
    repository: PodcastRepository,
    onEpisodeSelected: (Podcast, Episode) -> Unit,
) {
    var selectedPodcast by remember { mutableStateOf<Podcast?>(null) }

    val podcast = selectedPodcast
    if (podcast == null) {
        PodcastLibraryScreen(
            repository = repository,
            onPodcastSelected = { selectedPodcast = it },
        )
    } else {
        EpisodeListScreen(
            repository = repository,
            podcast = podcast,
            onBack = { selectedPodcast = null },
            onEpisodeSelected = { episode -> onEpisodeSelected(podcast, episode) },
        )
    }
}

@Composable
private fun PodcastLibraryScreen(
    repository: PodcastRepository,
    onPodcastSelected: (Podcast) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Podcast>>(emptyList()) }
    val subscribed by remember(repository) { repository.subscribedPodcasts() }
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        if (query.isBlank()) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        delay(PODCAST_SEARCH_DEBOUNCE_MS)
        searchResults = repository.search(query)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Podcasts",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search podcasts") },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )

        val subscribedIds = subscribed.map { it.id }.toSet()

        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
            if (query.isBlank()) {
                items(subscribed, key = { it.id }) { podcast ->
                    PodcastRow(
                        podcast = podcast,
                        isSubscribed = true,
                        onClick = { onPodcastSelected(podcast) },
                        onActionClick = { scope.launch { repository.unsubscribe(podcast.id) } },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }
            } else {
                items(searchResults, key = { it.id }) { podcast ->
                    PodcastRow(
                        podcast = podcast,
                        isSubscribed = podcast.id in subscribedIds,
                        onClick = { onPodcastSelected(podcast) },
                        onActionClick = { scope.launch { repository.subscribe(podcast) } },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun PodcastRow(
    podcast: Podcast,
    isSubscribed: Boolean,
    onClick: () -> Unit,
    onActionClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(podcast.title) },
        supportingContent = { Text(podcast.author) },
        trailingContent = {
            if (isSubscribed) {
                IconButton(onClick = onActionClick) {
                    Icon(Icons.Filled.Delete, contentDescription = "Unsubscribe")
                }
            } else {
                Button(onClick = onActionClick) { Text("Subscribe") }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun EpisodeListScreen(
    repository: PodcastRepository,
    podcast: Podcast,
    onBack: () -> Unit,
    onEpisodeSelected: (Episode) -> Unit,
) {
    val episodes by remember(podcast.id) { repository.episodesFor(podcast.id) }
        .collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text(text = podcast.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = podcast.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (episodes.isEmpty()) {
            Text(
                text = "Loading episodes...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        LazyColumn {
            items(episodes, key = { it.id }) { episode ->
                ListItem(
                    headlineContent = { Text(episode.title) },
                    supportingContent = { Text(episode.description.take(120)) },
                    modifier = Modifier.clickable { onEpisodeSelected(episode) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
        }
    }
}
