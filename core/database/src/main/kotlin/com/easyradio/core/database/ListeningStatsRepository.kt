package com.easyradio.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Tracks cumulative listening time per day, for a simple "time listened" stats display. */
class ListeningStatsRepository(
    private val dao: ListeningStatsDao,
    private val today: () -> LocalDate = { LocalDate.now() },
) {

    suspend fun addListenedSeconds(seconds: Long) {
        if (seconds <= 0) return
        val date = today().toString()
        val existing = dao.get(date)?.secondsListened ?: 0L
        dao.upsert(ListeningStatsEntity(date = date, secondsListened = existing + seconds))
    }

    /** Total seconds listened across the last [days] days, including today. */
    fun totalSecondsForLast(days: Long): Flow<Long> {
        val sinceDate = today().minusDays(days - 1).toString()
        return dao.observeSince(sinceDate).map { rows -> rows.sumOf { it.secondsListened } }
    }
}
