package com.example.boombee.heartrate

import com.example.boombee.coach.Difficulty
import com.example.boombee.coach.Phrases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateTest {

    @Test
    fun maxHeartRateIs220MinusAge() {
        assertEquals(190, HeartRateZones.maxHeartRate(30))
        assertEquals(220 - HeartRateZones.MIN_AGE, HeartRateZones.maxHeartRate(5))
    }

    @Test
    fun zonesArePercentOfMax() {
        val max = 200
        assertEquals(0, HeartRateZones.zoneOf(99, max))
        assertEquals(1, HeartRateZones.zoneOf(100, max))
        assertEquals(2, HeartRateZones.zoneOf(120, max))
        assertEquals(3, HeartRateZones.zoneOf(140, max))
        assertEquals(4, HeartRateZones.zoneOf(160, max))
        assertEquals(5, HeartRateZones.zoneOf(180, max))
        assertEquals(160 until 180, HeartRateZones.bpmRange(4, max))
        // Zone 5 target never goes past the 95% safety limit.
        assertEquals(180 until 190, HeartRateZones.bpmRange(5, max))
    }

    @Test
    fun targetClimbsSoLastRoundIsHighest() {
        val three = (1..3).map { HeartRateZones.targetZone(Difficulty.INTERMEDIATE, it, 3) }
        assertEquals(listOf(2, 3, 4), three)
        val twelve = (1..12).map { HeartRateZones.targetZone(Difficulty.BEGINNER, it, 12) }
        assertEquals(2, twelve.first())
        assertEquals(3, twelve.last())
        assertTrue(twelve.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(5, HeartRateZones.targetZone(Difficulty.ADVANCED, 1, 1))
    }

    @Test
    fun noBiasDuringGracePeriod() {
        val coach = HeartRateCoach(200)
        coach.startRound(4, 0)
        coach.onReading(110, 1_000)
        assertEquals(HeartRateBias.NONE, coach.bias(10_000))
        coach.onReading(110, HeartRateCoach.GRACE_MS)
        assertEquals(HeartRateBias.BELOW, coach.bias(HeartRateCoach.GRACE_MS))
    }

    @Test
    fun biasFollowsSmoothedReadingAgainstTarget() {
        val coach = HeartRateCoach(200)
        coach.startRound(3, 0) // 140–159
        val t = HeartRateCoach.GRACE_MS + 60_000
        coach.onReading(150, t)
        assertEquals(HeartRateBias.ON_TARGET, coach.bias(t))
        coach.onReading(175, t + 20_000) // previous reading drops out of the 10s window
        assertEquals(HeartRateBias.ABOVE, coach.bias(t + 20_000))
    }

    @Test
    fun pushCueOnlyAfterBiasHoldsAndNotRepeated() {
        val coach = HeartRateCoach(200)
        coach.startRound(4, 0)
        var t = HeartRateCoach.GRACE_MS
        coach.onReading(120, t)
        assertNull(coach.nextCue(t))
        t += HeartRateCoach.CUE_HOLD_MS
        coach.onReading(120, t)
        assertEquals(Phrases.PICK_UP_SPEED, coach.nextCue(t))
        t += 60_000
        coach.onReading(120, t)
        assertNull(coach.nextCue(t))
    }

    @Test
    fun overLimitForcesSafetyCueEvenWithoutTarget() {
        val coach = HeartRateCoach(200) // safety at 190
        coach.startRound(0, 0)
        coach.onReading(192, 0)
        coach.onReading(193, 10_000)
        assertEquals(HeartRateBias.NONE, coach.bias(10_000))
        coach.onReading(194, HeartRateCoach.OVER_LIMIT_HOLD_MS)
        assertEquals(HeartRateBias.OVER_LIMIT, coach.bias(HeartRateCoach.OVER_LIMIT_HOLD_MS))
        assertEquals(Phrases.HR_SLOW_DOWN, coach.nextCue(HeartRateCoach.OVER_LIMIT_HOLD_MS))
        coach.onReading(150, HeartRateCoach.OVER_LIMIT_HOLD_MS + 1_000)
        assertTrue(coach.bias(HeartRateCoach.OVER_LIMIT_HOLD_MS + 1_000) != HeartRateBias.OVER_LIMIT)
    }

    @Test
    fun recorderSummarisesRoundsZonesAndRecovery() {
        val rec = HeartRateRecorder(200)
        rec.startRound(2)
        repeat(10) { rec.tick(130) } // zone 2
        rec.startRest()
        rec.tick(150)
        repeat(8) { rec.tick(null) }
        rec.tick(120)
        rec.startRound(3)
        repeat(10) { rec.tick(150) } // zone 3
        val s = rec.summary()!!
        assertEquals(listOf(130, 150), s.roundAvg)
        assertEquals(listOf(2, 3), s.roundTarget)
        assertEquals(listOf(30), s.recoveries)
        assertEquals(150, s.maxBpm)
        assertEquals(11, s.zoneSeconds[2]) // 10 in round 1 + the 120 at rest end
        assertEquals(11, s.zoneSeconds[3]) // 10 in round 2 + the 150 at rest start
        assertEquals(30 / HeartRateSummary.SAMPLE_SECONDS, s.samples.size)
        assertEquals(true, s.peakReached())
    }

    @Test
    fun recorderWithoutReadingsHasNoSummary() {
        val rec = HeartRateRecorder(190)
        rec.startRound(3)
        repeat(30) { rec.tick(null) }
        assertNull(rec.summary())
    }
}
