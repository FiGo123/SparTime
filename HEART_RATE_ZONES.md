# BoomBee — Heart-rate zones (Wear OS only)

Heart rate from the watch during a session, saved to history, and a Boxing
Coach that adapts its calls so each difficulty actually *feels* like that
difficulty, with the heart rate peaking in the **last** round, not the first.
**Watch only**: the phone never reads heart rate and has no heart-rate UI.

Decided 2026-10-11: watch history first (phone↔watch sync stays a later,
separate phase); adaptive coaching **on by default** with a Settings toggle;
intended to ship as **1.1 after the 1.0 production release**. Zones come
from age with **max HR = 220 − age**.

Companion docs: [COACH_MODE_TACTICS.md](COACH_MODE_TACTICS.md) (the
intensity/ramp system this biases), [WEAR_OS_IMPLEMENTATION.md](WEAR_OS_IMPLEMENTATION.md)
(`RoundTimerService`).

---

## Zones

| Zone | % of max HR | Name |
|---|---|---|
| Z1 | 50–60% | Very light |
| Z2 | 60–70% | Light |
| Z3 | 70–80% | Moderate |
| Z4 | 80–90% | Hard |
| Z5 | 90–95%* | Maximum |

\*As a *target*, Z5 is capped at the 95% safety limit (see Safety).

**Code**: `core/.../heartrate/HeartRateZones.kt`.

## Target zone per round

First round → last round, climbing linearly (rounded), so the last round
is always the highest:

| Difficulty | Round 1 | Last round | 3 rounds |
|---|---|---|---|
| Beginner | Z2 | Z3 | Z2 → Z3 → Z3 |
| Intermediate | Z2 | Z4 | Z2 → Z3 → Z4 |
| Advanced | Z3 | Z5 | Z3 → Z4 → Z5 |

A 1-round session targets the peak. Timer-only sessions, and Coach
sessions with HR coaching off, have no target (they only record).

**Code**: `HeartRateZones.targetRange()` / `targetZone()`.

## Adaptive coaching

`HeartRateCoach` (pure Kotlin) turns readings into a `HeartRateBias`:

- **Grace**: nothing adapts in the first 30s of a round (heart rate lags effort).
- **Smoothing**: average of the last 10s of readings.
- `BELOW` / `ON_TARGET` / `ABOVE` = smoothed BPM vs the target zone's range ± 3 bpm.
- `OVER_LIMIT` = every reading ≥ 95% of max HR for 20s. This applies at every
  difficulty, in timer-only mode too, and even with HR coaching off.

`CoachSession.heartRateBias` (set by the watch before each call):

| Bias | Intensity roll (HIGH / NORMAL / LOW) | Extra |
|---|---|---|
| `BELOW` | 60 / 30 / 10 | 35% of Defense/Distance beats become Attack |
| `ON_TARGET`, `NONE` | unchanged random roll (always a different value) | — |
| `ABOVE` | 10 / 30 / 60 | — |
| `OVER_LIMIT` | forced LOW for 15s | — |

A changed bias re-rolls immediately instead of waiting out the 15–30s
window. HIGH/LOW are the existing intensity levels (pace ×0.7 / ×1.4,
combo length ±1), so the realistic-throwing floor from `DifficultyProfile`
still holds.

**Cues** (`HeartRateCoach.nextCue()`), spoken from `RoundTimerService`
(not recorded in coach stats). They fall back to TTS until clips are recorded:

| When | Cue |
|---|---|
| `BELOW` held 20s | "Pick up the speed" (existing `phrase_pick_up_speed`) |
| `ABOVE` held 20s | "Breathe, stay sharp" (`Phrases.HR_EASE_OFF`) |
| `OVER_LIMIT` | "Slow down, breathe" + double vibration, repeats every 30s |

A push/ease cue fires at most once per bias change, at least 45s apart.

## Safety

- First launch on **phone and watch**: a Health & safety screen that has to
  be accepted (`SafetyText.GENERAL`). The watch also shows `SafetyText.HEART_RATE`.
- Settings → **Safety & disclaimer** on both devices.
- `hosting/terms.html` (full terms) and a heart-rate/age section in
  `hosting/privacy.html`. Both are linked from `index.html`.
- During a session, over the limit: the BPM line turns red ("Slow down"),
  the watch vibrates, and the coach eases off.

## Watch flow

1. First launch: Safety → crash-report consent → **Age** (13–90, bezel or
   − / +, shows max HR) → heart-rate permission. "Not now" turns heart rate off.
2. Settings → Heart rate On/Off (tap again to re-ask the permission), Age,
   HR coaching On/Off, Safety & disclaimer.
3. In a session: `♥ 152 · Z3 / Z4` (current / target) under the clock, in the zone's colour.
4. After the session: `♥ avg · max · peak ✓/↓` above "How hard was that?".
5. History → session: avg/max, peak reached, a graph (rounds shaded, Z4
   guide line), time in zones, each round's average vs target, rest recovery.

## Platform

- `HeartRateMonitor` (wear): Health Services `ExerciseClient`, exercise
  type `BOXING` (falls back to `WORKOUT`), `HEART_RATE_BPM`, plus
  `BatchingMode.HEART_RATE_5_SECONDS` when supported, so the screen-off
  updates are still at most 5s apart. A reading older than 15s counts as none.
  Only one app can own an exercise: starting a BoomBee session ends an
  active Samsung Health workout.
- Permissions: `android.permission.health.READ_HEART_RATE` (API 36+),
  `BODY_SENSORS` (maxSdk 35), `FOREGROUND_SERVICE_HEALTH`. The service type
  is `specialUse|health`. `startForegroundTyped()` adds `health` only when
  heart rate is active, and falls back to `specialUse` if that's refused.
- An `activity-alias` handles `VIEW_PERMISSION_USAGE` / `HEALTH_PERMISSIONS`
  (the system's "why does this app need it" link).
- `HeartRateRecorder` ticks once per unpaused second. History DB **v4** adds
  `hrAvg, hrMax, hrMaxHr, hrZoneSeconds, hrRoundAvg, hrRoundMax,
  hrRoundTarget, hrRecovery, hrSamples` (peak per 5s, 0 = no reading).

## Status

- [x] Core: zones, targets, `HeartRateCoach`, `HeartRateRecorder`, DB v4, `CoachSession` bias — unit-tested (`HeartRateTest`)
- [x] Watch: monitor, service integration, cues, safety/age/settings screens, live HR, history detail
- [x] Phone: safety dialog + Settings row (no heart rate)
- [x] `privacy.html` + `terms.html` written — **not deployed yet**
- [x] Installed on the Galaxy Watch 8 (debug build)
- [ ] **(you)** Real workout on the watch: readings arrive with the screen off,
      cues sound right, targets feel right per difficulty
- [ ] Tune the numbers after real sessions (grace, tolerance, cue timing, target table)
- [ ] Record `phrase_hr_ease_off`, `phrase_hr_slow_down`, `phrase_pick_up_speed` clips
- [ ] Deploy hosting (`firebase deploy --only hosting`)
- [ ] Play Console for the 1.1 release: Data safety (Health info → heart
      rate, collected but not shared, processed on-device only, optional),
      the Health apps declaration if asked, and the `health` foreground-service
      type in the Wear FGS declaration
- [ ] Later: phone↔watch sync so the phone history shows watch heart rate
