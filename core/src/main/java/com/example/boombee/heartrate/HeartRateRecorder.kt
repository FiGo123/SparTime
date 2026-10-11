package com.example.boombee.heartrate

/** What a session's heart rate looked like — saved with the history row (see Training's v4 fields). */
data class HeartRateSummary(
    val avgBpm: Int,
    val maxBpm: Int,
    /** The user's max HR (220 − age) when the session ran, so zones can be redrawn later. */
    val maxHr: Int,
    /** Seconds in each zone; index 0 = below zone 1, 1–5 = zones 1–5. */
    val zoneSeconds: IntArray,
    val roundAvg: List<Int>,
    val roundMax: List<Int>,
    /** Target zone per round; 0 = no target (timer-only or adaptive coaching off). */
    val roundTarget: List<Int>,
    /** BPM dropped during each rest (start of rest minus end of rest). */
    val recoveries: List<Int>,
    /** One reading every [SAMPLE_SECONDS] seconds over the whole session; 0 = no reading. */
    val samples: List<Int>,
) {
    /** True when the final round's average reached its target zone. */
    fun peakReached(): Boolean? {
        val target = roundTarget.lastOrNull()?.takeIf { it > 0 } ?: return null
        val avg = roundAvg.lastOrNull()?.takeIf { it > 0 } ?: return null
        return HeartRateZones.zoneOf(avg, maxHr) >= target
    }

    companion object {
        const val SAMPLE_SECONDS = 5
    }
}

/**
 * Collects one session's heart rate. The caller ticks it once per second
 * of session time (not while paused) with the latest reading, and marks
 * where rounds and rests begin. Thread-safe: Stop saves the summary from
 * the main thread while the tick may still be running.
 */
class HeartRateRecorder(private val maxHr: Int) {

    private val zoneSeconds = IntArray(6)
    private var sum = 0L
    private var count = 0
    private var max = 0

    private val roundSums = mutableListOf<Long>()
    private val roundCounts = mutableListOf<Int>()
    private val roundMax = mutableListOf<Int>()
    private val roundTarget = mutableListOf<Int>()

    private var inRest = false
    private var restFirst: Int? = null
    private var restLast: Int? = null
    private val recoveries = mutableListOf<Int>()

    private val samples = mutableListOf<Int>()
    private var secondsSinceSample = 0
    private var sampleMax = 0

    @Synchronized
    fun startRound(targetZone: Int) {
        closeRest()
        roundSums.add(0L)
        roundCounts.add(0)
        roundMax.add(0)
        roundTarget.add(targetZone)
    }

    @Synchronized
    fun startRest() {
        closeRest()
        inRest = true
    }

    /** One second of session time with the latest reading (null = none or stale). */
    @Synchronized
    fun tick(bpm: Int?) {
        val reading = bpm?.takeIf { it > 0 }
        if (reading != null) {
            zoneSeconds[HeartRateZones.zoneOf(reading, maxHr)]++
            sum += reading
            count++
            if (reading > max) max = reading
            if (!inRest && roundSums.isNotEmpty()) {
                val i = roundSums.lastIndex
                roundSums[i] += reading
                roundCounts[i]++
                if (reading > roundMax[i]) roundMax[i] = reading
            }
            if (inRest) {
                if (restFirst == null) restFirst = reading
                restLast = reading
            }
            if (reading > sampleMax) sampleMax = reading
        }
        secondsSinceSample++
        if (secondsSinceSample >= HeartRateSummary.SAMPLE_SECONDS) {
            // Peak of the 5 seconds, so short spikes still show on the graph.
            samples.add(sampleMax)
            secondsSinceSample = 0
            sampleMax = 0
        }
    }

    private fun closeRest() {
        if (inRest) {
            val first = restFirst
            val last = restLast
            if (first != null && last != null) recoveries.add((first - last).coerceAtLeast(0))
        }
        inRest = false
        restFirst = null
        restLast = null
    }

    /** Null when no reading ever arrived (no permission, no sensor, watch off the wrist). */
    @Synchronized
    fun summary(): HeartRateSummary? {
        if (count == 0) return null
        closeRest()
        return HeartRateSummary(
            avgBpm = (sum / count).toInt(),
            maxBpm = max,
            maxHr = maxHr,
            zoneSeconds = zoneSeconds.copyOf(),
            roundAvg = roundSums.indices.map { i -> if (roundCounts[i] == 0) 0 else (roundSums[i] / roundCounts[i]).toInt() },
            roundMax = roundMax.toList(),
            roundTarget = roundTarget.toList(),
            recoveries = recoveries.toList(),
            samples = samples.toList(),
        )
    }
}
