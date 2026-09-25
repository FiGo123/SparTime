# BoomBee — App Logic Documentation

## Overview

BoomBee is an Android boxing/MMA interval timer with training history tracking. Users configure round parameters, run a timed training session with audio feedback (bell + voice), and optionally save completed or interrupted sessions to a local database.

This doc covers the **base timer app** (phone) as it stands today — setup, the round/rest loop, persistence, and the plain bell/voice feedback shared by every session. It's deliberately scoped there: everything about **Boxing Coach mode** itself (punch combos, tactics, flows, freestyle, warm-up, difficulty ramp, and the recorded-voice playback system) lives in [COACH_MODE_TACTICS.md](COACH_MODE_TACTICS.md); the watch app's own architecture (foreground service, wake lock, Ambient Mode, Ongoing Activity) lives in [WEAR_OS_IMPLEMENTATION.md](WEAR_OS_IMPLEMENTATION.md). A lot has changed since this file was first written — the Setup screen, the audio system, and the DB schema in particular — so treat this as the current source of truth for those, not the two companion docs' earlier snapshots of them.

---

## Architecture

**Pattern:** MVVM + Android Navigation Component
**State sharing:** Single `MainViewModel` (AndroidViewModel) shared across all fragments via `activityViewModels()`
**Persistence:** SQLite (training records) + SharedPreferences (settings, session state)

```
View Layer        → Fragments (First, Second, Rest, Dialog, Settings, HistoryTraining)
ViewModel Layer   → MainViewModel (LiveData, delegates to Dao)
Data Layer        → Dao → PreferencesProvider (SharedPreferences)
                        → DBHandler (SQLite)
```

---

## Navigation Flow

```
First (Setup)
  ├── [Start] ──────────────────────────────────────────────► Second (Round Timer)
  │                                                               │
  │                                                        [round ends]
  │                                                               │
  │                                                               ▼
  │                                                         Rest (Break Timer)
  │                                                               │
  │                                                        [break ends]
  │                                                               │
  │                                                               ▼
  │                                              [increment round] Second (next round)
  │                                                               │
  │                                                     [all rounds done]
  │                                                               │
  │                                                        [auto-save]
  │                                                               │
  │◄──────────────────────────────────────────────────────────────┘
  │
  ├── [Stop mid-round] ──────────────────────────────────► Dialog (Save?)
  │                                                               │
  │                                                          [Yes / No]
  │◄──────────────────────────────────────────────────────────────┘
  │
  ├── [Settings] ─────────────────────────────────────────► Settings
  └── [History]  ─────────────────────────────────────────► HistoryTraining
```

---

## Screen-by-Screen Logic

### First — Training Setup

**File:** `First.kt` / `fragment_first.xml`

- **Rounds / Round time / Rest are `-`/value/`+` steppers**, not text fields
  — `stepRounds()`/`stepRoundTime()`/`stepRest()` each clamp in place
  (Rounds 1–50, Round time/Rest 1–60 min) and push straight to the
  ViewModel on every tap, via `updateLocalValues()`. There's nothing left
  to validate on Start (a stepper can't hold an out-of-range value in the
  first place) — `validateInputs()` just forwards the current values.
- On load, populates defaults from `MainViewModel.trainingType` LiveData:
  - `BOXING`: 12 rounds × 3 min, 1 min rest
  - `MMA`: 5 rounds × 5 min, 1 min rest
  - Fallback: 3 rounds × 3 min, 1 min rest
- **Training Mode card** (only relevant field set covered here — see
  [COACH_MODE_TACTICS.md](COACH_MODE_TACTICS.md) for what each one
  actually does): a Timer-only/Boxing-Coach toggle that reveals three more
  toggle groups only in Coach mode — Difficulty (Beginner/Intermediate/
  Advanced), Voice Style (**Words** is the default — Numbers is the
  other option), and Warm-up (Off/30s/60s). Tapping Start calls
  `mainViewModel.resetCoachSession()`, which constructs a fresh
  `CoachSession` if Coach mode is selected.
- **Dialog response handling** (`handleDialogResponse()`): checked each time `First` loads
  - If `dialogAnswer == true`: saves an interrupted training record, resets answer flag

---

### Second — Round Timer

**File:** `Second.kt` / `fragment_second.xml`

#### Timer Lifecycle

```
onCreateView
  └── observeViewModel()
        └── roundLengthInMin.observe → setupRoundTimer(length)
              ├── Sets timeRemainingInMillis = length × 60 × 1000
              ├── Checks: if currentRound > totalRounds → saveTraining()
              ├── Round 1 only → startPrepareCountdown() (10s "GET READY",
              │     beeps in the last 3s) → onFinish → startRoundOneFlow()
              └── Round 2+: straight to the round-start sequence below
```

