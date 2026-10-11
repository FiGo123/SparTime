package com.example.boombee.heartrate

import com.example.boombee.coach.Difficulty
import kotlin.math.roundToInt

/**
 * Heart-rate zones from the user's age: max HR = 220 − age, and five zones
 * as fixed percentages of it. Zone 0 means "below zone 1" (under 50%).
 * See HEART_RATE_ZONES.md for the per-difficulty targets.
 */
object HeartRateZones {

    const val MIN_AGE = 13
    const val MAX_AGE = 90
    const val DEFAULT_AGE = 30

    /** Lower edge of zones 1–5 as a fraction of max HR; zone 5 runs up to 100%. */
    private val ZONE_FLOORS = doubleArrayOf(0.50, 0.60, 0.70, 0.80, 0.90)

    /** Above this share of max HR for a while, the coach forces an easy interval. */
    const val SAFETY_FRACTION = 0.95

    fun maxHeartRate(age: Int): Int = 220 - age.coerceIn(MIN_AGE, MAX_AGE)

    /** 0 (below zone 1) to 5. */
    fun zoneOf(bpm: Int, maxHr: Int): Int {
        if (maxHr <= 0) return 0
        val fraction = bpm.toDouble() / maxHr
        var zone = 0
        ZONE_FLOORS.forEachIndexed { index, floor -> if (fraction >= floor) zone = index + 1 }
        return zone
    }

    /** BPM range of [zone] (1–5). Zone 5's upper end is capped at the safety limit, so a target never asks for more. */
    fun bpmRange(zone: Int, maxHr: Int): IntRange {
        val z = zone.coerceIn(1, 5)
        val low = (ZONE_FLOORS[z - 1] * maxHr).roundToInt()
        val highFraction = if (z == 5) SAFETY_FRACTION else ZONE_FLOORS[z]
        return low until (highFraction * maxHr).roundToInt()
    }

    fun safetyBpm(maxHr: Int): Int = (SAFETY_FRACTION * maxHr).roundToInt()

    /** First-round and final-round target zone per difficulty; rounds in between climb linearly. */
    fun targetRange(difficulty: Difficulty): Pair<Int, Int> = when (difficulty) {
        Difficulty.BEGINNER -> 2 to 3
        Difficulty.INTERMEDIATE -> 2 to 4
        Difficulty.ADVANCED -> 3 to 5
    }

    /**
     * Target zone for [round] of [totalRounds]: starts at the difficulty's
     * first zone and climbs so the last round is the highest — in a
     * 3-round Intermediate session that's Z2 → Z3 → Z4. A single-round
     * session goes straight for the peak.
     */
    fun targetZone(difficulty: Difficulty, round: Int, totalRounds: Int): Int {
        val (start, peak) = targetRange(difficulty)
        if (totalRounds <= 1) return peak
        val progress = (round.coerceIn(1, totalRounds) - 1).toDouble() / (totalRounds - 1)
        return (start + (peak - start) * progress).roundToInt().coerceIn(start, peak)
    }

    fun label(zone: Int): String = if (zone <= 0) "Below Z1" else "Z$zone"

    fun name(zone: Int): String = when (zone) {
        1 -> "Very light"
        2 -> "Light"
        3 -> "Moderate"
        4 -> "Hard"
        5 -> "Maximum"
        else -> "Resting"
    }
}
