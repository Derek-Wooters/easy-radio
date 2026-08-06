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
class EpisodeDaoTest {

    private lateinit var database: EasyRadioDatabase
    private lateinit var dao: EpisodeDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EasyRadioDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.episodeDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun episode(
        id: String,
        podcastId: String,
        publishedAt: Long? = 100L,
        positionMs: Long = 0,
    ) = EpisodeEntity(
        id = id,
        podcastId = podcastId,
        title = "Episode $id",
        audioUrl = "https://example.com/$id.mp3",
        publishedAtEpochMillis = publishedAt,
        durationSeconds = 1800,
        description = "",
        positionMs = positionMs,
    )

    @Test
    fun `upsertAll then observeByPodcast returns only episodes for that podcast`() = runTest {
        dao.upsertAll(listOf(episode("e1", "p1"), episode("e2", "p1"), episode("e3", "p2")))

        val forP1 = dao.observeByPodcast("p1").first()

        assertThat(forP1).hasSize(2)
        assertThat(forP1.map { it.id }).containsExactly("e1", "e2")
    }

    @Test
    fun `updatePosition then getPosition returns the updated value`() = runTest {
        dao.upsertAll(listOf(episode("e1", "p1")))

        dao.updatePosition("e1", 45_000L)

        assertThat(dao.getPosition("e1")).isEqualTo(45_000L)
    }

    @Test
    fun `getPosition returns null for an unknown episode`() = runTest {
        assertThat(dao.getPosition("does-not-exist")).isNull()
    }
}
