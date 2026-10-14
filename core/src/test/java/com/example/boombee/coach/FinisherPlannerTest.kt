package com.example.boombee.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FinisherPlannerTest {
    private val cue = listOf(TakeCue(0, listOf(CommandPart.Punch(1))))
    private val short = Take("a", "take_a", 18_600, cue)
    private val long = Take("b", "take_b", 45_000, cue)

    private fun plan(
        takes: List<Take> = listOf(short),
        difficulty: Difficulty = Difficulty.INTERMEDIATE,
        roundMs: Long = 180_000,
        freestyleMs: Long = 0,
        last: String? = null,
        roll: Double = 0.1,
    ) = FinisherPlanner.plan(takes, difficulty, roundMs, freestyleMs, last, roll) { it.first() }

    @Test
    fun endsMarginBeforeTheBell() = assertEquals(18_600 + 1_500L, plan()?.startWhenRemainingMs)

    @Test
    fun endsWhereFreestyleBegins() = assertEquals(30_000 + 1_500 + 18_600L, plan(freestyleMs = 30_000)?.startWhenRemainingMs)

    @Test
    fun neverAtBeginner() = assertNull(plan(difficulty = Difficulty.BEGINNER))

    @Test
    fun onlyAboutHalfTheRounds() = assertNull(plan(roll = 0.5))

    @Test
    fun needsAMinuteOfBuildUp() {
        assertNull(plan(roundMs = 60_000))
        assertNotNull(plan(roundMs = 120_000))
    }

    @Test
    fun skipsTakesTooLongForTheRound() = assertEquals("a", plan(takes = listOf(long, short), roundMs = 100_000)?.take?.id)

    @Test
    fun avoidsRepeatingTheLastTake() = assertEquals("b", plan(takes = listOf(short, long), last = "a")?.take?.id)

    @Test
    fun parsesTakeLine() {
        val (id, clip, cues) = TakeLibrary.parseLine("attack_1 | take_attack_1 | 0.00=1 ; 1.30=1-1 ; 15.02=1-2-slip-2 ; 17.55=breathe")!!
        assertEquals("attack_1", id)
        assertEquals("take_attack_1", clip)
        assertEquals(listOf(0L, 1300L, 15020L, 17550L), cues.map { it.atMs })
        assertEquals(listOf(CommandPart.Phrase(Phrases.BREATHE)), cues.last().parts)
    }

    @Test
    fun everyLineInTheTakesFileParses() {
        val lines = java.io.File("src/main/assets/coach_takes_general.txt").readLines()
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        assert(lines.isNotEmpty())
        lines.forEach { line -> assertNotNull("Bad take line: $line", TakeLibrary.parseLine(line)) }
    }
}
