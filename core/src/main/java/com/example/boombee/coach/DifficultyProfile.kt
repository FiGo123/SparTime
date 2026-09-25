package com.example.boombee.coach

/**
 * Tunable pacing/volume parameters per [Difficulty]. The combo-length
 * range is a first draft, pending real values — safe to edit without
 * touching [CoachSession]'s logic. Which *section* (Attack/Defense/
 * Distance) comes next is no longer difficulty-dependent — that's
 * [SectionPicker]'s fixed transition table, the same at every difficulty;
 * difficulty only controls Attack combo length and overall pacing.
 *
 * [secondsPerPunch] and [baseRestBetweenCommandsSeconds] exist so pacing
 * always assumes a real person is physically throwing these punches:
 * higher difficulty raises volume/complexity, never raw calling speed
 * below what's realistic to throw.
 */
data class DifficultyProfile(
    val comboLengthRange: IntRange,
    val secondsPerPunch: Double,
    val baseRestBetweenCommandsSeconds: Double,
)

// v2.7.0: nudged up a little at every level per real-device feedback
// ("increase difficulty for all levels") — combo-length ceilings up by
// one, rest trimmed ~10-15%. Still ordered the same relative to each
// other (Beginner stays easiest/slowest of the three).
fun profileFor(difficulty: Difficulty): DifficultyProfile = when (difficulty) {
    Difficulty.BEGINNER -> DifficultyProfile(
        comboLengthRange = 1..3,
        secondsPerPunch = 0.6,
        baseRestBetweenCommandsSeconds = 1.8,
    )
    Difficulty.INTERMEDIATE -> DifficultyProfile(
        comboLengthRange = 2..4,
        secondsPerPunch = 0.5,
        baseRestBetweenCommandsSeconds = 1.3,
    )
    Difficulty.ADVANCED -> DifficultyProfile(
        comboLengthRange = 3..6,
        secondsPerPunch = 0.45,
        baseRestBetweenCommandsSeconds = 0.85,
    )
}