**`startRoundOneFlow()`** (round 1 only): if Coach mode + a warm-up
duration is selected, runs the pre-round `runWarmupPhase()` first (shows
"WARM UP" on screen, its own countdown, coach calls at warm-up pacing —
see COACH_MODE_TACTICS.md) and only calls `beginRoundOne()` once that
finishes; otherwise skips straight to it. **Round start sequence**
(`beginRoundOne()` for round 1, the `else` branch of `setupRoundTimer()`
for round 2+): `playRoundSound()` (voice), then `beginRoundWithLeadIn()`
— which shows the full round time immediately but doesn't start the real
`CountDownTimer` for `ANNOUNCE_LEAD_MS` (2.5s), so the clock doesn't
visibly move until the "Round X" announcement has actually finished
speaking — then `startCoachLoopIfNeeded()` if in Coach mode.

#### Timer States

| State | `isTimerRunning` | `isTimerPaused` | Button Text |
|-------|-----------------|-----------------|-------------|
| Running | `true` | `false` | PAUSE |
| Paused | `true` | `true` | RESUME |
| Stopped/Idle | `false` | `false` | — |

#### Pause / Resume

- **Pause**: `countDownTimer.cancel()`, sets `isTimerPaused = true`
- **Resume**: creates a new `CountDownTimer` with the remaining `timeRemainingInMillis`
- **Stop**: cancels timer, saves current state to ViewModel, navigates to Dialog

#### Round Completion (`handleRoundFinish`)

```
if currentRound >= totalRounds:
    announceWorkoutDone() → saveTraining() → insert DB record → navigate to First
else:
    playBellSound()
    setCurrentRound(currentRound + 1)
    (wait BELL_DURATION_MS so the bell finishes — see Audio collision below)
    navigate to Rest
```

#### Audio — voice

- Announces `"Round X, begin!"` at the start of each round, and (Coach
  mode) calls out punches/tactics on their own schedule — see
  [COACH_MODE_TACTICS.md](COACH_MODE_TACTICS.md) for everything about
  *what* gets called and *how*.
- **Your own recorded voice is used wherever a clip exists** for what's
  being said (`ClipPlayer`/`CoachAudio`, `assets/audio/`); anything not
  recorded falls back to Android `TextToSpeech` automatically, including
  the plain "Round X" announcement itself once you've recorded the
  matching clips. See `VOICE_RECORDING_SCRIPT.md` for the recording plan.
- TTS is initialized lazily; speech is only enqueued inside the `onInit`
  callback to avoid the async race condition. TTS (and the clip player)
  are shut down in `onDestroy()`.
- Respects the sound-enabled setting from SharedPreferences.

#### Audio — bell

- Uses `ToneGenerator` on `AudioManager.STREAM_ALARM` (**not** the system
  default notification ringtone) — reliably audible regardless of the
  ringtone setting and typically survives Do Not Disturb, which matters
  for a training timer. Same choice made independently on the watch side
  (`RoundAnnouncer`).
- Half-time bell, a 10-second warning beep (fires once per round, not
  every second — see Audio collision below), and the round/rest-end bell.
- Wrapped in try/catch for graceful fallback. Respects the sound-enabled
  setting.

#### Audio collision: bell vs. voice

A coach TTS/clip call and a bell/warning tone run on independent audio
paths with no built-in coordination — starting a tone mid-speech is
audible as garbled overlap, and the reverse (speech starting while a bell
is still ringing) is just as bad. `stopSpeaking()` (stops both TTS and
the clip player) runs before every tone; navigating to the next
fragment (`Second`↔`Rest`) after a bell is deliberately delayed by
`BELL_DURATION_MS` since the two fragments have entirely separate TTS
instances that can't otherwise know about each other's still-ringing
bell. See COACH_MODE_TACTICS.md's "Audio collision" section for the full
history of this fix (it took several rounds of real-device tuning).

---

### Rest — Break Timer

**File:** `Rest.kt` / `fragment_rest.xml`

- Observes `pauseLengthInMin` LiveData → converts to millis → starts `CountDownTimer`
- Increments `currentRound` in ViewModel (round counter advances during the break, so when navigating back to Second the correct round number is used)
- On timer finish: plays the bell, then waits `BELL_DURATION_MS` (same
  bell/voice-collision reasoning as `Second.kt` — this fragment has its
  own separate TTS instance) before navigating to `Second` (next round)
- Stop button: cancels the timer *and* the pending post-bell navigation, navigates to `First`
- **Audio**: Announces `"Rest time. Recover!"` via TTS at the start of the
  break (this exact wording isn't covered by the recorded-voice clip —
  `misc_rest.wav` is just the word "Rest" — so this announcement stays
  TTS-only for now); plays the bell (`ToneGenerator`/`STREAM_ALARM`, same
  as `Second.kt`) when the break ends

