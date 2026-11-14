package com.easyradio.core.database

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs each migration against a hand-built table matching its real pre-migration PodcastEntity
 * exactly, rather than through Room's MigrationTestHelper. MigrationTestHelper was tried for
 * MIGRATION_9_10 (version 8 predates this project's schema export, so it was never an option for
 * MIGRATION_8_9) and hit a genuine Robolectric/Room 2.8.x incompatibility: opening the test
 * database throws "This driver is configured to open a database named '<name>' but '<full temp
 * path>' was requested" regardless of which SupportSQLiteOpenHelper.Factory is passed in --
 * Robolectric's shadow SQLite layer reports the sandboxed absolute path where Room's newer
 * SupportSQLiteDriver strictly expects the bare name back. Confirmed as a tooling limitation, not
 * a test-code mistake, so every migration here uses this same hand-built-table approach instead.
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
    fun `MIGRATION_9_10 adds both columns defaulting to 0 and preserves existing rows`() {
        val helper = openHelper("migration-test-${System.nanoTime()}.db")
        val db = helper.writableDatabase

        // The exact version-9 "podcasts" table (version 8's columns plus MIGRATION_8_9's two).
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
            INSERT INTO podcasts (
                id, title, author, artworkUrl, feedUrl, subscribedAtEpochMillis,
                isPreset, lastPlayedAtEpochMillis, notifyNewEpisodes, autoDownloadNewEpisodes
            )
            VALUES (
                'p1', 'The Daily', 'NYT', NULL, 'https://example.com/feed.xml', 1000,
                0, NULL, 1, 0
            )
            """.trimIndent(),
        )

        MIGRATION_9_10.migrate(db)

        db.query("SELECT * FROM podcasts WHERE id = 'p1'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("title"))).isEqualTo("The Daily")
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("notifyNewEpisodes"))).isEqualTo(1)
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("skipFirstSeconds"))).isEqualTo(0)
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("skipLastSeconds"))).isEqualTo(0)
        }
        db.close()
    }

    @Test
    fun `MIGRATION_10_11 adds playbackSpeed defaulting to 1_0 and preserves existing rows`() {
        val helper = openHelper("migration-test-${System.nanoTime()}.db")
        val db = helper.writableDatabase

        // The exact version-10 "podcasts" table (version 9's columns plus MIGRATION_9_10's two).
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
                autoDownloadNewEpisodes INTEGER NOT NULL DEFAULT 0,
                skipFirstSeconds INTEGER NOT NULL DEFAULT 0,
                skipLastSeconds INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO podcasts (
                id, title, author, artworkUrl, feedUrl, subscribedAtEpochMillis,
                isPreset, lastPlayedAtEpochMillis, notifyNewEpisodes, autoDownloadNewEpisodes,
                skipFirstSeconds, skipLastSeconds
            )
            VALUES (
                'p1', 'The Daily', 'NYT', NULL, 'https://example.com/feed.xml', 1000,
                0, NULL, 1, 0, 30, 0
            )
            """.trimIndent(),
        )

        MIGRATION_10_11.migrate(db)

        db.query("SELECT * FROM podcasts WHERE id = 'p1'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("title"))).isEqualTo("The Daily")
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("skipFirstSeconds"))).isEqualTo(30)
            assertThat(cursor.getFloat(cursor.getColumnIndexOrThrow("playbackSpeed"))).isEqualTo(1.0f)
        }
        db.close()
    }
}
