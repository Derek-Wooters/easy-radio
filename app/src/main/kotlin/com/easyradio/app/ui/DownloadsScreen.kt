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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import com.easyradio.app.ui.theme.LocalEasyRadioColors
import com.easyradio.core.database.PodcastRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Matches the "Downloads" screen from docs/designer-brief.md's Priority 1 list. */
@Composable
fun DownloadsScreen(
    repository: PodcastRepository,
    onBack: () -> Unit,
) {
    val downloaded by remember(repository) { repository.downloadedEpisodes() }.collectAsState(initial = emptyList())
    val podcasts by remember(repository) { repository.subscribedPodcasts() }.collectAsState(initial = emptyList())
    val podcastsById = remember(podcasts) { podcasts.associateBy { it.id } }
    val scope = rememberCoroutineScope()

    var totalBytes by remember { mutableStateOf(0L) }
    LaunchedEffect(downloaded) {
        totalBytes = withContext(Dispatchers.IO) {
            downloaded.sumOf { episode -> episode.localFilePath?.let { File(it).length() } ?: 0L }
        }
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
            items(downloaded, key = { it.id }) { episode ->
                val podcast = podcastsById[episode.podcastId]
                val sizeLabel = episode.localFilePath?.let { formatStorageSize(File(it).length()) }.orEmpty()
                val tints = LocalEasyRadioColors.current.avatarTints
                val tint = tints[episode.podcastId.hashCode().mod(tints.size)]

                ListItem(
                    leadingContent = {
                        Avatar(
                            imageUrl = podcast?.artworkUrl,
                            letter = episode.title.firstOrNull()?.uppercase() ?: "?",
                            tint = tint,
                            cornerRadius = 8.dp,
                            modifier = Modifier.size(48.dp),
                        )
                    },
                    headlineContent = { Text(episode.title) },
                    supportingContent = {
                        Text(listOfNotNull(podcast?.title, sizeLabel).joinToString(" · "))
                    },
                    trailingContent = {
                        IconButton(onClick = { scope.launch { repository.deleteDownload(episode) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove download")
                        }
                    },
                )
            }
        }
    }
}

private fun formatStorageSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1) "${mb.roundToInt()} MB" else "${(bytes / 1024.0).roundToInt()} KB"
}
