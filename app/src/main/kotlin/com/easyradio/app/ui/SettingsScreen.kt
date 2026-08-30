package com.easyradio.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.easyradio.core.model.AppSettings
import com.easyradio.core.model.DownloadQuality
import com.easyradio.core.model.ThemeMode

// Design order (7e): Light, Dark, System.
private val THEME_ORDER = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
private val SLEEP_TIMER_OPTIONS = listOf(0, 15, 30, 45, 60)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDownloadQualityChange: (DownloadQuality) -> Unit,
    onDownloadOverWifiOnlyChange: (Boolean) -> Unit,
    onAutoDownloadNewEpisodesChange: (Boolean) -> Unit,
    onSleepTimerMinutesChange: (Int) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        SectionTitle("Appearance")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            THEME_ORDER.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = settings.themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, THEME_ORDER.size),
                ) {
                    Text(mode.displayName())
                }
            }
        }

        SectionTitle("Downloads")
        ValueRow(
            label = "Download quality",
            value = settings.downloadQuality,
            options = DownloadQuality.entries,
            display = DownloadQuality::displayName,
            onSelect = onDownloadQualityChange,
        )
        SwitchRow(
            label = "Wi‑Fi only",
            checked = settings.downloadOverWifiOnly,
            onCheckedChange = onDownloadOverWifiOnlyChange,
        )
        SwitchRow(
            label = "Auto‑download new episodes",
            checked = settings.autoDownloadNewEpisodes,
            onCheckedChange = onAutoDownloadNewEpisodesChange,
        )

        SectionTitle("Playback")
        ValueRow(
            label = "Sleep timer default",
            value = settings.sleepTimerMinutes,
            options = SLEEP_TIMER_OPTIONS,
            display = ::sleepTimerLabel,
            onSelect = onSleepTimerMinutesChange,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
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

/** A label with a tappable current value that opens a dropdown of [options]. */
@Composable
private fun <T> ValueRow(
    label: String,
    value: T,
    options: List<T>,
    display: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = display(value),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun ThemeMode.displayName(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun DownloadQuality.displayName(): String = when (this) {
    DownloadQuality.LOW -> "Low"
    DownloadQuality.NORMAL -> "Normal"
    DownloadQuality.HIGH -> "High"
}

private fun sleepTimerLabel(minutes: Int): String = if (minutes == 0) "Off" else "$minutes min"
