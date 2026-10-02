package com.easyradio.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.easyradio.app.ui.theme.LocalEasyRadioColors
import com.easyradio.core.database.PodcastRepository
import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Downloaded episodes grouped by podcast, sorted by how much space each show is using (biggest
 * first) -- the point of this screen is freeing up storage, so whichever show is actually eating
 * the most space should be the first thing a user sees, not just an alphabetical/chronological
 * dump of every episode. Each group gets its own "Delete all" alongside the existing per-episode
 * remove action, for the common case of just wanting a whole show's backlog gone rather than
 * removing episodes one at a time.
 *
 * Matches the "Downloads" screen from docs/designer-brief.md's Priority 1 list, extended with
 * per-podcast grouping based on real usage feedback after the original flat-list version shipped.
 */
@Composable
fun DownloadsScreen(
    repository: PodcastRepository,
    onBack: () -> Unit,
) {
    val downloaded by remember(repository) { repository.downloadedEpisodes() }.collectAsState(initial = emptyList())
    val podcasts by remember(repository) { repository.subscribedPodcasts() }.collectAsState(initial = emptyList())
    val podcastsById = remember(podcasts) { podcasts.associateBy { it.id } }
    val scope = rememberCoroutineScope()

    var episodeSizes by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    LaunchedEffect(downloaded) {
        episodeSizes = withContext(Dispatchers.IO) {
            downloaded.associate { episode -> episode.id to (episode.localFilePath?.let { File(it).length() } ?: 0L) }
        }
    }

    val totalBytes = remember(episodeSizes) { episodeSizes.values.sum() }
    val groups = remember(downloaded, podcastsById, episodeSizes) {
        downloaded.groupBy { podcastsById[it.podcastId] }
            .map { (podcast, episodes) ->
                PodcastDownloadGroup(
                    podcast = podcast,
                    episodes = episodes,
                    totalBytes = episodes.sumOf { episodeSizes[it.id] ?: 0L },
                )
            }
            .sortedByDescending { it.totalBytes }
    }

    var pendingDeleteGroup by remember { mutableStateOf<PodcastDownloadGroup?>(null) }
    pendingDeleteGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { pendingDeleteGroup = null },
            title = { Text("Delete all downloads?") },
            text = {
                Text(
                    "This removes all ${group.episodes.size} downloaded episodes of " +
                        "${group.podcast?.title ?: "this podcast"} (${formatStorageSize(group.totalBytes)}). " +
                        "You can download them again later.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { group.episodes.forEach { repository.deleteDownload(it) } }
                    pendingDeleteGroup = null
                }) { Text("Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteGroup = null }) { Text("Cancel") }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(text = "Downloads", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        }

        Text(
            text = "${formatStorageSize(totalBytes)} used",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )

        if (downloaded.isEmpty()) {
            Text(
                text = "No downloads yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        LazyColumn {
            groups.forEach { group ->
                item(key = "header-${group.podcast?.id ?: "unknown"}") {
                    PodcastDownloadHeader(
                        group = group,
                        onDeleteAll = { pendingDeleteGroup = group },
                    )
                }
                items(group.episodes, key = { it.id }) { episode ->
                    val sizeLabel = formatStorageSize(episodeSizes[episode.id] ?: 0L)
                    ListItem(
                        headlineContent = { Text(episode.title) },
                        supportingContent = { Text(sizeLabel) },
                        trailingContent = {
                            IconButton(onClick = { scope.launch { repository.deleteDownload(episode) } }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove download")
                            }
                        },
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

private data class PodcastDownloadGroup(
    val podcast: Podcast?,
    val episodes: List<Episode>,
    val totalBytes: Long,
)

@Composable
private fun PodcastDownloadHeader(
    group: PodcastDownloadGroup,
    onDeleteAll: () -> Unit,
) {
    val tints = LocalEasyRadioColors.current.avatarTints
    val tint = tints[(group.podcast?.id ?: "unknown").hashCode().mod(tints.size)]

    ListItem(
        leadingContent = {
            Avatar(
                imageUrl = group.podcast?.artworkUrl,
                letter = group.podcast?.title?.firstOrNull()?.uppercase() ?: "?",
                tint = tint,
                cornerRadius = 8.dp,
                modifier = Modifier.size(48.dp),
            )
        },
        headlineContent = { Text(group.podcast?.title ?: "Unknown podcast") },
        supportingContent = {
            Text("${group.episodes.size} episode${if (group.episodes.size == 1) "" else "s"} · ${formatStorageSize(group.totalBytes)}")
        },
        trailingContent = {
            IconButton(onClick = onDeleteAll) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = "Delete all downloads for ${group.podcast?.title ?: "this podcast"}")
            }
        },
    )
}

internal fun formatStorageSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1) "${mb.roundToInt()} MB" else "${(bytes / 1024.0).roundToInt()} KB"
}
