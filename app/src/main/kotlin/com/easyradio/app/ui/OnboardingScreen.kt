package com.easyradio.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Genres offered on the "Pick your favorites" onboarding step; see docs/designs/7f-onboarding.png. */
val ONBOARDING_GENRES = listOf(
    "Sports", "News & Talk", "Comedy", "Music", "True Crime", "Business", "Science", "Culture",
)

/**
 * First-run genre picker (docs/designs/7f-onboarding.png). The selection is persisted via
 * [com.easyradio.app.SettingsRepository.completeOnboarding] but doesn't yet filter Home/Browse
 * content -- the app has no genre-tagged station or podcast data to filter by. Persisting it now
 * means that personalization can be added later without another migration.
 */
@Composable
fun OnboardingScreen(
    selectedGenres: Set<String>,
    onToggleGenre: (String) -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onSkip) { Text("Skip") }
        }

        Text(
            text = "Pick your favorites",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "Choose a few genres and stations to tailor your Home.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ONBOARDING_GENRES.forEach { genre ->
                FilterChip(
                    selected = genre in selectedGenres,
                    onClick = { onToggleGenre(genre) },
                    label = { Text(genre) },
                )
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom) {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) {
                Text("Continue")
            }
        }
    }
}
