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
class QueueDaoTest {

    private lateinit var database: EasyRadioDatabase
    private lateinit var dao: QueueDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EasyRadioDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.queueDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `observeAll returns items ordered by position`() = runTest {
        dao.upsert(QueueItemEntity(episodeId = "e2", position = 1))
        dao.upsert(QueueItemEntity(episodeId = "e1", position = 0))

        val all = dao.observeAll().first()

        assertThat(all.map { it.episodeId }).containsExactly("e1", "e2").inOrder()
    }

    @Test
    fun `remove deletes the item`() = runTest {
        dao.upsert(QueueItemEntity(episodeId = "e1", position = 0))

        dao.remove("e1")

        assertThat(dao.observeAll().first()).isEmpty()
    }

    @Test
    fun `maxPosition returns -1 when the queue is empty`() = runTest {
        assertThat(dao.maxPosition()).isEqualTo(-1)
    }

    @Test
    fun `maxPosition returns the highest position`() = runTest {
        dao.upsert(QueueItemEntity(episodeId = "e1", position = 0))
        dao.upsert(QueueItemEntity(episodeId = "e2", position = 3))

        assertThat(dao.maxPosition()).isEqualTo(3)
    }

    @Test
    fun `upsertAll replaces positions for reordering`() = runTest {
        dao.upsert(QueueItemEntity(episodeId = "e1", position = 0))
        dao.upsert(QueueItemEntity(episodeId = "e2", position = 1))

        dao.upsertAll(listOf(QueueItemEntity(episodeId = "e2", position = 0), QueueItemEntity(episodeId = "e1", position = 1)))

        val all = dao.observeAll().first()
        assertThat(all.map { it.episodeId }).containsExactly("e2", "e1").inOrder()
    }
}
