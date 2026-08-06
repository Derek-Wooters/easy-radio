package com.easyradio.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    secondary = LightSecondary,
    background = LightBackground,
    surface = LightSurface,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
)

data class EasyRadioExtraColors(
    val avatarTints: List<AvatarTint>,
    val liveBadgeContainer: Color,
    val liveBadgeContent: Color,
)

private val LightExtraColors = EasyRadioExtraColors(LightAvatarTints, LightLiveBadgeContainer, LightLiveBadgeContent)
private val DarkExtraColors = EasyRadioExtraColors(DarkAvatarTints, DarkLiveBadgeContainer, DarkLiveBadgeContent)

val LocalEasyRadioColors = staticCompositionLocalOf { LightExtraColors }

@Composable
fun EasyRadioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extraColors = if (darkTheme) DarkExtraColors else LightExtraColors

    CompositionLocalProvider(LocalEasyRadioColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EasyRadioTypography,
            content = content,
        )
    }
}
