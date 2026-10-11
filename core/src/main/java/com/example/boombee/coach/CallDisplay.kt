package com.example.boombee.coach

/**
 * On-screen text for a coach call, matching what's spoken: "1 – 2 – 3" in
 * Numbers style, "JAB · CROSS · LEFT HOOK" in Words style. Phrases (slip,
 * distance, freestyle…) are shown in capitals in either style.
 */
object CallDisplay {
    fun text(parts: List<CommandPart>, style: VoiceStyle): String {
        val separator = if (style == VoiceStyle.NUMBERS) " – " else " · "
        return parts.joinToString(separator) { part ->
            when (part) {
                is CommandPart.Punch -> PunchNaming.spoken(part.number, style).uppercase()
                is CommandPart.Phrase -> part.text.uppercase()
            }
        }
    }
}
