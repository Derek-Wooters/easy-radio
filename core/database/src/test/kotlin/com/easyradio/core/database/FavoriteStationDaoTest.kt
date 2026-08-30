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
class FavoriteStationDaoTest {

    private lateinit var database: EasyRadioDatabase
    private lateinit var dao: FavoriteStationDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EasyRadioDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.favoriteStationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun station(id: String, name: String, favoritedAt: Long = 100L, isPreset: Boolean = false) =
        FavoriteStationEntity(
            id = id,
            name = name,
            streamUrl = "https://example.com/$id.mp3",
            tagline = "Tagline",
            imageUrl = null,
            favoritedAtEpochMillis = favoritedAt,
            isPreset = isPreset,
        )

    @Test
    fun `upsert then observeAll returns the station`() = runTest {
        dao.upsert(station("s1", "Station One"))

        val all = dao.observeAll().first()

        assertThat(all).hasSize(1)
        assertThat(all.first().id).isEqualTo("s1")
    }

    @Test
    fun `observeAll orders newest favorited first`() = runTest {
        dao.upsert(station("s1", "Station One", favoritedAt = 100L))
        dao.upsert(station("s2", "Station Two", favoritedAt = 200L))

        val all = dao.observeAll().first()

        assertThat(all.map { it.id }).containsExactly("s2", "s1").inOrder()
    }

    @Test
    fun `delete removes the station`() = runTest {
        dao.upsert(station("s1", "Station One"))

        dao.delete("s1")

        assertThat(dao.observeAll().first()).isEmpty()
    }

    @Test
    fun `setPreset updates only the targeted station`() = runTest {
        dao.upsert(station("s1", "Station One"))
        dao.upsert(station("s2", "Station Two"))

        dao.setPreset("s1", true)

        val all = dao.observeAll().first().associateBy { it.id }
        assertThat(all.getValue("s1").isPreset).isTrue()
        assertThat(all.getValue("s2").isPreset).isFalse()
    }
}
