package com.easyradio.core.database

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs MIGRATION_8_9 against a hand-built version-8 "podcasts" table (matching the real
 * pre-migration PodcastEntity exactly) rather than a full Room-opened database -- version 8
 * predates this project turning schema export on, so there's no exported schema file to drive
 * Room's usual MigrationTestHelper for that specific transition. Every migration from here on
 * gets that proper helper-based test instead; see core/database/schemas/.
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
}
