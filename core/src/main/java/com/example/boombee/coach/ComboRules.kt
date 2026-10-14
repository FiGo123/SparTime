package com.example.boombee.coach

/**
 * Boxing rules every combo must follow, checked when the combo/flow files
 * load (a breaking line is skipped and logged) and by unit tests on the
 * asset files.
 */
object ComboRules {
    /**
     * After a slip or duck, the next punch comes from the same hand as the
     * punch before it (2-slip-2 / 2-slip-4 / 2-slip-6, not 2-slip-5):
     * you slip to the side of the hand you just threw and counter with it.
     */
    fun sameHandAroundDefense(parts: List<CommandPart>): Boolean {
        parts.forEachIndexed { i, part ->
            if (part is CommandPart.Phrase && (part.text == Phrases.SLIP || part.text == Phrases.DUCK_UNDER)) {
                val before = parts.subList(0, i).lastOrNull { it is CommandPart.Punch } as CommandPart.Punch?
                val after = parts.subList(i + 1, parts.size).firstOrNull { it is CommandPart.Punch } as CommandPart.Punch?
                if (before != null && after != null && isLeadHand(before.number) != isLeadHand(after.number)) return false
            }
        }
        return true
    }

    /** Odd punch numbers (1, 3, 5) are the lead hand, even (2, 4, 6) the rear hand. */
    private fun isLeadHand(punch: Int) = punch % 2 == 1
}
