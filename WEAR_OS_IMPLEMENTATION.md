# BoomBee for Wear OS (Galaxy Watch 8) — Implementation Tracker

## Goal

Bring BoomBee's boxing/MMA round timer to Wear OS so it runs standalone on a
Galaxy Watch 8 (no phone required to start/run a session). Reuses the existing
data layer (SQLite history + SharedPreferences settings) via a new shared
`:core` module; ships a new `:wear` module with a Compose for Wear OS UI
suited to round screens.

Companion doc: [BOOMBEE_APP_LOGIC.md](BOOMBEE_APP_LOGIC.md) describes the
current phone-only app this is based on.

---

## Architecture decision

- **Standalone watch app**, not a phone-tethered "Wear app" bundled inside the
  phone APK. Galaxy Watch 8 (Wear OS 3+) runs apps independently of the phone;
  bundling into the phone APK for auto-install is a Play Console distribution
  detail we can revisit at publish time, not a dev-time requirement.
- **Separate applicationId** for the watch app (`com.example.boombee.wear`)
  to keep it installable side-by-side with the phone app during development
  without signature/collision concerns.
- **UI toolkit: Jetpack Compose for Wear OS** (`androidx.wear.compose`), not
  the phone's View/Fragment/Navigation-Component stack — round/small screens
  need `ScalingLazyColumn`, `Picker`, `Stepper`, `TimeText`, curved layouts
  that only the Wear Compose library provides.
- **Shared code**: `Training` model, `DBHandler`/`Dao` (SQLite), and
  `PreferencesProvider` are UI-agnostic already (no Fragment/Activity
  references) — extracted verbatim into a new `:core` Android library
  module. `MainViewModel` moved into `:core` too so `:app` keeps compiling
  unchanged, but `:wear` talks to `Dao`/`DBHandler` directly rather than
  through it (see Notes).
- **No phone↔watch sync in v1.** Each device keeps its own local training
  history. Sync via the Wear Data Layer API (`DataClient`/`MessageClient`) is
  a deliberate future phase (see below) — real complexity, not needed for the
  watch app to be useful on its own.
- **Keep-alive took three attempts before it actually worked on real
  hardware** — each caught a distinct failure mode the emulator never
  exposed:
  1. `FLAG_KEEP_SCREEN_ON` on the timer Activity. Insufficient: Samsung's
     watch software force-finishes an Activity it decides has held the
     screen awake too long, killing the whole process — silently ended
     round tracking after ~1 round.
  2. Moved the countdown into `RoundTimerService`, a real foreground
     service with a persistent notification — the mechanism the OS treats
     as legitimately active, so it stopped getting force-killed. Screen was
     now free to idle normally during a session.
  3. Still not enough: with the screen off, the watch's CPU goes to sleep
     and a plain coroutine `delay()` loop just **freezes** — it doesn't
     drift or crash, it silently stops advancing and only "catches up"
     once something wakes the CPU again (e.g. reopening the app). No
     bell/vibration fired at the real right time in between. Fixed with a
     partial `WakeLock` held for the session's duration, which keeps the
     CPU from fully sleeping. Verified: left the watch untouched, screen
     off, through more than one full round+rest cycle, and it correctly
     advanced the whole time.
  A plain foreground service is "legitimately alive" to the OS; a held
  wake lock is what makes its *background code actually keep running on
  schedule*. Both were necessary — neither alone was sufficient. Separately,
  Ambient Mode and an Ongoing Activity status address *visibility* (what
  you see, on this screen or the watch face) rather than correctness of
  the countdown itself — see Notes.

---

## Phases

### Phase 1 — Extract shared `:core` module ✅

- [x] Create `core` Android library module (`com.example.boombee.core`
      namespace; classes keep their original packages
      `com.example.boombee.data`, `com.example.boombee.data.models`,
      `com.example.boombee.viewmodel`).
- [x] Move `data/Dao.kt`, `data/DBHandler.kt`, `data/PreferencesProvider.kt`,
      `data/models/Training.kt`, `viewmodel/MainViewModel.kt` from `:app`
      into `:core`.
