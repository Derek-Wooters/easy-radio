package com.easyradio.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.easyradio.app.ui.theme.LocalEasyRadioColors
import com.easyradio.core.database.FavoriteStationRepository
import com.easyradio.core.database.RecentlyPlayedRepository
import com.easyradio.core.model.RadioStation
import com.easyradio.core.model.RecentlyPlayedItem
import com.easyradio.core.network.radiobrowser.RadioStationRepository

/** Matches docs/designs/2a-home-discover-light.png / 2b-home-discover-dark.png. */
@Composable
fun HomeScreen(
    radioRepository: RadioStationRepository,
    favoriteStationRepository: FavoriteStationRepository,
    recentlyPlayedRepository: RecentlyPlayedRepository,
    onStationSelected: (RadioStation) -> Unit,
    onRecentlyPlayedSelected: (RecentlyPlayedItem) -> Unit,
    onSettingsClick: () -> Unit,
    onNavigateStations: () -> Unit,
    onNavigatePodcasts: () -> Unit,
    onNavigatePlaylists: () -> Unit,
) {
    val presets by remember(favoriteStationRepository) { favoriteStationRepository.presets() }
        .collectAsState(initial = emptyList())
    val recentlyPlayed by remember(recentlyPlayedRepository) { recentlyPlayedRepository.recent() }
        .collectAsState(initial = emptyList())
    val curatedStations = remember(radioRepository) { radioRepository.curatedStations() }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        ) {
            Text(text = "Easy Radio", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }

        if (presets.isNotEmpty()) {
            SectionHeader(title = "Presets")
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
                items(presets, key = { it.id }) { station ->
                    HomeAvatarTile(
                        imageUrl = station.imageUrl,
                        letter = station.name.firstOrNull()?.uppercase() ?: "?",
                        tintSeed = station.id,
                        label = station.name,
                        onClick = { onStationSelected(station) },
                    )
                }
            }
        }

        SectionHeader(title = "Live Radio Dial", actionLabel = "All Stations", onActionClick = onNavigateStations)
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            curatedStations.forEach { station ->
                HomeStationRow(station = station, onClick = { onStationSelected(station) })
            }
        }

        if (recentlyPlayed.isNotEmpty()) {
            SectionHeader(title = "Recently Played")
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
                items(recentlyPlayed, key = { it.contentId }) { item ->
                    HomeAvatarTile(
                        imageUrl = item.imageUrl,
                        letter = item.title.firstOrNull()?.uppercase() ?: "?",
                        tintSeed = item.contentId,
                        label = item.title,
                        onClick = { onRecentlyPlayedSelected(item) },
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            OutlinedButton(onClick = onNavigateStations, modifier = Modifier.weight(1f)) { Text("Stations") }
            OutlinedButton(onClick = onNavigatePodcasts, modifier = Modifier.weight(1f)) { Text("Podcasts") }
            OutlinedButton(onClick = onNavigatePlaylists, modifier = Modifier.weight(1f)) { Text("Playlists") }
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String? = null, onActionClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onActionClick != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onActionClick),
            )
        }
    }
}

@Composable
private fun HomeAvatarTile(imageUrl: String?, letter: String, tintSeed: String, label: String, onClick: () -> Unit) {
    val tints = LocalEasyRadioColors.current.avatarTints
    val tint = tints[tintSeed.hashCode().mod(tints.size)]

    Column(
        modifier = Modifier.padding(end = 12.dp, bottom = 8.dp).width(72.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(
            imageUrl = imageUrl,
            letter = letter,
            tint = tint,
            cornerRadius = 12.dp,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun HomeStationRow(station: RadioStation, onClick: () -> Unit) {
    val tints = LocalEasyRadioColors.current.avatarTints
    val tint = tints[station.id.hashCode().mod(tints.size)]

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    ) {
        Avatar(
            imageUrl = station.imageUrl,
            letter = station.name.firstOrNull()?.uppercase() ?: "?",
            tint = tint,
            cornerRadius = 10.dp,
            modifier = Modifier.size(48.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(text = station.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = station.tagline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onClick) {
            Icon(
                Icons.Filled.PlayCircleOutline,
                contentDescription = "Play ${station.name}",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
