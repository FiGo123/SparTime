package com.example.boombee.wear

/** Rounds/round-length/rest-length for one training session, mirroring the phone app's setup fields. */
data class SessionConfig(
    val rounds: Int,
    val roundMinutes: Int,
    val restMinutes: Int,
)

fun defaultsFor(trainingType: String?): SessionConfig = when (trainingType) {
    "BOXING" -> SessionConfig(rounds = 12, roundMinutes = 3, restMinutes = 1)
    "MMA" -> SessionConfig(rounds = 5, roundMinutes = 5, restMinutes = 1)
    else -> SessionConfig(rounds = 3, roundMinutes = 3, restMinutes = 1)
}

/**
 * Navigation state for screens *outside* an active session. An active
 * round/rest is tracked separately by [RoundTimerService.state] — whenever
 * that's non-null, the running session takes over the UI regardless of
 * [WearScreen], since the service (not this local state) is the source of
 * truth once a session starts (see Notes in WEAR_OS_IMPLEMENTATION.md).
 */
enum class WearScreen { Setup, Settings, History }

enum class TimerPhase { WARMUP, ROUND, REST }

/** Live state of the round/rest currently running in [RoundTimerService]. */
data class RoundTimerState(
    val phase: TimerPhase,
    val roundNumber: Int,
    val totalRounds: Int,
    val remainingSeconds: Int,
    val isPaused: Boolean,
)
