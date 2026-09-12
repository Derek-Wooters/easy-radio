package com.easyradio.app.ui

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import com.easyradio.core.database.PodcastRepository
import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PODCAST_SEARCH_DEBOUNCE_MS = 400L

private enum class PodcastScreenState { LIBRARY, EPISODES, QUEUE, DOWNLOADS }
private enum class PodcastDetailTab(val label: String) {
    NOW_PLAYING("Now Playing"),
    EPISODES("Episodes"),
    ABOUT("About"),
}

@Composable
fun PodcastsScreen(
    repository: PodcastRepository,
    onEpisodeSelected: (Podcast, Episode) -> Unit,
    nowPlayingEpisode: Episode? = null,
    initialPodcast: Podcast? = null,
    onInitialPodcastConsumed: () -> Unit = {},
) {
    var screenState by remember { mutableStateOf(PodcastScreenState.LIBRARY) }
    var selectedPodcast by remember { mutableStateOf<Podcast?>(null) }

    LaunchedEffect(initialPodcast) {
        initialPodcast?.let {
            selectedPodcast = it
            screenState = PodcastScreenState.EPISODES
            onInitialPodcastConsumed()
        }
    }

    when (screenState) {
        PodcastScreenState.LIBRARY -> PodcastLibraryScreen(
            repository = repository,
            onPodcastSelected = { selectedPodcast = it; screenState = PodcastScreenState.EPISODES },
            onQueueClick = { screenState = PodcastScreenState.QUEUE },
            onDownloadsClick = { screenState = PodcastScreenState.DOWNLOADS },
        )
        PodcastScreenState.EPISODES -> selectedPodcast?.let { podcast ->
            EpisodeListScreen(
                repository = repository,
                podcast = podcast,
                onBack = { screenState = PodcastScreenState.LIBRARY },
                onEpisodeSelected = { episode -> onEpisodeSelected(podcast, episode) },
                nowPlayingEpisode = nowPlayingEpisode,
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
        PodcastScreenState.DOWNLOADS -> DownloadsScreen(
            repository = repository,
            onBack = { screenState = PodcastScreenState.LIBRARY },
        )
    }
}

@Composable
private fun PodcastLibraryScreen(
    repository: PodcastRepository,
    onPodcastSelected: (Podcast) -> Unit,
    onQueueClick: () -> Unit,
    onDownloadsClick: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Podcast>>(emptyList()) }
    val subscribed by remember(repository) { repository.subscribedPodcasts() }
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(query) {
        if (query.isBlank()) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        delay(PODCAST_SEARCH_DEBOUNCE_MS)
        searchResults = repository.search(query)
    }

    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        ) {
            Text(
                text = "Podcasts",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Up Next") },
                        onClick = {
                            menuExpanded = false
                            onQueueClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Downloads") },
                        onClick = {
                            menuExpanded = false
                            onDownloadsClick()
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search podcasts") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )

        val subscribedIds = subscribed.map { it.id }.toSet()

        if (query.isBlank()) {
            if (subscribed.isEmpty()) {
                Text(
                    text = "No podcasts yet. Search above to find shows to follow.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
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
                    val isSubscribed = podcast.id in subscribedIds
                    PodcastRow(
                        podcast = podcast,
                        isSubscribed = isSubscribed,
                        onClick = { onPodcastSelected(podcast) },
                        onActionClick = {
                            scope.launch {
                                if (isSubscribed) repository.unsubscribe(podcast.id) else repository.subscribe(podcast)
                            }
                        },
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
        Avatar(
            imageUrl = podcast.artworkUrl,
            letter = podcast.title.firstOrNull()?.uppercase() ?: "?",
            tint = tint,
            cornerRadius = 12.dp,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
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
    val tints = LocalEasyRadioColors.current.avatarTints
    val tint = tints[podcast.id.hashCode().mod(tints.size)]

    ListItem(
        leadingContent = {
            Avatar(
                imageUrl = podcast.artworkUrl,
                letter = podcast.title.firstOrNull()?.uppercase() ?: "?",
                tint = tint,
                cornerRadius = 12.dp,
                modifier = Modifier.size(48.dp),
            )
        },
        headlineContent = { Text(podcast.title) },
        supportingContent = { Text(podcast.author) },
        trailingContent = {
            if (isSubscribed) {
                OutlinedButton(
                    onClick = onActionClick,
                    modifier = Modifier.width(130.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Red,
                    ),
                    border = BorderStroke(1.dp, Color.Red),
                ) { Text("Unsubscribe") }
            } else {
                Button(onClick = onActionClick, modifier = Modifier.width(130.dp)) { Text("Subscribe") }
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
    nowPlayingEpisode: Episode? = null,
) {
    val episodesRaw by remember(podcast.id) { repository.episodesFor(podcast.id) }
        .collectAsState(initial = emptyList())
    LaunchedEffect(podcast.id) { repository.ensureEpisodesLoaded(podcast) }
    var newestFirst by remember { mutableStateOf(true) }
    val episodes = if (newestFirst) episodesRaw else episodesRaw.asReversed()
    val scope = rememberCoroutineScope()
    var downloadingIds by remember { mutableStateOf(setOf<String>()) }
    var selectedTab by remember { mutableStateOf(PodcastDetailTab.EPISODES) }

    val episodeListState = rememberLazyListState()
    var hasMoreEpisodes by remember(podcast.id) { mutableStateOf(true) }
    var loadingMoreEpisodes by remember(podcast.id) { mutableStateOf(false) }
    // loadingMoreEpisodes is deliberately NOT read here: LaunchedEffect below restarts whenever
    // this key's value changes, so if the loading flag were part of it, setting it to true at the
    // start of a load would flip the key and cancel that same in-flight load before it finished,
    // leaving loadingMoreEpisodes stuck true and pagination permanently stalled.
    //
    // episodesRaw (not the locally-derived `episodes`) is read here deliberately: this
    // derivedStateOf's calculation lambda is created once (remember has no keys) and closures
    // over a plain local `val` capture its value at that first creation, which for `episodes`
    // was the empty placeholder list from before the Flow's first real emission -- so
    // episodes.size would silently stay 0 forever. episodesRaw is a delegated State read, so
    // referencing it here re-reads its live value on every evaluation instead. Its size is
    // identical to episodes.size regardless of sort order (asReversed() doesn't change count).
    val shouldLoadMoreEpisodes by remember {
        derivedStateOf {
            val lastVisible = episodeListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMoreEpisodes && episodesRaw.isNotEmpty() && lastVisible >= episodesRaw.size - 5
        }
    }
    LaunchedEffect(shouldLoadMoreEpisodes) {
        if (shouldLoadMoreEpisodes && !loadingMoreEpisodes) {
            loadingMoreEpisodes = true
            try {
                hasMoreEpisodes = repository.loadMoreEpisodes(podcast)
            } finally {
                loadingMoreEpisodes = false
            }
        }
    }

    val subscribed by remember(repository) { repository.subscribedPodcasts() }
        .collectAsState(initial = emptyList())
    val subscribedEntry = subscribed.find { it.id == podcast.id }
    val isSubscribed = subscribedEntry != null
    val isPreset = subscribedEntry?.isPreset ?: podcast.isPreset

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Avatar(
                imageUrl = podcast.artworkUrl,
                letter = podcast.title.firstOrNull()?.uppercase() ?: "?",
                tint = podcastTint(podcast.id),
                cornerRadius = 10.dp,
                modifier = Modifier.size(48.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(text = podcast.title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text(
                    text = podcast.author.ifBlank { "Podcast" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        ) {
            FilterChip(
                selected = isSubscribed,
                onClick = {
                    scope.launch {
                        if (isSubscribed) repository.unsubscribe(podcast.id) else repository.subscribe(podcast)
                    }
                },
                label = { Text("Following") },
                leadingIcon = if (isSubscribed) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else {
                    null
                },
            )
            FilterChip(
                selected = isPreset,
                onClick = { scope.launch { repository.setPreset(podcast.id, !isPreset) } },
                label = { Text("Preset") },
                leadingIcon = if (isPreset) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else {
                    null
                },
            )
        }

        SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            PodcastDetailTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.label) },
                )
            }
        }

        when (selectedTab) {
            PodcastDetailTab.EPISODES -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                ) {
                    Text(
                        text = "All episodes",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { newestFirst = !newestFirst }) {
                        Text(if (newestFirst) "Newest" else "Oldest")
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Toggle sort order")
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

                LazyColumn(state = episodeListState) {
                    items(episodes, key = { it.id }) { episode ->
                        EpisodeRow(
                            episode = episode,
                            podcast = podcast,
                            onListen = { onEpisodeSelected(episode) },
                            onQueue = { scope.launch { repository.enqueue(episode) } },
                            onDownload = {
                                if (episode.localFilePath != null) {
                                    scope.launch { repository.deleteDownload(episode) }
                                } else if (episode.id !in downloadingIds) {
                                    downloadingIds = downloadingIds + episode.id
                                    scope.launch {
                                        repository.downloadEpisode(episode)
                                        downloadingIds = downloadingIds - episode.id
                                    }
                                }
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    }
                    if (loadingMoreEpisodes) {
                        item {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
            PodcastDetailTab.NOW_PLAYING -> {
                val playingEpisode = nowPlayingEpisode?.takeIf { it.podcastId == podcast.id }
                if (playingEpisode != null) {
                    EpisodeRow(
                        episode = playingEpisode,
                        podcast = podcast,
                        onListen = { onEpisodeSelected(playingEpisode) },
                        onQueue = { scope.launch { repository.enqueue(playingEpisode) } },
                        onDownload = {},
                    )
                } else {
                    Text(
                        text = "Nothing from this show is playing right now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
            PodcastDetailTab.ABOUT -> {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(text = podcast.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = podcast.author.ifBlank { "Unknown creator" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = podcast.feedUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    podcast: Podcast,
    onListen: () -> Unit,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onListen)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Avatar(
                imageUrl = podcast.artworkUrl,
                letter = podcast.title.firstOrNull()?.uppercase() ?: "?",
                tint = podcastTint(podcast.id),
                cornerRadius = 8.dp,
                modifier = Modifier.size(44.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(text = episode.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    episodeMeta(episode)?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isNewEpisode(episode)) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (episode.description.isNotBlank()) {
            Text(
                text = episode.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            FilledTonalButton(onClick = onListen) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Listen")
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onQueue) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to queue")
            }
            IconButton(onClick = onDownload) {
                Icon(
                    if (episode.localFilePath != null) Icons.Filled.DownloadDone else Icons.Filled.Download,
                    contentDescription = if (episode.localFilePath != null) "Downloaded" else "Download",
                )
            }
        }
    }
}

@Composable
private fun podcastTint(podcastId: String) =
    LocalEasyRadioColors.current.avatarTints.let { it[podcastId.hashCode().mod(it.size)] }

private fun episodeMeta(episode: Episode): String? {
    val date = episode.publishedAtEpochMillis?.let {
        DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()
    }
    val duration = episode.durationSeconds?.takeIf { it > 0 }?.let { seconds ->
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        when {
            hours > 0 && minutes > 0 -> "$hours hr $minutes min"
            hours > 0 -> "$hours hr"
            else -> "$minutes min"
        }
    }
    return listOfNotNull(date, duration).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun isNewEpisode(episode: Episode): Boolean {
    val published = episode.publishedAtEpochMillis ?: return false
    return System.currentTimeMillis() - published < 3 * DateUtils.DAY_IN_MILLIS
}

@Composable
fun QueueScreen(
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
