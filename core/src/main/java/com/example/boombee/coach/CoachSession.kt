package com.example.boombee.coach

import android.content.Context
import android.util.Log
import com.example.boombee.data.Dao
import com.example.boombee.data.PreferencesProvider
import kotlin.random.Random

private const val TAG = "boombeelogs"

/**
 * Drives one training session's worth of coaching: decides what to call
 * next and how long to wait before the next call, tracking simple stats
 * along the way for the training-history record.
 *
 * Beyond the per-call Attack/Defense/Distance transition table
 * ([SectionPicker]), this also tracks a small persistent "range" state —
 * [RangeState] — set by *specific* Distance phrases (not just the
 * section): "Close distance" forces a power-punch Attack next, then a
 * forced "Make distance" + "Hands up"; "Make distance" biases subsequent
 * Attack calls toward jab-only work until the range changes again. See
 * COACH_MODE_TACTICS.md's range-state section for the full rationale.
 *
 * Doesn't know about TTS or timers — callers (phone `Second.kt`, watch
 * `RoundTimerService`) drive it with their own tick/delay loop during
 * ROUND phases only, and render [CoachCommand.spokenText] with whatever
 * [VoiceStyle] the user picked. It does need [Context] (for [ComboLibrary]
 * and reading the freestyle/tactical-commands settings via [Dao]).
 *
 * The optional warm-up (0/30/60s, user's choice) is a separate *pre-round*
 * phase, not part of a real round at all — callers drive it with
 * [nextWarmupCommand]/[warmupDelayMillis] for their own chosen duration
 * *before* ever calling [startRound] for round 1, so it doesn't eat into
 * that round's configured length. See [RampStage.WARMUP].
 */
