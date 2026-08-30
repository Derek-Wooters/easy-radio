package com.easyradio.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `system theme mode follows the system dark flag`() {
        assertThat(ThemeMode.SYSTEM.resolveDarkTheme(systemInDark = true)).isTrue()
        assertThat(ThemeMode.SYSTEM.resolveDarkTheme(systemInDark = false)).isFalse()
    }

    @Test
    fun `light and dark modes ignore the system flag`() {
        assertThat(ThemeMode.LIGHT.resolveDarkTheme(systemInDark = true)).isFalse()
        assertThat(ThemeMode.DARK.resolveDarkTheme(systemInDark = false)).isTrue()
    }

    @Test
    fun `defaults are system theme, no sleep timer, wifi-only high-quality downloads, no auto-download`() {
        val settings = AppSettings()

        assertThat(settings.themeMode).isEqualTo(ThemeMode.SYSTEM)
        assertThat(settings.sleepTimerMinutes).isEqualTo(0)
        assertThat(settings.downloadQuality).isEqualTo(DownloadQuality.HIGH)
        assertThat(settings.downloadOverWifiOnly).isTrue()
        assertThat(settings.autoDownloadNewEpisodes).isFalse()
        assertThat(settings.hasCompletedOnboarding).isFalse()
        assertThat(settings.favoriteGenres).isEmpty()
    }
}
