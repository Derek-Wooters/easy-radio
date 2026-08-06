package com.easyradio.app.ui.theme

import androidx.compose.ui.graphics.Color

// Extracted from docs/designs/1a-foundations.png
val LightBackground = Color(0xFFF2F2F2)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF1A1A1A)
val LightOnSurfaceVariant = Color(0xFF757575)
val LightPrimary = Color(0xFF0089A8)
val LightSecondary = Color(0xFFC2185B)
val LightOutline = Color(0xFFE0E0E0)

val DarkBackground = Color(0xFF1C1C1E)
val DarkSurface = Color(0xFF2A2A2C)
val DarkOnSurface = Color(0xFFF5F5F5)
val DarkOnSurfaceVariant = Color(0xFF9E9E9E)
val DarkPrimary = Color(0xFF6EC6E8)
val DarkSecondary = Color(0xFFF48FB1)
val DarkOutline = Color(0xFF3A3A3C)

val LightLiveBadgeContainer = Color(0xFFFCE4EC)
val LightLiveBadgeContent = Color(0xFFC2185B)
val DarkLiveBadgeContainer = Color(0xFF4A1530)
val DarkLiveBadgeContent = Color(0xFFF48FB1)

data class AvatarTint(val container: Color, val content: Color)

// Rotating per-station tint palette, matches docs/designs/2c-live-radio-browse-light.png
val LightAvatarTints = listOf(
    AvatarTint(Color(0xFF0F3B57), Color.White),
    AvatarTint(Color(0xFFE0E0E0), Color(0xFF616161)),
    AvatarTint(Color(0xFFFCE4EC), Color(0xFFC2185B)),
    AvatarTint(Color(0xFFE1F5FE), Color(0xFF0089A8)),
)

val DarkAvatarTints = listOf(
    AvatarTint(Color(0xFF0E3A52), Color(0xFF6EC6E8)),
    AvatarTint(Color(0xFF3A3A3C), Color(0xFFD0D0D0)),
    AvatarTint(Color(0xFF5A1B37), Color(0xFFF48FB1)),
    AvatarTint(Color(0xFF0E3A46), Color(0xFF6EC6E8)),
)
