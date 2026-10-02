package com.easyradio.app

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import com.easyradio.app.ui.DownloadsScreen
import com.easyradio.app.ui.HomeScreen
import com.easyradio.app.ui.MiniPlayerScaffold
import com.easyradio.app.ui.NowPlayingBar
import com.easyradio.app.ui.NowPlayingScreen
import com.easyradio.app.ui.OnboardingScreen
import com.easyradio.app.ui.PlaylistsScreen
import com.easyradio.app.ui.QueueScreen
import com.easyradio.app.ui.SearchScreen
import com.easyradio.core.database.FavoriteStationRepository
import com.easyradio.core.database.RecentlyPlayedRepository
import com.easyradio.core.model.RecentlyPlayedItem
import com.easyradio.core.model.RecentlyPlayedType
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import com.easyradio.app.playback.ACTION_SLEEP_AT_END_OF_EPISODE
import com.easyradio.app.playback.EasyRadioPlaybackService
import com.easyradio.core.media.MediaBrowseTree
import com.easyradio.app.ui.PodcastsScreen
import com.easyradio.app.ui.RadioBrowseScreen
import com.easyradio.app.ui.theme.EasyRadioTheme
import com.easyradio.core.media.PlaybackUiState
import com.easyradio.core.model.Episode
import com.easyradio.core.model.Podcast
import com.easyradio.core.model.RadioStation
import com.easyradio.core.network.radiobrowser.RadioBrowserApiFactory
import com.easyradio.core.network.radiobrowser.RadioStationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.os.SystemClock
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.easyradio.app.ui.SettingsScreen
import com.easyradio.core.media.SleepTimer
import com.easyradio.core.model.AppSettings
import androidx.compose.ui.graphics.vector.ImageVector

private enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    SEARCH("Search", Icons.Filled.Search),
    RADIO("Radio", Icons.Filled.Radio),
    PODCASTS("Podcasts", Icons.Filled.Podcasts),
    PLAYLISTS("Playlists", Icons.AutoMirrored.Filled.PlaylistPlay),
}

private const val PODCAST_POSITION_SAVE_INTERVAL_MS = 5_000L
private val PLAYBACK_SPEEDS = listOf(1.0f, 1.25f, 1.5f, 2.0f)

// Must cover NowPlayingBar's own rendered content within this single sheetPeekHeight
// allocation (no drag handle to budget for anymore -- see sheetDragHandle = null below).
// 80dp verified on-device (via uiautomator bounds) to fully reveal the mini-player row
// without clipping it against the screen edge, with only a small margin to spare.
private val MINI_PLAYER_HEIGHT = 80.dp

internal fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/** Null (no progress bar) until a duration is actually known -- see [formatDuration]'s caller. */
internal fun episodeProgressFraction(positionMs: Long, durationMs: Long): Float? =
    if (durationMs > 0) positionMs.toFloat() / durationMs else null

/**
 * The mini-player's subtitle for a playing episode: "<podcast> · <time left> left" once a
 * duration is known, falling back to just the podcast title (or an empty string) until then.
 */