---

### Dialog — Save Interrupted Training

**File:** `Dialog.kt` / `fragment_dialog.xml`

- Shown when user taps Stop mid-round
- **Yes**: `mainViewModel.setDialogAnswer(true)` → navigate to First (First handles the actual DB save)
- **No**: `mainViewModel.setDialogAnswer(false)` → navigate to First (no save)

---

### Settings

**File:** `Settings.kt` / `fragment_settings.xml`

- Training type dropdown: Boxing / MMA (updates SharedPreferences + ViewModel)
- Sound toggle: enables/disables all audio (bell + voice)
- **Coach Options card**: two more toggles, "Coach: Tactics" and "Coach:
  Freestyle" (`checkboxTacticalCommands`/`checkboxFreestyle`) — turn off
  Defense/Distance calls or freestyle windows entirely without leaving
  Coach mode. See COACH_MODE_TACTICS.md's "Settings" section.
- Save Changes button persists the training-type selection (the sound and
  Coach Options toggles save immediately on tap, not on this button)

---

### HistoryTraining — Training Log

**File:** `HistoryTraining.kt` / `fragment_training_list.xml`

- Reads all records from SQLite via `DBHandler.getAllTraining()`
- Displays them in a `RecyclerView` using `TrainingAdapter`
- Each item shows: title, date, rounds, duration

---

## Data Layer

### Training Model (`Training.kt`)

| Field | Type | Description |
|-------|------|-------------|
| `id` | Int | Auto-increment PK |
| `title` | String | e.g. "Boxing Training" |
| `date` | String | "yyyy-MM-dd HH:mm" |
| `numberOfRounds` | Int | Rounds completed |
| `roundDuration` | Int | Minutes per round |
| `difficultyScale` | Int | 1–5 (currently always 3) |
| `description` | String | "Completed / Interrupted training" |
| `trainingMode` | String | `TIMER_ONLY` or `BOXING_COACH` |
| `coachDifficulty` | String? | `BEGINNER`/`INTERMEDIATE`/`ADVANCED`, null outside Coach mode |
| `totalPunches` | Int | Sum across all 6 punch numbers, Coach mode only |
| `totalCombos` | Int | Count of Attack beats called |
| `totalTacticalCommands` | Int | Count of Defense + Distance beats combined |
| `punchBreakdown` | String? | Serialized per-punch-number counts — see `CoachStats.serializeBreakdown()` |

All six Coach fields are optional/defaulted, so old rows saved before
Coach Mode existed still load fine. See COACH_MODE_TACTICS.md's "History
tracking" section for how these are populated.

### DBHandler (SQLite)

