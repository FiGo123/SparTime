# BoomBee Boxing Coach — Tactics Reference

This is the tunable spec for Coach Mode's combo/tactics engine — every
number and rule here maps to one specific spot in `:core`'s
`com.example.boombee.coach` package. Change a value in this doc, then make
the matching code edit; the file/constant is named next to each rule so you
don't have to hunt for it.

Companion docs: [BOOMBEE_APP_LOGIC.md](BOOMBEE_APP_LOGIC.md) (the base
timer app), [WEAR_OS_IMPLEMENTATION.md](WEAR_OS_IMPLEMENTATION.md) (the
watch app + background-survival architecture Coach Mode runs on top of).

---

## Punch numbering (standard 1-6)

| # | Punch | Word form |
|---|-------|-----------|
| 1 | Jab (left straight) | "Jab" |
| 2 | Cross (right straight) | "Cross" |
| 3 | Left hook | "Left hook" |
| 4 | Right hook | "Right hook" |
| 5 | Left uppercut | "Left uppercut" |
| 6 | Right uppercut | "Right uppercut" |

Left/right assumes **orthodox stance**. A southpaw setting to flip this is
a planned future addition — not built yet, so left/right is currently
fixed regardless of the user's actual stance.

**Code**: `PunchNaming.kt` — `wordNames` map. Numbers mode needs no map;
a bare digit string is read out as its number word by TTS automatically.

---

## Voice style: Numbers vs. Words

Two ways the coach calls punches, chosen per-session on the Setup screen
(directly below Difficulty, visible only in Coach Mode):

- **Numbers**: "One", "Two", "Three"...
- **Words**: "Jab", "Cross", "Left hook"...

Both modes speak at normal (1.0×) TTS rate. Words mode was tried at 1.2×
to compensate for longer phrases, but real-device testing found that too
fast to actually catch — reverted to 1.0× for both. (Word mode instead
compensates via shorter/simpler combos — see below — rather than talking
faster.)

**Code**: `VoiceStyle` enum (`VoiceStyle.kt`). Rate constant: wear —
`RoundTimerService.kt`'s `WORD_MODE_SPEECH_RATE`; phone — `Second.kt`'s
coach `Runnable` (`tts.setSpeechRate(1.0f)`). Retune both if revisiting.

**v2.9.2**: default flipped to **Words** (was Numbers) — `MainViewModel`'s
`_voiceStyle` initial value, `SetupScreen`'s `voiceStyle` initial
`mutableStateOf`, and `fragment_first.xml`'s `voice_words_btn` now carries
`android:checked="true"` instead of `voice_numbers_btn`. Every `?:
VoiceStyle.NUMBERS` null-safety fallback (there's no reason these should
ever actually be null, but for consistency) updated to `?: VoiceStyle.WORDS`
too.

---

## Your own recorded voice, instead of TTS

**v2.9.0.** Everything above is about *what* the coach says and *how it's
worded* — this is about *whose voice* says it. Every spoken unit
(a punch in either voice style, every tactical phrase, the freestyle/
warm-up lines) can have a real pre-recorded audio clip instead of being
synthesized by TTS. This is opt-in per clip, not all-or-nothing: whatever
clips exist get used, anything missing falls back to TTS exactly as
before, so a partially-recorded voice never breaks playback.

**How it fits together**:
- `CoachAudio.keysFor(parts, voiceStyle)` maps a command's parts to the
  asset keys they'd need (`number_1`..`number_6`, `word_1`..`word_6`,
  `phrase_hands_up`, etc. — see `VOICE_RECORDING_SCRIPT.md` for the full
  key list and the recording workflow). Returns null the moment *any*
  single part has no known key — a command is either fully your voice or
  fully TTS, never a mix of the two mid-phrase.
- `ClipPlayer` (shared in `:core`, same split as `CoachSession`/
  `RoundAnnouncer`) plays clips from `assets/audio/` back to back via
  `MediaPlayer`. `hasAllClips(keys)` tells a caller whether to actually
  use it; `ClipPlayer` itself has no TTS fallback of its own, callers
  decide that.
- Each platform's speak call site checks `hasAllClips` first: phone's
  `Second.kt.speak()`, wear's `RoundAnnouncer.announceCommand()`. Both
  fall back to the exact same `CoachCommand.spokenText`/TTS path used
  before this feature existed.
- `stopSpeaking()` (both platforms — the audio-collision fix below) stops
  `ClipPlayer` alongside TTS, so a bell/warning tone correctly interrupts
  a playing clip sequence the same way it already interrupted TTS.

### Combos play a little faster than a solo call, and slightly overlap

Stitching separately-recorded words together at a flat pace read as
slower/choppier than a real fluid combo callout (or than TTS saying the
same combo as one continuous utterance). `ClipPlayer.playCommand(keys,
isCombo)` — `isCombo` = the command has 2+ parts — speeds up **1.3x**
(via `MediaPlayer.PlaybackParams`, pitch locked to normal so it doesn't
sound chipmunked). Speed history: 1.15x → 1.25x → 1.5x (each round of
real-device testing still finding it too slow) → **back down to 1.3x**
once the gap trick below existed as an alternative way to tighten timing
— pushing `speed` much past 1.5x risks audible time-stretch artifacts
(a warbly/robotic quality), which a gap change doesn't risk at all.

Gap history: 100ms (default, untouched) → 50ms → 20ms → 5ms → 0ms (no
gap) → **-10ms**. A **negative** gap means the next clip starts playing
that many ms *before* the current one's audio actually finishes, a brief
genuine overlap — `ClipPlayer.play()`'s scheduling is a timer computed
from each clip's own (speed-adjusted) duration, not "wait for
`onCompletion`," specifically so this overlap is possible; the two clips
briefly play concurrently rather than one waiting for the other to fully
stop. A solo call (one clip, nothing to overlap with) is unaffected
either way. If the current values still read as slow, prefer pushing the
gap more negative over raising `COMBO_SPEED` further — same reasoning:
overlap doesn't degrade individual-word intelligibility the way heavy
time-stretching does.

**Code**: `CoachAudio.kt` (`keysFor`), `ClipPlayer.kt` (`hasAllClips`,
`play`, `playCommand`, `COMBO_SPEED`/`COMBO_GAP_MS`). Recording plan and
the full key list: `VOICE_RECORDING_SCRIPT.md` at the repo root. Clips
live in `core/src/main/assets/audio/` — same asset-loading pattern as
`ComboLibrary`/`FlowLibrary`'s `.txt` files, just audio instead of text.

