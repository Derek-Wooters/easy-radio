package com.easyradio.app

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.easyradio.app.playback.EasyRadioPlaybackService
import com.easyradio.app.ui.RadioBrowseScreen
import com.easyradio.core.media.PlaybackStateMapper
import com.easyradio.core.media.PlaybackUiState
import com.easyradio.core.model.RadioStation
import com.easyradio.core.network.radiobrowser.RadioBrowserApiFactory
import com.easyradio.core.network.radiobrowser.RadioStationRepository
import com.easyradio.app.ui.theme.EasyRadioTheme
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class MainActivity : ComponentActivity() {

    private val repository = RadioStationRepository(api = RadioBrowserApiFactory.create())

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController by mutableStateOf<MediaController?>(null)
    private var uiState by mutableStateOf(PlaybackUiState.IDLE)
    private var currentStation by mutableStateOf<RadioStation?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EasyRadioTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RadioBrowseScreen(
                        repository = repository,
                        currentStation = currentStation,
                        playbackState = uiState,
                        onStationSelected = ::playStation,
                        onPauseClick = { mediaController?.pause() },
                    )
                }
            }
        }
    }

    private fun playStation(station: RadioStation) {
        currentStation = station
        mediaController?.let { controller ->
            controller.setMediaItem(MediaItem.fromUri(station.streamUrl))
            controller.prepare()
            controller.play()
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
                        object : Player.Listener {
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
        uiState = PlaybackStateMapper.map(
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
