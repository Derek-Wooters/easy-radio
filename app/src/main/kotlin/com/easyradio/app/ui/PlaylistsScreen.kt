package com.easyradio.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.easyradio.app.ui.theme.LocalEasyRadioColors
import com.easyradio.core.database.FavoriteStationRepository
import com.easyradio.core.database.PodcastRepository
import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast
import com.easyradio.core.model.RadioStation
import com.easyradio.core.model.SmartPlaylist
import com.easyradio.core.model.SmartPlaylistStatus
import kotlinx.coroutines.launch

/**
 * Favorites (matches docs/designs/7d-favorites.png) plus two read-only smart lists computed from
 * existing episode state, the way Pocket Casts' built-in (non-custom-rule) smart playlists work:
 * "In Progress" surfaces episodes with a saved position partway through, and "New Episodes"
 * surfaces episodes never started, both across every subscribed podcast rather than one at a time.
 */
@Composable
fun PlaylistsScreen(
    repository: FavoriteStationRepository,
    podcastRepository: PodcastRepository,
    onStationSelected: (RadioStation) -> Unit,
    onEpisodeSelected: (Podcast, Episode) -> Unit,
) {
    val favorites by remember(repository) { repository.favorites() }.collectAsState(initial = emptyList())
    val podcasts by remember(podcastRepository) { podcastRepository.subscribedPodcasts() }.collectAsState(initial = emptyList())
    val episodes by remember(podcastRepository) { podcastRepository.allEpisodes() }.collectAsState(initial = emptyList())
    val starred by remember(podcastRepository) { podcastRepository.starredEpisodes() }.collectAsState(initial = emptyList())
    val smartPlaylists by remember(podcastRepository) { podcastRepository.smartPlaylists() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showCreateSmartPlaylistDialog by remember { mutableStateOf(false) }

    val podcastsById = remember(podcasts) { podcasts.associateBy { it.id } }
    val inProgress = remember(episodes) {
        episodes.filter { episodeListenState(it) == EpisodeListenState.RESUME }
            .sortedByDescending { it.publishedAtEpochMillis ?: 0L }
    }
    val newEpisodes = remember(episodes) {
        episodes.filter { episodeListenState(it) == EpisodeListenState.LISTEN }
            .sortedByDescending { it.publishedAtEpochMillis ?: 0L }
            .take(SMART_LIST_LIMIT)
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "Favorites",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        if (favorites.isEmpty()) {
            item {
                Text(
                    text = "Star a station from Live Radio to add it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        items(favorites, key = { "fav-${it.id}" }) { station ->
            val tints = LocalEasyRadioColors.current.avatarTints
            val tint = tints[station.id.hashCode().mod(tints.size)]

            ListItem(
                leadingContent = {
                    Avatar(
                        imageUrl = station.imageUrl,
                        letter = station.name.firstOrNull()?.uppercase() ?: "?",
                        tint = tint,
                        modifier = Modifier.size(48.dp),
                    )
                },
                headlineContent = { Text(station.name) },
                supportingContent = { Text(station.tagline) },
                trailingContent = {
                    Row {
                        IconButton(onClick = { scope.launch { repository.unfavorite(station.id) } }) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "Remove from favorites",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { onStationSelected(station) }) {
                            Icon(
                                Icons.Filled.PlayCircleOutline,
                                contentDescription = "Play ${station.name}",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                },
            )
        }

        item {
            Text(
                text = "Starred",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp),
            )
        }
        if (starred.isEmpty()) {
            item {
                Text(
                    text = "Star an episode from any podcast to pin it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        items(starred, key = { "starred-${it.id}" }) { episode ->
            SmartEpisodeRow(episode, podcastsById[episode.podcastId]) { podcast ->
                onEpisodeSelected(podcast, episode)
            }
        }

        item {
            Text(
                text = "In Progress",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp),
            )
        }
        if (inProgress.isEmpty()) {
            item {
                Text(
                    text = "Episodes you're partway through, across all your podcasts, show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        items(inProgress, key = { "progress-${it.id}" }) { episode ->
            SmartEpisodeRow(episode, podcastsById[episode.podcastId]) { podcast ->
                onEpisodeSelected(podcast, episode)
            }
        }

        item {
            Text(
                text = "New Episodes",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp),
            )
        }
        if (newEpisodes.isEmpty()) {
            item {
                Text(
                    text = "Unplayed episodes from your subscriptions show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        items(newEpisodes, key = { "new-${it.id}" }) { episode ->
            SmartEpisodeRow(episode, podcastsById[episode.podcastId]) { podcast ->
                onEpisodeSelected(podcast, episode)
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 24.dp, bottom = 8.dp),
            ) {
                Text(text = "My Smart Playlists", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = { showCreateSmartPlaylistDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Create smart playlist")
                }
            }
        }
        if (smartPlaylists.isEmpty()) {
            item {
                Text(
                    text = "Create a custom smart playlist to filter episodes by podcast, status, downloaded, or starred.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        for (playlist in smartPlaylists) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                ) {
                    Text(text = playlist.name, style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { scope.launch { podcastRepository.deleteSmartPlaylist(playlist.id) } }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${playlist.name}")
                    }
                }
            }
            val matching = episodes.filter { matchesSmartPlaylist(it, playlist) }
                .sortedByDescending { it.publishedAtEpochMillis ?: 0L }
            if (matching.isEmpty()) {
                item {
                    Text(
                        text = "No episodes match this smart playlist yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            items(matching, key = { "smart-${playlist.id}-${it.id}" }) { episode ->
                SmartEpisodeRow(episode, podcastsById[episode.podcastId]) { podcast ->
                    onEpisodeSelected(podcast, episode)
                }
            }
        }
    }

    if (showCreateSmartPlaylistDialog) {
        CreateSmartPlaylistDialog(
            podcasts = podcasts,
            onCreate = { name, podcastIds, status, downloadedOnly, starredOnly ->
                scope.launch {
                    podcastRepository.createSmartPlaylist(name, podcastIds, status, downloadedOnly, starredOnly)
                }
                showCreateSmartPlaylistDialog = false
            },
            onDismiss = { showCreateSmartPlaylistDialog = false },
        )
    }
}

/** Pure matching logic for a [SmartPlaylist]'s rules against one episode -- see [episodeListenState] for the status mapping. */
internal fun matchesSmartPlaylist(episode: Episode, playlist: SmartPlaylist): Boolean {
    if (playlist.podcastIds.isNotEmpty() && episode.podcastId !in playlist.podcastIds) return false
    if (playlist.starredOnly && !episode.isStarred) return false
    if (playlist.downloadedOnly && episode.localFilePath == null) return false
    return when (playlist.status) {
        SmartPlaylistStatus.ANY -> true
        SmartPlaylistStatus.UNPLAYED -> episodeListenState(episode) == EpisodeListenState.LISTEN
        SmartPlaylistStatus.IN_PROGRESS -> episodeListenState(episode) == EpisodeListenState.RESUME
        SmartPlaylistStatus.FINISHED -> episodeListenState(episode) == EpisodeListenState.REPLAY
    }
}

@Composable
private fun CreateSmartPlaylistDialog(
    podcasts: List<Podcast>,
    onCreate: (name: String, podcastIds: Set<String>, status: SmartPlaylistStatus, downloadedOnly: Boolean, starredOnly: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedPodcastIds by remember { mutableStateOf(emptySet<String>()) }
    var status by remember { mutableStateOf(SmartPlaylistStatus.ANY) }
    var downloadedOnly by remember { mutableStateOf(false) }
    var starredOnly by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New smart playlist") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("smart-playlist-name"),
                )

                Text(
                    text = "Podcasts (none selected = all)",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    for (podcast in podcasts) {
                        FilterChip(
                            selected = podcast.id in selectedPodcastIds,
                            onClick = {
                                selectedPodcastIds = if (podcast.id in selectedPodcastIds) {
                                    selectedPodcastIds - podcast.id
                                } else {
                                    selectedPodcastIds + podcast.id
                                }
                            },
                            label = { Text(podcast.title) },
                        )
                    }
                }

                Text(
                    text = "Status",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilterChip(selected = status == SmartPlaylistStatus.ANY, onClick = { status = SmartPlaylistStatus.ANY }, label = { Text("Any") })
                    FilterChip(selected = status == SmartPlaylistStatus.UNPLAYED, onClick = { status = SmartPlaylistStatus.UNPLAYED }, label = { Text("Unplayed") })
                    FilterChip(selected = status == SmartPlaylistStatus.IN_PROGRESS, onClick = { status = SmartPlaylistStatus.IN_PROGRESS }, label = { Text("In Progress") })
                    FilterChip(selected = status == SmartPlaylistStatus.FINISHED, onClick = { status = SmartPlaylistStatus.FINISHED }, label = { Text("Finished") })
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Checkbox(checked = downloadedOnly, onCheckedChange = { downloadedOnly = it })
                    Text("Downloaded only")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = starredOnly, onCheckedChange = { starredOnly = it })
                    Text("Starred only")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim(), selectedPodcastIds, status, downloadedOnly, starredOnly) },
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private const val SMART_LIST_LIMIT = 20

@Composable
private fun SmartEpisodeRow(episode: Episode, podcast: Podcast?, onPlay: (Podcast) -> Unit) {
    val tints = LocalEasyRadioColors.current.avatarTints
    val tint = tints[episode.podcastId.hashCode().mod(tints.size)]

    ListItem(
        leadingContent = {
            Avatar(
                imageUrl = podcast?.artworkUrl,
                letter = episode.title.firstOrNull()?.uppercase() ?: "?",
                tint = tint,
                modifier = Modifier.size(48.dp),
            )
        },
        headlineContent = { Text(episode.title, maxLines = 1) },
        supportingContent = { Text(podcast?.title.orEmpty(), maxLines = 1) },
        trailingContent = {
            IconButton(
                enabled = podcast != null,
                onClick = { podcast?.let(onPlay) },
            ) {
                Icon(
                    Icons.Filled.PlayCircleOutline,
                    contentDescription = "Play ${episode.title}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        },
    )
}
