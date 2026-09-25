package com.example.boombee.coach

/** How the coach calls punches out loud. Chosen per-session, next to Difficulty. */
enum class VoiceStyle {
    /** "One", "Two", "Three"... */
    NUMBERS,

    /** "Jab", "Cross", "Left hook"... — spoken faster since words take longer than a digit. */
    WORDS,
}

/**
 * Standard 1-6 gym numbering. Left/right assumes an orthodox stance (lead
 * hand = left); a southpaw setting to flip this is a planned future
 * addition, not built yet.
 */
object PunchNaming {
    private val wordNames = mapOf(
        1 to "Jab",
        2 to "Cross",
        3 to "Left hook",
        4 to "Right hook",
        5 to "Left uppercut",
        6 to "Right uppercut",
    )

    fun spoken(punch: Int, style: VoiceStyle): String = when (style) {
        // A bare digit string is read out as its number word by TTS engines
        // ("1" -> "one"), so Numbers mode needs nothing fancier than this.
        VoiceStyle.NUMBERS -> punch.toString()
        VoiceStyle.WORDS -> wordNames.getValue(punch)
    }
}
