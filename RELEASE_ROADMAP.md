# BoomBee — Release 1.0 Roadmap (orchestrator)

Master plan for taking BoomBee (phone + Wear OS, Boxing Coach included) to
its first public Play Store release. This doc owns **order, dependencies and
status**. Each feature has its own detailed doc:

| # | Doc | What | Size |
|---|-----|------|------|
| A | `RELEASE_PLAY_STORE.md` | Release blockers, signing, Play Console, policy forms, publishing automation (Gradle Play Publisher + optional Google Play MCP) | L |
| B | `RELEASE_ANALYTICS.md` | Firebase / Google Analytics 4 (+ Crashlytics), consent | S |
| C | `RELEASE_AUTO_UPDATE.md` | Play In-App Updates (rework of the existing check) | S |
| D | `RELEASE_SURVEY.md` | Occasional 1–5 star survey + optional "what should improve" text | M |
| E | `RELEASE_TUTORIAL.md` | Punch tutorial (1–6, defense, distance) + "tap the combo" mini-game | L |
| F | `HEART_RATE_ZONES.md` | **After 1.0 (planned 1.1)**: watch heart-rate zones, adaptive coach, safety disclaimer | L |

Created 2026-09-25. Update the checkboxes here as stages complete.

---

## Why this order

1. **The package name has to be chosen first.** `com.example.boombee` is
   rejected by Google Play, and Firebase (analytics and survey) registers
   the app *by package name*. So Firebase setup comes after the rename.
   Once published, the package name can't be changed.
