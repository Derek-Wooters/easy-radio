package com.easyradio.app

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.easyradio.app.ui.NowPlayingBar
import com.easyradio.app.ui.NowPlayingScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.easyradio.app.playback.EasyRadioPlaybackService
import com.easyradio.app.ui.PodcastsScreen
import com.easyradio.app.ui.RadioBrowseScreen
import com.easyradio.app.ui.theme.EasyRadioTheme
import com.easyradio.core.media.PlaybackStateMapper
import com.easyradio.core.media.PlaybackUiState
import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast
import com.easyradio.core.model.RadioStation
import com.easyradio.core.network.radiobrowser.RadioBrowserApiFactory
import com.easyradio.core.network.radiobrowser.RadioStationRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import android.os.SystemClock
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.easyradio.app.ui.SettingsScreen
import com.easyradio.core.media.SleepTimer
import com.easyradio.core.model.AppSettings
import androidx.compose.ui.graphics.vector.ImageVector

private enum class AppTab(val label: String, val icon: ImageVector) {
    RADIO("Radio", Icons.Filled.Radio),
    PODCASTS("Podcasts", Icons.Filled.Podcasts),
    SETTINGS("Settings", Icons.Filled.Settings),
}

private const val PODCAST_POSITION_SAVE_INTERVAL_MS = 5_000L
private const val SKIP_BACK_MS = 15_000L
private const val SKIP_FORWARD_MS = 30_000L
private val PLAYBACK_SPEEDS = listOf(1.0f, 1.25f, 1.5f, 2.0f)

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

class MainActivity : ComponentActivity() {

    private val radioRepository = RadioStationRepository(api = RadioBrowserApiFactory.create())

    private val podcastRepository by lazy { EasyRadioGraph.repository(applicationContext) }
    private val settingsRepository by lazy { EasyRadioGraph.settings(applicationContext) }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController by mutableStateOf<MediaController?>(null)
    private var uiState by mutableStateOf(PlaybackUiState.IDLE)
    private var currentStation by mutableStateOf<RadioStation?>(null)
    private var currentEpisode by mutableStateOf<Episode?>(null)
    private var currentPodcast by mutableStateOf<Podcast?>(null)

