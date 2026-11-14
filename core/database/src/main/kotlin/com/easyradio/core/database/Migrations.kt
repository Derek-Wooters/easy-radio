package com.easyradio.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds per-podcast new-episode notification and auto-download preferences (both default
 * off -- see [com.easyradio.core.model.Podcast]), replacing the single global auto-download
 * setting. Plain ADD COLUMN, so existing subscriptions, downloads, and listening history
 * survive the upgrade instead of the destructive-recreate this app relied on through
 * version 8.
 */
val MIGRATION_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE podcasts ADD COLUMN notifyNewEpisodes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE podcasts ADD COLUMN autoDownloadNewEpisodes INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Adds per-podcast "skip first"/"skip last" seconds (both default 0/off -- see
 * [com.easyradio.core.model.Podcast]), set from that show's Options tab to auto-skip a fixed
 * intro or outro on every episode.
 */
val MIGRATION_9_10: Migration = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE podcasts ADD COLUMN skipFirstSeconds INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE podcasts ADD COLUMN skipLastSeconds INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Adds a per-podcast playback speed (default 1.0x -- see [com.easyradio.core.model.Podcast]), set
 * implicitly whenever the user adjusts speed while playing an episode of that show, instead of
 * always resetting to 1.0x on the next episode.
 */
val MIGRATION_10_11: Migration = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE podcasts ADD COLUMN playbackSpeed REAL NOT NULL DEFAULT 1.0")
    }
}

/**
 * Adds per-episode starring (default off -- see [com.easyradio.core.model.Episode]) and a new
 * bookmarks table (saved timestamps within an episode, created from Now Playing's bookmark
 * button -- see [com.easyradio.core.model.Bookmark]). Shipped together since both are part of
 * the same "episode interactions" feature.
 */
val MIGRATION_11_12: Migration = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE episodes ADD COLUMN isStarred INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bookmarks (
                id TEXT NOT NULL PRIMARY KEY,
                episodeId TEXT NOT NULL,
                positionMs INTEGER NOT NULL,
                note TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

/**
 * Adds the `smart_playlists` table backing user-defined custom smart playlists (see
 * [com.easyradio.core.model.SmartPlaylist]) -- a new table only, no existing columns touched.
 */
val MIGRATION_12_13: Migration = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS smart_playlists (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                podcastIds TEXT NOT NULL,
                status TEXT NOT NULL,
                downloadedOnly INTEGER NOT NULL,
                starredOnly INTEGER NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

/**
 * Adds a per-episode `<itunes:season>` number (see [com.easyradio.core.model.Episode.season]),
 * parsed from the feed -- nullable, no default needed since most shows never publish it.
 */
val MIGRATION_13_14: Migration = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE episodes ADD COLUMN season INTEGER")
    }
}

/** Every migration the app knows how to run, in order; pass to `Room.databaseBuilder(...).addMigrations(*ALL_MIGRATIONS)`. */
val ALL_MIGRATIONS: Array<Migration> =
    arrayOf(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
