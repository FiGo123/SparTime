package com.example.boombee.coach

import android.util.Log
import kotlin.random.Random

private const val TAG = "boombeelogs"

/**
 * Generates Defense beats from a small vocabulary (guard, footwork, or a
 * light punch) — mostly a single call, occasionally a short 2-part combo,
 * matching real "stick and move" defense rather than standing still.
 *
 * Tunable here without touching any other file:
 * - [SINGLE_ACTION_CHANCE]: how often a beat is 1 action vs. a 2-action combo.
 * - [ACTION_WEIGHTS]: mix of guard / footwork / punch when picking an action.
 * - [DEFENSE_PUNCH_WEIGHTS]: jab-vs-cross mix when the action is a punch
 *   (only these two ever appear in Defense — no hooks/uppercuts here).
 */
class DefenseGenerator(private val random: Random = Random.Default) {

    // Last phrase actually spoken (across calls) — two independent Defense
    // beats landing on the same phrase back to back (e.g. "Hands up" twice
    // in a row) reads the same as a repeated in-combo phrase, so this is
    // checked at both the call boundary and within one beat's own combo.
    private var lastSpoken: CommandPart? = null

    fun generate(): List<CommandPart> {
        val length = if (random.nextDouble() < SINGLE_ACTION_CHANCE) 1 else 2

        var first = randomAction()
        var attempts = 0
        while (isRepeatedPhrase(lastSpoken, first) && attempts < 10) {
            first = randomAction()
            attempts++
        }

        if (length == 1) {
            lastSpoken = first
            Log.d(TAG, "DefenseGenerator.generate: [$first]")
            return listOf(first)
        }

        var second = randomAction()
        attempts = 0
        // Any repeated phrase ("Move more, move more" / "Hands up, Hands
        // up") reads as a no-op combo — retry a bounded number of times
        // rather than allowing it. Punches can repeat (double jab is fine).
        while (isRepeatedPhrase(first, second) && attempts < 10) {
            second = randomAction()
            attempts++
        }
        lastSpoken = second
        Log.d(TAG, "DefenseGenerator.generate: [$first, $second]")
        return listOf(first, second)
    }

    private fun isRepeatedPhrase(a: CommandPart?, b: CommandPart) =
        a is CommandPart.Phrase && b is CommandPart.Phrase && a.text == b.text

    private fun randomAction(): CommandPart {
        val roll = random.nextDouble()
        var cumulative = 0.0
        for ((kind, weight) in ACTION_WEIGHTS) {
            cumulative += weight
            if (roll <= cumulative) return kind.toPart()
        }
        return ActionKind.HANDS_UP.toPart()
    }

    private fun ActionKind.toPart(): CommandPart = when (this) {
        ActionKind.HANDS_UP -> CommandPart.Phrase(Phrases.HANDS_UP)
        ActionKind.MOVE_MORE -> CommandPart.Phrase(Phrases.MOVE_MORE)
        ActionKind.PUNCH -> CommandPart.Punch(defensePunchNumber())
    }

    private fun defensePunchNumber(): Int = if (random.nextDouble() < 0.7) 1 else 2

    private enum class ActionKind { HANDS_UP, MOVE_MORE, PUNCH }

    companion object {
        private const val SINGLE_ACTION_CHANCE = 0.9

        // Move-more and the punch action's own jab bias (defensePunchNumber,
        // 90%->70%) both demoted per real-device feedback — Defense was an
        // uncapped secondary source of the same "too much jab"/"too much
        // movement" complaints the Attack-side tuning already addresses. See
        // CoachSession.defenseParts() for the matching jab-only streak cap.
        private val ACTION_WEIGHTS = listOf(
            ActionKind.HANDS_UP to 0.60,
            ActionKind.MOVE_MORE to 0.15,
            ActionKind.PUNCH to 0.25,
        )
    }
}
