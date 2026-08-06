package com.easyradio.app

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import com.easyradio.app.ui.NowPlayingBar
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
import androidx.room.Room
import com.easyradio.app.playback.EasyRadioPlaybackService
import com.easyradio.app.ui.PodcastsScreen
import com.easyradio.app.ui.RadioBrowseScreen
import com.easyradio.app.ui.theme.EasyRadioTheme
import com.easyradio.core.database.EasyRadioDatabase
import com.easyradio.core.database.PodcastRepository
import com.easyradio.core.media.PlaybackStateMapper
import com.easyradio.core.media.PlaybackUiState
import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast
import com.easyradio.core.model.RadioStation
import com.easyradio.core.network.podcast.EpisodeDownloader
import com.easyradio.core.network.podcast.ItunesSearchApiFactory
import com.easyradio.core.network.podcast.PodcastFeedFetcher
import com.easyradio.core.network.radiobrowser.RadioBrowserApiFactory
import com.easyradio.core.network.radiobrowser.RadioStationRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

private enum class AppTab(val label: String) {
    RADIO("Radio"),
    PODCASTS("Podcasts"),
}

private const val PODCAST_POSITION_SAVE_INTERVAL_MS = 5_000L
private const val SKIP_BACK_MS = 15_000L
private const val SKIP_FORWARD_MS = 30_000L
private val PLAYBACK_SPEEDS = listOf(1.0f, 1.25f, 1.5f, 2.0f)

class MainActivity : ComponentActivity() {

    private val radioRepository = RadioStationRepository(api = RadioBrowserApiFactory.create())

    private val database by lazy {
        Room.databaseBuilder(applicationContext, EasyRadioDatabase::class.java, "easy-radio.db")
            .fallbackToDestructiveMigration()
            .build()
    }
    private val episodeDownloader by lazy {
        EpisodeDownloader(
            client = ItunesSearchApiFactory.defaultClient(),
            downloadsDir = File(applicationContext.filesDir, "podcast_downloads"),
        )
    }
    private val podcastRepository by lazy {
        PodcastRepository(
            itunesApi = ItunesSearchApiFactory.create(),
            fetchFeed = PodcastFeedFetcher(ItunesSearchApiFactory.defaultClient())::fetch,
            podcastDao = database.podcastDao(),
            episodeDao = database.episodeDao(),
            downloadFile = episodeDownloader::download,
            deleteFile = episodeDownloader::delete,
            queueDao = database.queueDao(),
        )
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController by mutableStateOf<MediaController?>(null)
    private var uiState by mutableStateOf(PlaybackUiState.IDLE)
    private var currentStation by mutableStateOf<RadioStation?>(null)
    private var currentEpisode by mutableStateOf<Episode?>(null)
    private var currentPodcast by mutableStateOf<Podcast?>(null)

    private var positionSaveJob: Job? = null
    private var playbackSpeedIndex by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EasyRadioTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var selectedTab by remember { mutableStateOf(AppTab.RADIO) }

                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(selectedTabIndex = selectedTab.ordinal) {
                            AppTab.entries.forEach { tab ->
                                Tab(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    text = { Text(tab.label) },
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            when (selectedTab) {
                                AppTab.RADIO -> RadioBrowseScreen(
                                    repository = radioRepository,
                                    onStationSelected = ::playStation,
                                )
                                AppTab.PODCASTS -> PodcastsScreen(
                                    repository = podcastRepository,
                                    onEpisodeSelected = { podcast, episode -> playEpisode(podcast, episode) },
                                )
                            }
                        }

                        val station = currentStation
                        val episode = currentEpisode
                        val podcast = currentPodcast
                        when {
                            station != null -> NowPlayingBar(
                                title = station.name,
                                tagline = station.tagline,
                                tintSeed = station.id,
                                badgeText = "LIVE",
                                playbackState = uiState,
                                onPlayClick = { playStation(station) },
                                onPauseClick = { mediaController?.pause() },
                            )
                            episode != null -> NowPlayingBar(
                                title = episode.title,
                                tagline = podcast?.title.orEmpty(),
                                tintSeed = episode.podcastId,
                                badgeText = null,
                                playbackState = uiState,
                                onPlayClick = { mediaController?.play() },
                                onPauseClick = { mediaController?.pause() },
                                onSkipBackClick = { skip(-SKIP_BACK_MS) },
                                onSkipForwardClick = { skip(SKIP_FORWARD_MS) },
                                onSpeedClick = ::cyclePlaybackSpeed,
                                speedLabel = "${PLAYBACK_SPEEDS[playbackSpeedIndex]}x",
                            )
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
