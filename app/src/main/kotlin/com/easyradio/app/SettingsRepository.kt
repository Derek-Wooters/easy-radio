package com.easyradio.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.easyradio.core.model.AppSettings
import com.easyradio.core.model.DownloadQuality
import com.easyradio.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Persists [AppSettings] locally via Preferences DataStore. Reads surface as a
 * [Flow] so the UI re-renders when a preference changes; unknown/absent keys
 * fall back to the [AppSettings] defaults, so a fresh install and a forward-
 * incompatible read both degrade gracefully.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val sleepTimerMinutes = intPreferencesKey("sleep_timer_minutes")
        val downloadQuality = stringPreferencesKey("download_quality")
        val downloadOverWifiOnly = booleanPreferencesKey("download_over_wifi_only")
        val autoDownloadNewEpisodes = booleanPreferencesKey("auto_download_new_episodes")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            themeMode = prefs[Keys.themeMode]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: defaults.themeMode,
            sleepTimerMinutes = prefs[Keys.sleepTimerMinutes] ?: defaults.sleepTimerMinutes,
            downloadQuality = prefs[Keys.downloadQuality]
                ?.let { runCatching { DownloadQuality.valueOf(it) }.getOrNull() }
                ?: defaults.downloadQuality,
            downloadOverWifiOnly = prefs[Keys.downloadOverWifiOnly] ?: defaults.downloadOverWifiOnly,
            autoDownloadNewEpisodes = prefs[Keys.autoDownloadNewEpisodes] ?: defaults.autoDownloadNewEpisodes,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setSleepTimerMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.sleepTimerMinutes] = minutes }
    }

    suspend fun setDownloadQuality(quality: DownloadQuality) {
        context.settingsDataStore.edit { it[Keys.downloadQuality] = quality.name }
    }

    suspend fun setDownloadOverWifiOnly(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.downloadOverWifiOnly] = value }
    }

    suspend fun setAutoDownloadNewEpisodes(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.autoDownloadNewEpisodes] = value }
    }
}
