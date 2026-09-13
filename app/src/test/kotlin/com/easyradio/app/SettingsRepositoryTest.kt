package com.easyradio.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.easyradio.core.model.AppSettings
import com.easyradio.core.model.DownloadQuality
import com.easyradio.core.model.ThemeMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    // Each test gets its own DataStore file/instance rather than going through the
    // `by preferencesDataStore(name = "settings")` singleton, which caches its
    // instance process-wide and would otherwise leak state between test methods.
    private fun repository(): SettingsRepository {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create {
            File(context.filesDir, "test_settings_${UUID.randomUUID()}.preferences_pb")
        }
        return SettingsRepository(dataStore)
    }

    @Test
    fun `settings starts at AppSettings defaults before any writes`() = runTest {
        val settings = repository().settings.first()

        assertThat(settings).isEqualTo(AppSettings())
    }

    @Test
    fun `setThemeMode persists and is reflected in settings`() = runTest {
        val repository = repository()

        repository.setThemeMode(ThemeMode.DARK)

        assertThat(repository.settings.first().themeMode).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun `setSleepTimerMinutes persists and is reflected in settings`() = runTest {
        val repository = repository()

        repository.setSleepTimerMinutes(30)

        assertThat(repository.settings.first().sleepTimerMinutes).isEqualTo(30)
    }

    @Test
    fun `setDownloadQuality persists and is reflected in settings`() = runTest {
        val repository = repository()

        repository.setDownloadQuality(DownloadQuality.LOW)

        assertThat(repository.settings.first().downloadQuality).isEqualTo(DownloadQuality.LOW)
    }

    @Test
    fun `setDownloadOverWifiOnly and setAutoDownloadNewEpisodes persist independently`() = runTest {
        val repository = repository()

        repository.setDownloadOverWifiOnly(false)
        repository.setAutoDownloadNewEpisodes(true)

        val settings = repository.settings.first()
        assertThat(settings.downloadOverWifiOnly).isFalse()
        assertThat(settings.autoDownloadNewEpisodes).isTrue()
    }

    @Test
    fun `setSkipBackSeconds and setSkipForwardSeconds persist independently`() = runTest {
        val repository = repository()

        repository.setSkipBackSeconds(10)
        repository.setSkipForwardSeconds(45)

        val settings = repository.settings.first()
        assertThat(settings.skipBackSeconds).isEqualTo(10)
        assertThat(settings.skipForwardSeconds).isEqualTo(45)
    }

    @Test
    fun `completeOnboarding persists the completed flag and the chosen genres`() = runTest {
        val repository = repository()

        repository.completeOnboarding(setOf("Sports", "Comedy"))

        val settings = repository.settings.first()
        assertThat(settings.hasCompletedOnboarding).isTrue()
        assertThat(settings.favoriteGenres).containsExactly("Sports", "Comedy")
    }
}
