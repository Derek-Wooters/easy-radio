package com.easyradio.app.ui

import androidx.compose.foundation.layout.Column
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
import com.easyradio.core.model.RadioStation
import kotlinx.coroutines.launch

/** Matches docs/designs/7d-favorites.png. */
@Composable
fun PlaylistsScreen(
    repository: FavoriteStationRepository,
    onStationSelected: (RadioStation) -> Unit,
) {
    val favorites by remember(repository) { repository.favorites() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Favorites",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
        )

        if (favorites.isEmpty()) {
            Text(
                text = "Star a station from Live Radio to add it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        LazyColumn {
            items(favorites, key = { it.id }) { station ->
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
        }
    }
}
