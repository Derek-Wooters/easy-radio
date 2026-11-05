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
 * Adds the transcript look-ahead ad-skipping feature: a per-podcast opt-in
 * ([com.easyradio.core.model.Podcast.adSkipEnabled]), a per-episode processing-state marker,
 * and a table of detected ad segments. Plain ADD COLUMN/CREATE TABLE, same no-data-loss shape
 * as [MIGRATION_8_9].
 */
val MIGRATION_9_10: Migration = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE podcasts ADD COLUMN adSkipEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE episodes ADD COLUMN adDetectionState TEXT NOT NULL DEFAULT 'NOT_PROCESSED'")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS ad_segments (" +
                "episodeId TEXT NOT NULL, startMs INTEGER NOT NULL, endMs INTEGER NOT NULL, " +
                "PRIMARY KEY(episodeId, startMs))",
        )
    }
}

/** Every migration the app knows how to run, in order; pass to `Room.databaseBuilder(...).addMigrations(*ALL_MIGRATIONS)`. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_8_9, MIGRATION_9_10)
