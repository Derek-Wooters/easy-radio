package com.easyradio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.easyradio.app.ui.theme.AvatarTint

/**
 * Square avatar used for stations, podcasts, and now-playing artwork. Renders
 * a letter tile immediately and swaps in [imageUrl] once it loads; falls back
 * to the letter tile on a null url or a failed load rather than leaving a gap.
 */
@Composable
fun Avatar(
    imageUrl: String?,
    letter: String,
    tint: AvatarTint,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(tint.container),
    ) {
        if (imageUrl != null) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (painter.state is AsyncImagePainter.State.Success) {
                    SubcomposeAsyncImageContent()
                } else {
                    AvatarLetter(letter, tint)
                }
            }
        } else {
            AvatarLetter(letter, tint)
        }
    }
}

@Composable
private fun AvatarLetter(letter: String, tint: AvatarTint) {
    Text(
        text = letter,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = tint.content,
    )
}
