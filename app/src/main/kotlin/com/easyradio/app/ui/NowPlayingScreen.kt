package com.easyradio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.easyradio.app.ui.theme.LocalEasyRadioColors

/**
 * Full-screen "now playing" surface reached by tapping the mini-player. Radio
 * shows a LIVE badge in place of the scrubber; podcasts show the position bar,
 * time labels, and the speed/skip controls. Sleep-timer and queue affordances
 * from the design are omitted here since they'd be dead controls without the
 * navigation to back them.
 */
@Composable
fun NowPlayingScreen(
    topLabel: String,
    title: String,
    subtitle: String,
    imageUrl: String?,
    tintSeed: String,
    isLive: Boolean,
    isPlaying: Boolean,
    progress: Float?,
    positionLabel: String?,
    durationLabel: String?,
    speedLabel: String?,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipBack: (() -> Unit)? = null,
    onSkipForward: (() -> Unit)? = null,
    onSpeedClick: (() -> Unit)? = null,
) {
    val extraColors = LocalEasyRadioColors.current
    val tints = extraColors.avatarTints
    val tint = tints[tintSeed.hashCode().mod(tints.size)]

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Collapse")
                }
                Text(
                    text = topLabel.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(48.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Avatar(
                imageUrl = imageUrl,
                letter = title.firstOrNull()?.uppercase() ?: "?",
                tint = tint,
                cornerRadius = 24.dp,
                modifier = Modifier.fillMaxWidth(0.82f).aspectRatio(1f),
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(
                        text = positionLabel.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = durationLabel.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (isLive) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(extraColors.liveBadgeContainer)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelMedium,
                        color = extraColors.liveBadgeContent,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            ) {
                if (onSpeedClick != null && speedLabel != null) {
                    TextButton(onClick = onSpeedClick) { Text(speedLabel) }
                }
                if (onSkipBack != null) {
                    IconButton(onClick = onSkipBack) {
                        Icon(Icons.Filled.Replay, contentDescription = "Skip back 15 seconds")
                    }
                }
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp),
                    )
                }
                if (onSkipForward != null) {
                    IconButton(onClick = onSkipForward) {
                        Icon(Icons.Filled.Forward30, contentDescription = "Skip forward 30 seconds")
                    }
                }
            }
        }
    }
}
