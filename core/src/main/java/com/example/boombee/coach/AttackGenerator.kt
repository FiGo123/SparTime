package com.example.boombee.coach

import kotlin.random.Random

/**
 * Generates Attack combos from the standard 1-6 numbering, weighted to
 * reflect real usage (jab thrown far more than anything else) and mostly
 * alternating hands between consecutive punches, the way real combos flow
 * (jab→cross→hook, not hook→hook→hook) — with two deliberate exceptions
 * that are themselves real boxing patterns: doubling the jab, and
 * occasionally staying on the same hand for a second punch (e.g. jab then
 * left hook).
 *
 * Tunable here without touching any other file:
 * - [PUNCH_WEIGHTS]: relative frequency of each punch number.
 * - [JAB_REPEAT_CHANCE]: how often a jab is followed by another jab.
 * - [SAME_HAND_CHANCE]: how often a (non-jab-repeat) transition stays on
 *   the same hand instead of alternating.
 */
class AttackGenerator(private val random: Random = Random.Default) {

    fun generateCombo(length: Int): List<Int> {
        require(length >= 1) { "Combo length must be at least 1" }
        val combo = mutableListOf<Int>()
        while (combo.size < length) {
            combo.add(nextPunch(combo.lastOrNull()))
        }
        return combo
    }

    private fun nextPunch(prev: Int?): Int {
        if (prev == null) return weightedPunch(ALL_PUNCHES)

        if (prev == JAB && random.nextDouble() < JAB_REPEAT_CHANCE) {
            return JAB
        }

        val sameHandPool = HAND.getValue(prev)
        return if (random.nextDouble() < SAME_HAND_CHANCE) {
            // Same hand, different punch — exact repeats (besides jab-jab
            // above) stay off-limits by excluding prev from the pool.
            weightedPunch(sameHandPool.filter { it != prev })
        } else {
            val oppositeHandPool = if (sameHandPool === LEFT_HAND) RIGHT_HAND else LEFT_HAND
            weightedPunch(oppositeHandPool)
        }
    }

    private fun weightedPunch(pool: List<Int>): Int {
        val weights = PUNCH_WEIGHTS.filter { it.first in pool }
        val total = weights.sumOf { it.second }
        val roll = random.nextDouble() * total
        var cumulative = 0.0
        for ((punch, weight) in weights) {
            cumulative += weight
            if (roll <= cumulative) return punch
        }
        return weights.last().first
    }

    companion object {
        private const val JAB = 1

        /**
         * Rank (most to least frequent): jab, cross, right hook, left hook,
         * right uppercut, left uppercut. Weights are a first draft — retune
         * freely, they're renormalized against whichever hand-pool subset
         * is in play at each pick, not a fixed total.
         */
        private val PUNCH_WEIGHTS = listOf(
            1 to 0.35, // jab
            2 to 0.25, // cross
            4 to 0.15, // right hook
            3 to 0.12, // left hook
            6 to 0.08, // right uppercut
            5 to 0.05, // left uppercut
        )
        private val LEFT_HAND = listOf(1, 3, 5)
        private val RIGHT_HAND = listOf(2, 4, 6)
        private val ALL_PUNCHES = LEFT_HAND + RIGHT_HAND
        private val HAND: Map<Int, List<Int>> =
            LEFT_HAND.associateWith { LEFT_HAND } + RIGHT_HAND.associateWith { RIGHT_HAND }

        private const val JAB_REPEAT_CHANCE = 0.15
        private const val SAME_HAND_CHANCE = 0.05
    }
}
