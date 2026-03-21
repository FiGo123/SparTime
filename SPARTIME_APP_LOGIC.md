# SparTime — App Logic Documentation

## Overview

SparTime is an Android boxing/MMA interval timer with training history tracking. Users configure round parameters, run a timed training session with audio feedback (bell + TTS), and optionally save completed or interrupted sessions to a local database.

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

- Displays three `EditText` fields: Rounds, Rest (min), Round Duration (min)
- On load, populates defaults from `MainViewModel.trainingType` LiveData:
  - `BOXING`: 12 rounds × 3 min, 1 min rest
  - `MMA`: 5 rounds × 5 min, 1 min rest
  - Fallback: 3 rounds × 3 min, 1 min rest
- `TextWatcher` listeners update ViewModel in real time as user types
- **Validation** on Start press:
  - Rounds: 1–50
  - Rest: 1–60 min
  - Round time: 1–60 min
  - Shows inline errors on `EditText` if invalid
- On valid Start: sets ViewModel values, navigates to `Second`
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
              └── startTimer()
                    └── CountDownTimer(timeRemainingInMillis, 1000)
                          onTick → updateTimeText() [MM:SS display]
                          onFinish → handleTimerFinish()
```

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

#### Round Completion (`handleTimerFinish`)

```
if currentRound >= totalRounds:
    saveTraining()          → insert DB record → navigate to First
else:
    playComplianceBellSound()
    navigate to Rest
```

#### Audio — Round Start TTS

- Announces `"Round X"` via Android `TextToSpeech` at the start of each round
- TTS is initialized lazily; `announceRound()` is called inside the `onInit` callback to avoid the async race condition
- TTS is shut down in `onDestroy()`
- Respects the sound-enabled setting from SharedPreferences

#### Audio — Round End Bell

- Plays the system default notification ringtone when a round timer expires
- Wrapped in try/catch for graceful fallback
- Respects the sound-enabled setting

---

### Rest — Break Timer

**File:** `Rest.kt` / `fragment_rest.xml`

- Observes `pauseLengthInMin` LiveData → converts to millis → starts `CountDownTimer`
- Increments `currentRound` in ViewModel (round counter advances during the break, so when navigating back to Second the correct round number is used)
- On timer finish: navigates to `Second` (next round)
- Stop button: cancels timer, navigates to `First`
- **Audio**: Announces `"Rest"` via TTS at the start of the break; plays bell when break ends

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
- Sound toggle: enables/disables all audio (bell + TTS)
- Save Changes button persists selections

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

### DBHandler (SQLite)

- Table: `training`
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

---

## Audio System

### Text-to-Speech (TTS)

| Event | Utterance |
|-------|-----------|
| Round starts | `"Round X"` |
| Rest starts | `"Rest"` |

- Engine: Android built-in `TextToSpeech`
- Language: `Locale.getDefault()`
- Initialization is async — speech is only enqueued inside the `onInit` callback
- Instance created once per `Second` / `Rest` fragment, shut down in `onDestroy()`
- Gated by the `sound_status` preference

### Bell Sound

| Event | Sound |
|-------|-------|
| Round ends | System default notification ringtone |
| Rest ends | System default notification ringtone |

- Uses `RingtoneManager.getDefaultUri(TYPE_NOTIFICATION)` + `Ringtone.play()`
- Gated by the `sound_status` preference

---

## Training Save Flow

### Completed Training (all rounds done)

```
Second.handleTimerFinish()
  └── currentRound >= totalRounds
        └── Second.saveTraining()
              └── DBHandler.insertData(Training(...))
              └── navigate to First
```

### Interrupted Training (user stops)

```
Second.stopTimer()
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

The counter increments in **Rest.kt** `onCreateView`:
```kotlin
val currentRoundValue = mainViewModel.currentRound.value?.plus(1)
mainViewModel.setCurrentRound(currentRoundValue)
```

This means when navigating from Rest back to Second, `currentRound` already reflects the next round number — so Second displays and announces the correct round.

The session ends when `Second.setupRoundTimer()` detects `currentRound > totalRounds`.

---

## Known Limitations / Future Improvements

- `difficultyScale` is always saved as `3` — no UI to select difficulty
- Sound setting is read at start of session but not watched for live changes
- No wakelock — screen may sleep during a long round
- No notification / foreground service — timer stops if app is backgrounded
- Rest period cannot be paused (only stopped)
- Training history shows no delete/edit functionality
