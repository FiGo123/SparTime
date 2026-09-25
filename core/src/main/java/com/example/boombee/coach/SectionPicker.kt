package com.example.boombee.coach

import kotlin.random.Random

/**
 * Picks the next [CoachSection] to call, biased by the current one instead
 * of independently at random each beat — so a session reads like real
 * tactics (e.g. you create space, then attack) rather than noise.
 *
 * [TRANSITIONS] is the one thing to edit to retune this. Its long-run
 * (stationary) mix, solved analytically for the weights below, comes out to
 * **Attack ≈ 64%, Defense ≈ 5.3%, Distance ≈ 30.8%** — inside the required
 * Attack ≥ 60% / Defense 5-10% band. If you change the weights, re-derive
 * the stationary distribution (or just log section counts over a long
 * simulated session) before assuming a new table still hits the targets —
 * the aggregate mix is *not* obvious from the per-row numbers alone.
 */
class SectionPicker(private val random: Random = Random.Default) {

    private var current: CoachSection = CoachSection.DISTANCE

    fun next(): CoachSection {
        val options = TRANSITIONS.getValue(current)
        val roll = random.nextDouble()
        var cumulative = 0.0
        var picked = options.last().first
        for ((section, weight) in options) {
            cumulative += weight
            if (roll <= cumulative) {
                picked = section
                break
            }
        }
        current = picked
        return picked
    }

    /**
     * Overrides the next pick outright (no randomness), while still
     * updating [current] so the *following* pick flows naturally from
     * here via the normal transition table. Used to force every round to
     * open on Attack rather than leaving it to chance.
     */
    fun forceNext(section: CoachSection): CoachSection {
        current = section
        return section
    }

    companion object {
        private val TRANSITIONS: Map<CoachSection, List<Pair<CoachSection, Double>>> = mapOf(
            // From Attack: mostly keep attacking; rarely fall back to Defense.
            CoachSection.ATTACK to listOf(
                CoachSection.ATTACK to 0.65,
                CoachSection.DEFENSE to 0.05,
                CoachSection.DISTANCE to 0.30,
            ),
            // From Defense: don't linger in Defense — reset via Distance or counter into Attack.
            CoachSection.DEFENSE to listOf(
                CoachSection.ATTACK to 0.45,
                CoachSection.DEFENSE to 0.10,
                CoachSection.DISTANCE to 0.45,
            ),
            // From Distance: the key rule — favor going to Attack, not Defense.
            CoachSection.DISTANCE to listOf(
                CoachSection.ATTACK to 0.65,
                CoachSection.DEFENSE to 0.05,
                CoachSection.DISTANCE to 0.30,
            ),
        )
    }
}