    private var positionSaveJob: Job? = null
    private var playbackSpeedIndex by mutableStateOf(0)
    private var positionMs by mutableStateOf(0L)
    private var durationMs by mutableStateOf(0L)
    private var showNowPlaying by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())

            LaunchedEffect(currentEpisode?.id, mediaController) {
                val controller = mediaController
                if (currentEpisode != null && controller != null) {
                    while (true) {
                        positionMs = controller.currentPosition.coerceAtLeast(0)
                        durationMs = controller.duration.coerceAtLeast(0)
                        delay(1_000)
                    }
                }
            }

            LaunchedEffect(settings.sleepTimerMinutes) {
                val minutes = settings.sleepTimerMinutes
                if (minutes > 0) {
                    val start = SystemClock.elapsedRealtime()
                    val durationMs = minutes * 60_000L
                    while (!SleepTimer.isExpired(start, durationMs, SystemClock.elapsedRealtime())) {
                        delay(1_000)
                    }
                    mediaController?.pause()
                }
            }

            EasyRadioTheme(darkTheme = settings.themeMode.resolveDarkTheme(isSystemInDarkTheme())) {
                var selectedTab by remember { mutableStateOf(AppTab.RADIO) }
                val playing = uiState == PlaybackUiState.PLAYING || uiState == PlaybackUiState.BUFFERING

                BackHandler(enabled = showNowPlaying) { showNowPlaying = false }

                if (showNowPlaying && (currentStation != null || currentEpisode != null)) {
                    val station = currentStation
                    val episode = currentEpisode
                    val podcast = currentPodcast
                    when {
                        station != null -> NowPlayingScreen(
                            topLabel = "Live Radio",
                            title = station.name,
                            subtitle = station.tagline,
                            imageUrl = station.imageUrl,
                            tintSeed = station.id,
                            isLive = true,
                            isPlaying = playing,
                            progress = null,
                            positionLabel = null,
                            durationLabel = null,
                            speedLabel = null,
                            onCollapse = { showNowPlaying = false },
                            onPlayPause = { if (playing) mediaController?.pause() else playStation(station) },
                        )
                        episode != null -> NowPlayingScreen(
                            topLabel = podcast?.title.orEmpty(),
                            title = episode.title,
                            subtitle = podcast?.title.orEmpty(),
                            imageUrl = podcast?.artworkUrl,
                            tintSeed = episode.podcastId,
                            isLive = false,
                            isPlaying = playing,
                            progress = if (durationMs > 0) positionMs.toFloat() / durationMs else null,
                            positionLabel = formatDuration(positionMs),
                            durationLabel = formatDuration(durationMs),
                            speedLabel = "${PLAYBACK_SPEEDS[playbackSpeedIndex]}x",
                            onCollapse = { showNowPlaying = false },
                            onPlayPause = { if (playing) mediaController?.pause() else mediaController?.play() },
                            onSkipBack = { skip(-SKIP_BACK_MS) },
                            onSkipForward = { skip(SKIP_FORWARD_MS) },
                            onSpeedClick = ::cyclePlaybackSpeed,
                        )
                    }
                } else {
                Scaffold(
                    bottomBar = {
                        Column {
                            val station = currentStation
                            val episode = currentEpisode
                            val podcast = currentPodcast
                            when {
                                station != null -> NowPlayingBar(
                                    title = station.name,
                                    tagline = station.tagline,
                                    tintSeed = station.id,
                                    imageUrl = station.imageUrl,
                                    badgeText = "LIVE",
                                    playbackState = uiState,
                                    onPlayClick = { playStation(station) },
                                    onPauseClick = { mediaController?.pause() },
                                    onExpand = { showNowPlaying = true },
                                )
                                episode != null -> {
                                    val podcastTitle = podcast?.title.orEmpty()
                                    val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else null
                                    val tagline = if (durationMs > 0) {
                                        val left = formatDuration((durationMs - positionMs).coerceAtLeast(0))
                                        if (podcastTitle.isNotEmpty()) "$podcastTitle · $left left" else "$left left"
                                    } else {
                                        podcastTitle
                                    }
                                    NowPlayingBar(
                                        title = episode.title,
                                        tagline = tagline,
                                        tintSeed = episode.podcastId,
                                        imageUrl = podcast?.artworkUrl,
                                        badgeText = null,
                                        playbackState = uiState,
                                        onPlayClick = { mediaController?.play() },
                                        onPauseClick = { mediaController?.pause() },
                                        onSkipBackClick = { skip(-SKIP_BACK_MS) },
                                        onSkipForwardClick = { skip(SKIP_FORWARD_MS) },
                                        onSpeedClick = ::cyclePlaybackSpeed,
                                        speedLabel = "${PLAYBACK_SPEEDS[playbackSpeedIndex]}x",
                                        progress = fraction,
                                        onExpand = { showNowPlaying = true },
                                    )
                                }
                            }
                            NavigationBar {
                                AppTab.entries.forEach { tab ->
                                    NavigationBarItem(
                                        selected = selectedTab == tab,
                                        onClick = { selectedTab = tab },
                                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                                        label = { Text(tab.label) },
                                    )
                                }
                            }
                        }
                    },
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        when (selectedTab) {
                            AppTab.RADIO -> RadioBrowseScreen(
                                repository = radioRepository,
                                onStationSelected = ::playStation,
                            )
                            AppTab.PODCASTS -> PodcastsScreen(
                                repository = podcastRepository,
                                onEpisodeSelected = { podcast, episode -> playEpisode(podcast, episode) },
                            )
                            AppTab.SETTINGS -> SettingsScreen(
                                settings = settings,
                                onThemeModeChange = { lifecycleScope.launch { settingsRepository.setThemeMode(it) } },
                                onDownloadQualityChange = {
                                    lifecycleScope.launch { settingsRepository.setDownloadQuality(it) }
                                },
                                onDownloadOverWifiOnlyChange = {
                                    lifecycleScope.launch { settingsRepository.setDownloadOverWifiOnly(it) }
                                },
                                onAutoDownloadNewEpisodesChange = {
                                    lifecycleScope.launch { settingsRepository.setAutoDownloadNewEpisodes(it) }
                                },
                                onSleepTimerMinutesChange = {
                                    lifecycleScope.launch { settingsRepository.setSleepTimerMinutes(it) }
                                },
                            )
                        }
                    }
                }
                }
            }
        }
    }

    private fun playStation(station: RadioStation) {
        positionSaveJob?.cancel()
        currentEpisode = null
        currentPodcast = null
        currentStation = station
        mediaController?.let { controller ->
            controller.setMediaItem(MediaItem.fromUri(station.streamUrl))
            controller.prepare()
            controller.play()
        }
    }

    private fun playEpisode(podcast: Podcast, episode: Episode) {
        currentStation = null
        currentEpisode = episode
        currentPodcast = podcast
        playbackSpeedIndex = 0
        val controller = mediaController ?: return

        val localPath = episode.localFilePath
        val mediaItem = if (localPath != null && File(localPath).exists()) {
            MediaItem.fromUri(android.net.Uri.fromFile(File(localPath)))
        } else {
            MediaItem.fromUri(episode.audioUrl)
        }
        controller.setMediaItem(mediaItem)
        controller.prepare()

        lifecycleScope.launch {
            val resumeMs = podcastRepository.lastPosition(episode.id)
            if (resumeMs > 0) controller.seekTo(resumeMs)
            controller.play()
        }

        startPositionSaving(episode.id)
    }

    private fun skip(deltaMs: Long) {
        val controller = mediaController ?: return
        val target = com.easyradio.core.media.SeekMath.clampSeek(
            currentMs = controller.currentPosition,
            deltaMs = deltaMs,
            durationMs = controller.duration.coerceAtLeast(0),
        )
        controller.seekTo(target)
    }

    private fun cyclePlaybackSpeed() {
        playbackSpeedIndex = (playbackSpeedIndex + 1) % PLAYBACK_SPEEDS.size
        mediaController?.setPlaybackSpeed(PLAYBACK_SPEEDS[playbackSpeedIndex])
    }

    private fun startPositionSaving(episodeId: String) {
        positionSaveJob?.cancel()
        positionSaveJob = lifecycleScope.launch {
            while (isActive) {
                delay(PODCAST_POSITION_SAVE_INTERVAL_MS)
                val controller = mediaController ?: continue
                if (controller.isPlaying) {
                    podcastRepository.savePosition(episodeId, controller.currentPosition)
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
        currentEpisode?.let { episode ->
            mediaController?.let { controller ->
                lifecycleScope.launch { podcastRepository.savePosition(episode.id, controller.currentPosition) }
            }
        }
        positionSaveJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        super.onStop()
    }
}
