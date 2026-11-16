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

/** Every migration the app knows how to run, in order; pass to `Room.databaseBuilder(...).addMigrations(*ALL_MIGRATIONS)`. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_8_9, MIGRATION_9_10)
