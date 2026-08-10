package com.easyradio.app

import android.content.Context
import androidx.room.Room
import com.easyradio.core.database.EasyRadioDatabase
import com.easyradio.core.database.PodcastRepository
import com.easyradio.core.network.podcast.EpisodeDownloader
import com.easyradio.core.network.podcast.ItunesSearchApiFactory
import com.easyradio.core.network.podcast.PodcastFeedFetcher
import java.io.File

/**
 * Single owner of the Room database and podcast stack, shared by [MainActivity]
 * and the Android Auto browse tree in
 * [com.easyradio.app.playback.EasyRadioPlaybackService]. Both the phone UI and
 * the service must read/write one database instance rather than opening two
 * competing handles on the same "easy-radio.db" file.
 */
object EasyRadioGraph {

    @Volatile
    private var repository: PodcastRepository? = null

    @Volatile
    private var settingsRepository: SettingsRepository? = null

    fun repository(context: Context): PodcastRepository =
        repository ?: synchronized(this) {
            repository ?: build(context.applicationContext).also { repository = it }
        }

    fun settings(context: Context): SettingsRepository =
        settingsRepository ?: synchronized(this) {
            settingsRepository ?: SettingsRepository(context.applicationContext).also { settingsRepository = it }
        }

    private fun build(appContext: Context): PodcastRepository {
        val database = Room.databaseBuilder(appContext, EasyRadioDatabase::class.java, "easy-radio.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
        val downloader = EpisodeDownloader(
            client = ItunesSearchApiFactory.defaultClient(),
            downloadsDir = File(appContext.filesDir, "podcast_downloads"),
        )
        return PodcastRepository(
            itunesApi = ItunesSearchApiFactory.create(),
            fetchFeed = PodcastFeedFetcher(ItunesSearchApiFactory.defaultClient())::fetch,
            podcastDao = database.podcastDao(),
            episodeDao = database.episodeDao(),
            downloadFile = downloader::download,
            deleteFile = downloader::delete,
            queueDao = database.queueDao(),
        )
    }
}
