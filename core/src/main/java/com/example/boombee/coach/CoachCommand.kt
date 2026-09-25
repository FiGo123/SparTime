package com.example.boombee.coach

/**
 * Which "section" a [CoachCommand] belongs to. [FREESTYLE] is a fourth,
 * separate mode (not part of the Attack/Defense/Distance mix or its
 * transition table) — see [RampStage]/`CoachSession`'s freestyle handling.
 */
enum class CoachSection {
    ATTACK,
    DEFENSE,
    DISTANCE,
    FREESTYLE,
}

/** One spoken unit within a command: either a numbered punch or a named tactical phrase. */
sealed class CommandPart {
    data class Punch(val number: Int) : CommandPart()
    data class Phrase(val text: String) : CommandPart()
}

/** Fixed tactical phrases — not punches, so unaffected by [VoiceStyle]. */
object Phrases {
    const val HANDS_UP = "Hands up"
    const val MOVE_MORE = "Move more"
    const val MAKE_DISTANCE = "Make distance"
    const val CLOSE_DISTANCE = "Close distance"

    // Defensive moves insertable *inside* an Attack combo (see
    // ComboLibrary/coach_combos_general.txt) — not standalone Defense-
    // section calls, these bridge two same-hand punches mid-combo.
    const val SLIP = "Slip"
    const val DUCK_UNDER = "Duck under"

    const val FREESTYLE_START = "Now go freestyle, max power and speed"

    // Spoken once, standalone, before the very first warm-up curriculum
    // step — see CoachSession.nextWarmupCommand(). No hyphen on purpose
    // (a hyphenated warm-up phrase once tripped a broken text-normalizer
    // rule in one device's TTS engine).
    const val WARMUP_START = "Warm up starting now"

    // Freestyle reminder phrases only — see CoachSession.freestyleCommand().
    // No hyphens on purpose (see WARMUP's git history: a hyphenated phrase
    // once tripped a broken text-normalizer rule in one device's TTS engine).
    const val PICK_UP_SPEED = "Pick up the speed"
    const val GO_POWER = "Power now"
}

/**
 * One thing the coach calls out: a punch combo (Attack), a guard/footwork/
 * light-punch beat (Defense), or a footwork call (Distance). [parts] is
 * spoken in order — a 1-part command is a single call, 2+ parts is a combo.
 */
data class CoachCommand(
    val section: CoachSection,
    val parts: List<CommandPart>,
    /**
     * TTS rate multiplier for this one call — only ever above 1.0 for a
     * beat sourced from an active [Flow] (see [FlowLibrary]/`CoachSession`'s
     * `buildFlowStep`), so pacing automatically reverts to normal (1.0) the
     * moment the flow drains, without any separate "reset" step needed.
     */
    val speechRate: Float = 1.0f,
) {
    fun spokenText(voiceStyle: VoiceStyle): String = parts.joinToString(" ") { part ->
        when (part) {
            is CommandPart.Punch -> PunchNaming.spoken(part.number, voiceStyle)
            is CommandPart.Phrase -> part.text
        }
    }
}
