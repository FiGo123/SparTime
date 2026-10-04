package com.example.boombee.tutorial

import kotlin.random.Random

/**
 * Rules for the "Tap the combo" mini-game, free of Android so they can be
 * unit-tested. The player taps each combo's punches in order; a wrong tap
 * restarts the current combo (the coach calls it again) and costs that
 * combo its point.
 */
class ComboGame(private val combos: List<List<Int>>) {

    enum class TapResult { CORRECT, WRONG, COMBO_DONE, GAME_OVER }

    var comboIndex = 0
        private set

    /** How many punches of the current combo have been tapped correctly. */
    var position = 0
        private set

    /** Combos finished without a wrong tap. */
    var score = 0
        private set

    private var missedCurrent = false

    val total: Int get() = combos.size
    val isOver: Boolean get() = comboIndex >= combos.size
    val currentCombo: List<Int> get() = combos[comboIndex]
    val expectedPunch: Int get() = currentCombo[position]
    val level: Int get() = levelOf(comboIndex)

    fun tap(punch: Int): TapResult {
        check(!isOver) { "Game is over" }
        if (punch != expectedPunch) {
            missedCurrent = true
            position = 0
            return TapResult.WRONG
        }
        position++
        if (position < currentCombo.size) return TapResult.CORRECT

        if (!missedCurrent) score++
        comboIndex++
        position = 0
        missedCurrent = false
        return if (isOver) TapResult.GAME_OVER else TapResult.COMBO_DONE
    }

    companion object {
        val LEVEL_1 = listOf(listOf(1, 2), listOf(1, 1, 2), listOf(1, 3))
        val LEVEL_2 = listOf(listOf(1, 2, 3), listOf(2, 3, 2), listOf(1, 6, 3, 2))
        private val LEVEL_3_FALLBACK = listOf(listOf(1, 2, 3, 2), listOf(3, 2, 3))
        private const val LEVEL_3_COUNT = 2

        /** 1 = combo shown + spoken, 2 = spoken only, 3 = spoken only and faster. */
        fun levelOf(comboIndex: Int): Int = when {
            comboIndex < LEVEL_1.size -> 1
            comboIndex < LEVEL_1.size + LEVEL_2.size -> 2
            else -> 3
        }

        /** Levels 1–2 are fixed; level 3 picks from [level3Pool] (punch-only combos), padded with defaults. */
        fun build(level3Pool: List<List<Int>>, random: Random = Random.Default): ComboGame {
            val level3 = (level3Pool.filter { it.isNotEmpty() && it.all { p -> p in 1..6 } }.shuffled(random) + LEVEL_3_FALLBACK)
                .distinct()
                .take(LEVEL_3_COUNT)
            return ComboGame(LEVEL_1 + LEVEL_2 + level3)
        }
    }
}
