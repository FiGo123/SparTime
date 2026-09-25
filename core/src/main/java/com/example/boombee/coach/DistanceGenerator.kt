package com.example.boombee.coach

import kotlin.random.Random

/**
 * Generates Distance calls — plain footwork guidance, always a single call.
 * "Move more" is demoted relative to Make/Close distance — real-device
 * feedback found generic movement instructions too frequent. "Make
 * distance" is demoted further still, and can't repeat two calls in a row
 * — a second real-device complaint, independent of the first.
 */
class DistanceGenerator(private val random: Random = Random.Default) {

    // Last phrase actually spoken — two independent Distance beats landing
    // on the same phrase back to back (most visibly "Make distance" twice)
    // reads as a stuck loop. Retried a bounded number of times rather than
    // allowed, same pattern as DefenseGenerator's repeated-phrase guard.
    private var lastPhrase: String? = null

    fun generate(): List<CommandPart> {
        var phrase = pickPhrase()
        var attempts = 0
        while (phrase == lastPhrase && attempts < 10) {
            phrase = pickPhrase()
            attempts++
        }
        lastPhrase = phrase
        return listOf(CommandPart.Phrase(phrase))
    }

    private fun pickPhrase(): String {
        val roll = random.nextDouble()
        var cumulative = 0.0
        for ((phrase, weight) in WEIGHTS) {
            cumulative += weight
            if (roll <= cumulative) return phrase
        }
        return WEIGHTS.last().first
    }

    companion object {
        // "Make distance" demoted from 0.4 — real-device feedback found it
        // too frequent even after the first Move-more demotion pass.
        private val WEIGHTS = listOf(
            Phrases.MAKE_DISTANCE to 0.3,
            Phrases.CLOSE_DISTANCE to 0.45,
            Phrases.MOVE_MORE to 0.25,
        )
    }
}
