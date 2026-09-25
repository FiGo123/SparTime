package com.example.boombee.coach

/**
 * Maps spoken content to the asset filename (under `assets/audio/`, no
 * extension — [ClipPlayer] matches whatever extension is actually there)
 * a pre-recorded voice clip would live at. See `VOICE_RECORDING_SCRIPT.md`
 * for the recording plan and the exact key names below.
 *
 * Recording the vocabulary is meant to happen incrementally — [keysFor]
 * returns null the moment any single part has no known key, so a caller
 * can fall back to [CoachCommand.spokenText]/TTS for that whole command;
 * a partially-recorded voice never breaks playback, it just means some
 * commands still use TTS until more clips exist.
 */
object CoachAudio {
    private val PHRASE_KEYS: Map<String, String> = mapOf(
        Phrases.HANDS_UP to "phrase_hands_up",
        Phrases.MOVE_MORE to "phrase_move_more",
        Phrases.MAKE_DISTANCE to "phrase_make_distance",
        Phrases.CLOSE_DISTANCE to "phrase_close_distance",
        Phrases.SLIP to "phrase_slip",
        Phrases.DUCK_UNDER to "phrase_duck_under",
        Phrases.FREESTYLE_START to "phrase_freestyle_start",
        Phrases.PICK_UP_SPEED to "phrase_pick_up_speed",
        Phrases.GO_POWER to "phrase_go_power",
        Phrases.WARMUP_START to "phrase_warmup_start",
    )

    /** Ordered clip keys for [parts] rendered in [voiceStyle], or null if any part has no known key. */
    fun keysFor(parts: List<CommandPart>, voiceStyle: VoiceStyle): List<String>? {
        val keys = parts.map { part ->
            when (part) {
                is CommandPart.Punch -> if (voiceStyle == VoiceStyle.WORDS) "word_${part.number}" else "number_${part.number}"
                is CommandPart.Phrase -> PHRASE_KEYS[part.text]
            }
        }
        @Suppress("UNCHECKED_CAST")
        return if (keys.any { it == null }) null else keys as List<String>
    }
}
