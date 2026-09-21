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
        title: String = "Episode $id",
    ) = EpisodeEntity(
        id = id,
        podcastId = podcastId,
        title = title,
        audioUrl = "https://example.com/$id.mp3",
        publishedAtEpochMillis = publishedAt,
        durationSeconds = 1800,
        description = "",
        positionMs = positionMs,
    )

    private fun metadata(entity: EpisodeEntity) = EpisodeMetadata(
        id = entity.id,
        podcastId = entity.podcastId,
        title = entity.title,
        audioUrl = entity.audioUrl,
        publishedAtEpochMillis = entity.publishedAtEpochMillis,
        durationSeconds = entity.durationSeconds,
        description = entity.description,
        chaptersUrl = entity.chaptersUrl,
        transcriptUrl = entity.transcriptUrl,
        transcriptType = entity.transcriptType,
    )

    @Test
    fun `insertIgnore then observeByPodcast returns only episodes for that podcast`() = runTest {
        dao.insertIgnore(listOf(episode("e1", "p1"), episode("e2", "p1"), episode("e3", "p2")))

        val forP1 = dao.observeByPodcast("p1").first()

        assertThat(forP1).hasSize(2)
        assertThat(forP1.map { it.id }).containsExactly("e1", "e2")
    }

    @Test
    fun `insertIgnore leaves an already-stored row completely untouched`() = runTest {
        dao.insertIgnore(listOf(episode("e1", "p1", positionMs = 45_000L)))

        // Re-inserting the same id with different feed-sourced data (and default positionMs=0)
        // must not overwrite the already-stored row at all -- insertIgnore is only for genuinely
        // new rows; updateMetadata is the mechanism for refreshing an existing one.
        dao.insertIgnore(listOf(episode("e1", "p1", title = "Retitled", positionMs = 0)))

        val stored = dao.observeByPodcast("p1").first().single()
        assertThat(stored.title).isEqualTo("Episode e1")
        assertThat(stored.positionMs).isEqualTo(45_000L)
    }

    @Test
    fun `updateMetadata refreshes feed-sourced fields without touching positionMs or localFilePath`() = runTest {
        // Regression test for a real, confirmed bug: re-syncing a podcast's feed (e.g. scrolling
        // to load more episodes) previously used a full-row REPLACE upsert built from a fresh
        // Episode straight off the feed parser -- which has no positionMs/localFilePath to carry
        // forward -- silently resetting every already-in-progress episode's saved position and
        // downloaded-file association back to their feed-parsed defaults on every re-sync.
        dao.insertIgnore(listOf(episode("e1", "p1", positionMs = 45_000L)))
        dao.updateLocalFilePath("e1", "/local/path/e1.mp3")

        dao.updateMetadata(listOf(metadata(episode("e1", "p1", title = "Updated Title"))))

        val stored = dao.observeByPodcast("p1").first().single()
        assertThat(stored.title).isEqualTo("Updated Title")
        assertThat(stored.positionMs).isEqualTo(45_000L)
        assertThat(stored.localFilePath).isEqualTo("/local/path/e1.mp3")
    }

    @Test
    fun `updatePosition then getPosition returns the updated value`() = runTest {
        dao.insertIgnore(listOf(episode("e1", "p1")))

        dao.updatePosition("e1", 45_000L)

        assertThat(dao.getPosition("e1")).isEqualTo(45_000L)
    }

    @Test
    fun `getPosition returns null for an unknown episode`() = runTest {
        assertThat(dao.getPosition("does-not-exist")).isNull()
    }
}
