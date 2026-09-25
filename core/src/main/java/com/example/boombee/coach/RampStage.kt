package com.example.boombee.coach

/**
 * A brand-new session throwing full-difficulty combos from beat one felt
 * overwhelming in real testing — both Numbers (unfamiliar mapping) and
 * Words (long phrases) modes. Rather than a single "easier" toggle, the
 * session ramps in, regardless of the selected [Difficulty]:
 *
 * - [WARMUP]: an optional window (0/30/60s, user's choice) driven by
 *   `CoachSession.nextWarmupCommand()`/`warmupDelayMillis()` — a *separate,
 *   dedicated pre-round phase* the caller runs entirely before Round 1's
 *   own timer/coaching starts, purely so the user gets familiar with the
 *   coach's voice and pacing first. Not a simplified slice of Round 1
 *   itself. Content is a fixed teaching curriculum (see
 *   `CoachSession.WARMUP_CURRICULUM`), not driven by these fields at all —
 *   only [extraRestSeconds] (the pacing) is actually read, via
 *   `warmupDelayMillis()`. Skipped entirely if the user picked "Off".
 * - [ROUND_ONE_EARLY]: the first ~45s of Round 1 (see
 *   `CoachSession.ROUND_ONE_EARLY_WINDOW_MS`) — heavily single-punch/short,
 *   from the real combo library.
 * - [ROUND_ONE_LATE]: the rest of Round 1 — deliberately identical numbers
 *   to [ROUND_TWO], so a round eases into that pacing rather than jumping
 *   straight from "very simple" to "Round 2's pacing" at the round
 *   boundary.
 * - [ROUND_TWO]: all of round 2 — a lighter version of the selected
 *   difficulty.
 * - [NORMAL]: round 3 onward — full selected difficulty, unchanged from
 *   before this feature existed.
 *
 * [maxComboLength] combines with [VoiceStyle]'s own cap and the
 * difficulty's [DifficultyProfile.comboLengthRange] via `minOf(...)` at
 * the call site — whichever is most restrictive wins.
 */
enum class RampStage(
    val singleJabChance: Double,
    val maxComboLength: Int,
    val extraRestSeconds: Double,
) {
    WARMUP(singleJabChance = 0.7, maxComboLength = 2, extraRestSeconds = 2.0),
    // singleJabChance shifted down across the board over three rounds of
    // real-device feedback (was 0.35/0.15/0.15/0.08, then 0.30/0.10/0.10/0.03,
    // then 0.25/0.05/0.05/0.0) — solo jab / jab-jab kept reading as spammy.
    // See also CoachSession.JAB_JAB_WEIGHT (the matching jab-jab combo
    // demotion) and its 4-in-a-row streak cooldown, which now also covers
    // Defense-section jabs (DefenseGenerator's own jab bias was a separate,
    // previously-uncapped source of the same complaint).
    ROUND_ONE_EARLY(singleJabChance = 0.20, maxComboLength = 2, extraRestSeconds = 1.0),
    ROUND_ONE_LATE(singleJabChance = 0.05, maxComboLength = 3, extraRestSeconds = 0.5),
    ROUND_TWO(singleJabChance = 0.05, maxComboLength = 3, extraRestSeconds = 0.5),
    NORMAL(singleJabChance = 0.0, maxComboLength = Int.MAX_VALUE, extraRestSeconds = 0.0),
}