### Round/Rest announcements

**v2.9.2.** The above only ever covered `CoachCommand`-driven content
(punches, tactical phrases) — the Round/Rest phase boundary announcement
was still hardcoded TTS on both platforms even after v2.9.0, since it's
built ad hoc (`"Round $n"`/`"Rest"`), not from a `CoachCommand`. Fixed by
building the clip sequence directly at the call site: `["misc_round",
"number_$n"]` for a round, `["misc_rest"]` for rest.

The recorded `misc_round` clip is just the word "Round" (no "begin!" —
that word was never recorded), so the clip version of the phone's
announcement is shorter than its TTS phrase ("Round $n" instead of
"Round $n, begin!"). Round numbers past 6 (or before any clips exist at
all) fall back to the original full TTS phrase automatically — nothing
special needed, `hasAllClips()` simply doesn't find a `number_7`+ file.

**Code**: `RoundAnnouncer.announcePhrase(text, keys)` (wear — a
`CoachCommand`-free sibling to `announceCommand()`, same has-clips-then-
TTS-fallback shape), called from `RoundTimerService.runPhase()`. Phone:
inlined the same check directly in `Second.kt`'s `playRoundSound()` (no
`Rest.kt` equivalent — its "Rest time. Recover!" wording doesn't match
the recorded "Rest" clip, so it stays TTS-only for now; wear's plain
"Rest" phrasing already matched, so wear got both Round *and* Rest wired,
phone only Round).

### A real cutting mistake and how it got caught

**v2.9.2.** A second recording pass (to fix "Slip"/"Duck under"/the
freestyle line/"Warm up starting now"/"Round"/"Rest"/"Workout complete")
got mis-cut: the trim point meant to remove leading dead air landed
*inside* the word "Slip", losing it, and every phrase after it shifted
into the wrong file — most visibly, `phrase_warmup_start.wav` actually
contained the word "Round". Duration-based guessing (comparing a clip's
length to how long its expected phrase "should" take) produced multiple
*wrong, confident-sounding* hypotheses before this was caught.

**Fix, and the general lesson**: stop guessing from duration once it's
produced more than one hypothesis — install a real transcription tool
(`brew install whisper-cpp` + a `ggml-base.en.bin` model, both free) and
actually transcribe each candidate clip. That immediately revealed the
true boundaries (including that "Now go freestyle, max power and speed"
and "Workout complete, well done" each contain a mid-sentence comma pause
long enough to look like two separate phrases to a silence detector —
they should be kept as one clip each, gap and all, not split). Re-verify
every finished clip the same way as a final check before calling a
re-cut done. Duration is a fine *sanity check* (a 3-second clip for a
2-word phrase is obviously wrong) but is not sufficient *evidence* on its
own for what a clip actually contains.

---

## Attack — punch combos

### Hand alternation (consecutive punches mostly switch hands)

Punches have a "hand": **left = 1 (jab), 3 (left hook), 5 (left
uppercut)**; **right = 2 (cross), 4 (right hook), 6 (right uppercut)**.
Consecutive punches in a combo mostly alternate hands — that's what makes
`jab→cross`, `cross→left hook`, `jab→right hook` the dominant shapes
instead of noise. Two deliberate exceptions, both real boxing patterns:

- **After a jab**: 15% chance of a second jab ("jab, jab" — a real,
  common opener), 85% chance it proceeds to the normal pick below.
- **Every other transition**: 95% picks from the **opposite hand**
  (the norm), 5% stays on the **same hand** with a different punch (e.g.
  "jab, left hook") — exact repeats are still excluded from this branch,
  so this can never produce a same-hand *repeat* outside the jab-jab case
  above.

This replaces the old flat "any punch, any punch, just not an exact
repeat" rule — the exact-repeat-except-jab behavior still holds, it's now
just a consequence of the hand logic rather than a separate check.

**"Most useful" combos aren't a hard-coded template list.** `jab-cross`,
`jab-jab-cross`, `jab-right hook`, `jab-jab-right hook`, `cross-left
hook` are all hand-alternating (or jab-repeat-then-alternate) shapes —
exactly what this logic is built to favor. If real sessions aren't
producing combos like these often enough, retune `JAB_REPEAT_CHANCE`/
`SAME_HAND_CHANCE`/`PUNCH_WEIGHTS` rather than bolting a template list on
top.

**Code**: `AttackGenerator.kt` — `JAB_REPEAT_CHANCE` (0.15),
`SAME_HAND_CHANCE` (0.05), `HAND`/`LEFT_HAND`/`RIGHT_HAND` (the hand map).

### Round openers are always Attack