internal fun episodeMiniPlayerTagline(podcastTitle: String, positionMs: Long, durationMs: Long): String {
    if (durationMs <= 0) return podcastTitle
    val left = formatDuration((durationMs - positionMs).coerceAtLeast(0))
    return if (podcastTitle.isNotEmpty()) "$podcastTitle · $left left" else "$left left"
}

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}

    private var opmlMessage by mutableStateOf<String?>(null)

    private val exportOpmlLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/xml"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val opml = podcastRepository.exportOpml()
            opmlMessage = try {
                contentResolver.openOutputStream(uri)?.use { it.write(opml.toByteArray()) }
                "Subscriptions exported"
            } catch (e: Exception) {
                "Export failed"
            }
        }
    }

    private val importOpmlLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            opmlMessage = try {
                val xml = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (xml == null) {
                    "Couldn't read file"
                } else {
                    val count = podcastRepository.importOpml(xml)
                    if (count == 0) "No new subscriptions found" else "Imported $count subscription${if (count == 1) "" else "s"}"
                }
            } catch (e: Exception) {
                "Import failed"
            }
        }
    }

    private val radioRepository = RadioStationRepository(api = RadioBrowserApiFactory.create())

    private val podcastRepository by lazy { EasyRadioGraph.repository(applicationContext) }
    private val settingsRepository by lazy { EasyRadioGraph.settings(applicationContext) }
    private val favoriteStationRepository by lazy { EasyRadioGraph.favoriteStations(applicationContext) }
    private val recentlyPlayedRepository by lazy { EasyRadioGraph.recentlyPlayed(applicationContext) }
    private val listeningStatsRepository by lazy { EasyRadioGraph.listeningStats(applicationContext) }

    private var mediaBrowser: MediaBrowserCompat? = null

    // The last episode media id this activity has already reflected in currentEpisode/
    // currentPodcast, so a metadata echo of our own playEpisode() call (or a repeat) doesn't
    // trigger a redundant resync. Set both when we resync from a controller callback and
    // immediately in playEpisode() itself, since a manual play already knows the answer.
    private var lastSyncedMediaId: String? = null

    // Owns the MediaControllerCompat connection, the reactive uiState it derives from it, and
    // the reconnect/stale-state recovery logic around both -- see SessionConnection's own doc for
    // why that logic lives there now instead of inline here.
    private val sessionConnection = com.easyradio.app.playback.SessionConnection(
        onNowPlayingMediaIdChanged = ::syncNowPlayingFromMediaId,
        onSessionDestroyed = {
            // The service stopping itself out from under an already-foregrounded MainActivity
            // (no onStop()/onStart() cycle to naturally reconnect through) previously left stale
            // now-playing info on screen with a Play button that called transportControls on a
            // dead controller -- clear it so the UI matches reality, then reconnect so playback
            // (new or resumed) works again without the user needing to background/reopen the app.
            lastSyncedMediaId = null
            currentStation = null
            currentEpisode = null
            currentPodcast = null
            connectToPlaybackService()
        },
    )

    private var currentStation by mutableStateOf<RadioStation?>(null)
    private var currentEpisode by mutableStateOf<Episode?>(null)
    private var currentChapters by mutableStateOf<List<com.easyradio.core.model.Chapter>>(emptyList())
    private var currentTranscript by mutableStateOf<String?>(null)
    private var currentPodcast by mutableStateOf<Podcast?>(null)

    private var playbackSpeedIndex by mutableStateOf(0)
    private var positionMs by mutableStateOf(0L)
    private var durationMs by mutableStateOf(0L)
    // playStation()/playEpisode() run outside composition and can't call the suspend
    // SheetState.expand() directly, so they bump this counter instead; a LaunchedEffect
    // inside the composable (which does have access to the sheet state) reacts to it.
    private var expandRequestId by mutableStateOf(0)
    // Bumped from onStart() (the app gaining focus) so the mini-player's sheet gets another
    // explicit chance to leave Hidden if it should already be showing content -- the sheet's own
    // hasContent-keyed effect only fires on an actual false-to-true transition, which can miss
    // cases where hasContent was already true for the entire time the app was backgrounded (e.g.
    // resyncing to a station/episode kept alive by the service the whole time). Reported from a
    // real device: reopening the app after that could leave the now-playing bar invisible despite
    // the underlying state being entirely correct.
    private var visibilityRefreshToken by mutableStateOf(0)
    private var selectedTab by mutableStateOf(AppTab.HOME)
    private var showQueue by mutableStateOf(false)
    private var showSettings by mutableStateOf(false)
    private var showDownloads by mutableStateOf(false)
    private var showSleepTimerPicker by mutableStateOf(false)
    private var searchSelectedPodcast by mutableStateOf<Podcast?>(null)
    private var showOnboarding by mutableStateOf(false)
    private var onboardingGenres by mutableStateOf<Set<String>>(emptySet())

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        scheduleNewEpisodeCheck()
        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
            val listenedTodaySeconds by listeningStatsRepository.totalSecondsForLast(1).collectAsState(initial = 0L)
            val listenedThisWeekSeconds by listeningStatsRepository.totalSecondsForLast(7).collectAsState(initial = 0L)
            val listenedAllTimeSeconds by listeningStatsRepository.totalSecondsAllTime().collectAsState(initial = 0L)

            LaunchedEffect(Unit) {
                // Wait for the first real DataStore emission rather than the collectAsState
                // default above, so a returning user never sees a flash of onboarding while
                // the real "already completed" value is still loading.
                showOnboarding = !settingsRepository.settings.first().hasCompletedOnboarding
            }

            LaunchedEffect(Unit) {
                while (true) {
                    delay(PODCAST_POSITION_SAVE_INTERVAL_MS)
                    if (sessionConnection.uiState == PlaybackUiState.PLAYING) {
                        listeningStatsRepository.addListenedSeconds(PODCAST_POSITION_SAVE_INTERVAL_MS / 1_000)
                    }
                }
            }

            LaunchedEffect(currentEpisode?.id) {
                currentChapters = currentEpisode?.let { podcastRepository.loadChapters(it) }.orEmpty()
            }

            LaunchedEffect(currentEpisode?.id) {
                currentTranscript = currentEpisode?.let { podcastRepository.loadTranscript(it) }
            }

            LaunchedEffect(currentEpisode?.id, sessionConnection.controller) {
                val controller = sessionConnection.controller
                val episode = currentEpisode
                if (episode != null) {
                    while (true) {
                        if (controller != null && sessionConnection.uiState != PlaybackUiState.IDLE) {
                            positionMs = controllerPositionMs(controller).coerceAtLeast(0)
                            durationMs = controllerDurationMs(controller).coerceAtLeast(0)
                        } else {
                            // The live session can genuinely have nothing loaded right after
                            // reconnecting (e.g. the service was torn down while the app was
                            // backgrounded and reconnected fresh) -- its own position/duration
                            // read 0/0 in that state, which made the progress bar look broken
                            // (reset to the very start) despite a real, resumable position
                            // existing. Show the app's own saved position/known duration instead
                            // until something's actually loaded and playing again -- checked
                            // every tick, not just once, so a later resumeOrRestartEpisode() tap
                            // (which reuses this same controller, just with real media loaded)
                            // switches back to live polling without needing this effect to restart.
                            positionMs = podcastRepository.lastPosition(episode.id)
                            durationMs = (episode.durationSeconds ?: 0) * 1_000L
                        }
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
                    sessionConnection.controller?.transportControls?.pause()
                }
            }

            EasyRadioTheme(darkTheme = settings.themeMode.resolveDarkTheme(isSystemInDarkTheme())) {
                val playing = sessionConnection.uiState == PlaybackUiState.PLAYING || sessionConnection.uiState == PlaybackUiState.BUFFERING
                val favoriteStationIds by favoriteStationRepository.favoriteIds()
                    .collectAsState(initial = emptySet())
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(currentStation?.id, currentEpisode?.id, playing) {
                    val title = currentStation?.name ?: currentEpisode?.title ?: "Easy Radio"
                    val subtitle = currentStation?.tagline ?: currentPodcast?.title ?: "Nothing playing"
                    com.easyradio.app.widget.EasyRadioWidget.updateState(applicationContext, title, subtitle, playing)
                }

                LaunchedEffect(sessionConnection.uiState) {
                    if (sessionConnection.uiState == PlaybackUiState.ERROR) {
                        val name = currentStation?.name ?: currentEpisode?.title
                        val message = if (name != null) {
                            "Couldn't play \"$name\". Check your connection and try again."
                        } else {
                            "Playback failed. Check your connection and try again."
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                }

                LaunchedEffect(opmlMessage) {
                    opmlMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        opmlMessage = null
                    }
                }

                BackHandler(enabled = showQueue) { showQueue = false }
                BackHandler(enabled = showSettings) { showSettings = false }
                BackHandler(enabled = showDownloads) { showDownloads = false }

                if (showSleepTimerPicker) {
                    AlertDialog(
                        onDismissRequest = { showSleepTimerPicker = false },
                        confirmButton = {},
                        title = { Text("Sleep timer") },
                        text = {
                            Column {
                                listOf(0, 5, 15, 30, 45, 60).forEach { minutes ->
                                    TextButton(onClick = {
                                        lifecycleScope.launch { settingsRepository.setSleepTimerMinutes(minutes) }
                                        showSleepTimerPicker = false
                                    }) {
                                        Text(if (minutes == 0) "Off" else "$minutes min")
                                    }
                                }
                                TextButton(onClick = {
                                    // Mutually exclusive with the minutes-based countdown above --
                                    // clear any running one so it can't also fire later and pause
                                    // (again, redundantly) after this episode has already stopped.
                                    lifecycleScope.launch { settingsRepository.setSleepTimerMinutes(0) }
                                    sessionConnection.controller?.transportControls?.sendCustomAction(ACTION_SLEEP_AT_END_OF_EPISODE, null)
                                    showSleepTimerPicker = false
                                }) {
                                    Text("End of episode")
                                }
                            }
                        },
                    )
                }

                if (showOnboarding) {
                    OnboardingScreen(
                        selectedGenres = onboardingGenres,
                        onToggleGenre = { genre ->
                            onboardingGenres = if (genre in onboardingGenres) {
                                onboardingGenres - genre
                            } else {
                                onboardingGenres + genre
                            }
                        },
                        onSkip = {
                            showOnboarding = false
                            lifecycleScope.launch { settingsRepository.completeOnboarding(emptySet()) }
                        },
                        onContinue = {
                            showOnboarding = false
                            lifecycleScope.launch { settingsRepository.completeOnboarding(onboardingGenres) }
                        },
                    )
                } else if (showSettings) {
                    SettingsScreen(
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
                        onSkipBackSecondsChange = {
                            lifecycleScope.launch { settingsRepository.setSkipBackSeconds(it) }
                        },
                        onSkipForwardSecondsChange = {
                            lifecycleScope.launch { settingsRepository.setSkipForwardSeconds(it) }
                        },
                        onSkipSilenceEnabledChange = {
                            lifecycleScope.launch { settingsRepository.setSkipSilenceEnabled(it) }
                        },
                        onVoiceBoostEnabledChange = {
                            lifecycleScope.launch { settingsRepository.setVoiceBoostEnabled(it) }
                        },
                        onManageDownloadsClick = {
                            showSettings = false
                            showDownloads = true
                        },
                        listenedTodaySeconds = listenedTodaySeconds,
                        listenedThisWeekSeconds = listenedThisWeekSeconds,
                        listenedAllTimeSeconds = listenedAllTimeSeconds,
                        onBack = { showSettings = false },
                    )
                } else if (showDownloads) {
                    DownloadsScreen(
                        repository = podcastRepository,
                        onBack = { showDownloads = false },
                    )
                } else if (showQueue) {
                    QueueScreen(
                        repository = podcastRepository,
                        onBack = { showQueue = false },
                        onEpisodeSelected = { episode ->
                            lifecycleScope.launch {
                                val podcast = resolvePlayablePodcast(
                                    episode,
                                    podcastRepository.subscribedPodcasts().first(),
                                )
                                playEpisode(podcast, episode)
                            }
                            showQueue = false
                        },
                    )
                } else {
                val nothingPlaying = !hasNowPlayingContent(
                    uiState = sessionConnection.uiState,
                    hasStation = currentStation != null,
                    hasEpisode = currentEpisode != null,
                )
                MiniPlayerScaffold(
                    hasContent = !nothingPlaying,
                    expandRequestId = expandRequestId,
                    visibilityRefreshToken = visibilityRefreshToken,
                    peekHeight = MINI_PLAYER_HEIGHT,
                    navigationBar = {
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
                    },
                    collapsedContent = { onExpand ->
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
                                playbackState = sessionConnection.uiState,
                                onPlayClick = { playStation(station) },
                                onPauseClick = { sessionConnection.controller?.transportControls?.pause() },
                                onExpand = onExpand,
                            )
                            episode != null -> {
                                val podcastTitle = podcast?.title.orEmpty()
                                val fraction = episodeProgressFraction(positionMs, durationMs)
                                val tagline = episodeMiniPlayerTagline(podcastTitle, positionMs, durationMs)
                                NowPlayingBar(
                                    title = episode.title,
                                    tagline = tagline,
                                    tintSeed = episode.podcastId,
                                    imageUrl = podcast?.artworkUrl,
                                    badgeText = null,
                                    playbackState = sessionConnection.uiState,
                                    onPlayClick = { resumeOrRestartEpisode(episode) },
                                    onPauseClick = { sessionConnection.controller?.transportControls?.pause() },
                                    onSkipBackClick = { skip(-settings.skipBackSeconds * 1_000L) },
                                    onSkipForwardClick = { skip(settings.skipForwardSeconds * 1_000L) },
                                    skipBackSeconds = settings.skipBackSeconds,
                                    skipForwardSeconds = settings.skipForwardSeconds,
                                    onSpeedClick = ::cyclePlaybackSpeed,
                                    speedLabel = "${PLAYBACK_SPEEDS[playbackSpeedIndex]}x",
                                    progress = fraction,
                                    onExpand = onExpand,
                                )
                            }
                            // uiState says something's active but the resync hasn't resolved
                            // which station/episode yet (or never will) -- a generic but fully
                            // functional bar beats showing nothing at all.
                            else -> NowPlayingBar(
                                title = "Loading…",
                                tagline = "",
                                tintSeed = "loading",
                                imageUrl = null,
                                badgeText = null,
                                playbackState = sessionConnection.uiState,
                                onPlayClick = { sessionConnection.controller?.transportControls?.play() },
                                onPauseClick = { sessionConnection.controller?.transportControls?.pause() },
                                onExpand = onExpand,
                            )
                        }
                    },
                    expandedContent = { onCollapse ->
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
                                isBuffering = sessionConnection.uiState == PlaybackUiState.BUFFERING,
                                progress = null,
                                positionLabel = null,
                                durationLabel = null,
                                speedLabel = null,
                                onCollapse = onCollapse,
                                onPlayPause = { if (playing) sessionConnection.controller?.transportControls?.pause() else playStation(station) },
                                onQueueClick = { showQueue = true },
                                isFavorite = station.id in favoriteStationIds,
                                onFavoriteClick = {
                                    lifecycleScope.launch {
                                        if (station.id in favoriteStationIds) {
                                            favoriteStationRepository.unfavorite(station.id)
                                        } else {
                                            favoriteStationRepository.favorite(station)
                                            // This toggle only ever applies to the currently-playing
                                            // station, so favoriting it now means it's playing now too --
                                            // markPlayed() at playback-start time already no-op'd since
                                            // there was no favorited row yet to update.
                                            favoriteStationRepository.markPlayed(station.id)
                                        }
                                    }
                                },
                            )
                            episode != null -> NowPlayingScreen(
                                topLabel = podcast?.title.orEmpty(),
                                title = episode.title,
                                subtitle = podcast?.title.orEmpty(),
                                imageUrl = podcast?.artworkUrl,
                                tintSeed = episode.podcastId,
                                isLive = false,
                                isPlaying = playing,
                                isBuffering = sessionConnection.uiState == PlaybackUiState.BUFFERING,
                                progress = episodeProgressFraction(positionMs, durationMs),
                                positionLabel = formatDuration(positionMs),
                                durationLabel = formatDuration(durationMs),
                                durationMs = durationMs,
                                onSeek = ::seekToFraction,
                                speedLabel = "${PLAYBACK_SPEEDS[playbackSpeedIndex]}x",
                                onCollapse = onCollapse,
                                onPlayPause = { if (playing) sessionConnection.controller?.transportControls?.pause() else resumeOrRestartEpisode(episode) },
                                onSkipBack = { skip(-settings.skipBackSeconds * 1_000L) },
                                onSkipForward = { skip(settings.skipForwardSeconds * 1_000L) },
                                skipBackSeconds = settings.skipBackSeconds,
                                skipForwardSeconds = settings.skipForwardSeconds,
                                onSpeedClick = ::cyclePlaybackSpeed,
                                onSleepTimerClick = { showSleepTimerPicker = true },
                                onQueueClick = { showQueue = true },
                                chapters = currentChapters,
                                onChapterClick = { chapter ->
                                    if (durationMs > 0) seekToFraction(chapter.startTimeMs.toFloat() / durationMs)
                                },
                                transcript = currentTranscript,
                            )
                            else -> NowPlayingScreen(
                                topLabel = "",
                                title = "Loading…",
                                subtitle = "",
                                imageUrl = null,
                                tintSeed = "loading",
                                isLive = false,
                                isPlaying = playing,
                                isBuffering = sessionConnection.uiState == PlaybackUiState.BUFFERING,
                                progress = null,
                                positionLabel = null,
                                durationLabel = null,
                                speedLabel = null,
                                onCollapse = onCollapse,
                                onPlayPause = {
                                    if (playing) {
                                        sessionConnection.controller?.transportControls?.pause()
                                    } else {
                                        sessionConnection.controller?.transportControls?.play()
                                    }
                                },
                            )
                        }
                    },
                ) {
                Scaffold(
                    modifier = Modifier.padding(bottom = if (nothingPlaying) 0.dp else MINI_PLAYER_HEIGHT),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        when (selectedTab) {
                            AppTab.HOME -> HomeScreen(
                                podcastRepository = podcastRepository,
                                favoriteStationRepository = favoriteStationRepository,
                                recentlyPlayedRepository = recentlyPlayedRepository,
                                radioRepository = radioRepository,
                                favoriteGenres = settings.favoriteGenres,
                                onStationSelected = ::playStation,
                                onPodcastSelected = { podcast ->
                                    searchSelectedPodcast = podcast
                                    selectedTab = AppTab.PODCASTS
                                },
                                onRecentlyPlayedSelected = ::playRecentlyPlayed,
                                onSettingsClick = { showSettings = true },
                            )
                            AppTab.SEARCH -> SearchScreen(
                                radioRepository = radioRepository,
                                podcastRepository = podcastRepository,
                                favoriteStationRepository = favoriteStationRepository,
                                onStationSelected = ::playStation,
                                onPodcastSelected = { podcast ->
                                    searchSelectedPodcast = podcast
                                    selectedTab = AppTab.PODCASTS
                                },
                            )
                            AppTab.RADIO -> RadioBrowseScreen(
                                repository = radioRepository,
                                favoriteStationRepository = favoriteStationRepository,
                                onStationSelected = ::playStation,
                            )
                            AppTab.PODCASTS -> PodcastsScreen(
                                repository = podcastRepository,
                                onEpisodeSelected = { podcast, episode -> playEpisode(podcast, episode) },
                                initialPodcast = searchSelectedPodcast,
                                onInitialPodcastConsumed = { searchSelectedPodcast = null },
                                onExportOpml = { exportOpmlLauncher.launch("easy-radio-subscriptions.opml") },
                                onImportOpml = { importOpmlLauncher.launch(arrayOf("*/*")) },
                            )
                            AppTab.PLAYLISTS -> PlaylistsScreen(
                                repository = favoriteStationRepository,
                                podcastRepository = podcastRepository,
                                onStationSelected = ::playStation,
                                onEpisodeSelected = { podcast, episode -> playEpisode(podcast, episode) },
                            )
                        }
                    }
                }
                }
            }
        }
    }
    }

    private fun playStation(station: RadioStation) {
        adoptNowPlayingStation(station)
        expandRequestId++
        val request = com.easyradio.app.playback.PlaybackRequests.forStation(station)
        sessionConnection.controller?.transportControls?.playFromUri(request.uri, request.extras)
        lifecycleScope.launch {
            recentlyPlayedRepository.record(
                RecentlyPlayedItem(
                    contentId = station.id,
                    type = RecentlyPlayedType.STATION,
                    title = station.name,
                    subtitle = station.tagline,
                    imageUrl = station.imageUrl,
                    playedAtEpochMillis = System.currentTimeMillis(),
                    stationStreamUrl = station.streamUrl,
                ),
            )
            // No-op if this station isn't followed -- there's no row to update.
            favoriteStationRepository.markPlayed(station.id)
        }
    }

    private fun playRecentlyPlayed(item: RecentlyPlayedItem) {
        when (item.type) {
            RecentlyPlayedType.STATION -> {
                val streamUrl = item.stationStreamUrl ?: return
                playStation(
                    RadioStation(
                        id = item.contentId,
                        name = item.title,
                        streamUrl = streamUrl,
                        tagline = item.subtitle,
                        imageUrl = item.imageUrl,
                    ),
                )
            }
            RecentlyPlayedType.EPISODE -> {
                // Recently-played podcasts link to the show's main page, not straight into
                // whichever episode was last played -- unlike radio, there's no single
                // "resume this channel" action that makes sense for a podcast.
                val podcastId = item.podcastId ?: return
                lifecycleScope.launch {
                    val podcast = podcastRepository.subscribedPodcasts().first().find { it.id == podcastId }
                        ?: Podcast(
                            id = podcastId,
                            title = item.title,
                            author = item.subtitle,
                            artworkUrl = item.imageUrl,
                            feedUrl = "https://placeholder.invalid/",
                        )
                    searchSelectedPodcast = podcast
                    selectedTab = AppTab.PODCASTS
                }
            }
        }
    }

    private fun playEpisode(podcast: Podcast, episode: Episode) {
        adoptNowPlayingEpisode(podcast, episode)
        expandRequestId++
        val controller = sessionConnection.controller ?: return

        // The resume position must be baked into the initial playFromUri call, not applied via
        // a later seekTo(): if playWhenReady was already true from a previous item (e.g. the user
        // was already playing something else), starting playback alone would begin this item
        // from 0 immediately, and the resume seek would only land after the fact as a jarring
        // jump -- or never, if this coroutine lost the race with something else changing the
        // media item first. Looking the position up before touching the controller at all
        // removes that window entirely; EasyRadioPlaybackService.startPlayback() then applies it
        // atomically via ExoPlayer's own setMediaItem(item, startPositionMs).
        lifecycleScope.launch {
            val resumeMs = podcastRepository.lastPosition(episode.id)
            val request = com.easyradio.app.playback.PlaybackRequests.forEpisode(podcast, episode, resumeMs)
            controller.transportControls.playFromUri(request.uri, request.extras)
        }
    }

    /**
     * What tapping Play for the currently-adopted episode should actually do: a cheap
     * transportControls.play() resume when the live session still genuinely has this episode
     * loaded (the common pause<->play toggle during normal use), or a full restart via
     * [playEpisode] (which looks up the saved position itself) when it doesn't.
     *
     * uiState == IDLE is what a freshly (re)connected session with nothing loaded at all reports
     * -- confirmed via LegacyPlaybackStateMapper: a brand-new ExoPlayer instance is
     * Player.STATE_IDLE, which maps to PlaybackStateCompat.STATE_NONE, which maps to
     * PlaybackUiState.IDLE. This is a real scenario, not a hypothetical: the service stopping
     * itself while the app is backgrounded (onTaskRemoved while paused, or an explicit
     * ACTION_STOP) and reconnecting fresh when reopened leaves exactly this state, and a bare
     * transportControls.play() in it is a silent no-op -- there's no media item left on the new,
     * empty player for it to resume. currentEpisode/currentPodcast staying exactly as they were
     * (this function doesn't touch them) is deliberate: what the app was last playing is the
     * app's own knowledge, not something the live session's own lifecycle should get to erase.
     */
    private fun resumeOrRestartEpisode(episode: Episode) {
        if (sessionConnection.uiState == PlaybackUiState.IDLE) {
            currentPodcast?.let { playEpisode(it, episode) }
        } else {
            sessionConnection.controller?.transportControls?.play()
        }
    }

    /**
     * Local UI bookkeeping for "this episode is now playing" -- currentEpisode/currentPodcast,
     * position-saving, and Recently Played/markPlayed -- shared by [playEpisode] (a manual,
     * user-initiated play, which also sends the actual playFromUri) and
     * [syncNowPlayingFromMediaId] (the service advanced to this episode on its own; playback is
     * already underway, only the local UI needs to catch up).
     */
    private fun adoptNowPlayingEpisode(podcast: Podcast, episode: Episode) {
        lastSyncedMediaId = MediaBrowseTree.EPISODE_PREFIX + episode.id
        currentStation = null
        currentEpisode = episode
        currentPodcast = podcast
        playbackSpeedIndex = 0

        lifecycleScope.launch {
            recentlyPlayedRepository.record(
                RecentlyPlayedItem(
                    // Keyed by podcast, not episode -- Recently Played shows the show itself
                    // (like a radio station), not whichever episode happened to play last, so
                    // replaying any episode of the same podcast updates one row instead of
                    // piling up a separate entry per episode.
                    contentId = podcast.id,
                    type = RecentlyPlayedType.EPISODE,
                    title = podcast.title,
                    subtitle = podcast.author,
                    imageUrl = podcast.artworkUrl,
                    playedAtEpochMillis = System.currentTimeMillis(),
                    podcastId = podcast.id,
                ),
            )
            // No-op if this podcast isn't subscribed -- there's no row to update.
            podcastRepository.markPlayed(podcast.id)
        }
    }

    /**
     * Local UI bookkeeping for "this station is now playing" -- currentStation/currentEpisode --
     * shared by [playStation] (a manual, user-initiated play, which also sends the actual
     * playFromUri and records Recently Played/markPlayed) and [syncNowPlayingFromMediaId]
     * (MainActivity was recreated while the service kept this exact station playing; nothing new
     * actually started, so -- unlike [adoptNowPlayingEpisode], which auto-advance uses for a
     * genuinely new episode -- this never re-records Recently Played or re-bumps markPlayed).
     */
    private fun adoptNowPlayingStation(station: RadioStation) {
        lastSyncedMediaId = MediaBrowseTree.STATION_PREFIX + station.id
        currentEpisode = null
        currentPodcast = null
        currentStation = station
    }

    /**
     * Reacts to the service's own now-playing media id. Two real cases: the service
     * auto-advancing to the next queued episode on its own, and -- the bug this was extended to
     * fix -- MainActivity being recreated (e.g. backing out of the app, or the process being
     * reclaimed) while the service keeps a station playing independently, which previously left
     * currentStation null and the mini-player/Now Playing bar missing despite audio still
     * genuinely playing. A no-op if [mediaId] isn't a station or episode, or is one we've already
     * adopted (our own play call echoing back through the session, or a repeat notification).
     */
    private fun syncNowPlayingFromMediaId(mediaId: String?) {
        when (val target = com.easyradio.core.media.NowPlayingSyncDecision.resolve(mediaId, lastSyncedMediaId)) {
            is com.easyradio.core.media.NowPlayingSyncTarget.Episode -> {
                lastSyncedMediaId = mediaId
                lifecycleScope.launch {
                    val episode = podcastRepository.allEpisodes().first().firstOrNull { it.id == target.episodeId }
                        ?: return@launch
                    val podcast = resolvePlayablePodcast(episode, podcastRepository.subscribedPodcasts().first())
                    adoptNowPlayingEpisode(podcast, episode)
                }
            }
            is com.easyradio.core.media.NowPlayingSyncTarget.Station -> {
                lastSyncedMediaId = mediaId
                lifecycleScope.launch {
                    // Recently Played already carries everything needed to resume showing this
                    // station -- including a resumable streamUrl -- since playStation() records it
                    // there the moment the station actually started playing (well before this
                    // resync could ever run).
                    val recent = recentlyPlayedRepository.recent().first().firstOrNull {
                        it.type == RecentlyPlayedType.STATION && it.contentId == target.stationId
                    }
                    val streamUrl = recent?.stationStreamUrl ?: return@launch
                    adoptNowPlayingStation(
                        RadioStation(
                            id = target.stationId,
                            name = recent.title,
                            streamUrl = streamUrl,
                            tagline = recent.subtitle,
                            imageUrl = recent.imageUrl,
                        ),
                    )
                }
            }
            null -> Unit
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun scheduleNewEpisodeCheck() {
        val request = androidx.work.PeriodicWorkRequestBuilder<com.easyradio.app.notifications.NewEpisodeCheckWorker>(
            2, java.util.concurrent.TimeUnit.HOURS,
        ).setConstraints(
            androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build(),
        ).build()
        androidx.work.WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "new_episode_check",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun controllerPositionMs(controller: MediaControllerCompat): Long =
        controller.playbackState?.getCurrentPosition(null) ?: 0L

    private fun controllerDurationMs(controller: MediaControllerCompat): Long =
        controller.metadata?.getLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L

    private fun skip(deltaMs: Long) {
        val controller = sessionConnection.controller ?: return
        val target = com.easyradio.core.media.SeekMath.clampSeek(
            currentMs = controllerPositionMs(controller),
            deltaMs = deltaMs,
            durationMs = controllerDurationMs(controller).coerceAtLeast(0),
        )
        controller.transportControls.seekTo(target)
    }

    private fun seekToFraction(fraction: Float) {
        val controller = sessionConnection.controller ?: return
        val duration = controllerDurationMs(controller).coerceAtLeast(0)
        if (duration <= 0) return
        controller.transportControls.seekTo((fraction.coerceIn(0f, 1f) * duration).toLong())
    }

    private fun cyclePlaybackSpeed() {
        playbackSpeedIndex = (playbackSpeedIndex + 1) % PLAYBACK_SPEEDS.size
        sessionConnection.controller?.transportControls?.setPlaybackSpeed(PLAYBACK_SPEEDS[playbackSpeedIndex])
    }

    /**
     * Connects (or reconnects) to [EasyRadioPlaybackService] via a fresh [MediaBrowserCompat].
     * Called from [onStart], and also from the [sessionConnection]'s onSessionDestroyed handler
     * -- the service can stop itself (onTaskRemoved while paused, or an explicit ACTION_STOP)
     * without MainActivity ever going through another onStop()/onStart() cycle if it stayed in
     * the foreground the whole time, which otherwise left a dead controller and a stale,
     * unresponsive now-playing bar on screen forever (confirmed on a real device, and reproduced
     * by sending a STOP media button while the app stayed open). A fresh connect() here
     * recreates the service (MediaBrowserServiceCompat binds with BIND_AUTO_CREATE) exactly as
     * it would on a normal cold start.
     */
    private fun connectToPlaybackService() {
        mediaBrowser?.disconnect()
        val browser = MediaBrowserCompat(
            this,
            ComponentName(this, EasyRadioPlaybackService::class.java),
            object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    val browserRef = mediaBrowser ?: return
                    val controller = MediaControllerCompat(this@MainActivity, browserRef.sessionToken)
                    sessionConnection.attach(controller)
                    MediaControllerCompat.setMediaController(this@MainActivity, controller)
                }
            },
            null,
        )
        mediaBrowser = browser
        browser.connect()
    }

    override fun onStart() {
        super.onStart()
        connectToPlaybackService()
        visibilityRefreshToken++
    }

    override fun onStop() {
        sessionConnection.detach()
        mediaBrowser?.disconnect()
        mediaBrowser = null
        super.onStop()
    }
}
