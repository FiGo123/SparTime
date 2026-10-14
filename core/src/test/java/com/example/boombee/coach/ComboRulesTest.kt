package com.example.boombee.coach

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ComboRulesTest {
    private fun parts(combo: String) = combo.split("-").map {
        when (it) {
            "slip" -> CommandPart.Phrase(Phrases.SLIP)
            "duck" -> CommandPart.Phrase(Phrases.DUCK_UNDER)
            else -> CommandPart.Punch(it.toInt())
        }
    }

    @Test
    fun sameHandAfterSlipOrDuck() {
        assertTrue(ComboRules.sameHandAroundDefense(parts("1-2-slip-2")))
        assertTrue(ComboRules.sameHandAroundDefense(parts("1-1-2-slip-4")))
        assertTrue(ComboRules.sameHandAroundDefense(parts("1-3-duck-5-2")))
        assertFalse(ComboRules.sameHandAroundDefense(parts("1-1-2-slip-5")))
        assertFalse(ComboRules.sameHandAroundDefense(parts("1-1-2-slip-3")))
        assertFalse(ComboRules.sameHandAroundDefense(parts("2-duck-1")))
    }

    /** Every combo in the combo, flow and take files follows the rule (so none is silently skipped on device). */
    @Test
    fun assetFilesFollowTheRule() {
        val combo = Regex("""[0-9a-z]+(?:-[0-9a-z]+)+""")
        val bad = listOf("coach_combos_general.txt", "coach_flows_general.txt", "coach_takes_general.txt").flatMap { name ->
            File("src/main/assets/$name").readLines()
                .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
                .map { line -> if (name.startsWith("coach_takes")) line.split("|").getOrElse(2) { "" } else line.split("|").first() }
                .flatMap { body -> combo.findAll(body).map { it.value } }
                .filter { token -> token.split("-").all { it in setOf("slip", "duck") || it.toIntOrNull() in 1..6 } }
                .filterNot { ComboRules.sameHandAroundDefense(parts(it)) }
                .map { "$name: $it" }
        }
        assertTrue("Break the same-hand slip/duck rule: $bad", bad.isEmpty())
    }
}