class CoachSession(
    context: Context,
    difficulty: Difficulty,
    private val random: Random = Random.Default,
) {
    private val profile = profileFor(difficulty)
    private val sectionPicker = SectionPicker(random)
    private val comboLibrary = ComboLibrary(context)
    private val flowLibrary = FlowLibrary(context)
    private val attackGenerator = AttackGenerator(random)
    private val defenseGenerator = DefenseGenerator(random)
    private val distanceGenerator = DistanceGenerator(random)
    private val dao = Dao(PreferencesProvider(context))

    val stats = CoachStats()

    private var isFirstCommandOfRound = true
    private var currentRoundNumber = 1
    private var elapsedInRoundMs = 0L

    private enum class RangeState { NEUTRAL, NEAR, FAR }
    private var rangeState = RangeState.NEUTRAL

    /**
     * Work/ease bias applied on top of [RampStage]'s own numbers — see
     * [maybeRollIntensity]. [paceMultiplier] scales the rest portion of
     * [delayMillisFor] (below 1.0 = faster); [comboLengthBonus] shifts
     * [attackParts]'s max combo length; [singleJabDelta] shifts
     * [RampStage.singleJabChance] directly (both clamped to sane ranges at
     * the call site).
     */
    private enum class Intensity(val paceMultiplier: Double, val comboLengthBonus: Int, val singleJabDelta: Double) {
        LOW(paceMultiplier = 1.4, comboLengthBonus = -1, singleJabDelta = 0.10),
        NORMAL(paceMultiplier = 1.0, comboLengthBonus = 0, singleJabDelta = 0.0),
        HIGH(paceMultiplier = 0.7, comboLengthBonus = 1, singleJabDelta = -0.05),
    }

    // A tied-together tactical sequence in progress — see distanceParts()
    // (where "Close distance" rolls a chance to queue one from
    // [flowLibrary]) and buildFlowStep() (where each queued FlowStep
    // becomes a real CoachCommand, tagged with FLOW_SPEECH_RATE so pacing
    // speeds up for exactly the flow's own beats and reverts automatically
    // once the queue drains). See COACH_MODE_TACTICS.md's flows section.
    private val pendingFlow = ArrayDeque<FlowStep>()

    private var freestyleArmed = false
    private var freestyleStartElapsedMs = Long.MAX_VALUE
    private var freestyleActive = false
    private var freestyleAnnounced = false
    // How many reminder beats this freestyle window will give (1 or 2,
    // rolled once when it activates) and how many it's given so far — see
    // freestyleCommand(). Replaces a per-beat independent roll, which could
    // land anywhere from 0 to several reminders in one window.
    private var freestyleReminderTarget = 0
    private var freestyleReminderCount = 0

    private var warmupStepIndex = 0
    private var warmupAnnounced = false

    // Consecutive jab-only (solo jab or jab-jab) Attack calls — tracked
    // across every source that can produce one (the normal ramp-driven
    // pick, and farRangeAttack()'s heavy jab bias) so "spammy" streaks get
    // capped regardless of which path produced them. See recordJabOnlyStreak().
    private var consecutiveJabOnlyCount = 0
    private var jabOnlyCooldownRemaining = 0

    // Last punch number actually spoken and how many times in a row —
    // tracked across Attack calls (including flow-sourced ones) so a punch
    // in {2,3,4,5,6} can repeat back-to-back at most twice; jab isn't
    // included here since it already has its own, separate jab-only-streak
    // system above. See applyPunchRepeatCap()/recordAttackParts().
    private var lastPunchNumber: Int? = null
    private var consecutiveSamePunchCount = 0

    // Unpredictable work/ease intervals within a round — real interval
    // training isn't one flat pace, and testing found a session that never
    // varies read as too mechanical. Re-rolled every 15-30s (see
    // maybeRollIntensity()); HIGH means faster/harder, LOW means
    // slower/simpler, both layered on top of the existing ramp-stage base
    // rather than replacing it.
    private var intensity = Intensity.NORMAL
    private var nextIntensityRollMs = 0L

    /** Call once at the start of each round (not Rest) — resets ramp/range/freestyle state and rolls a fresh freestyle window. */
    fun startRound(roundNumber: Int, roundDurationSeconds: Int) {
        isFirstCommandOfRound = true
        currentRoundNumber = roundNumber
        elapsedInRoundMs = 0L
        rangeState = RangeState.NEUTRAL
        pendingFlow.clear()
        freestyleActive = false
        freestyleAnnounced = false
        freestyleArmed = false
        freestyleReminderTarget = 0
        freestyleReminderCount = 0
        consecutiveJabOnlyCount = 0
        jabOnlyCooldownRemaining = 0
        lastPunchNumber = null
        consecutiveSamePunchCount = 0
        intensity = Intensity.NORMAL
        nextIntensityRollMs = 0L

        if (roundNumber >= 2 && dao.getFreestyleEnabled() && random.nextDouble() < FREESTYLE_CHANCE) {
            val durationSeconds = FREESTYLE_DURATIONS_SECONDS.random(random)
            val roundDurationMs = roundDurationSeconds * 1000L
            val freestyleMs = durationSeconds * 1000L
            if (roundDurationMs > freestyleMs + MIN_ROUND_BUFFER_MS) {
                freestyleArmed = true
                freestyleStartElapsedMs = roundDurationMs - freestyleMs
            }
        }
        Log.d(TAG, "startRound: round=$roundNumber durationSec=$roundDurationSeconds freestyleArmed=$freestyleArmed")
    }

    private fun rampStage(): RampStage = when {
        currentRoundNumber == 1 && elapsedInRoundMs < ROUND_ONE_EARLY_WINDOW_MS -> RampStage.ROUND_ONE_EARLY
        currentRoundNumber == 1 -> RampStage.ROUND_ONE_LATE
        currentRoundNumber == 2 -> RampStage.ROUND_TWO
        else -> RampStage.NORMAL
    }

    /**
     * Unpredictable work/ease intervals layered on top of [rampStage] — see
     * the [intensity] field doc. Picks a *different* intensity than the
     * current one (so a reroll is always a perceptible change) and a fresh
     * 15-30s window every time the previous one expires.
     */
    private fun maybeRollIntensity() {
        if (elapsedInRoundMs < nextIntensityRollMs) return
        intensity = Intensity.values().filter { it != intensity }.random(random)
        val durationSeconds = random.nextInt(15, 31)
        nextIntensityRollMs = elapsedInRoundMs + durationSeconds * 1000L
        Log.d(TAG, "maybeRollIntensity: intensity=$intensity for ${durationSeconds}s")
    }

    /**
     * One call for the optional pre-round warm-up phase — a separate,
     * dedicated block (see the class doc) meant to get the user familiar
     * with the coach's voice/pacing before Round 1 actually starts, not a
     * simplified slice of Round 1 itself.
     *
     * Plays a fixed, escalating teaching sequence rather than a random
     * pool — real testing found "mostly just Jab" didn't actually teach
     * anything: single punch, then a 2-punch combo, then a tactical call
     * (so it's not "only punches" in the user's ear), then two 3-punch
     * combos, then a second tactical call, then repeats/extends. Loops if
     * the chosen warm-up length runs past the list.
     *
     * The very first call of any warm-up is [Phrases.WARMUP_START], spoken
     * once and standalone (not merged into the first curriculum step) so
     * the warm-up phase has a clear, meaningful start rather than just
     * launching straight into "Jab".
     */
    fun nextWarmupCommand(): CoachCommand {
        if (!warmupAnnounced) {
            warmupAnnounced = true
            val command = CoachCommand(CoachSection.ATTACK, listOf(CommandPart.Phrase(Phrases.WARMUP_START)))
            stats.record(command)
            Log.d(TAG, "nextWarmupCommand: announcement")
            return command
        }
        val step = WARMUP_CURRICULUM[warmupStepIndex % WARMUP_CURRICULUM.size]
        warmupStepIndex++
        val command = CoachCommand(step.section, step.parts)
        stats.record(command)
        Log.d(TAG, "nextWarmupCommand: step=${(warmupStepIndex - 1) % WARMUP_CURRICULUM.size} parts=${step.parts}")
        return command
    }

    /** How long to wait (ms) after speaking a [nextWarmupCommand] result before the next one. */
    fun warmupDelayMillis(command: CoachCommand): Long {
        val throwSeconds = command.parts.size * profile.secondsPerPunch
        val delayMs = ((throwSeconds + profile.baseRestBetweenCommandsSeconds + RampStage.WARMUP.extraRestSeconds) * 1000).toLong()
        Log.d(TAG, "warmupDelayMillis: delayMs=$delayMs")
        return delayMs
    }

    /** Picks the next command to speak (rendered for [voiceStyle]), or null for a deliberately silent beat (freestyle only). */
    fun nextCommand(voiceStyle: VoiceStyle): CoachCommand? {
        if (freestyleArmed && !freestyleActive && elapsedInRoundMs >= freestyleStartElapsedMs) {
            freestyleActive = true
            freestyleReminderTarget = random.nextInt(1, 3)
            freestyleReminderCount = 0
        }
        if (freestyleActive) {
            return freestyleCommand()?.also { stats.record(it) }
        }
        maybeRollIntensity()

        val tacticalEnabled = dao.getTacticalCommandsEnabled()
        if (!tacticalEnabled) pendingFlow.clear()

        val command = when {
            isFirstCommandOfRound -> {
                isFirstCommandOfRound = false
                sectionPicker.forceNext(CoachSection.ATTACK)
                CoachCommand(CoachSection.ATTACK, attackParts(voiceStyle, rampStage()))
            }
            !tacticalEnabled -> {
                sectionPicker.forceNext(CoachSection.ATTACK)
                CoachCommand(CoachSection.ATTACK, attackParts(voiceStyle, rampStage()))
            }
            pendingFlow.isNotEmpty() -> buildFlowStep(pendingFlow.removeFirst(), voiceStyle)
            else -> {
                val section = sectionPicker.next()
                val parts = when (section) {
                    CoachSection.ATTACK -> attackParts(voiceStyle, rampStage())
                    CoachSection.DEFENSE -> defenseParts()
                    CoachSection.DISTANCE -> distanceParts()
                    CoachSection.FREESTYLE -> emptyList() // unreachable via normal section flow
                }
                CoachCommand(section, parts)
            }
        }
        stats.record(command)
        Log.d(TAG, "nextCommand: round=$currentRoundNumber stage=${rampStage()} elapsedMs=$elapsedInRoundMs range=$rangeState section=${command.section} parts=${command.parts}")
        return command
    }

    /**
     * Turns one queued [FlowStep] into a real [CoachCommand], tagged with
     * [FLOW_SPEECH_RATE] so this beat (and the pause after it, via
     * [delayMillisFor]) plays faster than normal — see [pendingFlow].
     */
    private fun buildFlowStep(step: FlowStep, voiceStyle: VoiceStyle): CoachCommand = when (step) {
        is FlowStep.Phrase -> {
            val section = sectionForPhrase(step.text)
            sectionPicker.forceNext(section)
            when (step.text) {
                Phrases.CLOSE_DISTANCE -> rangeState = RangeState.NEAR
                Phrases.MAKE_DISTANCE -> rangeState = RangeState.FAR
            }
            CoachCommand(section, listOf(CommandPart.Phrase(step.text)), speechRate = FLOW_SPEECH_RATE)
        }
        FlowStep.PowerCombo -> {
            sectionPicker.forceNext(CoachSection.ATTACK)
            val parts = comboLibrary.randomPowerCombo(random)?.parts ?: attackParts(voiceStyle, rampStage())
            CoachCommand(CoachSection.ATTACK, recordAttackParts(parts), speechRate = FLOW_SPEECH_RATE)
        }
        FlowStep.NormalAttack -> {
            sectionPicker.forceNext(CoachSection.ATTACK)
            CoachCommand(CoachSection.ATTACK, attackParts(voiceStyle, rampStage()), speechRate = FLOW_SPEECH_RATE)
        }
        is FlowStep.LiteralCombo -> {
            sectionPicker.forceNext(CoachSection.ATTACK)
            CoachCommand(CoachSection.ATTACK, recordAttackParts(step.parts), speechRate = FLOW_SPEECH_RATE)
        }
    }

    private fun sectionForPhrase(text: String): CoachSection = when (text) {
        Phrases.HANDS_UP -> CoachSection.DEFENSE
        else -> CoachSection.DISTANCE
    }

    /**
     * Generates a Defense call and, unlike [DefenseGenerator] on its own,
     * respects the shared jab-only cooldown ([recordJabOnlyStreak]) — the
     * "punch" action there defaults to jab most of the time, which used to
     * be a completely uncapped secondary source of the same "too much jab"
     * complaint the Attack-side streak limit already addresses. Swaps to
     * "Hands up" if the cooldown is active and Defense still landed on a
     * jab-only result.
     */
    private fun defenseParts(): List<CommandPart> {
        val blockJabOnly = jabOnlyCooldownRemaining > 0
        val parts = defenseGenerator.generate()
        val safeParts = if (blockJabOnly && isJabOnlyParts(parts)) listOf(CommandPart.Phrase(Phrases.HANDS_UP)) else parts
        return recordJabOnlyStreak(safeParts)
    }

    /**
     * Generates a Distance call and reacts if it's specifically "Close
     * distance"/"Make distance" — see [RangeState]. "Close distance" also
     * rolls [FLOW_TRIGGER_CHANCE] to queue a random tied-together sequence
     * from [flowLibrary] (dropping that flow's own leading "close_distance"
     * step, since it's this very call) — not guaranteed every time, so a
     * closed distance doesn't always turn into the same scripted burst.
     */
    private fun distanceParts(): List<CommandPart> {
        val parts = distanceGenerator.generate()
        when ((parts.firstOrNull() as? CommandPart.Phrase)?.text) {
            Phrases.CLOSE_DISTANCE -> {
                rangeState = RangeState.NEAR
                pendingFlow.clear()
                if (random.nextDouble() < FLOW_TRIGGER_CHANCE) {
                    flowLibrary.randomFlow(random)?.let { flow ->
                        flow.steps
                            .dropWhile { it is FlowStep.Phrase && it.text == Phrases.CLOSE_DISTANCE }
                            .forEach { pendingFlow.addLast(it) }
                    }
                }
            }
            Phrases.MAKE_DISTANCE -> rangeState = RangeState.FAR
        }
        return parts
    }

    private fun attackParts(voiceStyle: VoiceStyle, stage: RampStage): List<CommandPart> {
        // 4 jab-only calls in a row (see recordJabOnlyStreak) blocks the
        // next 3 from being jab-only too, regardless of which path below
        // would otherwise have produced one.
        val blockJabOnly = jabOnlyCooldownRemaining > 0

        // Range state overrides the normal ramp/difficulty-driven pick —
        // "just closed distance" and "just made distance" are stronger
        // signals than where we are in the warm-up/round ramp.
        if (rangeState == RangeState.NEAR) {
            comboLibrary.randomPowerCombo(random)?.let { return recordAttackParts(it.parts) }
        }
        if (rangeState == RangeState.FAR) {
            return recordAttackParts(farRangeAttack(blockJabOnly))
        }

        // Small chance the whole call is just one punch — and a lone punch
        // is always the jab. Higher during early rounds; nudged further by
        // the current work/ease interval (see Intensity).
        val singleJabChance = (stage.singleJabChance + intensity.singleJabDelta).coerceIn(0.0, 1.0)
        if (!blockJabOnly && random.nextDouble() < singleJabChance) {
            return recordAttackParts(listOf(CommandPart.Punch(1)))
        }

        // Word mode: named punches read out fine in a short combo, but
        // stacking several long words is a mouthful — cap length. Whichever
        // cap (voice style, ramp stage, or the difficulty's own range) is
        // smallest wins. Intensity nudges the ramp-stage cap up or down
        // (HIGH: longer combos, LOW: shorter) before that minOf.
        val wordModeCap = if (voiceStyle == VoiceStyle.WORDS) WORD_MODE_MAX_LENGTH else Int.MAX_VALUE
        val intensityStageCap = (stage.maxComboLength + intensity.comboLengthBonus).coerceAtLeast(1)
        val maxLength = minOf(profile.comboLengthRange.last, wordModeCap, intensityStageCap)
        var minLength = minOf(profile.comboLengthRange.first, maxLength)
        // On cooldown, never let the length roll land on a lone punch —
        // bump both ends up to 2 if needed so nextInt(...) stays valid.
        var effectiveMaxLength = maxLength
        if (blockJabOnly && minLength < 2) {
            minLength = 2
            effectiveMaxLength = maxOf(effectiveMaxLength, minLength)
        }
        val length = random.nextInt(minLength, effectiveMaxLength + 1)
        if (length <= 1) return recordAttackParts(listOf(CommandPart.Punch(1)))

        // A punch in {2,3,4,5,6} that's already appeared its per-punch limit
        // of times in a row can't lead the next combo either (see
        // blockedLeadingPunchOrNull — cross's limit is 1, no immediate
        // repeat at all; others allow 2). Jab isn't included here; it has
        // its own, separate jab-only-streak system. "Cross, jab" (2-1) is
        // banned outright, not just demoted — a real, repeated complaint.
        val blockedLeadingPunch = blockedLeadingPunchOrNull()
        val candidates = comboLibrary.combosOfLength(length).let { list ->
            var filtered = list.filterNot { isCrossJab(it) }
            filtered = if (blockJabOnly) filtered.filterNot { isJabJab(it) } else filtered
            if (blockedLeadingPunch != null) {
                filtered = filtered.filterNot { (it.parts.firstOrNull() as? CommandPart.Punch)?.number == blockedLeadingPunch }
            }
            filtered
        }
        val picked = if (candidates.isEmpty()) null else pickWeighted(candidates, favorSimpleWords = voiceStyle == VoiceStyle.WORDS)
        // Curated combos are preferred; the heuristic only fills in
        // lengths/moments the library doesn't cover.
        val parts = picked?.parts ?: attackGenerator.generateCombo(length).map { CommandPart.Punch(it) }
        // Safety net: the heuristic fallback above doesn't itself know about
        // the cooldown — swap to a guaranteed-safe jab-cross rather than let
        // it slip. (The cross-jab ban doesn't need a check here too —
        // recordAttackParts() re-applies it universally regardless of source.)
        val safeParts = if (blockJabOnly && isJabOnlyParts(parts)) listOf(CommandPart.Punch(1), CommandPart.Punch(2)) else parts
        return recordAttackParts(safeParts)
    }

    /** Mostly solo jab, sometimes jab-jab, rarely a lone cross — the bias while [RangeState.FAR]. Forced to solo cross (its only non-jab-only option) during a jab-only cooldown. */
    private fun farRangeAttack(blockJabOnly: Boolean): List<CommandPart> {
        if (blockJabOnly) return listOf(CommandPart.Punch(2))
        val roll = random.nextDouble()
        return when {
            roll < 0.50 -> listOf(CommandPart.Punch(1))
            roll < 0.85 -> listOf(CommandPart.Punch(1), CommandPart.Punch(1))
            else -> listOf(CommandPart.Punch(2))
        }
    }

    private fun isJabOnlyParts(parts: List<CommandPart>) =
        parts == SINGLE_JAB_PARTS || parts == JAB_JAB_PARTS

    private fun isCrossJabParts(parts: List<CommandPart>) = parts == CROSS_JAB_PARTS

    /**
     * Updates the consecutive-jab-only streak with this Attack call's
     * final [parts], arming a [JAB_ONLY_COOLDOWN_LENGTH]-call cooldown
     * once [JAB_ONLY_STREAK_LIMIT] jab-only calls land in a row — see
     * [attackParts]/[farRangeAttack] for where that cooldown is enforced.
     * Always returns [parts] unchanged; call this at every Attack return
     * point so the streak counter sees every call, not just some paths.
     */
    private fun recordJabOnlyStreak(parts: List<CommandPart>): List<CommandPart> {
        if (jabOnlyCooldownRemaining > 0) jabOnlyCooldownRemaining--
        if (isJabOnlyParts(parts)) {
            consecutiveJabOnlyCount++
            if (consecutiveJabOnlyCount >= JAB_ONLY_STREAK_LIMIT) {
                jabOnlyCooldownRemaining = JAB_ONLY_COOLDOWN_LENGTH
                consecutiveJabOnlyCount = 0
            }
        } else {
            consecutiveJabOnlyCount = 0
        }
        return parts
    }

    /** How many times in a row [number] may repeat before it can't lead/continue a combo — cross gets no immediate repeat at all, everything else in [NON_JAB_PUNCHES] allows 2. */
    private fun repeatLimitFor(number: Int) = if (number == CROSS) 1 else 2

    /** [lastPunchNumber] if it's already at its own [repeatLimitFor] streak (so one more would be illegal), else null. */
    private fun blockedLeadingPunchOrNull(): Int? {
        val last = lastPunchNumber ?: return null
        return if (last in NON_JAB_PUNCHES && consecutiveSamePunchCount >= repeatLimitFor(last)) last else null
    }

    /**
     * Caps a non-jab punch (cross, either hook, either uppercut) at its own
     * [repeatLimitFor] consecutive repeats — cross can't repeat at all,
     * others allow 2, either way the next one over is swapped for a solo
     * jab, the one punch that's never blocked. Checks both the boundary
     * with the previous Attack call's last punch and any run fully inside
     * [parts] itself (curated/flow combos are hand-written, so don't trust
     * them blindly). Also swaps a banned "cross, jab" (2-1) whole-combo —
     * [attackParts]'s own candidate list already filters it out, but flow
     * steps (power/literal combos in `buildFlowStep`) call straight into
     * this function and skip that filtering, so the ban is re-applied here
     * too. Call this on every Attack-producing return point so both rules
     * hold regardless of source, the same principle as [recordJabOnlyStreak].
     */
    private fun recordAttackParts(parts: List<CommandPart>): List<CommandPart> {
        if (isCrossJabParts(parts)) return recordAttackParts(listOf(CommandPart.Punch(1), CommandPart.Punch(2)))
        val fixed = parts.toMutableList()
        val blockedLeading = blockedLeadingPunchOrNull()
        if (blockedLeading != null && (fixed.firstOrNull() as? CommandPart.Punch)?.number == blockedLeading) {
            fixed[0] = CommandPart.Punch(1)
        }
        // Cross specifically: no back-to-back repeat anywhere in the combo.
        for (i in 1 until fixed.size) {
            val prev = (fixed[i - 1] as? CommandPart.Punch)?.number
            val curr = (fixed[i] as? CommandPart.Punch)?.number
            if (prev == CROSS && curr == CROSS) {
                fixed[i] = CommandPart.Punch(1)
            }
        }
        // Everything else in NON_JAB_PUNCHES: a 3rd in a row is illegal.
        for (i in 2 until fixed.size) {
            val a = (fixed[i - 2] as? CommandPart.Punch)?.number
            val b = (fixed[i - 1] as? CommandPart.Punch)?.number
            val c = (fixed[i] as? CommandPart.Punch)?.number
            if (a != null && a == b && b == c && a in NON_JAB_PUNCHES) {
                fixed[i] = CommandPart.Punch(1)
            }
        }
        for (part in fixed) {
            if (part is CommandPart.Punch) {
                if (part.number == lastPunchNumber) consecutiveSamePunchCount++ else {
                    lastPunchNumber = part.number
                    consecutiveSamePunchCount = 1
                }
            }
        }
        return recordJabOnlyStreak(fixed)
    }

    /**
     * Weights combos away from "jab, jab" — real testing found it and solo
     * jab together too frequent; see [JAB_JAB_WEIGHT] and
     * `RampStage.singleJabChance` for the matching solo-jab reduction —
     * and, in Word mode only, away from ones containing an uppercut (5/6)
     * since stacking long words is a mouthful. "Cross, jab" (2-1) isn't
     * weighted here at all — it's filtered out of [candidates] entirely
     * before this is ever called (see [attackParts]), a real complaint that
     * kept showing up even at a steep demotion.
     */
    private fun pickWeighted(candidates: List<LibraryCombo>, favorSimpleWords: Boolean): LibraryCombo {
        val weights = candidates.map { combo ->
            var weight = 1.0
            if (isJabJab(combo)) weight *= JAB_JAB_WEIGHT
            if (favorSimpleWords && hasUppercut(combo)) weight *= UPPERCUT_WEIGHT
            combo to weight
        }
        val total = weights.sumOf { it.second }
        val roll = random.nextDouble() * total
        var cumulative = 0.0
        for ((combo, weight) in weights) {
            cumulative += weight
            if (roll <= cumulative) return combo
        }
        return weights.last().first
    }

    private fun isCrossJab(combo: LibraryCombo) = combo.parts == listOf(CommandPart.Punch(2), CommandPart.Punch(1))

    private fun isJabJab(combo: LibraryCombo) = combo.parts == listOf(CommandPart.Punch(1), CommandPart.Punch(1))

    private fun hasUppercut(combo: LibraryCombo) =
        combo.parts.any { it is CommandPart.Punch && (it.number == 5 || it.number == 6) }

    /**
     * First beat announces once; afterward, up to [freestyleReminderTarget]
     * reminders (1 or 2, rolled once per window — see the activation check
     * in [nextCommand]) on a slow cadence, mostly silence otherwise. A
     * fixed target instead of an independent per-beat roll keeps a window
     * reliably sparse — the old approach could land anywhere from zero to
     * several reminders by chance.
     */
    private fun freestyleCommand(): CoachCommand? {
        if (!freestyleAnnounced) {
            freestyleAnnounced = true
            return CoachCommand(CoachSection.FREESTYLE, listOf(CommandPart.Phrase(Phrases.FREESTYLE_START)))
        }
        if (freestyleReminderCount >= freestyleReminderTarget) return null
        if (random.nextDouble() >= FREESTYLE_REMINDER_CHANCE) return null
        freestyleReminderCount++
        val phrase = FREESTYLE_REMINDERS.random(random)
        return CoachCommand(CoachSection.FREESTYLE, listOf(CommandPart.Phrase(phrase)))
    }

    /**
     * How long to wait (ms) after speaking [command] (or a silent beat, if
     * null) before the next one. A flow-sourced [command] (`speechRate` >
     * 1.0 — see [buildFlowStep]) also shortens the rest portion by
     * [FLOW_REST_MULTIPLIER], on top of already being spoken faster, so a
     * flow's beats land closer together, not just quicker to say.
     */
    fun delayMillisFor(command: CoachCommand?): Long {
        val delayMs = if (freestyleActive) {
            (FREESTYLE_BEAT_SECONDS * 1000).toLong()
        } else {
            val stage = rampStage()
            val throwSeconds = (command?.parts?.size ?: 0) * profile.secondsPerPunch
            val flowMultiplier = if ((command?.speechRate ?: 1.0f) > 1.0f) FLOW_REST_MULTIPLIER else 1.0
            // Intensity's pace multiplier stacks with the flow one — a flow
            // firing during a HIGH interval reads as even more urgent, a
            // LOW interval still slows a flow down some, just not as much.
            val restSeconds = profile.baseRestBetweenCommandsSeconds * flowMultiplier * intensity.paceMultiplier
            ((throwSeconds + restSeconds + stage.extraRestSeconds) * 1000).toLong()
        }
        elapsedInRoundMs += delayMs
        Log.d(TAG, "delayMillisFor: delayMs=$delayMs newElapsedMs=$elapsedInRoundMs")
        return delayMs
    }

    /** One step of [WARMUP_CURRICULUM] — its own [CoachSection] so training-history stats categorize it correctly even though it's spoken pre-round. */
    private data class WarmupStep(val section: CoachSection, val parts: List<CommandPart>)

    companion object {
        private const val WORD_MODE_MAX_LENGTH = 3
        private const val CROSS = 2

        private const val JAB_JAB_WEIGHT = 0.55
        private const val UPPERCUT_WEIGHT = 0.3

        private val SINGLE_JAB_PARTS = listOf(CommandPart.Punch(1))
        private val JAB_JAB_PARTS = listOf(CommandPart.Punch(1), CommandPart.Punch(1))
        private val CROSS_JAB_PARTS = listOf(CommandPart.Punch(2), CommandPart.Punch(1))
        private const val JAB_ONLY_STREAK_LIMIT = 4
        private const val JAB_ONLY_COOLDOWN_LENGTH = 3

        // Cross, either hook, either uppercut — repeat limit for each is
        // repeatLimitFor() (cross: 1, i.e. none at all; others: 2). Jab is
        // deliberately excluded, it has its own jab-only-streak system.
        private val NON_JAB_PUNCHES = setOf(2, 3, 4, 5, 6)

        // "Close distance" rolls this chance to queue a random FlowLibrary
        // sequence instead of always doing so — see distanceParts().
        private const val FLOW_TRIGGER_CHANCE = 0.7
        // TTS rate + rest-between-commands multiplier while a flow's own
        // beats play — see buildFlowStep()/delayMillisFor(). Reverts to
        // normal automatically once pendingFlow drains, since only
        // flow-sourced CoachCommands ever carry this rate. Pushed further
        // (was 1.1/0.75) per real-device feedback that a flow should read
        // as one continuous attack sequence, not separately-paced calls.
        private const val FLOW_SPEECH_RATE = 1.2f
        private const val FLOW_REST_MULTIPLIER = 0.45

        // First ~45s of round 1 uses ROUND_ONE_EARLY; the rest of round 1
        // eases into ROUND_ONE_LATE (== ROUND_TWO's numbers) rather than
        // staying flat for the whole round.
        private const val ROUND_ONE_EARLY_WINDOW_MS = 45_000L

        // Jab -> jab-cross -> a tactical call -> two 3-punch combos -> a
        // second tactical call -> repeat/extend. See nextWarmupCommand().
        private val WARMUP_CURRICULUM: List<WarmupStep> = listOf(
            WarmupStep(CoachSection.ATTACK, listOf(CommandPart.Punch(1))),
            WarmupStep(CoachSection.ATTACK, listOf(CommandPart.Punch(1), CommandPart.Punch(2))),
            WarmupStep(CoachSection.DISTANCE, listOf(CommandPart.Phrase(Phrases.MOVE_MORE))),
            WarmupStep(CoachSection.ATTACK, listOf(CommandPart.Punch(1), CommandPart.Punch(2), CommandPart.Punch(3))),
            WarmupStep(CoachSection.ATTACK, listOf(CommandPart.Punch(1), CommandPart.Punch(1), CommandPart.Punch(4))),
            WarmupStep(CoachSection.DEFENSE, listOf(CommandPart.Phrase(Phrases.HANDS_UP))),
            WarmupStep(CoachSection.ATTACK, listOf(CommandPart.Punch(1), CommandPart.Punch(2))),
            WarmupStep(CoachSection.ATTACK, listOf(CommandPart.Punch(2), CommandPart.Punch(3))),
        )

        // Raised from 0.4 per real-device feedback wanting more freestyle
        // overall.
        private const val FREESTYLE_CHANCE = 0.6
        private val FREESTYLE_DURATIONS_SECONDS = listOf(20, 30, 60)
        private const val MIN_ROUND_BUFFER_MS = 20_000L
        private const val FREESTYLE_BEAT_SECONDS = 13.0
        private const val FREESTYLE_REMINDER_CHANCE = 0.3
        // Which reminder fires when the per-beat roll (above) hits and the
        // window's target (freestyleReminderTarget) hasn't been reached yet.
        private val FREESTYLE_REMINDERS = listOf(Phrases.MOVE_MORE, Phrases.HANDS_UP, Phrases.PICK_UP_SPEED, Phrases.GO_POWER)
    }
}
