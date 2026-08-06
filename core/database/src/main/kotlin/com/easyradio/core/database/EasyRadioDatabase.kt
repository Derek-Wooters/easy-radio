package com.easyradio.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class, QueueItemEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class EasyRadioDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun queueDao(): QueueDao
}