2. **Start the closed-testing clock early.** A *personal* Play developer
   account created after Nov 2023 must run a closed test with **12+ testers
   opted in for 14 consecutive days** before it can publish to production.
   Uploading a closed-test build as soon as Stage 1 is done lets those 14
   days run while Stages 2–5 are built. Every new build just goes to the
   same track. (An *organization* account doesn't have this requirement.)
3. **Analytics before the features.** When Firebase is in first, the
   survey and tutorial log their events from their first build.
4. **Auto-update before public release.** It only helps users who install
   a build that already contains it, so it has to be in 1.0.
5. **The tutorial is the biggest job and needs artwork.** Start sourcing
   the illustrations on day 1 so they aren't the thing that blocks the
   release (see `RELEASE_TUTORIAL.md` → Artwork).

---

## Decisions needed from you (before / during Stage 1)

- [x] **Package name (applicationId)**: **`com.filipgolovic.boombee`**
      (decided 2026-09-26). Permanent once published. The phone and
      watch apps use **the same** ID.
- [x] **Play developer account type**: **personal** (decided 2026-09-27;
      no company or D-U-N-S number). So the closed test with 12+ testers
      for 14 days is required before production. One-time $25 fee plus
      identity verification.
- [x] **Survey backend**: Firestore, location `eur3` (decided 2026-10-04). See
      `RELEASE_SURVEY.md` for the trade-off.
- [ ] **Tutorial artwork source**: commissioned, AI-generated then cleaned
      up, or placeholder vectors first. See `RELEASE_TUTORIAL.md`.
- [x] **Support email**: `filipgolovicc@gmail.com` (decided 2026-10-09).
- [x] **Privacy policy hosting**: Firebase Hosting →
      https://boombee.web.app/privacy.html (source `hosting/`).

---

## Stages

Each stage ends with: builds for `:app` and `:wear`, tested on a real phone
(and on the Galaxy Watch 8 when the watch is affected), a doc checkbox
update, and a commit.

### Stage 0 — Housekeeping (S)
- [x] Commit the current Coach Mode + Wear work (commit `491d09b`).
- [x] Remove `local.properties` from git tracking (`git rm --cached`). It
      was already in `.gitignore`.
- [x] Tag the commit `v2.9.6-pre-release` as a rollback point.

### Stage 1 — Release foundations (M) → `RELEASE_PLAY_STORE.md` §1–§4
- [x] New applicationId `com.filipgolovic.boombee` in `app/` **and**
      `wear/`. Both debug builds pass. `ExampleInstrumentedTest` updated
      to the new package name.
- [x] Toolchain upgrade: AGP 8.13.2, Gradle 8.14, Kotlin 2.2.21, Compose
      compiler plugin in `:wear`; `jcenter()` dropped. Build with the
      Android Studio JDK 21 (Gradle 8.14 can't run on the system JDK 26).
- [x] targetSdk / compileSdk 36; edge-to-edge insets handled once in
      `MainActivity` for every phone screen; back during a round/rest now
      acts like STOP. Checked on a Pixel 10 emulator (API 37). Still to do:
      a real phone, and the watch checks (see `RELEASE_PLAY_STORE.md` §3).
- [x] Versioning scheme: `boombeeVersionName` / `boombeeVersionCode` in
      `gradle.properties` (3.0.0 / 30000); watch = 1_000_000 + code;
      `AppVersion.VERSION` generated from it.
- [x] Release signing config reading `keystore.properties` (gitignored);
      `bundleRelease` works for both modules (unsigned until the keystore exists).
- [x] Upload keystore `~/keys/boombee-upload.jks` (password only in the
      gitignored `keystore.properties`; **back both up**). Signed AABs for
      3.0.0 build and verify (2026-10-09).
- [x] **(you)** Play developer account created and verified (2026-10-09).
- [x] **(you)** Play Console app "BoomBee: Boxing Timer & Coach" created,
      Wear OS form factor added (2026-10-11).
- [x] Phone 3.0.0 (30000) uploaded (internal testing, then added to the
      closed track **"Beta testers"**, 177+ countries, testers list
      "BoomBee") and **sent for review 2026-10-11** together with the store
      listing and all App content forms.
- [ ] Wear OS closed track ("Beta testers", same list) + upload
      `boombee-watch-3.0.0.aab` (versionCode 1030000). Deliberately held
      back; add it a few days into the test so its stricter Wear review
      runs in parallel. The 14-day rule is per app, so the watch doesn't
      need its own 14 days.
- [ ] After approval ("Available to selected testers"): send the opt-in
      link. **The 14-day clock starts once 12+ testers are opted in.**
      Aim for 15–20 testers; ask each to keep it installed, open it 3–4×
      a week and send at least one piece of feedback (Google asks about
      engagement when you apply for production).
- [ ] **(you)** Verify the Data safety answers match
      `PLAY_CONSOLE_ANSWERS.md` (filled in without a review pass).

### Stage 2 — Analytics + Crashlytics (S) → `RELEASE_ANALYTICS.md`
- [x] Firebase project `boombee`, Android app `com.filipgolovic.boombee`
      registered (covers phone + watch), `app/google-services.json` committed.
- [x] Analytics + Crashlytics wired; screen views via NavController.
- [x] Opt-in: first-launch prompt + "Share anonymous usage data" toggle in
      Settings; off until yes. No AD_ID / ad-services permissions.
- [x] Events: training start / complete / abort, tutorial, combo game,
      survey, settings.
- [ ] **(you)** Verify in Firebase DebugView on a real phone; enable
      Crashlytics in the console.

### Stage 3 — Auto-update (S) → `RELEASE_AUTO_UPDATE.md`
- [x] Replace the current always-IMMEDIATE check with flexible +
      priority-based immediate. (`update/UpdateController.kt`; home-screen
      "Update ready · Restart" Snackbar.)
- [ ] Verified via Internal App Sharing (two builds, N → N+1).

### Stage 4 — Survey (M) → `RELEASE_SURVEY.md`
- [x] Firestore (`eur3`) + create-only rules deployed (`firebase.json`).
- [x] Survey sheet (`survey/SurveySheet.kt`): 5 stars + optional text.
      Answers queue locally until Firebase is connected.
- [x] Trigger rules (first after 10 completed trainings) + "Rate &
      feedback" in Settings. Built ahead of Stages 2–3 on request.
- [x] Analytics events.

### Stage 5 — Tutorial + mini-game (L) → `RELEASE_TUTORIAL.md`
- [x] Pager with Next / Back / Skip, punch pages 1–6, defense, distance,
      "how the coach calls combos".
- [x] "Tap the combo" mini-game using the recorded voice clips.
- [x] Shown on first training start; reachable from the home screen and
      Settings.
- [x] Watch: optional "Learn punches" (setup screen) → punch list with
      tap-to-hear + the same combo game (`ComboGame` now in `:core`).
      Checked on the Galaxy Watch 8 (2026-10-10).
- [ ] Final artwork swapped in (placeholder = `tutorial/FighterView`, an
      orthodox fighter facing the user).
- [x] Analytics events.

### Stage 6 — Store readiness (M) → `RELEASE_PLAY_STORE.md` §5–§6
- [x] Privacy policy published: https://boombee.web.app/privacy.html.
- [x] Drafted answers for Data safety, content rating, target audience
      (18+), ads, health (Fitness) and the watch foreground-service
      declaration → `PLAY_CONSOLE_ANSWERS.md`.
- [x] **(you)** App content forms submitted: content rating (Everyone /
      PEGI 3 / 3+), target audience 18+, ads none, advertising ID no,
      health = Activity and fitness, government/financial none, app access
      unrestricted, category Health & Fitness with tags Workout, Martial
      arts, Sports coaching, Clock/alarm/timer, Health & fitness.
- [ ] Foreground-service declaration for the watch (comes up with the
      Wear OS upload; text in `PLAY_CONSOLE_ANSWERS.md`).
- [x] Listing text + release notes in `app/src/main/play/`.
- [x] Icon 512×512, feature graphic 1024×500, 6 phone + 6 Wear OS
      screenshots in `app/src/main/play/listings/en-US/graphics/` (copies
      in `~/Desktop/BoomBee-release/store/`). Declared as AI-made/edited in
      the store listing's AI asset declaration.
- [ ] Full QA pass: phone (timer + coach), watch (timer + coach, ambient,
      ongoing activity), upgrade from a previous closed-test build.

### Stage 7 — Publishing automation + production (M) → `RELEASE_PLAY_STORE.md` §7
- [ ] **(you)** Service account + Google Play Developer API access
      (`~/keys/boombee-play.json`; steps in `RELEASE_PLAY_STORE.md` §7).
      Only possible after the app exists and has one manual upload.
- [ ] Gradle Play Publisher configured (release notes, listing from repo,
      update priority).
- [ ] Optional: a Play MCP server. Google has no official one (checked
      2026-10-10); the Publishing API via GPP covers uploads.
- [ ] Production release with a staged rollout (e.g. 20% → 50% → 100%).

After Stage 7 the release flow is a single request, for example: *"release
3.0.1 to internal testing"* or *"promote 3.0.1 to production at 20%"*.
Claude bumps the versions, builds both AABs, uploads them with release
notes, and you confirm before anything reaches production.

---

## After 1.0

- **Built after the 1.0 test build (in code, not yet uploaded):** recorded
  attack takes as round finishers on phone and watch (`attack_1`,
  `attack_2`; see COACH_MODE_TACTICS.md "Recorded takes"), the same-hand
  slip/duck rule, sessions saved to History as they run (a killed session
  is kept as "stopped"; "No" on the stop dialog still discards it), and
  the watch asking for notifications only once. Next upload: **3.1.2**.

- **1.1 — Heart-rate zones (watch only)** → `HEART_RATE_ZONES.md`. Built
  2026-10-11 and installed on the watch for testing. Not part of the 1.0
  release. Its Play Console items (Data safety: heart rate; `health` FGS type)
  belong to the 1.1 upload. The safety disclaimer and the new
  `terms.html` ship with whichever build comes next.
- **Nice to have, later: optional Pro plan** (in-app purchase or
  subscription), only if users like the app. Everything free in 1.0 stays
  free. Ideas: extra coach styles, custom combos/programs, long-term stats,
  watch → phone sync, voice packs. Needs a Play payments profile (check
  that Montenegro is a supported payout country), Google Play Billing, and
  updated privacy policy / Data safety. The 1.0 "free, no ads" choice
  allows this (only switching to a paid download is impossible).

## Future development (proposals, 2026-10-14)

Boxing content (combos, programs, exercises, recordings) comes from Filip;
Claude builds the screens and the engine. Not started.

- **A. Combo builder.** The user picks combos from the library or types
  their own ("1-2-3") and sets per block a time or number of rounds
  ("1-2-3 for 2 rounds, then 1-1-2-slip-2 for 1 round"). The coach then
  calls only those combos in that block. Solution: a Builder screen saving
  sessions as presets, reusing the coach engine limited to the chosen
  combos.
- **B. Training cards (predefined programs).** Big, easy-to-pick cards on
  the Training tab: e.g. *Distance* (jab-heavy, footwork), *Closing
  distance* (entries, hooks and power punches, then move out), *Defense &
  counter*, *Pressure*, *Conditioning*. Each card shows name, short
  description, length and difficulty and starts with one tap. Solution:
  each program is a text file like the combo files: per round a focus
  (which combos/calls get more weight), the takes to use and the round
  plan.
- **C. Strength + boxing circuit.** Rounds that alternate boxing and
  exercise stations ("10 squats, 10 lunges, 15 push-ups, 20 jumps"), or
  mixed inside a round (30 s boxing → 30 s burpees), circuit style.
  Solution: a new station type in the round plan (boxing / exercise, with
  reps or time; user taps Done or it's time-based), new voice clips for
  exercise names and counts, and exercises logged in History.

## How we work through it

- One stage per session is a sensible pace. Tell Claude "continue the
  release roadmap" and it picks the first unchecked stage.
- Anything that needs your hands is marked **(you)** in the feature docs:
  consoles, payments, accounts, testing on the device.
- No production publish happens without your explicit confirmation in
  that session.
