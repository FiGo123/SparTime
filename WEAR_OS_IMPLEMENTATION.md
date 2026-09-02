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
- **Keep-alive**: like the phone app, v1 has no foreground service — a run
  can be interrupted if the watch aggressively deep-sleeps mid-round. Flagged
  as a known limitation, matching the phone app's own documented gap.

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

### Phase 6 — Real Galaxy Watch 8 (you do this part)

- [ ] Enable Developer Options + ADB debugging on the watch (Settings →
      About watch → tap Software version 7×, then Developer options →
      Wireless debugging / ADB debugging).
- [ ] `adb pair <watch-ip>:<port>` / `adb connect`, or USB-C-to-watch cable
      if you have one.
- [ ] `./gradlew :wear:installDebug` targeting the watch's device id.
- [ ] Confirm a full 3-round session end-to-end, screen stays awake/ambient
      transitions don't kill the timer, haptics fire on round change.

### Phase 7 — Future / stretch (not started)

- [ ] Phone↔watch history sync via Wear `DataClient`.
- [ ] "Quick start last training" **Tile**.
- [ ] Ambient-mode-optimized timer screen (currently relies on default
      always-on burn-in-safe redraw; not yet tuned).
- [ ] Foreground service so a round survives the watch entering deep sleep.
- [ ] Play Console: bundle as a proper Wear feature module for phone
      auto-install distribution.

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
