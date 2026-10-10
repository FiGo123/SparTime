package com.example.boombee.data.models

class Training {
    var id: Int = -1
    var title: String = ""
    var date: String = ""
    var numberOfRounds: Int = -1
    var roundDuration: Int = -1
    var difficultyScale: Int = -1
    var description: String = ""

    // Boxing Coach (v2) fields. Null/0 for plain timer-only sessions, and
    // for every session recorded before this schema addition (see
    // DBHandler's onUpgrade — old rows just come back with these unset).
    var trainingMode: String = "TIMER_ONLY"
    var coachDifficulty: String? = null
    var totalPunches: Int = 0
    var totalCombos: Int = 0
    var totalTacticalCommands: Int = 0
    var punchBreakdown: String? = null

    // History v3 fields (DATABASE_VERSION 3). Rows saved before v3 keep the
    // defaults below; use the helpers at the bottom, which fall back to the
    // older columns, instead of reading these directly in the UI.
    /** Epoch millis when the session started; 0 = unknown (pre-v3 row, see [date]). */
    var startedAt: Long = 0L
    /** Seconds actually spent in rounds (excludes rest, warm-up and pauses); 0 = unknown. */
    var activeSeconds: Int = 0
    /** Rounds the user set up; 0 = unknown (pre-v3: same as [numberOfRounds]). */
    var plannedRounds: Int = 0
    var completed: Boolean = true
    var restSeconds: Int = 0
    var warmupSeconds: Int = 0
    var voiceStyle: String? = null
    /** "phone" or "watch". */
    var platform: String = "phone"
    var defenseCalls: Int = 0
    var distanceCalls: Int = 0
    var freestyleCalls: Int = 0
    /** Punches called per round, comma-separated ("38,41,35"); null for timer-only or pre-v3. */
    var roundPunches: String? = null
    /** Rate of perceived exertion 1–10, from the post-session prompt; null = not rated. */
    var effort: Int? = null
    var notes: String? = null

    constructor(
        title: String,
        date: String,
        numberOfRounds: Int,
        roundDuration: Int,
        dificultyScale: Int,
        description: String,
        trainingMode: String = "TIMER_ONLY",
        coachDifficulty: String? = null,
        totalPunches: Int = 0,
        totalCombos: Int = 0,
        totalTacticalCommands: Int = 0,
        punchBreakdown: String? = null,
    ) {
        this.title = title
        this.date = date
        this.numberOfRounds = numberOfRounds
        this.roundDuration = roundDuration
        this.difficultyScale = dificultyScale
        this.description = description
        this.trainingMode = trainingMode
        this.coachDifficulty = coachDifficulty
        this.totalPunches = totalPunches
        this.totalCombos = totalCombos
        this.totalTacticalCommands = totalTacticalCommands
        this.punchBreakdown = punchBreakdown
    }

    val isCoach: Boolean get() = trainingMode == "BOXING_COACH"

    /** Called punches 1–6 as an array (index 0 = punch 1); all zeros if none. */
    fun punchCounts(): IntArray {
        val counts = IntArray(6)
        punchBreakdown?.split(",")?.forEach { entry ->
            val (number, count) = entry.split(":").takeIf { it.size == 2 } ?: return@forEach
            val n = number.trim().toIntOrNull() ?: return@forEach
            if (n in 1..6) counts[n - 1] = count.trim().toIntOrNull() ?: 0
        }
        return counts
    }

    fun roundPunchList(): List<Int> =
        roundPunches?.split(",")?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()

    fun plannedRoundsOrDone(): Int = if (plannedRounds > 0) plannedRounds else numberOfRounds

    /** Active seconds, estimated from rounds × round length for pre-v3 rows. */
    fun activeSecondsOrEstimate(): Int =
        if (activeSeconds > 0) activeSeconds else (numberOfRounds.coerceAtLeast(0) * roundDuration.coerceAtLeast(0) * 60)
}
