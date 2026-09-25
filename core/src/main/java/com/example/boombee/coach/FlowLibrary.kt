package com.example.boombee.coach

import android.content.Context
import kotlin.random.Random

/** One step in a curated tactical [Flow] loaded from a `coach_flows_*.txt` asset file. */
sealed class FlowStep {
    data class Phrase(val text: String) : FlowStep()
    object PowerCombo : FlowStep()
    object NormalAttack : FlowStep()
    /** A fixed combo, spoken exactly as written — punches *and* slip/duck insertions, same vocabulary as [ComboLibrary]'s asset format. */
    data class LiteralCombo(val parts: List<CommandPart>) : FlowStep()
}

/**
 * A short, ordered chain of tactical beats — e.g. "just closed distance,
 * throw power, get back out, counter, reset" — meant to be spoken faster
 * and closer together than independently-picked calls. See
 * `coach_flows_general.txt` and COACH_MODE_TACTICS.md's flows section.
 */
data class Flow(val steps: List<FlowStep>)

/**
 * Loads and parses `coach_flows_*.txt` asset files into ready-to-queue
 * [Flow]s — the "tied-together tactics" layer above single Attack/Defense/
 * Distance calls, mirroring how [ComboLibrary] loads punch combos.
 *
 * File format: one flow per line, steps separated by `>`, e.g.:
 *
 *   close_distance > power > make_distance > 2-1 > move_more
 *
 * See `coach_flows_general.txt`'s own header for the full step vocabulary.
 * `#` full-line comments and blank lines are ignored; a trailing `| notes`
 * segment (like [ComboLibrary]'s) is stripped and ignored too. Add more
 * flows by editing the `.txt` file — no code changes needed.
 */
class FlowLibrary(context: Context, schools: List<String> = listOf("general")) {

    private val flows: List<Flow> = schools
        .mapNotNull { school -> runCatching { context.assets.open("coach_flows_$school.txt") }.getOrNull() }
        .flatMap { stream -> stream.bufferedReader().readLines() }
        .mapNotNull(::parseLine)

    /** A random flow from the library, or null if none loaded. */
    fun randomFlow(random: Random = Random.Default): Flow? = flows.randomOrNull(random)

    private fun parseLine(rawLine: String): Flow? {
        val line = rawLine.split("|").first().trim()
        if (line.isEmpty() || line.startsWith("#")) return null
        val steps = line.split(">").map { parseStep(it.trim().lowercase()) }
        if (steps.any { it == null } || steps.isEmpty()) return null
        @Suppress("UNCHECKED_CAST")
        return Flow(steps as List<FlowStep>)
    }

    private fun parseStep(token: String): FlowStep? = when (token) {
        "close_distance" -> FlowStep.Phrase(Phrases.CLOSE_DISTANCE)
        "make_distance" -> FlowStep.Phrase(Phrases.MAKE_DISTANCE)
        "move_more" -> FlowStep.Phrase(Phrases.MOVE_MORE)
        "hands_up" -> FlowStep.Phrase(Phrases.HANDS_UP)
        "power" -> FlowStep.PowerCombo
        "attack" -> FlowStep.NormalAttack
        else -> parseLiteralCombo(token)
    }

    /** `1-6` digits become punches; `slip`/`duck` become the matching mid-combo phrase — same vocabulary [ComboLibrary] uses for `coach_combos_*.txt`. */
    private fun parseLiteralCombo(token: String): FlowStep.LiteralCombo? {
        val rawTokens = token.split("-")
        val parts = rawTokens.map {
            when (it) {
                "slip" -> CommandPart.Phrase(Phrases.SLIP)
                "duck" -> CommandPart.Phrase(Phrases.DUCK_UNDER)
                else -> it.toIntOrNull()?.let { n -> CommandPart.Punch(n) }
            }
        }
        return if (parts.isEmpty() || parts.any { it == null }) null else {
            @Suppress("UNCHECKED_CAST")
            FlowStep.LiteralCombo(parts as List<CommandPart>)
        }
    }
}
