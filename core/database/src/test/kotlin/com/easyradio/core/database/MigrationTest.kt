package com.easyradio.core.database

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs each migration against a hand-built pre-migration table (matching the real entity as it
 * existed before that migration) rather than a full Room-opened database -- kept consistent
 * with MIGRATION_8_9's original test rather than switching to Room's MigrationTestHelper, since
 * the hand-built approach is already proven in this module and doesn't need an
 * instrumentation/Robolectric interop story this codebase hasn't established yet.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private fun openHelper(name: String): SupportSQLiteOpenHelper {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    @Test
    fun `MIGRATION_8_9 adds both columns defaulting to false and preserves existing rows`() {
        val helper = openHelper("migration-test-${System.nanoTime()}.db")
        val db = helper.writableDatabase

        // The exact version-8 "podcasts" table (core/database's PodcastEntity before this
        // migration was added), built by hand since version 8 predates schema export.
        db.execSQL(
            """
            CREATE TABLE podcasts (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                author TEXT NOT NULL,
                artworkUrl TEXT,
                feedUrl TEXT NOT NULL,
                subscribedAtEpochMillis INTEGER NOT NULL,
                isPreset INTEGER NOT NULL,
                lastPlayedAtEpochMillis INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO podcasts (id, title, author, artworkUrl, feedUrl, subscribedAtEpochMillis, isPreset, lastPlayedAtEpochMillis)
            VALUES ('p1', 'The Daily', 'NYT', NULL, 'https://example.com/feed.xml', 1000, 0, NULL)
            """.trimIndent(),
        )

        MIGRATION_8_9.migrate(db)

        db.query("SELECT * FROM podcasts WHERE id = 'p1'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("title"))).isEqualTo("The Daily")
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("notifyNewEpisodes"))).isEqualTo(0)
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("autoDownloadNewEpisodes"))).isEqualTo(0)
        }
        db.close()
    }

    @Test
    fun `MIGRATION_9_10 adds ad-skip columns and the ad_segments table, preserving existing rows`() {
        val helper = openHelper("migration-test-${System.nanoTime()}.db")
        val db = helper.writableDatabase

        // The exact version-9 "podcasts" and "episodes" tables (before this migration's columns).
        db.execSQL(
            """
            CREATE TABLE podcasts (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                author TEXT NOT NULL,
                artworkUrl TEXT,
                feedUrl TEXT NOT NULL,
                subscribedAtEpochMillis INTEGER NOT NULL,
                isPreset INTEGER NOT NULL,
                lastPlayedAtEpochMillis INTEGER,
                notifyNewEpisodes INTEGER NOT NULL DEFAULT 0,
                autoDownloadNewEpisodes INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE episodes (
                id TEXT NOT NULL PRIMARY KEY,
                podcastId TEXT NOT NULL,
                title TEXT NOT NULL,
                audioUrl TEXT NOT NULL,
                publishedAtEpochMillis INTEGER,
                durationSeconds INTEGER,
                description TEXT NOT NULL,
                positionMs INTEGER NOT NULL,
                localFilePath TEXT,
                chaptersUrl TEXT,
                transcriptUrl TEXT,
                transcriptType TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO podcasts (id, title, author, artworkUrl, feedUrl, subscribedAtEpochMillis, isPreset, lastPlayedAtEpochMillis)
            VALUES ('p1', 'Stuff To Blow Your Mind', 'iHeart', NULL, 'https://example.com/feed.xml', 1000, 0, NULL)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO episodes (id, podcastId, title, audioUrl, publishedAtEpochMillis, durationSeconds, description, positionMs)
            VALUES ('e1', 'p1', 'An Episode', 'https://example.com/e1.mp3', 2000, 3600, '', 0)
            """.trimIndent(),
        )

        MIGRATION_9_10.migrate(db)

        db.query("SELECT * FROM podcasts WHERE id = 'p1'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("adSkipEnabled"))).isEqualTo(0)
        }
        db.query("SELECT * FROM episodes WHERE id = 'e1'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("adDetectionState"))).isEqualTo("NOT_PROCESSED")
        }
        db.execSQL("INSERT INTO ad_segments (episodeId, startMs, endMs) VALUES ('e1', 0, 120000)")
        db.query("SELECT * FROM ad_segments WHERE episodeId = 'e1'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getLong(cursor.getColumnIndexOrThrow("endMs"))).isEqualTo(120000L)
        }
        db.close()
    }
}
