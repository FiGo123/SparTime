package com.example.boombee.data

import com.example.boombee.data.models.Training
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** Calculations behind the history screens — free of Android so they can be unit-tested. */
object HistoryStats {

    private val LEGACY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    data class Totals(val sessions: Int, val rounds: Int, val activeSeconds: Int, val punches: Int)

    data class PunchMix(val counts: IntArray) {
        val total: Int get() = counts.sum()
        val left: Int get() = counts[0] + counts[2] + counts[4]
        val right: Int get() = counts[1] + counts[3] + counts[5]
        val straights: Int get() = counts[0] + counts[1]
        val hooks: Int get() = counts[2] + counts[3]
        val uppercuts: Int get() = counts[4] + counts[5]
        fun percent(part: Int): Int = if (total == 0) 0 else Math.round(part * 100f / total)
    }

    data class WeekGroup(val weekStart: LocalDate, val trainings: List<Training>)

    /** When the session started; parses the legacy text date for pre-v3 rows. */
    fun startTime(t: Training, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        if (t.startedAt > 0) {
            Instant.ofEpochMilli(t.startedAt).atZone(zone).toLocalDateTime()
        } else {
            runCatching { LocalDateTime.parse(t.date, LEGACY_DATE) }.getOrDefault(LocalDateTime.of(2000, 1, 1, 0, 0))
        }

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Newest first. */
    fun sorted(trainings: List<Training>, zone: ZoneId = ZoneId.systemDefault()): List<Training> =
        trainings.sortedWith(compareByDescending<Training> { startTime(it, zone) }.thenByDescending { it.id })

    fun totals(trainings: List<Training>) = Totals(
        sessions = trainings.size,
        rounds = trainings.sumOf { it.numberOfRounds.coerceAtLeast(0) },
        activeSeconds = trainings.sumOf { it.activeSecondsOrEstimate() },
        punches = trainings.sumOf { it.totalPunches },
    )

    fun thisWeek(trainings: List<Training>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Training> {
        val start = weekStart(today)
        return trainings.filter { !startTime(it, zone).toLocalDate().isBefore(start) }
    }

    /** Consecutive training days ending today (or yesterday, so the streak isn't lost before today's session). */
    fun streakDays(trainings: List<Training>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Int {
        val days = trainings.map { startTime(it, zone).toLocalDate() }.toSet()
        var day = if (today in days) today else today.minusDays(1)
        var streak = 0
        while (day in days) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** Rounds per week for the last [weeks] weeks, oldest first, ending with the current week. */
    fun weeklyRounds(
        trainings: List<Training>,
        today: LocalDate,
        weeks: Int = 8,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Pair<LocalDate, Int>> {
        val current = weekStart(today)
        val byWeek = trainings.groupBy { weekStart(startTime(it, zone).toLocalDate()) }
        return (weeks - 1 downTo 0).map { back ->
            val start = current.minusWeeks(back.toLong())
            start to byWeek[start].orEmpty().sumOf { it.numberOfRounds.coerceAtLeast(0) }
        }
    }

    /** Newest week first, sessions newest first inside each week. */
    fun groupByWeek(trainings: List<Training>, zone: ZoneId = ZoneId.systemDefault()): List<WeekGroup> =
        sorted(trainings, zone)
            .groupBy { weekStart(startTime(it, zone).toLocalDate()) }
            .map { (start, list) -> WeekGroup(start, list) }

    fun punchMix(trainings: List<Training>): PunchMix {
        val counts = IntArray(6)
        trainings.forEach { t -> t.punchCounts().forEachIndexed { i, c -> counts[i] += c } }
        return PunchMix(counts)
    }

    fun effortLabel(effort: Int): String = when (effort) {
        in 1..2 -> "Very easy"
        in 3..4 -> "Easy"
        in 5..6 -> "Moderate"
        in 7..8 -> "Hard"
        else -> "Max effort"
    }

    /** "36 min" / "1h 05m". */
    fun formatDuration(seconds: Int): String {
        val minutes = (seconds + 30) / 60
        return if (minutes < 60) "$minutes min" else "${minutes / 60}h ${"%02d".format(minutes % 60)}m"
    }
}
