package com.easyradio.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SkipDirection { BACK, FORWARD }

/**
 * A skip-back/skip-forward glyph with the actual configured seconds drawn on top of it, mirroring
 * Pocket Casts' player controls. Material's own [Icons.Filled.Replay]/`Forward30`-style icons only
 * ever draw one hardcoded number, so they silently go wrong the moment skip-back/skip-forward is
 * changed to anything other than that baked-in value (skip-back defaults to 15s here, which has no
 * matching Material icon at all). Mirroring the same circular-arrow glyph horizontally for
 * [SkipDirection.FORWARD] keeps the two directions visually paired, the way Pocket Casts' own
 * rewind/forward icons are.
 */
@Composable
fun SkipIcon(
    seconds: Int,
    direction: SkipDirection,
    modifier: Modifier = Modifier,
) {
    val contentDescription = when (direction) {
        SkipDirection.BACK -> "Skip back $seconds seconds"
        SkipDirection.FORWARD -> "Skip forward $seconds seconds"
    }
    Box(modifier = modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Filled.Replay,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp).graphicsLayer {
                if (direction == SkipDirection.FORWARD) scaleX = -1f
            },
        )
        Text(
            text = seconds.toString(),
            color = LocalContentColor.current,
            fontSize = if (seconds >= 100) 8.sp else 9.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
