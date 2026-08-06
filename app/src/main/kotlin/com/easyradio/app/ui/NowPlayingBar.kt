package com.easyradio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.easyradio.app.ui.theme.LocalEasyRadioColors
import com.easyradio.core.media.PlaybackUiState

/**
 * Persistent playback bar, shared across tabs so it stays visible whether a
 * radio station or a podcast episode is playing. [badgeText] (e.g. "LIVE") is
 * only shown for radio; pass null for podcast episodes.
 */
@Composable
fun NowPlayingBar(
    title: String,
    tagline: String,
    tintSeed: String,
    badgeText: String?,
    playbackState: PlaybackUiState,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
) {
    val extraColors = LocalEasyRadioColors.current
    val tints = extraColors.avatarTints
    val tint = tints[tintSeed.hashCode().mod(tints.size)]
    val isPlaying = playbackState == PlaybackUiState.PLAYING || playbackState == PlaybackUiState.BUFFERING

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(tint.container),
            ) {
                Text(
                    text = title.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = tint.content,
                )
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            if (badgeText != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(extraColors.liveBadgeContainer)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = extraColors.liveBadgeContent,
                    )
                }
            }

            IconButton(onClick = if (isPlaying) onPauseClick else onPlayClick) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                )
            }
        }
    }
}
