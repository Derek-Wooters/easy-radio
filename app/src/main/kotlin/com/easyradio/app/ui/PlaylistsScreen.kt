package com.easyradio.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.easyradio.app.ui.theme.LocalEasyRadioColors
import com.easyradio.core.database.FavoriteStationRepository
import com.easyradio.core.database.PodcastRepository
import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast
import com.easyradio.core.model.RadioStation
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
    val scope = rememberCoroutineScope()

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
    }
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
