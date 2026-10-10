package com.example.boombee.data

import com.example.boombee.data.models.Training
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class HistoryStatsTest {

    private val zone = ZoneOffset.UTC

    private fun training(start: LocalDateTime, rounds: Int = 3, minutes: Int = 3, breakdown: String? = null) =
        Training("Boxing Training", "", rounds, minutes, 0, "", punchBreakdown = breakdown).apply {
            startedAt = start.toInstant(zone).toEpochMilli()
        }

    // Thursday 9 Oct 2026
    private val today = LocalDate.of(2026, 10, 9)

    @Test
    fun thisWeekStartsMonday() {
        val list = listOf(
            training(LocalDateTime.of(2026, 10, 5, 18, 0)), // Mon
            training(LocalDateTime.of(2026, 10, 4, 18, 0)), // Sun, last week
        )
        assertEquals(1, HistoryStats.thisWeek(list, today, zone).size)
    }

    @Test
    fun streakCountsBackFromTodayOrYesterday() {
        val list = listOf(
            training(LocalDateTime.of(2026, 10, 8, 18, 0)),
            training(LocalDateTime.of(2026, 10, 7, 18, 0)),
            training(LocalDateTime.of(2026, 10, 7, 8, 0)),
            training(LocalDateTime.of(2026, 10, 5, 18, 0)),
        )
        assertEquals(2, HistoryStats.streakDays(list, today, zone))
        assertEquals(0, HistoryStats.streakDays(list, today.plusDays(2), zone))
    }

    @Test
    fun weeklyRoundsEndsWithCurrentWeek() {
        val list = listOf(
            training(LocalDateTime.of(2026, 10, 6, 18, 0), rounds = 12),
            training(LocalDateTime.of(2026, 9, 30, 18, 0), rounds = 5),
        )
        val weeks = HistoryStats.weeklyRounds(list, today, weeks = 3, zone = zone)
        assertEquals(listOf(0, 5, 12), weeks.map { it.second })
        assertEquals(LocalDate.of(2026, 10, 5), weeks.last().first)
    }

    @Test
    fun legacyRowsUseTextDateAndEstimatedTime() {
        val old = Training("Boxing Training", "2026-09-01 07:30", 4, 3, 3, "Completed training")
        assertEquals(LocalDateTime.of(2026, 9, 1, 7, 30), HistoryStats.startTime(old, zone))
        assertEquals(720, old.activeSecondsOrEstimate())
    }

    @Test
    fun punchMixSplitsHandsAndTypes() {
        val mix = HistoryStats.punchMix(listOf(training(today.atStartOfDay(), breakdown = "1:10,2:6,3:4,4:0,5:2,6:2")))
        assertEquals(24, mix.total)
        assertEquals(16, mix.left)
        assertEquals(8, mix.right)
        assertEquals(16, mix.straights)
        assertEquals(67, mix.percent(mix.left))
    }

    @Test
    fun formatsDurations() {
        assertEquals("36 min", HistoryStats.formatDuration(36 * 60))
        assertEquals("1h 05m", HistoryStats.formatDuration(65 * 60))
    }
}
