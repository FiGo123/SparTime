package com.example.boombee.coach

/** Accumulates what a [CoachSession] called during a session, for the training-history record. */
class CoachStats {
    private val punchCounts = IntArray(6)
    var attackCalls = 0
        private set
    var defenseCalls = 0
        private set
    var distanceCalls = 0
        private set
    var freestyleCalls = 0
        private set

    // Punches called in each round; warm-up calls (before the first
    // startRound()) count toward the totals only.
    private val perRound = mutableListOf<Int>()

    /** Opens a new per-round bucket; [CoachSession.startRound] calls this. */
    fun startRound() {
        perRound.add(0)
    }

    fun record(command: CoachCommand) {
        when (command.section) {
            CoachSection.ATTACK -> attackCalls++
            CoachSection.DEFENSE -> defenseCalls++
            CoachSection.DISTANCE -> distanceCalls++
            CoachSection.FREESTYLE -> freestyleCalls++
        }
        command.parts.forEach { part ->
            if (part is CommandPart.Punch) {
                punchCounts[part.number - 1]++
                if (perRound.isNotEmpty()) perRound[perRound.lastIndex]++
            }
        }
    }

    fun totalPunches(): Int = punchCounts.sum()

    fun punchCount(number: Int): Int = punchCounts[number - 1]

    /** `"1:count,2:count,...,6:count"` — compact form for storage in [Training.punchBreakdown]. */
    fun serializeBreakdown(): String = (1..6).joinToString(",") { "$it:${punchCount(it)}" }

    /** Punches per round as `"38,41,35"` for [Training.roundPunches]; null if no round started. */
    fun serializeRoundPunches(): String? = perRound.takeIf { it.isNotEmpty() }?.joinToString(",")
}
