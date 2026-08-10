package com.easyradio.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.easyradio.core.model.AppSettings
import com.easyradio.core.model.ThemeMode

private val SLEEP_TIMER_OPTIONS = listOf(0, 15, 30, 45, 60)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDownloadOverWifiOnlyChange: (Boolean) -> Unit,
    onAutoDownloadNewEpisodesChange: (Boolean) -> Unit,
    onSleepTimerMinutesChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SectionTitle("Theme")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    label = { Text(mode.displayName()) },
                )
            }
        }

        SectionTitle("Downloads")
        SwitchRow(
            label = "Download over Wi‑Fi only",
            checked = settings.downloadOverWifiOnly,
            onCheckedChange = onDownloadOverWifiOnlyChange,
        )
        SwitchRow(
            label = "Auto‑download new episodes",
            checked = settings.autoDownloadNewEpisodes,
            onCheckedChange = onAutoDownloadNewEpisodesChange,
        )

        SectionTitle("Sleep timer")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SLEEP_TIMER_OPTIONS.forEach { minutes ->
                FilterChip(
                    selected = settings.sleepTimerMinutes == minutes,
                    onClick = { onSleepTimerMinutesChange(minutes) },
                    label = { Text(if (minutes == 0) "Off" else "$minutes min") },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun ThemeMode.displayName(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}