The first command of every round (not Rest — Coach doesn't run there) is
forced to Attack, regardless of what the transition table would have
picked and regardless of what section the previous round ended on. After
that one forced pick, normal section flow resumes.

**Code**: `CoachSession.startRound()` / `SectionPicker.forceNext()`.
Callers: wear's `RoundTimerService.runCoachLoop()` calls it once per
round (that coroutine is itself relaunched exactly once per round, so
there's nothing extra to gate). Phone's `Second.kt` needs an explicit
`isRoundStart` flag on `startCoachLoopIfNeeded()` since that function is
also called on resume-from-pause, which must *not* re-force Attack —
only the two genuine round-start call sites pass `isRoundStart = true`.

### Curated combo library (primary source)

Attack combos are now picked from **real, human-curated combos** first —
`core/src/main/assets/coach_combos_general.txt` — not generated from
scratch by the hand-alternation heuristic below. The heuristic only fills
in a length the file has no combo for (currently: nothing above 4
punches). This is the actual "training data" for the coach: add a line,
the app picks from it, no code changes needed.

**File format**: one combo per line, hyphen-separated (`1-2-slip-6-3`),
`1`-`6` = punch numbers, `slip`/`duck` = a defensive move spoken *inside*
the combo (not a Defense-section call — see the file's own header
comment for the full format, including why some combos deliberately
interleave a move: a same-hand-twice punch transition like `2` then `6`
reads as awkward on its own, but `slip`/`duck` in between resets your
position and makes it flow).

**Code**: `ComboLibrary.kt` (loader/parser), `coach_combos_general.txt`
(the data). "School" support (Russian/Mexican/American/Cuban) is just
more files later — pass their names to `ComboLibrary`'s `schools` param
once they exist.

### Small chance of a solo jab

Independent of combo length, there's an **8% chance** the whole Attack
call is just one punch — and a lone punch is always the jab (a solo
hook/uppercut isn't a realistic standalone call the way a solo jab is).
This also governs the rare case where Beginner's normal length range
(1–2) itself rolls a 1.

**Code**: `CoachSession.attackParts()` — `SINGLE_JAB_CHANCE` (0.08).

### Word mode: simpler, shorter

When Words voice is selected, Attack combos are generated differently
than Numbers mode:

- **Combo length capped at 3** punches, regardless of difficulty (Numbers
  mode keeps the full difficulty range — Advanced can still reach 5).
- **Combos containing an uppercut (5/6) are picked less often** — not
  banned, just demoted (30% relative weight vs. 100% for combos without
  one) — since stacking multiple long words ("jab, left hook, right
  uppercut") is a mouthful, but an occasional uppercut in a short combo
  reads fine.

**Code**: `CoachSession.attackParts()` — `WORD_MODE_MAX_LENGTH` (3),
`pickFavoringSimpleWords()` (the uppercut demotion, 0.3 weight).

### Punch frequency weighting (heuristic fallback only)

When the curated library has nothing at the needed length, the
`AttackGenerator` heuristic fills in — it doesn't pick punches uniformly,
jab dominates, matching real usage:

| Rank | Punch | # | Weight |
|---|---|---|---|
| 1 | Jab | 1 | 35% |
| 2 | Cross | 2 | 25% |
| 3 | Right hook | 4 | 15% |
| 4 | Left hook | 3 | 12% |
| 5 | Right uppercut | 6 | 8% |
| 6 | Left uppercut | 5 | 5% |

**Code**: `AttackGenerator.kt` — `PUNCH_WEIGHTS` list. Weights don't need
to sum to 100 (they're normalized against their own total), so you can
tweak any single value without rebalancing the rest by hand.

### Combo length (by difficulty)

| Difficulty | Length range |
|---|---|
| Beginner | 1–3 |
| Intermediate | 2–4 |
| Advanced | 3–6 |

**v2.7.0**: every ceiling raised by one, and `baseRestBetweenCommandsSeconds`
trimmed ~10-15% at each level too (Beginner 2.0s→1.8s, Intermediate
1.5s→1.3s, Advanced 1.0s→0.85s) — real-device feedback wanted difficulty
raised a bit "for all levels," not just one. Relative ordering between
the three is unchanged (Beginner stays easiest/slowest).

**Code**: `DifficultyProfile.kt` — `comboLengthRange`/
`baseRestBetweenCommandsSeconds` per `profileFor()` branch.

---

## Defense — not just "Hands up"

Defense beats are built from a small vocabulary, the same way Attack
combos are built from punches — not always the single word "Hands up".

**Vocabulary**: `Hands up`, `Move more`, and a punch (**jab or cross
only** — no hooks/uppercuts while defending).

**Beat length**: 90% of Defense beats are a single call (e.g. just "Hands
up"); 10% are a short 2-part combo (e.g. "Move more, hands up").

**Action mix** (which of the three vocabulary items gets picked) — *not
specified by name yet, first-draft weights*:

| Action | Weight |
|---|---|
| Hands up | 50% |
| Move more | 25% |
| Punch (jab/cross) | 25% |

**When the action is a punch**: 90% jab / 10% cross — deliberately
different from Attack's full 6-punch weighting, matching real "stick and
move" defensive punching (never a hook/uppercut here).

**Constraint**: no repeated phrase back to back — not "Move more, Move
more" within one 2-part combo, and not the *previous* beat's phrase
repeating as this beat's first pick either (e.g. two independent Defense
beats both landing on "Hands up" in a row). Both cases reads as a no-op
or a stutter, so the offending pick is re-rolled (bounded retries).
Punches may repeat (double jab is fine). v2.6.0: generalized from a
Move-more-only check (real-device testing surfaced consecutive "Hands
up, Hands up" across two separate beats, which the old check didn't
cover since it only compared within one combo and only for Move more).

**Code**: `DefenseGenerator.kt` — `SINGLE_ACTION_CHANCE` (beat length),
`ACTION_WEIGHTS` (action mix), `defensePunchNumber()` (jab/cross split),
`lastSpoken` + `isRepeatedPhrase()` (the no-repeated-phrase retry, checked
both within a beat and against the previous beat's last phrase).

---

## Distance — plain footwork

Always a single call, weighted (not even) between `Make distance`,
`Close distance`, `Move more`. (`Move more` is shared with Defense — same
phrase, different tactical context: standalone footwork here, part of a
guard combo there.)

| Phrase | Weight |
|---|---|
| Close distance | 45% |
| Make distance | 30% |
| Move more | 25% |

Two real-device demotions, in order: `Move more` was cut from an even
1-in-3 (real feedback: generic movement instructions too frequent), then
`Make distance` was cut too (a separate complaint, after `Move more`'s
fix). A phrase also **can't repeat two Distance beats in a row** — most
visibly reported as "Make distance" twice — via the same
retry-a-bounded-number-of-times pattern `DefenseGenerator` already used
for its own repeated-phrase guard (`lastPhrase`, checked and updated in
`generate()`).

**Code**: `DistanceGenerator.kt` — `WEIGHTS`, `lastPhrase` + the retry
loop in `generate()`.

---

## Range state: "Close distance" and "Make distance" aren't just random calls

Two specific Distance phrases now drive what happens next — a real,
persistent state, not just the per-beat section-transition table below:

- **"Close distance" spoken** (from anywhere — normal section flow or
  another trigger) → range becomes **NEAR** unconditionally, *and* there's
  a 70% chance a multi-beat **flow** gets queued on top of that (see
  "Tactical flows" below) — not guaranteed anymore, so "Attack after
  closing distance" is likely, not forced, though NEAR's own power-combo
  bias (below) still applies every time regardless of whether a flow
  fires.
- **"Make distance" spoken** (whether from a flow or organically) → range
  becomes **FAR**. While FAR, every subsequent Attack
  call (until the next Close/Make Distance changes range again) is
  jab-biased: 50% solo jab, 35% jab-jab, 15% solo cross — not the normal
  weighted/library pick.
- **While NEAR**, every Attack call pulls from combos tagged `| power` in
  `coach_combos_general.txt` (2-3 punches, rear-hand/hook-heavy —
  `1-4-3`, `2-3-6`, `6-3-2`, `5-4-3`, `2-3`, `3-6`, `4-5` are tagged so
  far) instead of the normal pick.

Both NEAR and FAR are **persistent**, not one-shot — they keep biasing
Attack calls until the opposite Distance phrase is spoken, matching "stay
aggressive at close range" / "stay at range until you close back in"
rather than reverting after a single combo.

**Code**: `CoachSession.kt` — `RangeState` (private enum), `distanceParts()`
(detects Close/Make Distance, sets range, and rolls the flow trigger — see
"Tactical flows" below), `attackParts()`'s range checks at the top,
`farRangeAttack()` (the jab-biased picker). Power tagging: `| power`
suffix in `coach_combos_general.txt`, read via
`ComboLibrary.randomPowerCombo()`/`powerCombosOfLength()`.

---

## Tactical flows: tied-together sequences, faster pacing, not always the same

Beyond the range-state biasing above, a "Close distance" call can trigger a
**flow** — a short, ordered chain of beats meant to read as one connected
tactical moment ("just closed the distance, throw power, get back out,
counter, reset the guard") instead of independently-picked calls that
happen to follow each other. Not guaranteed every time, and not always the
same shape:

- **Trigger**: 70% chance, rolled every time "Close distance" is spoken
  (`FLOW_TRIGGER_CHANCE`). On a miss, the old always-on behavior still
  applies underneath — range still goes NEAR, so Attack still favors power
  combos — there just isn't a forced multi-beat chain.
- **Which flow**: picked at random from `coach_flows_general.txt` — a
  human-editable file, one flow per line, steps separated by `>`. Step
  vocabulary: `close_distance`/`make_distance`/`move_more`/`hands_up` (a
  literal phrase), `power` (a random `| power`-tagged combo, re-rolled
  fresh each time), `attack` (a normal ramp/library-driven pick), or a
  literal combo (`2-1`, `1-2-3`) spoken exactly as written every time. Add
  a line, the app can pick it — no code changes needed, same pattern as
  `coach_combos_general.txt`. All flows currently open on `close_distance`
  since that's the only wired-up trigger; other entry points are a natural
  future extension.
- **Pacing while a flow plays**: every beat sourced from the flow is
  spoken at **1.1×** TTS rate with **25% less rest** between it and the
  next call (`FLOW_SPEECH_RATE`, `FLOW_REST_MULTIPLIER`) — "smaller delay
  between commands," not just faster speech. This reverts to normal
  (1.0×, normal rest) automatically the moment the flow's queue drains,
  with no separate reset step: only a flow-sourced `CoachCommand` ever
  carries a `speechRate` above 1.0 in the first place.

**Code**: `FlowLibrary.kt` (loader/parser), `coach_flows_general.txt` (the
data), `CoachCommand.speechRate` (the per-call rate, default 1.0),
`CoachSession.kt` — `pendingFlow` (the queue), `distanceParts()` (rolls the
trigger and enqueues a flow's steps, dropping its own leading
`close_distance` step since that's the call that just fired), `buildFlowStep()`
(turns one `FlowStep` into a real `CoachCommand`), `delayMillisFor()` (the
rest-shortening check on `command.speechRate`).

---

## Consecutive same-punch cap: cross never repeats, hooks/uppercuts max out at 2 in a row

A punch in **{cross, left hook, right hook, left uppercut, right
uppercut}** (2/3/4/5/6) has a repeat limit — `CoachSession.repeatLimitFor()`
— checked at the **boundary between Attack calls** and **inside a single
combo**. Jab itself is deliberately excluded from this rule; it already
has its own, separate jab-only-streak system (see below), which is more
lenient by design (jab-jab is a real, common pattern).

- **Cross (2): limit 1** — no back-to-back repeat at all, not even once.
  (**v2.7.0**, tightened from "2 allowed" — real-device feedback found even
  two crosses in a row read as wrong.)
- **Everything else (3/4/5/6): limit 2** — a 3rd identical punch in a row
  is swapped for a solo jab instead.

The candidate-filtering happens before picking where possible (a combo
that would create the violation is excluded from the pool up front); a
safety net swaps the offending punch for a solo jab if a violation still
makes it through (e.g. the heuristic fallback, or a flow's literal/power
combo).

**Code**: `CoachSession.kt` — `lastPunchNumber`/`consecutiveSamePunchCount`
(the tracker, reset in `startRound()`), `NON_JAB_PUNCHES` (the 2/3/4/5/6
set), `repeatLimitFor()` (per-punch limit — 1 for cross, 2 otherwise),
`blockedLeadingPunchOrNull()`, `recordAttackParts()` (the combined
cap-enforcement + streak-update wrapper — call this, not
`recordJabOnlyStreak()` directly, at every Attack-producing return point,
including flow steps that bypass `attackParts()` in `buildFlowStep()`).

---

## Cross-jab (2-1): banned outright. Jab-jab (1-1): demoted, not removed

**v2.7.0**: `2-1` ("cross, jab") is **banned outright**, not just
demoted — a real complaint that kept showing up even at a steep (25% of
normal weight) demotion. It's filtered out of the candidate pool entirely
in `attackParts()`, *and* re-checked as a universal safety net inside
`recordAttackParts()` (so a flow's literal/power-combo steps, which call
straight into `recordAttackParts()` and skip `attackParts()`'s own
filtering, can't slip it through either — swapped to `1-2`, "jab, cross",
if it ever would). `coach_flows_general.txt` must not use a literal `2-1`
step for the same reason — see that file's header comment.

`1-1` ("jab, jab") stays a *demotion*, not a ban — it's a real, common
pattern, just kept from being too frequent:

**v2.6.4**: demoted to **70% of its normal weight** per real-device
feedback that solo jab and jab-jab together were too frequent. Paired
with a matching cut to solo-jab frequency itself: every
`RampStage.singleJabChance` dropped 5 percentage points (`ROUND_ONE_EARLY`
35%→30%, `ROUND_ONE_LATE`/`ROUND_TWO` 15%→10%, `NORMAL` 8%→3%) — that's
the "whole call is just one jab" case, a separate roll from which
2-punch combo gets picked.

**v2.6.5**: still not enough — two more changes:
- `JAB_JAB_WEIGHT` cut again, 70%→65%, and `singleJabChance` cut another
  5 points everywhere (`ROUND_ONE_EARLY` 30%→25%, `ROUND_ONE_LATE`/
  `ROUND_TWO` 10%→5%, `NORMAL` 3%→0% — solo jab as a random pick is now
  switched off entirely at full difficulty).
- A **hard cap**, independent of all the probability tuning above: 4
  jab-only calls (solo jab or jab-jab) in a row now forces the next 3
  Attack calls to not be jab-only, regardless of what the odds say. This
  matters because probability tuning alone can't bound a *streak* — and
  there's a second source of jab-only calls the weighting above doesn't
  touch at all: `farRangeAttack()` (triggered by `RangeState.FAR`, i.e.
  right after a "Make distance" call) is 85% jab-or-jab-jab on its own by
  design, so a few Distance calls landing close together could chain
  several jab-heavy Attack beats even with `JAB_JAB_WEIGHT`/
  `singleJabChance` both low. The streak counter watches every Attack
  return point (ramp-driven pick *and* `farRangeAttack()`) so the cap
  holds no matter which path is producing the streak.

**v2.7.0**: found a *third*, previously-uncapped source of jab-only
calls — `DefenseGenerator`'s own "punch" action defaults to jab 90% of
the time, and that jab never went through the streak tracker at all
(Defense calls bypassed `attackParts()` entirely). Fixed two ways:
`CoachSession.defenseParts()` now wraps `DefenseGenerator.generate()`,
routing its output through the same `recordJabOnlyStreak()` and swapping
to "Hands up" if a cooldown is active and Defense still landed on
jab-only content; and `DefenseGenerator`'s own jab bias was lowered
(90%→70%) and its punch-action weight trimmed (25%, redistributed from
"Move more" — see the Defense section above) as a further reduction at
the source. `JAB_JAB_WEIGHT` cut once more, 65%→55%, and
`ROUND_ONE_EARLY`'s solo-jab chance 25%→20%.

**Code**: `CoachSession.pickWeighted()` (jab-jab demotion only — cross-jab
isn't weighted, it's filtered before this is ever called), `JAB_JAB_WEIGHT`
(0.55), `isJabJab()`/`isCrossJab()`/`isCrossJabParts()`. Solo-jab
frequency: `RampStage.kt`'s `singleJabChance` column. Streak cap:
`CoachSession.recordJabOnlyStreak()` (called from every Attack *and*
Defense return point via `defenseParts()`),
`consecutiveJabOnlyCount`/`jabOnlyCooldownRemaining`,
`JAB_ONLY_STREAK_LIMIT` (4), `JAB_ONLY_COOLDOWN_LENGTH` (3). During a
cooldown: the ramp-driven path forces combo length ≥ 2 and excludes `1-1`
from candidates (with a same-cooldown check on the heuristic fallback's
output as a last-resort safety net), `farRangeAttack()` falls back to its
one non-jab-only option (solo cross), and Defense swaps to "Hands up".

---

## Intensity: unpredictable work/ease intervals within a round

**v2.7.0.** A round with one flat pace/difficulty for its entire length
(beyond the existing early/late ramp — see "Session ramp-up" below) read
as too mechanical in real-device testing — real interval training isn't
uniform. `CoachSession` now layers a second, independent, unpredictable
cycle on top of everything else:

Every 15-30 seconds (re-rolled via `maybeRollIntensity()`, always to a
*different* value than the current one so a change is always
perceptible), the session picks one of:

| Intensity | Pace | Combo length | Solo-jab chance |
|---|---|---|---|
| `LOW` | 1.4x rest (slower) | −1 | +10 points |
| `NORMAL` | unchanged | unchanged | unchanged |
| `HIGH` | 0.7x rest (faster) | +1 | −5 points |

These are *deltas/multipliers on top of* the existing `RampStage` numbers
for wherever the round currently is (early round 1, round 2, etc.), not a
replacement for them — a `HIGH` interval during `ROUND_ONE_EARLY` is
still gentler than a `HIGH` interval at `NORMAL` difficulty, just
relatively more intense than its own baseline. Applies to Attack pacing
and combo length/solo-jab-chance only, not Defense/Distance content, and
not during warm-up or freestyle (both have their own fixed pacing
already). A flow's own faster pacing (`FLOW_REST_MULTIPLIER`) stacks
multiplicatively with intensity's pace multiplier, so a flow firing
during a `HIGH` window reads as more urgent still, and one during a `LOW`
window is slowed down some but not as much as it would without the flow.

**Code**: `CoachSession.Intensity` (the enum + its three fields),
`intensity`/`nextIntensityRollMs` (state, reset in `startRound()`),
`maybeRollIntensity()` (called once per `nextCommand()`, skipped during
freestyle), applied in `attackParts()` (`singleJabChance`/`maxComboLength`
adjustments) and `delayMillisFor()` (`paceMultiplier`).

---

## Freestyle: sometimes a round ends with "figure it out yourself"

From round 2 onward, **40%** chance a round gets a freestyle ending — a
window of the round's last **20, 30, or 60 seconds** (random pick) where
the coach:

1. Announces once: *"Now go freestyle, max power and speed"*.
2. Then goes **mostly silent** — a beat every ~13s, and even then only a
   **30%** chance of saying `Move more` or `Hands up`; the other 70% of
   beats say nothing at all.

No punches are called during freestyle. Skipped if the round is too short
for the chosen window to make sense (needs at least 20s of round left
over on top of the freestyle window itself).

This is the one place `CoachSession.nextCommand()` can return **null** —
a deliberate silent beat, not an error. Both platforms already treat
`null` as "nothing to speak this beat, just wait and check again."

Requires the round timer to tell `CoachSession` the round's total
duration (it didn't need to know this before) — `startRound(roundNumber,
roundDurationSeconds)`, called from wear's `RoundTimerService`
(`config.roundMinutes * 60`) and phone's `Second.kt` (`roundLength *
60`).

**Code**: `CoachSession.kt` — `startRound()`'s freestyle roll,
`freestyleCommand()`, `FREESTYLE_CHANCE` (0.4), `FREESTYLE_DURATIONS_SECONDS`
(20/30/60), `FREESTYLE_BEAT_SECONDS` (13.0), `FREESTYLE_REMINDER_CHANCE`
(0.3), `Phrases.FREESTYLE_START`.

---

## Settings: turning off Tactics or Freestyle entirely

Two persistent toggles (Settings screen, both platforms — not per-session
like Difficulty/Voice/Warm-up, since these are "I never want this"
preferences rather than per-workout choices):

- **Tactics: On/Off** — when off, Defense and Distance never fire at
  all; every beat is Attack (regular difficulty-driven punch calling,
  same as always). The whole range-state/forced-sequence machinery above
  is moot when this is off, since it's only ever triggered by a Distance
  call.
- **Freestyle: On/Off** — when off, `startRound()` never arms a freestyle
  window for that session, full stop.

Both default to **on**. Read live from `Dao` on every relevant decision
(not snapshotted once at session start), same pattern as the existing
`sound_status` check — so a mid-session settings change would take effect
immediately, though there's no UI to change them mid-session today.

**Code**: `Dao.kt` (`KEY_TACTICAL_COMMANDS_ENABLED`,
`KEY_FREESTYLE_ENABLED`, both default `true`), `CoachSession`'s own `Dao`
instance (checked in `nextCommand()`/`startRound()`). UI: wear's
`SettingsScreen.kt`, phone's `fragment_settings.xml` + `Settings.kt`
(`coachOptionsCard`).

---

## Audio collision: bell/warning tones vs. coach speech

Found via real-device testing: a coach TTS call and the round-timer's
bell/warning tone run on **independent audio paths** (the TTS engine vs.
`ToneGenerator`) with zero coordination between them — `QUEUE_FLUSH`
stops one TTS utterance for the next, but does nothing when a *tone*
starts while TTS is still mid-sentence, which is audible as garbled
overlap.

**Fix**: explicitly stop the TTS engine right before every tone plays —
`stopSpeaking()` (wear: `RoundAnnouncer.kt`; phone: `Second.kt`) called at
the top of `playBell()`/`playWarning()` (wear) and
`playBellSound()`/`playWarningBeep()` (phone). Self-contained in the
audio layer — no caller changes needed on either platform.

### The reverse case: speech starting over a still-ringing bell

**v2.6.6.** The fix above only covers *a tone interrupting speech*. Real-
device testing found the opposite collision at every Round↔Rest boundary:
*speech starting while the previous bell tone is still audible*, garbling
the round/rest announcement itself.

- **Watch**: `RoundAnnouncer.playBell()` fires two clangs but is a
  fire-and-forget `ToneGenerator.startTone()` call — the `suspend fun`
  returned as soon as the *second* clang was triggered, not once it
  actually finished playing. `runPhase()`'s very next step after
  `playBell()` is `onPhaseFinished()` → the next `runPhase()` →
  `announce("Round X")`, so the round/rest announcement started speaking
  while the bell's second clang was still ringing.
- **Phone**: same root problem, different mechanism — `Second.kt` and
  `Rest.kt` are separate fragments, each with its **own independent
  `TextToSpeech` instance**. `stopSpeaking()` only stops TTS on the same
  instance, so it can't reach across fragments. `Second.kt`'s bell fires
  an 800ms tone and immediately navigates to `Rest.kt`, which speaks
  "Rest time. Recover!" on a *different* TTS object with no way to know
  the previous fragment's tone is still ringing — and the same thing in
  reverse at Rest→Round.

**Fix**: wait for the bell to actually finish before doing anything that
speaks next.
- Wear: `playBell()` gained a trailing `delay(300)` after the second
  clang's `startTone()` call, so the `suspend fun` doesn't return — and
  `runPhase()` doesn't move on to `announce()` — until the tone has
  genuinely finished.
- Phone: `Second.kt`'s `handleRoundFinish()` and `Rest.kt`'s round-timer
  `onFinish()` now `postDelayed` their navigation by `BELL_DURATION_MS`
  instead of navigating immediately, so the next fragment's TTS never
  starts until the previous fragment's bell has finished. Each pending
  navigation is cancelable (`cancelPendingRestNavigate()` /
  `cancelPendingNavigateToSecond()`) and wired into that fragment's
  stop/destroy paths, matching the existing `pendingRoundStartRunnable`
  pattern.

**v2.7.0 — still audible on watch, at both transitions.** Real-device
testing found the gap above wasn't quite enough — the very next
announcement ("Round X"/"Rest") still read as running into the bell's
tail, at *both* Round→Rest and Rest→Round (every round/rest boundary,
not just one direction). Rather than assume the exact tone duration is
authoritative down to the millisecond on real hardware, both platforms
now pad extra silence on top of the already-correct sequencing:
- Wear: `EXTRA_BELL_GAP_MS` (500ms) — an explicit `delay()` after
  `playBell()`/`vibrateEnd()` and before `onPhaseFinished()` (which is
  what triggers the next phase's `announce()`), in `runPhase()`'s
  round/rest-end branch.
- Phone: `BELL_DURATION_MS` raised 800ms→1300ms (same constant, same
  purpose, just more margin) in both `Second.kt` and `Rest.kt`.

If this still isn't enough, don't reach for a differently-clever
sequencing trick first — raise these two padding constants further. The
underlying sequencing (tone finishes, *then* wait, *then* speak) is
already correct; what's left is purely "how much margin," which is a
number to tune, not a bug to hunt for.

---

## Section flow: Attack / Defense / Distance

Instead of picking a section independently at random each beat, the next
section is biased by the current one, so a session reads like real
tactics instead of noise. This is a Markov chain over
`{ATTACK, DEFENSE, DISTANCE}`.

### Target aggregate mix (long-run, over a full session)

- **Attack ≥ 60%**
- **Defense 5–10%**
- **Distance**: the remainder (~30–35%)

### Current transition table

| From ↓ / To → | Attack | Defense | Distance |
|---|---|---|---|
| **Attack** | 65% | 5% | 30% |
| **Defense** | 45% | 10% | 45% |
| **Distance** | **65%** | **5%** | 30% |

The load-bearing rule from this table: **out of Distance, go to Attack,
not Defense** (65% vs. 5%) — reflected in the Distance row matching the
Attack row.

**This table's solved stationary distribution is Attack ≈ 64%, Defense ≈
5.3%, Distance ≈ 30.8%** — verified analytically to sit inside the target
band above. **If you change any weight in this table, the aggregate mix
changes in a way that is not obvious from the row alone** — re-derive the
stationary distribution (solve `π = πP`) or simulate a long run and count
sections before assuming a new table still hits the 60% / 5-10% targets.

**Code**: `SectionPicker.kt` — `TRANSITIONS` map. Same table is used at
every Difficulty — Difficulty currently only affects Attack combo length
and pacing (below), not the section mix. Making the section mix
difficulty-dependent too is a natural future extension, not built yet.

---

## Pacing

Regardless of section, after speaking a command the coach waits:

```
delay = (parts_in_command × secondsPerPunch + baseRestBetweenCommandsSeconds) seconds
```

| Difficulty | secondsPerPunch | baseRestBetweenCommandsSeconds |
|---|---|---|
| Beginner | 0.6 | 2.0 |
| Intermediate | 0.5 | 1.5 |
| Advanced | 0.45 | 1.0 |

This is the mechanism that keeps higher difficulty from calling combos
faster than a real person can throw them — it raises volume/complexity,
not raw calling speed below a realistic floor.

Beginner's rest was 2.5s originally; real-device testing found that "too
rare" — calls felt too sparse. Nudged to 2.0s: noticeably more frequent
without closing the gap to Intermediate's 1.5s (Beginner should still
throw visibly less than harder difficulties, just not *that* little).
Combo length for Beginner stayed at 1–2 — the "too rare" complaint was
about calling frequency, not combo length, so that's the lever that
moved.

Separately, there's a **2.5s lead-in** before the coach's first call each
round/rest, so "Round X"/"Rest" finishes being spoken before the clock (or
the coach) starts. On wear, this lead-in also delays the *visual*
countdown itself (`RoundTimerService.runPhase()`'s ticking coroutine
`delay(ANNOUNCE_LEAD_MS)`s before its first tick). v2.6.0: the phone side
was missing this for its visual timer — `Second.kt`'s `CountDownTimer`
used to start immediately alongside "Round X, begin!", so the displayed
clock was already several seconds in by the time the announcement
finished (real-device testing: "timer and sound dont look synchronized").
Fixed with `beginRoundWithLeadIn()`, which shows the full round time
immediately, then starts the real `CountDownTimer` after the same
`ANNOUNCE_LEAD_MS` delay the coach loop already used — so both platforms
now hold the display still during the announcement instead of just the
phone's coach calls.

**Code**: `DifficultyProfile.kt` (`secondsPerPunch`,
`baseRestBetweenCommandsSeconds`), `CoachSession.delayMillisFor()` (the
formula). Lead-in: `ANNOUNCE_LEAD_MS` in `RoundTimerService.kt` (wear) and
`Second.kt` (phone) — kept as two separate constants, one per platform.
Phone visual-timer lead-in: `Second.kt`'s `beginRoundWithLeadIn()` /
`cancelPendingRoundStart()`.

---

## Session ramp-up (new sessions don't start at full difficulty)

Real-device testing found that even a length-capped combo at the
selected difficulty felt overwhelming from beat one — both Numbers
(unfamiliar number→punch mapping) and Words (longer phrases) modes. So a
session ramps in, **independent of the selected difficulty**, via
`RampStage`:

| Stage | When | Single-jab chance | Max combo length | Extra rest |
|---|---|---|---|---|
| `WARMUP` | The optional pre-round warm-up phase (below) | — | — | +2.0s |
| `ROUND_ONE_EARLY` | First ~45s of round 1 | 35% | 2 | +1.0s |
| `ROUND_ONE_LATE` | Rest of round 1 (deliberately == `ROUND_TWO`) | 15% | 3 | +0.5s |
| `ROUND_TWO` | All of round 2 | 15% | 3 | +0.5s |
| `NORMAL` | Round 3 onward | 8% | difficulty's own range | +0s |

`WARMUP`'s single-jab-chance/max-length columns are blank because
`nextWarmupCommand()` doesn't read them at all (see below) — only its
`extraRestSeconds` is used, for pacing.

### Warm-up is a separate pre-round phase with a fixed teaching curriculum

**v2.6.2 redesign** (the phase itself), **v2.6.3 tuning** (the content).
Four earlier versions all shipped and all failed real-device testing, in
order:
1. Warm-up as the first N seconds *inside* Round 1's own configured
   time, via a probability shift only. Read as identical to normal
   Round 1 — the shift wasn't audible.
2. Same timing, plus a spoken "Warm-up" announcement at the start.
   Explicitly rejected ("dont want that") — an extra spoken line isn't
   the fix, and a hyphenated phrase also happened to trip a real bug in
   one device's TTS engine (a broken text-normalizer regex silently
   swallowed the utterance).
3. Warm-up moved to its own pre-round phase (the structural part that
   stuck — see below) but made jab-only. Rejected too: "mostly just Jab"
   doesn't teach the user anything about the coach's actual vocabulary.
4. **Current**: still its own pre-round phase, but content is a fixed,
   escalating **teaching curriculum** instead of jab-only or a random
   pool — the point is the user can predict and "catch" what's coming,
   the same way a real corner walks a beginner through combos one piece
   at a time before the bell:
   1. Jab
   2. Jab, Cross
   3. Move more *(a tactical call — so it's not "only punches" either)*
   4. Jab, Cross, Left hook
   5. Jab, Jab, Right hook
   6. Hands up
   7. Jab, Cross *(repeats)*
   8. Cross, Left hook

   If the chosen warm-up length (30s/60s) runs past the list, it loops
   back to step 1 rather than inventing something harder.

**v2.7.0**: a fifth adjustment, but additive rather than another
rewrite — a **title announcement**, `Phrases.WARMUP_START` ("Warm up
starting now"), spoken once, standalone, before step 1 of the curriculum
above. This is *not* the same idea as the rejected version-2 attempt: that
one was inside Round 1's own time and got explicit pushback on the
phrase itself; this one is scoped to the (already-separate, already
well-received) pre-round phase and exists so warm-up has a clear,
meaningful start instead of launching straight into "Jab" with no
framing. No hyphen, learning from version 2's TTS-engine bug.

**Structural design (unchanged since v2.6.2)**: warm-up is a *third
phase*, distinct from Round and Rest, that runs to completion **before
Round 1's timer starts at all**. A 3:00 round with 30s warm-up now takes
3:30 total before Round 1 ends — Round 1 always gets its full configured
length.

Structurally this is *not* `CoachSession.nextCommand()`/`startRound()` —
those remain exactly the "one real round's worth of Attack/Defense/
Distance flow" API they always were. Warm-up gets its own tiny API
instead: `CoachSession.nextWarmupCommand()` (the title announcement once,
gated by `warmupAnnounced`, then steps through `WARMUP_CURRICULUM`,
wrapping at the end — stateful via `warmupStepIndex`, not random) and
`CoachSession.warmupDelayMillis(command)` (same pacing formula as
`delayMillisFor()`, fixed to `RampStage.WARMUP.extraRestSeconds`).
Callers loop these for their own chosen `warmupSeconds` real-world
duration, then call
`startRound(1, roundDurationSeconds)` and enter the normal round flow
exactly as if warm-up had never happened.

**Per platform**: phone's `Second.kt` runs this as a new pre-round state
— `startRoundOneFlow()` → `runWarmupPhase()` (shows "WARM UP" on the
existing round-number/timer views, counts down `warmupSeconds`, drives
its own coach-call loop) → `beginRoundOne()` (the original "Round 1,
begin!" + lead-in + `startCoachLoopIfNeeded` sequence) once warm-up's
countdown reaches zero. Wear's `RoundTimerService` adds a third
`TimerPhase.WARMUP` state alongside `ROUND`/`REST`; `runWarmupPhase()`
mirrors `runPhase()`'s tick-loop shape but counts down `warmupSeconds`
directly (not `config.roundMinutes`) and calls `runPhase(ROUND, 1)` on
completion instead of `onPhaseFinished()`. `TimerScreen` renders
`TimerPhase.WARMUP` as "Warm Up"; the Ongoing Activity notification does
the same.

**Code**: `RampStage.kt` (the table). `CoachSession.WARMUP_CURRICULUM`/
`WarmupStep` (the fixed sequence — each step carries its own
`CoachSection` so training-history stats categorize the tactical steps
correctly even though they're spoken pre-round). `CoachSession
.nextWarmupCommand()` / `warmupDelayMillis()` (the pre-round API). UI:
`warmupSeconds` still comes from the Setup screen's Off/30s/60s toggle
but is no longer passed into the `CoachSession` constructor at all — it's
read directly by `Second.kt`/`RoundTimerService` to drive their own
pre-round phase.

### Round 1 eases in over three stages instead of one flat window

**v2.6.3.** A round with one flat probability for its whole length felt
like it jumped around rather than building — a real round starts
cautious and picks up. So `CoachSession.rampStage()` now splits Round 1
by *elapsed time within the round* (`elapsedInRoundMs`, the same running
total `delayMillisFor()` already tracked):

- **First ~45s** (`CoachSession.ROUND_ONE_EARLY_WINDOW_MS`): `ROUND_ONE_EARLY`
  — mostly single jabs and short 2-punch combos.
- **Rest of round 1**: `ROUND_ONE_LATE` — deliberately identical numbers
  to `ROUND_TWO`, so the round eases into that pacing rather than
  jumping straight from "very simple" to "Round 2's pacing" right at the
  round boundary.

On top of that, both platforms now hold a short **silent grace period**
at the very start of *every* round (not just round 1) before the coach's
first call — `ROUND_START_COACH_GRACE_MS` (6s, vs. the plain
`ANNOUNCE_LEAD_MS` 2.5s used only when resuming from pause, which has no
announcement to wait out at all). A real fighter gets a beat to feel out
the opening before anyone's calling combos; the previous 2.5s lead-in
was purely about not talking over "Round X, begin!", not about giving
that opening beat.

**Code**: `CoachSession.rampStage()` (the elapsed-time split for round
1), `ROUND_ONE_EARLY_WINDOW_MS` (companion constant). Grace period:
`Second.kt`'s `ROUND_START_COACH_GRACE_MS` (used only when
`isRoundStart` — a paused/resumed round keeps the plain
`ANNOUNCE_LEAD_MS`) and `RoundTimerService.kt`'s constant of the same
name (`runCoachLoop` is always a genuine round start there, since
pause/resume reuses the running loop instead of relaunching it).

### Last 10 seconds: one warning beep, not one per second

**v2.6.3.** `playWarningBeep()` calls `stopSpeaking()` before every beep
(see "Audio collision" below) — phone's round timer used to call it on
*every single tick* for the last 10 seconds, which cut off any coach
command mid-word, every time, for the entire closing stretch of a round.
Wear already only beeped once (a `warned` boolean gate); phone now
matches it via `finalWarningPlayed`, reset per round alongside
`halfTimeBellPlayed`. The coach keeps calling commands right through the
end of the round on both platforms — only the *beep* is one-shot, not
the coaching.

**Code**: `Second.kt`'s `finalWarningPlayed` (mirrors the pre-existing
`halfTimeBellPlayed` pattern), gating the `playWarningBeep()` call in
both `startTimer()` and `resumeTimer()`'s `onTick`. Wear's
`RoundTimerService.runPhase()` already had this right (`warned`) — no
change needed there.

---

## History tracking

Coach sessions save richer stats than a plain timer session:

| Field | Meaning |
|---|---|
| `trainingMode` | `TIMER_ONLY` or `BOXING_COACH` |
| `coachDifficulty` | `BEGINNER` / `INTERMEDIATE` / `ADVANCED` |
| `totalPunches` | Sum across all 6 punch numbers, Attack + Defense combined |
| `totalCombos` | Count of **Attack** beats called (`CoachStats.attackCalls`) |
| `totalTacticalCommands` | Count of **Defense + Distance** beats combined |
| `punchBreakdown` | `"1:12,2:8,3:5,4:2,5:1,6:0"` — per-punch-number counts |

Note the DB column names (`totalCombos`/`totalTacticalCommands`) predate
this Attack/Defense/Distance split and were kept as-is to avoid another
schema migration — mentally read them as "Attack calls" / "Defense +
Distance calls".

**Code**: `CoachStats.kt` (`attackCalls`/`defenseCalls`/`distanceCalls`/
`serializeBreakdown()`), `Training.kt` + `DBHandler.kt` (schema, version 2
migration), mapped at the save call sites in `RoundTimerService.kt` (wear)
and `Second.kt`/`First.kt` (phone, completed and interrupted sessions
respectively).

---

## Open / not yet built

- Southpaw stance setting (would flip left/right in `PunchNaming`).
- Difficulty-dependent section mix (currently one fixed `SectionPicker`
  table for all difficulties).
- The exact Defense `ACTION_WEIGHTS` split (Hands up / Move more / Punch)
  — first-draft numbers above, not specified by name.
