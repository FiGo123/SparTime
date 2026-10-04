package com.example.boombee.tutorial

import com.example.boombee.tutorial.ComboGame.TapResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ComboGameTest {

    @Test
    fun correctTapsFinishComboAndScore() {
        val game = ComboGame(listOf(listOf(1, 2), listOf(3)))
        assertEquals(TapResult.CORRECT, game.tap(1))
        assertEquals(TapResult.COMBO_DONE, game.tap(2))
        assertEquals(1, game.score)
        assertEquals(1, game.comboIndex)
        assertEquals(TapResult.GAME_OVER, game.tap(3))
        assertEquals(2, game.score)
        assertTrue(game.isOver)
    }

    @Test
    fun wrongTapRestartsComboAndLosesItsPoint() {
        val game = ComboGame(listOf(listOf(1, 2), listOf(3)))
        game.tap(1)
        assertEquals(TapResult.WRONG, game.tap(4))
        assertEquals(0, game.position)
        assertEquals(1, game.expectedPunch)
        game.tap(1)
        assertEquals(TapResult.COMBO_DONE, game.tap(2))
        assertEquals(0, game.score)
        assertEquals(TapResult.GAME_OVER, game.tap(3))
        assertEquals(1, game.score)
    }

    @Test
    fun buildHasEightCombosAcrossThreeLevels() {
        val game = ComboGame.build(listOf(listOf(2, 3, 2), listOf(1, 2, 3, 4), listOf(1, 7)), Random(1))
        assertEquals(8, game.total)
        assertEquals(listOf(1, 1, 1, 2, 2, 2, 3, 3), (0 until 8).map(ComboGame::levelOf))
    }

    @Test
    fun buildFallsBackWhenPoolIsEmpty() {
        assertEquals(8, ComboGame.build(emptyList()).total)
    }
}
