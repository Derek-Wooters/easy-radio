package com.easyradio.app.widget

import android.content.ComponentName
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.compose.ui.unit.dp
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import androidx.glance.action.ActionParameters
import com.easyradio.app.playback.EasyRadioPlaybackService
import kotlinx.coroutines.suspendCancellableCoroutine

private object WidgetKeys {
    val title = stringPreferencesKey("title")
    val subtitle = stringPreferencesKey("subtitle")
    val isPlaying = booleanPreferencesKey("is_playing")
}

/** Home-screen widget showing what's currently playing, with play/pause and skip-forward. */
class EasyRadioWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val title = prefs[WidgetKeys.title] ?: "Easy Radio"
            val subtitle = prefs[WidgetKeys.subtitle] ?: "Nothing playing"
            val isPlaying = prefs[WidgetKeys.isPlaying] ?: false

            Column(modifier = GlanceModifier.fillMaxWidth().padding(12.dp)) {
                Text(text = title)
                Text(text = subtitle)
                Row {
                    Text(
                        text = if (isPlaying) "Pause" else "Play",
                        modifier = GlanceModifier.clickable(actionRunCallback<PlayPauseAction>()).padding(end = 16.dp),
                    )
                    Text(
                        text = "+30s",
                        modifier = GlanceModifier.clickable(actionRunCallback<SkipForwardAction>()),
                    )
                }
            }
        }
    }

    companion object {
        suspend fun updateState(context: Context, title: String, subtitle: String, isPlaying: Boolean) {
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(EasyRadioWidget::class.java)
            for (id in ids) {
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[WidgetKeys.title] = title
                        this[WidgetKeys.subtitle] = subtitle
                        this[WidgetKeys.isPlaying] = isPlaying
                    }
                }
                EasyRadioWidget().update(context, id)
            }
        }
    }
}

class EasyRadioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EasyRadioWidget()
}

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withMediaController(context) { controller ->
            val isPlaying = controller.playbackState?.state == android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
            if (isPlaying) controller.transportControls.pause() else controller.transportControls.play()
        }
    }
}

class SkipForwardAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withMediaController(context) { controller -> controller.transportControls.fastForward() }
    }
}

private suspend fun connectBrowser(context: Context): MediaBrowserCompat = suspendCancellableCoroutine { continuation ->
    lateinit var browser: MediaBrowserCompat
    browser = MediaBrowserCompat(
        context,
        ComponentName(context, EasyRadioPlaybackService::class.java),
        object : MediaBrowserCompat.ConnectionCallback() {
            override fun onConnected() {
                continuation.resumeWith(Result.success(browser))
            }

            override fun onConnectionFailed() {
                continuation.resumeWith(Result.failure(IllegalStateException("Widget action: session connection failed")))
            }
        },
        null,
    )
    browser.connect()
    continuation.invokeOnCancellation { browser.disconnect() }
}

private suspend fun withMediaController(context: Context, block: (MediaControllerCompat) -> Unit) {
    try {
        val browser = connectBrowser(context)
        try {
            block(MediaControllerCompat(context, browser.sessionToken))
        } finally {
            browser.disconnect()
        }
    } catch (e: Exception) {
        // Best-effort -- no active session to control yet.
    }
}
