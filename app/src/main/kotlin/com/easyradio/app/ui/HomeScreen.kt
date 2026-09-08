package com.easyradio.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.easyradio.core.database.PodcastRepository
import com.easyradio.core.database.RecentlyPlayedRepository
import com.easyradio.core.model.Podcast
import com.easyradio.core.model.RadioStation
import com.easyradio.core.model.RecentlyPlayedItem

/** Matches docs/designs/2a-home-discover-light.png / 2b-home-discover-dark.png. */
@Composable
fun HomeScreen(
    podcastRepository: PodcastRepository,
    favoriteStationRepository: FavoriteStationRepository,
    recentlyPlayedRepository: RecentlyPlayedRepository,
    onStationSelected: (RadioStation) -> Unit,
    onPodcastSelected: (Podcast) -> Unit,
    onRecentlyPlayedSelected: (RecentlyPlayedItem) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val presets by remember(favoriteStationRepository) { favoriteStationRepository.presets() }
        .collectAsState(initial = emptyList())
    val recentlyPlayed by remember(recentlyPlayedRepository) { recentlyPlayedRepository.recent() }
        .collectAsState(initial = emptyList())
    val subscribedPodcasts by remember(podcastRepository) { podcastRepository.subscribedPodcasts() }
        .collectAsState(initial = emptyList())
    val followedStations by remember(favoriteStationRepository) { favoriteStationRepository.favorites() }
        .collectAsState(initial = emptyList())

    val subscribedChannels = (
        subscribedPodcasts.mapNotNull { podcast ->
            podcast.lastPlayedAtEpochMillis?.let { SubscribedChannel.PodcastChannel(podcast, it) }
        } +
            followedStations.mapNotNull { station ->
                station.lastPlayedAtEpochMillis?.let { SubscribedChannel.StationChannel(station, it) }
            }
        ).sortedByDescending { it.lastPlayedAtEpochMillis }.take(4)

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

        if (subscribedChannels.isNotEmpty()) {
            SectionHeader(title = "Subscribed Channels")
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
                items(subscribedChannels, key = { it.id }) { channel ->
                    HomeAvatarTile(
                        imageUrl = channel.imageUrl,
                        letter = channel.label.firstOrNull()?.uppercase() ?: "?",
                        tintSeed = channel.id,
                        label = channel.label,
                        onClick = {
                            when (channel) {
                                is SubscribedChannel.StationChannel -> onStationSelected(channel.station)
                                is SubscribedChannel.PodcastChannel -> onPodcastSelected(channel.podcast)
                            }
                        },
                    )
                }
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
    }
}

/** A followed radio station or subscribed podcast, unified for the "Subscribed Channels" row. */
private sealed interface SubscribedChannel {
    val id: String
    val label: String
    val imageUrl: String?
    val lastPlayedAtEpochMillis: Long

    data class StationChannel(val station: RadioStation, override val lastPlayedAtEpochMillis: Long) :
        SubscribedChannel {
        override val id get() = station.id
        override val label get() = station.name
        override val imageUrl get() = station.imageUrl
    }

    data class PodcastChannel(val podcast: Podcast, override val lastPlayedAtEpochMillis: Long) : SubscribedChannel {
        override val id get() = podcast.id
        override val label get() = podcast.title
        override val imageUrl get() = podcast.artworkUrl
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
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
