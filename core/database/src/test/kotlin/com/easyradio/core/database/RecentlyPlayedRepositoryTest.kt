package com.easyradio.core.database

import com.easyradio.core.model.RecentlyPlayedItem
import com.easyradio.core.model.RecentlyPlayedType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakeRecentlyPlayedDao : RecentlyPlayedDao {
    val state = MutableStateFlow<List<RecentlyPlayedEntity>>(emptyList())
    var trimCalled = false

    override fun observeRecent() = state.map { it.sortedByDescending { r -> r.playedAtEpochMillis } }

    override suspend fun upsert(item: RecentlyPlayedEntity) {
        state.update { list -> list.filterNot { it.contentId == item.contentId } + item }
    }

    override suspend fun trim() {
        trimCalled = true
    }
}

class RecentlyPlayedRepositoryTest {

    @Test
    fun `record persists a station item and calls trim`() = runTest {
        val dao = FakeRecentlyPlayedDao()
        val repository = RecentlyPlayedRepository(dao)
        val item = RecentlyPlayedItem(
            contentId = "s1",
            type = RecentlyPlayedType.STATION,
            title = "KFAN",
            subtitle = "Sports",
            imageUrl = null,
            playedAtEpochMillis = 100L,
            stationStreamUrl = "https://example.com/s1.mp3",
        )

        repository.record(item)

        assertThat(repository.recent().first()).containsExactly(item)
        assertThat(dao.trimCalled).isTrue()
    }

    @Test
    fun `record round-trips an episode item through the type discriminator`() = runTest {
        val dao = FakeRecentlyPlayedDao()
        val repository = RecentlyPlayedRepository(dao)
        val item = RecentlyPlayedItem(
            contentId = "e1",
            type = RecentlyPlayedType.EPISODE,
            title = "Episode One",
            subtitle = "Show",
            imageUrl = null,
            playedAtEpochMillis = 200L,
            podcastId = "p1",
        )

        repository.record(item)

        val recent = repository.recent().first()
        assertThat(recent).containsExactly(item)
        assertThat(recent.first().type).isEqualTo(RecentlyPlayedType.EPISODE)
        assertThat(recent.first().podcastId).isEqualTo("p1")
    }
}