- Table: `training`, currently schema **version 2** (bumped from 1 to add
  the six Coach columns above via `ALTER TABLE ADD COLUMN` in
  `onUpgrade()` — existing rows keep their data, new columns default per
  `Training.kt`'s field defaults)
- `insertData(training)`: inserts a record
- `getAllTraining()`: returns all records ordered by id descending

### SharedPreferences Keys (via PreferencesProvider)

| Key | Type | Purpose |
|-----|------|---------|
| `progress` | Boolean | Whether a session is in progress |
| `default` | String | Training type ("BOXING" / "MMA") |
| `sound_status` | Boolean | Sound on/off |
| `key_dialog` | Boolean | Did user choose to save interrupted session |
| `time_if_interupt` | Int | Remaining time when session was stopped |
| `coach_freestyle_enabled` | Boolean | Coach Mode: freestyle windows on/off (default true) |
| `coach_tactical_commands_enabled` | Boolean | Coach Mode: Defense/Distance calls on/off (default true) |

(Coach difficulty, voice style, and warm-up length are **not** persisted
— they're plain `MutableLiveData` on `MainViewModel`, chosen fresh each
time on the Setup screen, not remembered session to session.)

---

## ViewModel (`MainViewModel`)

All fragments share one ViewModel instance. Key LiveData fields:

| LiveData | Type | Source |
|----------|------|--------|
| `numOfRounds` | `Int` | Set from First on Start |
| `roundLengthInMin` | `Int` | Set from First on Start |
| `pauseLengthInMin` | `Int` | Set from First on Start |
| `currentRound` | `Int` | Starts at 1, incremented in Rest |
| `leftTime` | `Int` | Seconds remaining when paused/stopped |
| `trainingType` | `String` | Loaded from SharedPreferences |
| `finishedRounds` | `Int` | Used for interrupted session saves |
| `trainingMode` | `TrainingMode` | `TIMER_ONLY` or `BOXING_COACH`, set from First |
| `coachDifficulty` | `Difficulty?` | Set from First, Coach mode only |
| `voiceStyle` | `VoiceStyle` | Numbers or Words, default **Words** |
| `warmupSeconds` | `Int` | 0/30/60, set from First |

Plus a plain (non-LiveData) property: **`coachSession: CoachSession?`**
— persists across the `Second`/`Rest` fragment recreation that happens
every round (Nav Component replaces the fragment instance each round),
which a fragment-local field couldn't survive. Reset via
`resetCoachSession()` whenever a fresh session starts from `First`.

---

## Audio System

See the "Second — Round Timer" section above for the full picture
(voice + bell + how they're kept from colliding); summary here:

### Voice

| Event | Utterance |
|-------|-----------|
| Round starts | `"Round X, begin!"` (phone) — your recorded voice for "Round X" if the clips exist, TTS for the rest |
| Rest starts | `"Rest time. Recover!"` (TTS only — no matching recorded clip) |
| Coach mode | Punches/tactics — see COACH_MODE_TACTICS.md |

- Engine: Android built-in `TextToSpeech`, **or** your own recorded voice
  via `ClipPlayer` wherever a matching clip exists in `assets/audio/`
  (`CoachAudio` decides which) — see `VOICE_RECORDING_SCRIPT.md`.
- TTS language: `Locale.getDefault()`. Initialization is async — speech is
  only enqueued inside the `onInit` callback.
- One TTS instance (and one `ClipPlayer`) per `Second`/`Rest` fragment
  instance, shut down in `onDestroy()` — this is *why* the two fragments
  can't directly interrupt each other's audio, see the bell/voice
  collision note above.
- Gated by the `sound_status` preference.

### Bell Sound

| Event | Sound |
|-------|-------|
| Half-time | Single tone |
| 10 seconds remaining | Single warning beep (fires once, not every second) |
| Round/Rest ends | Double-tone bell |

- `ToneGenerator` on `AudioManager.STREAM_ALARM` — **not** the system
  default notification ringtone (that was the original implementation;
  changed because it's unreliable when the notification ringtone itself
  is set to silent, and alarm-stream tones survive Do Not Disturb, which
  a training timer needs).
- Gated by the `sound_status` preference.

---

## Training Save Flow

### Completed Training (all rounds done)

```
Second.handleRoundFinish()
  └── currentRound >= totalRounds
        └── Second.saveTraining()
              └── DBHandler.insertData(Training(...))
              └── navigate to First
```

### Interrupted Training (user stops)

```
Second.stopSession()
  └── navigate to Dialog
        ├── [Yes] → mainViewModel.setDialogAnswer(true) → navigate to First
        │               └── First.handleDialogResponse()
        │                     └── First.saveInterruptedTraining()
        │                           └── DBHandler.insertData(Training(...))
        └── [No]  → mainViewModel.setDialogAnswer(false) → navigate to First
```

---

## Round Counter Logic

`currentRound` starts at `1` (set in `First` when training begins).

The counter increments in **`Second.kt`'s `handleRoundFinish()`**,
*before* navigating to Rest (not in `Rest.kt` — it doesn't touch
`currentRound` at all):
```kotlin
mainViewModel.setCurrentRound(currentRoundNumber + 1)
// ...then navigate to Rest
```

This means `currentRound` already reflects the next round number by the
time Rest even starts — so when Rest finishes and navigates back to
Second, the new Second instance's `roundLengthInMin` observer fires with
the correct round already set, and displays/announces it correctly.

The session ends when `Second.setupRoundTimer()` detects `currentRound > totalRounds`.

---

## Known Limitations / Future Improvements

- `difficultyScale` (the original 1–5 field) is still always saved as
  `3` and has no UI — don't confuse it with `coachDifficulty`
  (Beginner/Intermediate/Advanced), a separate field that *does* have a
  Setup-screen toggle, just only in Coach mode.
- Sound setting is read at start of session but not watched for live changes
- **Phone has no wakelock** — screen may sleep during a long round. (The
  watch app *does* hold a wake lock for the session — see
  WEAR_OS_IMPLEMENTATION.md — this is phone-specific.)
- **Phone has no foreground service** — timer stops if the app is fully
  backgrounded/killed. (Same watch/phone asymmetry as above.)
- Rest period cannot be paused (only stopped)
- Training history shows no delete/edit functionality
- Rest's TTS-only "Rest time. Recover!" wording doesn't match the
  recorded `misc_rest` clip ("Rest") — either re-record it as the exact
  phrase, or shorten the spoken text to just "Rest" to let it use your
  voice like the Round announcement and wear's Rest announcement already do.
