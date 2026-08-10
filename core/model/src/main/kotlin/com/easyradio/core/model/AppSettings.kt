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

/**
 * User preferences persisted locally (no backend in v1). Defaults are the
 * out-of-the-box behaviour: follow the system theme, no sleep timer, download
 * only on Wi-Fi, and don't auto-download new episodes.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val sleepTimerMinutes: Int = 0,
    val downloadOverWifiOnly: Boolean = true,
    val autoDownloadNewEpisodes: Boolean = false,
)
