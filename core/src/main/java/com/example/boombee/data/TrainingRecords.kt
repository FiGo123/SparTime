package com.example.boombee.data

import com.example.boombee.coach.CoachStats
import com.example.boombee.coach.Difficulty
import com.example.boombee.coach.TrainingMode
import com.example.boombee.coach.VoiceStyle
import com.example.boombee.data.models.Training
import com.example.boombee.heartrate.HeartRateSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Builds the history row for a finished or stopped session — shared by phone and watch so both save the same fields. */
object TrainingRecords {

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun build(
        trainingType: String,
        mode: TrainingMode,
        coachDifficulty: Difficulty?,
        voiceStyle: VoiceStyle?,
        platform: String,
        startedAt: Long,
        activeSeconds: Int,
        plannedRounds: Int,
        roundsReached: Int,
        roundMinutes: Int,
        restSeconds: Int,
        warmupSeconds: Int,
        completed: Boolean,
        stats: CoachStats?,
        heartRate: HeartRateSummary? = null,
    ): Training {
        val title = when (trainingType) {
            "BOXING" -> "Boxing Training"
            "MMA" -> "MMA Training"
            else -> "Custom Training"
        }
        val isCoach = mode == TrainingMode.BOXING_COACH
        val description = when {
            !completed -> "Interrupted at round $roundsReached"
            isCoach -> "Completed boxing coach session"
            else -> "Completed training"
        }
        val date = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).format(DATE_FORMAT)
        return Training(
            title,
            date,
            roundsReached,
            roundMinutes,
            0, // legacy difficultyScale column, no longer shown
            description,
            trainingMode = mode.name,
            coachDifficulty = if (isCoach) coachDifficulty?.name else null,
            totalPunches = stats?.totalPunches() ?: 0,
            totalCombos = stats?.attackCalls ?: 0,
            totalTacticalCommands = (stats?.defenseCalls ?: 0) + (stats?.distanceCalls ?: 0),
            punchBreakdown = stats?.serializeBreakdown(),
        ).apply {
            this.startedAt = startedAt
            this.activeSeconds = activeSeconds
            this.plannedRounds = plannedRounds
            this.completed = completed
            this.restSeconds = restSeconds
            this.warmupSeconds = if (isCoach) warmupSeconds else 0
            this.voiceStyle = if (isCoach) voiceStyle?.name else null
            this.platform = platform
            this.defenseCalls = stats?.defenseCalls ?: 0
            this.distanceCalls = stats?.distanceCalls ?: 0
            this.freestyleCalls = stats?.freestyleCalls ?: 0
            this.roundPunches = stats?.serializeRoundPunches()
            applyHeartRate(heartRate)
        }
    }
}
