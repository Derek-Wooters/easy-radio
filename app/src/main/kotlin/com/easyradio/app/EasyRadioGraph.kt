package com.easyradio.app

import android.content.Context
import androidx.room.Room
import com.easyradio.core.database.EasyRadioDatabase
import com.easyradio.core.database.FavoriteStationRepository
import com.easyradio.core.database.PodcastRepository
import com.easyradio.core.database.RecentlyPlayedRepository
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
    private var database: EasyRadioDatabase? = null

    @Volatile
    private var repository: PodcastRepository? = null

    @Volatile
    private var settingsRepository: SettingsRepository? = null

    @Volatile
    private var favoriteStationRepository: FavoriteStationRepository? = null

    @Volatile
    private var recentlyPlayedRepository: RecentlyPlayedRepository? = null

    fun repository(context: Context): PodcastRepository =
        repository ?: synchronized(this) {
            repository ?: buildPodcastRepository(context.applicationContext, database(context)).also {
                repository = it
            }
        }

    fun settings(context: Context): SettingsRepository =
        settingsRepository ?: synchronized(this) {
            settingsRepository ?: SettingsRepository(context.applicationContext).also { settingsRepository = it }
        }

    fun favoriteStations(context: Context): FavoriteStationRepository =
        favoriteStationRepository ?: synchronized(this) {
            favoriteStationRepository
                ?: FavoriteStationRepository(database(context).favoriteStationDao()).also {
                    favoriteStationRepository = it
                }
        }

    fun recentlyPlayed(context: Context): RecentlyPlayedRepository =
        recentlyPlayedRepository ?: synchronized(this) {
            recentlyPlayedRepository
                ?: RecentlyPlayedRepository(database(context).recentlyPlayedDao()).also {
                    recentlyPlayedRepository = it
                }
        }

    private fun database(context: Context): EasyRadioDatabase =
        database ?: synchronized(this) {
            database ?: Room.databaseBuilder(context.applicationContext, EasyRadioDatabase::class.java, "easy-radio.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                .also { database = it }
        }

    private fun buildPodcastRepository(appContext: Context, database: EasyRadioDatabase): PodcastRepository {
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
