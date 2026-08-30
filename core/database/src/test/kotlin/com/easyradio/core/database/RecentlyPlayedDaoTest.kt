package com.easyradio.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecentlyPlayedDaoTest {

    private lateinit var database: EasyRadioDatabase
    private lateinit var dao: RecentlyPlayedDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EasyRadioDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.recentlyPlayedDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun item(id: String, playedAt: Long) = RecentlyPlayedEntity(
        contentId = id,
        type = "STATION",
        title = "Title $id",
        subtitle = "Subtitle",
        imageUrl = null,
        playedAtEpochMillis = playedAt,
        stationStreamUrl = "https://example.com/$id.mp3",
        podcastId = null,
    )

    @Test
    fun `upsert then observeRecent returns the item`() = runTest {
        dao.upsert(item("r1", playedAt = 100L))

        val recent = dao.observeRecent().first()

        assertThat(recent).hasSize(1)
        assertThat(recent.first().contentId).isEqualTo("r1")
    }

    @Test
    fun `observeRecent orders most recently played first`() = runTest {
        dao.upsert(item("r1", playedAt = 100L))
        dao.upsert(item("r2", playedAt = 200L))

        val recent = dao.observeRecent().first()

        assertThat(recent.map { it.contentId }).containsExactly("r2", "r1").inOrder()
    }

    @Test
    fun `re-playing the same content replaces its row instead of duplicating`() = runTest {
        dao.upsert(item("r1", playedAt = 100L))
        dao.upsert(item("r1", playedAt = 200L))

        val recent = dao.observeRecent().first()

        assertThat(recent).hasSize(1)
        assertThat(recent.first().playedAtEpochMillis).isEqualTo(200L)
    }

    @Test
    fun `trim caps the table to the 20 most recently played rows`() = runTest {
        for (i in 1..25) {
            dao.upsert(item("r$i", playedAt = i.toLong()))
        }

        dao.trim()

        val recent = dao.observeRecent().first()
        assertThat(recent).hasSize(20)
        assertThat(recent.map { it.contentId }).contains("r25")
        assertThat(recent.map { it.contentId }).doesNotContain("r1")
    }
}
