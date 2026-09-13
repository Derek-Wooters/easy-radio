package com.easyradio.core.database

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

private class FakeListeningStatsDao : ListeningStatsDao {
    val state = MutableStateFlow<List<ListeningStatsEntity>>(emptyList())

    override suspend fun get(date: String): ListeningStatsEntity? = state.value.find { it.date == date }

    override suspend fun upsert(entity: ListeningStatsEntity) {
        state.update { list -> list.filterNot { it.date == entity.date } + entity }
    }

    override fun observeSince(sinceDate: String) =
        state.map { list -> list.filter { it.date >= sinceDate } }
}

class ListeningStatsRepositoryTest {

    private val fixedToday = LocalDate.of(2026, 9, 12)

    @Test
    fun `addListenedSeconds accumulates within the same day`() = runTest {
        val dao = FakeListeningStatsDao()
        val repository = ListeningStatsRepository(dao, today = { fixedToday })

        repository.addListenedSeconds(60)
        repository.addListenedSeconds(30)

        assertThat(dao.get("2026-09-12")?.secondsListened).isEqualTo(90)
    }

    @Test
    fun `addListenedSeconds ignores zero or negative durations`() = runTest {
        val dao = FakeListeningStatsDao()
        val repository = ListeningStatsRepository(dao, today = { fixedToday })

        repository.addListenedSeconds(0)
        repository.addListenedSeconds(-5)

        assertThat(dao.get("2026-09-12")).isNull()
    }

    @Test
    fun `totalSecondsForLast sums only days within a 7-day window ending today`() = runTest {
        val dao = FakeListeningStatsDao()
        dao.upsert(ListeningStatsEntity(date = "2026-09-12", secondsListened = 100)) // today
        dao.upsert(ListeningStatsEntity(date = "2026-09-06", secondsListened = 200)) // 6 days ago: on the window boundary, included
        dao.upsert(ListeningStatsEntity(date = "2026-09-01", secondsListened = 999)) // 11 days ago: outside the window
        val repository = ListeningStatsRepository(dao, today = { fixedToday })

        val total = repository.totalSecondsForLast(days = 7).first()

        assertThat(total).isEqualTo(300)
    }
}
