package com.easyradio.app

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.easyradio.app.playback.EasyRadioPlaybackService
import com.easyradio.core.media.PlaybackUiState
import com.easyradio.core.model.RadioStation
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController by mutableStateOf<MediaController?>(null)
    private var uiState by mutableStateOf(PlaybackUiState.IDLE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NowPlayingScreen(
                        state = uiState,
                        onPlayClick = {
                            mediaController?.let { controller ->
                                controller.setMediaItem(
                                    androidx.media3.common.MediaItem.fromUri(
                                        RadioStation.KFAN_TEST_STATION.streamUrl,
                                    ),
                                )
                                controller.prepare()
                                controller.play()
                            }
                        },
                        onPauseClick = { mediaController?.pause() },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, EasyRadioPlaybackService::class.java))
        val future = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                mediaController = future.get().also { controller ->
                    controller.addListener(
                        object : androidx.media3.common.Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) = refreshState(controller)
                            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) =
                                refreshState(controller)
                        },
                    )
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    private fun refreshState(controller: MediaController) {
        uiState = com.easyradio.core.media.PlaybackStateMapper.map(
            playbackState = controller.playbackState,
            playWhenReady = controller.playWhenReady,
            hasError = controller.playerError != null,
        )
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        super.onStop()
    }
}

@androidx.compose.runtime.Composable
private fun NowPlayingScreen(
    state: PlaybackUiState,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = RadioStation.KFAN_TEST_STATION.name, style = MaterialTheme.typography.headlineMedium)
            Text(text = RadioStation.KFAN_TEST_STATION.tagline)
            Text(text = "State: $state")
            Button(onClick = onPlayClick) { Text("Play") }
            Button(onClick = onPauseClick) { Text("Pause") }
        }
    }
}
