package com.easyradio.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PodcastEntity::class,
        EpisodeEntity::class,
        QueueItemEntity::class,
        FavoriteStationEntity::class,
        RecentlyPlayedEntity::class,
        ListeningStatsEntity::class,
        BookmarkEntity::class,
        SmartPlaylistEntity::class,
    ],
    version = 14,
    exportSchema = true,
)
abstract class EasyRadioDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun queueDao(): QueueDao
    abstract fun favoriteStationDao(): FavoriteStationDao
    abstract fun recentlyPlayedDao(): RecentlyPlayedDao
    abstract fun listeningStatsDao(): ListeningStatsDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun smartPlaylistDao(): SmartPlaylistDao
}
