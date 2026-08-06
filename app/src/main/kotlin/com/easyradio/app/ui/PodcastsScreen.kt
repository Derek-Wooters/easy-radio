package com.easyradio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Sort
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.easyradio.app.ui.theme.LocalEasyRadioColors
import com.easyradio.core.database.PodcastRepository
import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PODCAST_SEARCH_DEBOUNCE_MS = 400L

private enum class PodcastScreenState { LIBRARY, EPISODES, QUEUE }

@Composable
fun PodcastsScreen(
    repository: PodcastRepository,
    onEpisodeSelected: (Podcast, Episode) -> Unit,
) {
    var screenState by remember { mutableStateOf(PodcastScreenState.LIBRARY) }
    var selectedPodcast by remember { mutableStateOf<Podcast?>(null) }

    when (screenState) {
        PodcastScreenState.LIBRARY -> PodcastLibraryScreen(
            repository = repository,
            onPodcastSelected = { selectedPodcast = it; screenState = PodcastScreenState.EPISODES },
            onQueueClick = { screenState = PodcastScreenState.QUEUE },
        )
        PodcastScreenState.EPISODES -> selectedPodcast?.let { podcast ->
            EpisodeListScreen(
                repository = repository,
                podcast = podcast,
                onBack = { screenState = PodcastScreenState.LIBRARY },
                onEpisodeSelected = { episode -> onEpisodeSelected(podcast, episode) },
            )
        }
        PodcastScreenState.QUEUE -> QueueScreen(
            repository = repository,
            onBack = { screenState = PodcastScreenState.LIBRARY },
            onEpisodeSelected = { episode ->
                // Queue rows only carry Episode data; a full Podcast lookup by id isn't
                // wired yet, so we pass a minimal placeholder for the tagline. Playback
                // itself is unaffected since audioUrl comes from the episode.
                onEpisodeSelected(
                    Podcast(id = episode.podcastId, title = "", author = "", artworkUrl = null, feedUrl = "https://placeholder.invalid/"),
                    episode,
                )
            },
        )
    }
}

@Composable
private fun PodcastLibraryScreen(
    repository: PodcastRepository,
    onPodcastSelected: (Podcast) -> Unit,
    onQueueClick: () -> Unit,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Podcasts",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onQueueClick) {
                Icon(Icons.Filled.QueueMusic, contentDescription = "Up Next")
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search podcasts") },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )

        val subscribedIds = subscribed.map { it.id }.toSet()

        if (query.isBlank()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
            ) {
                items(subscribed, key = { it.id }) { podcast ->
                    PodcastGridTile(podcast = podcast, onClick = { onPodcastSelected(podcast) })
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
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
private fun PodcastGridTile(podcast: Podcast, onClick: () -> Unit) {
    val tints = LocalEasyRadioColors.current.avatarTints
    val tint = tints[podcast.id.hashCode().mod(tints.size)]

    Column(
        modifier = Modifier.padding(6.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.container),
        ) {
            Text(
                text = podcast.title.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = tint.content,
            )
        }
        Text(
            text = podcast.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp),
        )
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
    val episodesRaw by remember(podcast.id) { repository.episodesFor(podcast.id) }
        .collectAsState(initial = emptyList())
    var newestFirst by remember { mutableStateOf(true) }
    val episodes = if (newestFirst) episodesRaw else episodesRaw.asReversed()
    val scope = rememberCoroutineScope()
    var downloadingIds by remember { mutableStateOf(setOf<String>()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = podcast.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = podcast.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { newestFirst = !newestFirst }) {
                Icon(Icons.Filled.Sort, contentDescription = if (newestFirst) "Newest first" else "Oldest first")
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
                    trailingContent = {
                        Row {
                            IconButton(onClick = { scope.launch { repository.enqueue(episode) } }) {
                                Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add to queue")
                            }
                            IconButton(onClick = {
                                if (episode.localFilePath != null) {
                                    scope.launch { repository.deleteDownload(episode) }
                                } else if (episode.id !in downloadingIds) {
                                    downloadingIds = downloadingIds + episode.id
                                    scope.launch {
                                        repository.downloadEpisode(episode)
                                        downloadingIds = downloadingIds - episode.id
                                    }
                                }
                            }) {
                                Icon(
                                    if (episode.localFilePath != null) Icons.Filled.DownloadDone else Icons.Filled.Download,
                                    contentDescription = if (episode.localFilePath != null) "Downloaded" else "Download",
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable { onEpisodeSelected(episode) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun QueueScreen(
    repository: PodcastRepository,
    onBack: () -> Unit,
    onEpisodeSelected: (Episode) -> Unit,
) {
    val queue by remember(repository) { repository.queue() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(text = "Up Next", style = MaterialTheme.typography.titleLarge)
        }

        if (queue.isEmpty()) {
            Text(
                text = "Nothing queued yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        LazyColumn {
            items(queue, key = { it.id }) { episode ->
                val index = queue.indexOf(episode)
                ListItem(
                    headlineContent = { Text(episode.title) },
                    supportingContent = { Text(episode.description.take(80)) },
                    trailingContent = {
                        Row {
                            IconButton(
                                enabled = index > 0,
                                onClick = {
                                    val reordered = queue.toMutableList()
                                    reordered.removeAt(index)
                                    reordered.add(index - 1, episode)
                                    scope.launch { repository.reorderQueue(reordered.map { it.id }) }
                                },
                            ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up") }
                            IconButton(
                                enabled = index < queue.size - 1,
                                onClick = {
                                    val reordered = queue.toMutableList()
                                    reordered.removeAt(index)
                                    reordered.add(index + 1, episode)
                                    scope.launch { repository.reorderQueue(reordered.map { it.id }) }
                                },
                            ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down") }
                            IconButton(onClick = { scope.launch { repository.removeFromQueue(episode.id) } }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove from queue")
                            }
                        }
                    },
                    modifier = Modifier.clickable { onEpisodeSelected(episode) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
        }
    }
}
