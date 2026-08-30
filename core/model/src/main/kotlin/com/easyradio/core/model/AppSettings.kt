package com.easyradio.core.model

/** User-selectable theme, resolved against the system dark setting when [SYSTEM]. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    fun resolveDarkTheme(systemInDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDark
        LIGHT -> false
        DARK -> true
    }
}

/** Audio quality used when downloading episodes for offline playback. */
enum class DownloadQuality { LOW, NORMAL, HIGH }

/**
 * User preferences persisted locally (no backend in v1). Defaults are the
 * out-of-the-box behaviour: follow the system theme, no sleep timer, high-
 * quality downloads only on Wi-Fi, and don't auto-download new episodes.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val sleepTimerMinutes: Int = 0,
    val downloadQuality: DownloadQuality = DownloadQuality.HIGH,
    val downloadOverWifiOnly: Boolean = true,
    val autoDownloadNewEpisodes: Boolean = false,
    val hasCompletedOnboarding: Boolean = false,
    val favoriteGenres: Set<String> = emptySet(),
)
