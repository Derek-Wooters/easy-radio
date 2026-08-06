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
class PodcastDaoTest {

    private lateinit var database: EasyRadioDatabase
    private lateinit var dao: PodcastDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EasyRadioDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.podcastDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun podcast(id: String, title: String, subscribedAt: Long = 100L) = PodcastEntity(
        id = id,
        title = title,
        author = "Author",
        artworkUrl = null,
        feedUrl = "https://example.com/$id.xml",
        subscribedAtEpochMillis = subscribedAt,
    )

    @Test
    fun `upsert then observeAll returns the podcast`() = runTest {
        dao.upsert(podcast("p1", "Show One"))

        val all = dao.observeAll().first()

        assertThat(all).hasSize(1)
        assertThat(all.first().id).isEqualTo("p1")
    }

    @Test
    fun `delete removes the podcast`() = runTest {
        dao.upsert(podcast("p1", "Show One"))

        dao.delete("p1")

        assertThat(dao.observeAll().first()).isEmpty()
    }

    @Test
    fun `upsert with same id replaces the existing row`() = runTest {
        dao.upsert(podcast("p1", "Old Title"))
        dao.upsert(podcast("p1", "New Title"))

        val all = dao.observeAll().first()

        assertThat(all).hasSize(1)
        assertThat(all.first().title).isEqualTo("New Title")
    }
}
