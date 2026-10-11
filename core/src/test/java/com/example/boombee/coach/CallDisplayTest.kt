package com.example.boombee.coach

import org.junit.Assert.assertEquals
import org.junit.Test

class CallDisplayTest {
    private val combo = listOf(CommandPart.Punch(1), CommandPart.Punch(2), CommandPart.Phrase(Phrases.SLIP), CommandPart.Punch(3))

    @Test
    fun numbersStyleShowsDigits() = assertEquals("1 – 2 – SLIP – 3", CallDisplay.text(combo, VoiceStyle.NUMBERS))

    @Test
    fun wordsStyleShowsNames() = assertEquals("JAB · CROSS · SLIP · LEFT HOOK", CallDisplay.text(combo, VoiceStyle.WORDS))

    @Test
    fun phraseOnly() = assertEquals("CLOSE DISTANCE", CallDisplay.text(listOf(CommandPart.Phrase(Phrases.CLOSE_DISTANCE)), VoiceStyle.NUMBERS))
}