- [x] `:app` depends on `:core`; phone app compiles unchanged (same package
      names, only physical file location + module moved).
- [x] `./gradlew :app:assembleDebug` succeeds after the move.

### Phase 2 — Scaffold `:wear` module ✅

- [x] New `wear` Gradle module: `com.android.application`, min/target/compile
      SDK for Wear OS (minSdk 30 = Wear OS 3, the floor for Galaxy Watch
      4/5/6/7/8-class hardware; compileSdk 34).
- [x] Manifest: `<uses-feature android:name="android.hardware.type.watch"/>`,
      `com.google.android.wearable.standalone` = `true`, launcher activity.
- [x] Compose for Wear OS dependencies (`compose-material`,
      `compose-foundation`, `activity-compose`, `wear-tooling-preview`,
      `play-services-wearable`).
- [x] Depends on `:core` for the data layer (`Dao`, `DBHandler`,
      `PreferencesProvider`, `Training`) — **not** `MainViewModel`; see
      Notes below for why.
- [x] Minimal `MainActivity` + theme that builds and installs.
- [x] `./gradlew :wear:assembleDebug` succeeds.

### Phase 3 — Wear UI (round timer core loop) ✅

- [x] **Setup screen**: rounds / rest minutes / round-length minutes via
      a `-`/value/`+` stepper row (see Notes — not Wear `Picker`), Start
      button. Ranges 1–50 rounds, 1–60 rest, 1–60 round length, matching the
      phone app's validation bounds (no inline-error UI needed since the
      stepper can't go out of range).
- [x] **Timer screen**: big MM:SS countdown, current round / total rounds,
      Pause/Resume + Stop buttons, `TimeText` (watch clock overlay).
- [x] **Rest screen**: same `TimerScreen` composable, `REST` phase.
- [x] Round-complete → auto-advance to Rest → next round; last round →
      save to DB → back to Setup. Mirrors the phone's state machine in
      `BOOMBEE_APP_LOGIC.md`.
- [x] Haptic feedback (`Vibrator`) on round/rest boundaries — watch-native
      substitute for the phone's audible bell, since a silent wrist buzz is
      the expected UX on Wear OS (bell + TTS also included, gated the same
      `sound_status` preference as the phone).
- [x] TTS "Round X" / "Rest" announcements, gated by the shared sound
      setting, same as phone.
- [x] **Bell switched to `ToneGenerator` on `STREAM_ALARM`**, not
      `RingtoneManager`'s default notification sound — the latter was
      inaudible on a real watch (notification ringtone is commonly set to
      silent/none), see Notes.
- [x] **10-seconds-remaining warning** (short tone + double-tap vibration)
      before each round/rest ends — requested after real-device testing,
      not part of the original phone-parity scope.
- [x] **Countdown moved out of the Activity into `RoundTimerService`**
      (foreground service) — required after real-device testing showed the
      Activity-hosted version gets killed; see Architecture decision and
      Notes.
- [x] **Partial `WakeLock` held for the session** so the countdown keeps
      advancing on schedule while the screen is off, instead of freezing
      until the app is reopened; see Architecture decision.
