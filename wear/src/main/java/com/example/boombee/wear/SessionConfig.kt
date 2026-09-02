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

sealed class WearScreen {
    object Setup : WearScreen()
    data class RoundTimer(val round: Int) : WearScreen()
    data class Rest(val nextRound: Int) : WearScreen()
    object Settings : WearScreen()
    object History : WearScreen()
}

enum class TimerPhase { ROUND, REST }
