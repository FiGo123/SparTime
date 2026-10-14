package com.example.boombee.history

import android.content.Context
import com.example.boombee.coach.TrainingMode
import com.example.boombee.data.DBHandler
import com.example.boombee.data.TrainingRecords
import com.example.boombee.viewmodel.MainViewModel

/**
 * The phone session's History row. Saved as "stopped" once a minute of
 * rounds is done and kept up to date while training ([checkpoint]), so a
 * session isn't lost if the app is killed; [save] finalises the same row.
 */
object PhoneSessionRecord {
    private const val MIN_SAVED_ACTIVE_SECONDS = 60

    /** Round time actually trained: full rounds before the current one plus the part of it that ran. */
    fun activeSeconds(vm: MainViewModel, currentRound: Int, remainingSecondsInRound: Int): Int {
        val roundSeconds = (vm.roundLengthInMin.value ?: 0) * 60
        return (currentRound - 1).coerceAtLeast(0) * roundSeconds + (roundSeconds - remainingSecondsInRound).coerceIn(0, roundSeconds)
    }

    fun checkpoint(context: Context, vm: MainViewModel, currentRound: Int, activeSeconds: Int) {
        if (activeSeconds < MIN_SAVED_ACTIVE_SECONDS) return
        upsert(context, vm, build(vm, completed = false, roundsReached = currentRound, activeSeconds = activeSeconds))
    }

    /** Finalises the session; returns its row id (for the effort prompt). */
    fun save(context: Context, vm: MainViewModel, completed: Boolean, roundsReached: Int, activeSeconds: Int): Long {
        val id = upsert(context, vm, build(vm, completed, roundsReached, activeSeconds))
        vm.liveTrainingId = null
        return id
    }

    /** "Don't save" on the stop dialog: removes the row a checkpoint may already have written. */
    fun discard(context: Context, vm: MainViewModel) {
        vm.liveTrainingId?.let { DBHandler(context).deleteTraining(it.toInt()) }
        vm.liveTrainingId = null
    }

    private fun upsert(context: Context, vm: MainViewModel, record: com.example.boombee.data.models.Training): Long {
        val db = DBHandler(context)
        val id = vm.liveTrainingId
        return if (id != null) {
            db.updateTraining(id, record)
            id
        } else {
            db.insertData(record).also { if (it > 0) vm.liveTrainingId = it }
        }
    }

    private fun build(vm: MainViewModel, completed: Boolean, roundsReached: Int, activeSeconds: Int) = TrainingRecords.build(
        trainingType = vm.trainingType.value ?: "Custom",
        mode = vm.trainingMode.value ?: TrainingMode.TIMER_ONLY,
        coachDifficulty = vm.coachDifficulty.value,
        voiceStyle = vm.voiceStyle.value,
        platform = "phone",
        startedAt = vm.sessionStartedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        activeSeconds = activeSeconds,
        plannedRounds = vm.numOfRounds.value ?: roundsReached,
        roundsReached = roundsReached,
        roundMinutes = vm.roundLengthInMin.value ?: 0,
        restSeconds = (vm.pauseLengthInMin.value ?: 0) * 60,
        warmupSeconds = vm.warmupSeconds.value ?: 0,
        completed = completed,
        stats = vm.coachSession?.stats,
    )
}