- [x] **Ambient Mode** (`AmbientModeSupport`, `MainActivity` now a
      `FragmentActivity`) — keeps the timer screen itself as the visible
      (dimmed) surface on idle instead of handing off to the watch face, so
      raising the wrist shows the current round/time directly. This is a
      *display* fix (what you see on this same screen), separate from —
      and complementary to — the Ongoing Activity status below (what shows
      elsewhere, e.g. the watch face, while the app isn't open at all).
- [x] **Ongoing Activity status** (`androidx.wear:wear-ongoing`) — a live,
      system-rendered "Round X — MM:SS" status (ticking countdown handled
      by the OS itself via `Status.TimerPart`, not per-second app pushes)
      that Wear OS can surface on the watch face while the app is closed —
      requested so a session "shows a widget on the home screen" instead
      of appearing to have exited. Compiles now; see Notes for the
      `OngoingActivityStatus`/`TimerPart` dead-end this replaced, and for
      why on-watch-face visibility still depends on the watch face in use.
- [x] **Clock (`TimeText`) kept visible and styled bee-yellow** on every
      screen, including during an active round/rest (dims to gray in
      ambient) — requested so you can see the actual time-of-day while
      training, not just the countdown.
- [x] **1.5s announce lead-in** (`ANNOUNCE_LEAD_MS`) before the clock starts
      moving each phase, so "Round X"/"Rest" finishes being spoken before
      time starts counting down — requested after real-device testing
      showed the round already a few seconds in by the time the
      announcement finished.
- [x] **Double-clang end-of-phase bell**, distinct from the single
      10-second-warning beep — requested after real-device testing.
- [ ] Not carried over from phone: the 10-second "GET READY" pre-round-1
      countdown (`Second.kt`'s `startPrepareCountdown`) — deliberately
      skipped for v1 scope, see Notes.

### Phase 4 — Settings & History on watch ✅

- [x] **Settings screen**: training type (Boxing/MMA) toggle, sound on/off —
      reuses `Dao`/`PreferencesProvider` from `:core` directly, so a value
      set on the watch doesn't clash with the phone's copy (separate
      SharedPreferences files per device, by design — no sync in v1).
- [x] **History screen**: `ScalingLazyColumn` listing local
      `DBHandler.getAllTraining()` results.
- [x] Simple in-app navigation between the four screens (no Fragment/Nav
      Component — a `WearScreen` sealed-class state machine + Compose
      recomposition, matching Wear app conventions).

### Phase 5 — Build verification (this environment) ✅

- [x] Installed a Wear OS system image via `sdkmanager`
      (`system-images;android-36;android-wear-signed;arm64-v8a`, Wear OS
      6.0 — none was present initially, only phone images).
- [x] Created a round-screen Wear AVD (`Wear_Large_Round`, device profile
      `wearos_large_round`).
- [x] Booted the emulator, installed the `:wear` debug APK, launched
      `MainActivity` — no `FATAL EXCEPTION` in logcat, process stayed alive.
- [x] Manual click-through, screenshot-verified: Setup (scrolled to see
      Start/Settings/History), tapped Start → Timer screen showed
      "Round 1/12", live MM:SS countdown, Pause/Stop; tapped Stop → back to
      Setup cleanly. Re-verified again post-rebrand under the new
      `com.example.boombee.wear` applicationId with the new palette/name.
- [x] Phone app (`:app`) also reinstalled and launched clean on a second
      emulator (`Medium_Phone`) after the rebrand, confirming the `:core`
      extraction + rename didn't regress the original app.

### Phase 6 — Real Galaxy Watch 8 ✅

- [x] Enabled Developer Options + Wireless debugging on the watch, paired
      over Wi-Fi via `adb pair <ip>:<port> <code>` then auto-connected
      (mDNS `_adb-tls-connect._tcp`) — no USB cable path used/needed.
- [x] Installed via `adb install -r wear/build/outputs/apk/debug/wear-debug.apk`
      (device: Samsung `SM_L330`).
- [x] Found & fixed bug #1: the Activity-hosted timer got force-killed by
      the OS after ~1 round (see Architecture decision + Notes) — not caught
      by the emulator in Phase 5, only surfaced on real hardware. Fixed by
      moving the countdown into `RoundTimerService` (foreground service).
- [x] Found & fixed bug #2: even as a surviving service, the ticking
      coroutine silently froze once the watch's CPU went to sleep (screen
      off + idle) — round tracking just stalled and only "caught up" when
      the app was reopened, with no bell/vibration firing at the real right
      time in between. Fixed with a partial `WakeLock` held for the
      duration of a session (see Notes) — real workout-app trade-off:
      more battery use, guaranteed on-time alerts.
- [x] Confirmed with the fix in place: started a 1-minute-round session,
      left the watch completely untouched (screen off) for 75+ seconds —
      reopening showed **Round 2/3**, proving it correctly ran through
      Round 1 end → bell/vibrate → Rest → bell/vibrate → Round 2 while
      backgrounded the whole time, not just "caught up" on reopen.
- [x] Confirmed pressing the Home button mid-round does **not** kill the
      session (process/service/notification all persist) — it just, as
      expected, shows the watch face; the "app exited" perception before
      the wake-lock fix was actually the freeze bug above, not this.
- [x] **Milestone: user completed a real boxing workout on the watch** with
      the wake-lock + lead-in + double-clang-bell build — confirmed working
      well enough for actual training use, not just isolated testing.
- [x] Full multi-round session through to auto-save + History — confirmed
      many times over since, across dozens of real-hardware installs during
      the Coach Mode build-out (see `COACH_MODE_TACTICS.md`).
- [x] Ongoing Activity status and the bee-yellow `TimeText` styling —
      confirmed working on real hardware; the Wi-Fi-debugging-drops issue
      noted below turned out to be a recurring nuisance throughout the
      watch's whole development (fixed the same way every time: `adb
      kill-server && adb start-server`, sometimes 2-3 attempts, occasionally
      a full re-pair via "Pair new device" when the connection is dropped
      for a while), not a blocker on any particular feature.

**Everything after Phase 6** — the Boxing Coach engine (combos, tactics,
flows, freestyle, warm-up, difficulty ramp), the recorded-voice playback
system, and all their platform-specific wiring in `RoundTimerService`/
`RoundAnnouncer` — is tracked in `COACH_MODE_TACTICS.md`, not here. This
doc stays scoped to the base Wear OS port (timer survival, Ambient Mode,
Ongoing Activity, Settings/History parity) per the "Companion docs" note
at the top of that file.

### Phase 7 — Future / stretch (not started)

- [ ] Phone↔watch history sync via Wear `DataClient`.
- [ ] "Quick start last training" **Tile**.
- [ ] Migrate off `AmbientModeSupport` (deprecated, still functional) to the
      newer `androidx.wear.ambient` Compose-first ambient API once the
      current approach's UX has been validated more — not urgent, it works.
- [ ] Play Console: bundle as a proper Wear feature module for phone
      auto-install distribution.
- [ ] Consider Health Services' Exercise API instead of a plain foreground
      service + wake lock — the more "correct" long-term mechanism for a
      workout timer (exempts from background limits more robustly without
      needing to hold the CPU fully awake, and is what Samsung's own
      fitness apps use), but a materially bigger integration than the
      current `RoundTimerService`. Current approach is working end-to-end;
      revisit if the battery cost of the wake lock proves too high in
      practice, or it ever proves insufficient on other watches.

---

## Notes / decisions log

- Picked Wear OS **6.0 (API 36, arm64-v8a, "signed" image)** for the dev
  emulator — closest available match to a current Galaxy Watch 8 without
  bleeding-edge (37.0/Wear OS 7.0 also available in the SDK if needed later).
- Kept `:core`'s classes on their original package names
  (`com.example.boombee.*`) rather than renaming to `com.example.boombee.core.*`
  to keep the extraction a pure move — zero import changes needed in `:app`.
- **`:wear` does not use `MainViewModel`.** It's a thin LiveData wrapper
  around `Dao`, built for the phone's Fragment/`activityViewModels()` world.
  The watch app is a single Activity with Compose state (`SessionConfig` +
  a `WearScreen` sealed class); it talks to `Dao`/`DBHandler` straight from
  `remember {}`, which is the idiomatic Compose pattern and avoids pulling
  in `androidx.compose.runtime:runtime-livedata` just to bridge LiveData
  into Compose for no real benefit. Both UIs still sit on the exact same
  persistence classes — that's the actual shared-code win.
- **Setup screen uses a plain `-`/value/`+` stepper**, not Wear Compose's
  `Picker`/`Stepper` composables. Those have version-sensitive APIs (float
  ranges, icon slots) that weren't worth the compile-risk for a first pass;
  the plain stepper uses only `Button`/`Text`/`Chip`, which have been stable
  since Wear Compose Material 1.0. Worth revisiting for a more native feel.
- **Settings toggles use plain `Chip`s that flip on tap**, not `ToggleChip`
  — same reasoning (avoids `ToggleChipDefaults.SwitchIcon` API-surface risk)
  and same size UI text can double.
- Skipped the phone's 10-second pre-round-1 "GET READY" countdown — nice UX
  parity item, not essential to the core loop, left for a follow-up pass.
- Did **not** attempt phone-app-bundling / Play auto-install wiring — that's
  a distribution concern, deferred to Phase 7.
- **`OngoingActivityStatus`/`TimerPart` (as top-level classes) are not the
  public API — `androidx.wear.ongoing.Status` and its nested
  `Status.TimerPart`/`Status.TextPart`/`Status.Builder` are.** First
  attempt used `OngoingActivityStatus.Builder()`/`TimerPart(Instant, Boolean)`
  (matching some docs/sample code found from memory) against
  `wear-ongoing:1.0.0` — failed: `OngoingActivityStatus` package-private,
  `TimerPart` unresolved. Bumping to the latest stable, `1.1.0`, gave the
  *same* errors — so it was never a version problem. Only resolved by
  unzipping the actual resolved AAR
  (`~/.gradle/caches/.../wear-ongoing/1.1.0/.../wear-ongoing-1.1.0.aar`
  → `classes.jar`) and running `javap` against the class files directly:
  that showed `OngoingActivityStatus` is real but internal (a parcelable
  used by `Status.toVersionedParcelable()`), and the actual public surface
  is `Status.Builder().addTemplate(...).addPart(key, Status.Part)`, with
  `Status.TimerPart(endTimeMillis: Long)` (epoch millis the countdown
  reaches zero at — not an `Instant`, and no boolean "count down" flag;
  `TimerPart` is inherently a countdown, `StopwatchPart` is the count-up
  sibling). `OngoingActivity.Builder(...).setStatus(status: Status)` ties
  it together. **If a similarly-named androidx class won't resolve or is
  inaccessible, don't guess at a different version — decompile/`javap` the
  actual resolved artifact first; it's faster and it's ground truth.**
  Whether this actually renders on-watch-face depends on the watch face in
  use (not all faces show ongoing-activity indicators) — not yet confirmed
  visually on the real Galaxy Watch, only that it compiles and installs.
- Ambient Mode (`AmbientModeSupport`, requires `MainActivity` to extend
  `FragmentActivity`) and the Ongoing Activity status above solve
  *different* halves of "don't look like the app exited": Ambient Mode
  covers this same screen staying visible when idle; Ongoing Activity
  covers something showing elsewhere (watch face) when the app is fully
  closed. Both are wanted, neither substitutes for the other.
- **Ambient Mode and the wake lock fix are solving two different
  problems** — worth being explicit about, since they were built back to
  back and are easy to conflate: Ambient Mode is purely about what's drawn
  on screen when idle (does raising your wrist show the timer or the watch
  face); the wake lock is about whether the countdown *is still correct*
  regardless of what's on screen. Fixing one without the other leaves a
  real gap — Ambient Mode alone would show a screen that's just as frozen
  as before, and the wake lock alone leaves you back at "check by
  reopening the app."
- **Wireless debugging (Wi-Fi adb) to the watch is flaky** — the
  connection silently drops after roughly a minute or two of watch
  inactivity (its own Wi-Fi power saving, unrelated to the app). Recovery
  that reliably worked: `adb kill-server && adb start-server`, then
  `adb devices -l` — it usually rediscovers the watch via mDNS
  (`_adb-tls-connect._tcp`) without re-pairing, *if* Wireless debugging is
  still toggled on in Developer options on the watch side. If that comes
  back empty, the watch's toggle itself needs to be re-enabled first.
  Keeping the watch on its charger during a testing session reduces how
  often this happens.
