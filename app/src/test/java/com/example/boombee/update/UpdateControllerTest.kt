package com.example.boombee.update

import com.example.boombee.update.UpdateController.Companion.chooseUpdateMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateControllerTest {

    private fun choose(
        priority: Int = 0,
        stalenessDays: Int? = null,
        flexibleAllowed: Boolean = true,
        immediateAllowed: Boolean = true,
        declinedBefore: Boolean = false,
    ) = chooseUpdateMode(priority, stalenessDays, flexibleAllowed, immediateAllowed, declinedBefore)

    @Test
    fun normalUpdateIsFlexible() = assertEquals(UpdateMode.FLEXIBLE, choose())

    @Test
    fun criticalPriorityIsImmediate() = assertEquals(UpdateMode.IMMEDIATE, choose(priority = 4))

    @Test
    fun weekOldUpdateIsImmediate() = assertEquals(UpdateMode.IMMEDIATE, choose(stalenessDays = 7))

    @Test
    fun declinedFlexibleUpdateIsNotOfferedAgain() = assertNull(choose(declinedBefore = true, stalenessDays = 3))

    @Test
    fun declinedButStaleBecomesImmediate() =
        assertEquals(UpdateMode.IMMEDIATE, choose(declinedBefore = true, stalenessDays = 8))

    @Test
    fun urgentFallsBackToFlexibleWhenImmediateNotAllowed() =
        assertEquals(UpdateMode.FLEXIBLE, choose(priority = 5, immediateAllowed = false))

    @Test
    fun nothingAllowedStaysQuiet() = assertNull(choose(flexibleAllowed = false, immediateAllowed = false))
}
