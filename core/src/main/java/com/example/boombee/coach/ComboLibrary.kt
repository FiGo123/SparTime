package com.example.boombee.coach

import android.content.Context
import kotlin.random.Random

/** A curated combo loaded from a `coach_combos_*.txt` asset file. */
data class LibraryCombo(
    val parts: List<CommandPart>,
    /** Punch count only — inserted moves (slip/duck) don't count toward difficulty length-matching. */
    val punchCount: Int,
    /** Tagged `| power` in the file — a rear-hand/hook-heavy combo for the "just closed distance" moment. */
    val isPower: Boolean = false,
)

/**
 * Loads and parses `coach_combos_*.txt` asset files into ready-to-speak
 * combos, and picks one matching a requested punch count.
 *
 * File format (see `coach_combos_general.txt` for the real thing): one
 * combo per line, hyphen-separated tokens (`1`-`6` = punches, `slip`/`duck`
 * = inserted defensive moves), `#` comments, blank lines ignored. After the
 * combo, any number of `| `-separated segments follow: the literal word
 * `power` tags it as a power combo (see [powerCombosOfLength]), anything
 * else is a free-text note (ignored by the parser, for humans reading the
 * file). Add more combos by editing the `.txt` file — no code changes.
 *
 * "School" support (Russian/Mexican/American/Cuban, per the roadmap) is
 * just more files: pass their names in [schools] once they exist
 * alongside `coach_combos_general.txt`; combos from all listed schools are
 * pooled together with no per-school weighting (not specified yet).
 */
class ComboLibrary(context: Context, schools: List<String> = listOf("general")) {

    private val allCombos: List<LibraryCombo> = schools
        .mapNotNull { school -> runCatching { context.assets.open("coach_combos_$school.txt") }.getOrNull() }
        .flatMap { stream -> stream.bufferedReader().readLines() }
        .mapNotNull(::parseLine)

    private val combosByPunchCount: Map<Int, List<LibraryCombo>> = allCombos.groupBy { it.punchCount }
    private val powerCombosByPunchCount: Map<Int, List<LibraryCombo>> =
        allCombos.filter { it.isPower }.groupBy { it.punchCount }

    /** A random combo with exactly [punchCount] punches, or null if the library has none that length. */
    fun randomCombo(punchCount: Int, random: Random = Random.Default): LibraryCombo? =
        combosByPunchCount[punchCount]?.randomOrNull(random)

    /** All combos with exactly [punchCount] punches — for callers that want to weight/filter before picking. */
    fun combosOfLength(punchCount: Int): List<LibraryCombo> = combosByPunchCount[punchCount].orEmpty()

    /** All combos tagged `| power` with exactly [punchCount] punches. */
    fun powerCombosOfLength(punchCount: Int): List<LibraryCombo> = powerCombosByPunchCount[punchCount].orEmpty()

    /** Any power combo regardless of length, or null if none are tagged. */
    fun randomPowerCombo(random: Random = Random.Default): LibraryCombo? =
        powerCombosByPunchCount.values.flatten().randomOrNull(random)

    private fun parseLine(rawLine: String): LibraryCombo? {
        val segments = rawLine.split("|").map { it.trim() }
        val line = segments.first()
        if (line.isEmpty() || line.startsWith("#")) return null
        val isPower = segments.drop(1).any { it.equals("power", ignoreCase = true) }

        val parts = mutableListOf<CommandPart>()
        var punchCount = 0
        for (token in line.split("-").map { it.trim().lowercase() }) {
            when (token) {
                "slip" -> parts.add(CommandPart.Phrase(Phrases.SLIP))
                "duck" -> parts.add(CommandPart.Phrase(Phrases.DUCK_UNDER))
                else -> {
                    val punch = token.toIntOrNull() ?: return null
                    parts.add(CommandPart.Punch(punch))
                    punchCount++
                }
            }
        }
        if (parts.isEmpty()) return null
        return LibraryCombo(parts, punchCount, isPower)
    }
}
