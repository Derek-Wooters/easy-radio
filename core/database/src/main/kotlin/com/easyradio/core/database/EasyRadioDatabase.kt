package com.easyradio.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class EasyRadioDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
}
