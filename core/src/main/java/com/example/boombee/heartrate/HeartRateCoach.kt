package com.example.boombee.heartrate

import com.example.boombee.coach.Phrases

/** Where the boxer's heart rate sits against the current round's target zone. */
enum class HeartRateBias {
    /** No reading yet, adaptive coaching off, or still inside the round's grace period. */
    NONE,
    BELOW,
    ON_TARGET,
    ABOVE,
    /** Stayed above the safety limit (95% of max) — always eases off, at every difficulty. */
    OVER_LIMIT,
}

/**
 * Turns live heart-rate readings into a [HeartRateBias] for
 * `CoachSession` and an occasional spoken cue. No Android dependencies —
 * the caller feeds it readings and the current time.
 *
 * Heart rate lags effort by 20–40 seconds, so nothing adapts during the
 * first [GRACE_MS] of a round, readings are averaged over [SMOOTHING_MS],
 * and a cue only fires once a bias has held for [CUE_HOLD_MS].
 *
 * Thread-safe: on the watch the 1-second tick feeds readings while the
 * coach loop reads the bias, on different Dispatchers.Default threads.
 */
class HeartRateCoach(private val maxHr: Int) {

    private val readings = ArrayDeque<Pair<Long, Int>>()
    private var roundStartMs = 0L
    private var targetZone = 0
    private var overLimitSinceMs: Long? = null

    private var biasSince = 0L
    private var lastBias = HeartRateBias.NONE
    private var lastCueMs = Long.MIN_VALUE / 2
    private var cuedBias = HeartRateBias.NONE

    /** Starts a round with [zone] (1–5) as its target, or 0 to only record (timer-only, coaching off). */
    @Synchronized
    fun startRound(zone: Int, nowMs: Long) {
        targetZone = zone
        roundStartMs = nowMs
        lastBias = HeartRateBias.NONE
        biasSince = nowMs
        cuedBias = HeartRateBias.NONE
    }

    /** Stops adapting (rest, warm-up, paused) while still tracking the safety limit. */
    @Synchronized
    fun stopRound() {
        targetZone = 0
    }

    @Synchronized
    fun onReading(bpm: Int, nowMs: Long) {
        if (bpm <= 0) return
        readings.addLast(nowMs to bpm)
        while (readings.isNotEmpty() && nowMs - readings.first().first > SMOOTHING_MS) readings.removeFirst()
        if (bpm >= HeartRateZones.safetyBpm(maxHr)) {
            if (overLimitSinceMs == null) overLimitSinceMs = nowMs
        } else {
            overLimitSinceMs = null
        }
    }

    /** Average of the last [SMOOTHING_MS] of readings, or null if there are none. */
    @Synchronized
    fun smoothedBpm(): Int? = readings.takeIf { it.isNotEmpty() }?.let { r -> r.sumOf { it.second } / r.size }

    @Synchronized
    fun bias(nowMs: Long): HeartRateBias {
        val bias = computeBias(nowMs)
        if (bias != lastBias) {
            lastBias = bias
            biasSince = nowMs
        }
        return bias
    }

    private fun computeBias(nowMs: Long): HeartRateBias {
        val since = overLimitSinceMs
        if (since != null && nowMs - since >= OVER_LIMIT_HOLD_MS) return HeartRateBias.OVER_LIMIT
        if (targetZone <= 0 || nowMs - roundStartMs < GRACE_MS) return HeartRateBias.NONE
        val bpm = smoothedBpm() ?: return HeartRateBias.NONE
        val range = HeartRateZones.bpmRange(targetZone, maxHr)
        return when {
            bpm < range.first - TOLERANCE_BPM -> HeartRateBias.BELOW
            bpm > range.last + TOLERANCE_BPM -> HeartRateBias.ABOVE
            else -> HeartRateBias.ON_TARGET
        }
    }

    /**
     * A short spoken cue for the current bias, or null. The safety cue
     * repeats every [SAFETY_CUE_INTERVAL_MS] while over the limit; push/ease
     * cues fire at most once per bias change and never closer than
     * [CUE_COOLDOWN_MS] apart, so the coach doesn't nag.
     */
    @Synchronized
    fun nextCue(nowMs: Long): String? {
        val bias = bias(nowMs)
        if (bias == HeartRateBias.OVER_LIMIT) {
            if (nowMs - lastCueMs < SAFETY_CUE_INTERVAL_MS) return null
            lastCueMs = nowMs
            cuedBias = bias
            return Phrases.HR_SLOW_DOWN
        }
        if (bias != HeartRateBias.BELOW && bias != HeartRateBias.ABOVE) return null
        if (bias == cuedBias || nowMs - biasSince < CUE_HOLD_MS || nowMs - lastCueMs < CUE_COOLDOWN_MS) return null
        lastCueMs = nowMs
        cuedBias = bias
        return if (bias == HeartRateBias.BELOW) Phrases.PICK_UP_SPEED else Phrases.HR_EASE_OFF
    }

    companion object {
        const val GRACE_MS = 30_000L
        const val SMOOTHING_MS = 10_000L
        const val TOLERANCE_BPM = 3
        const val OVER_LIMIT_HOLD_MS = 20_000L
        const val CUE_HOLD_MS = 20_000L
        const val CUE_COOLDOWN_MS = 45_000L
        const val SAFETY_CUE_INTERVAL_MS = 30_000L
    }
}
