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
- [ ] **Play developer account type**: personal or organization. This
      decides whether the 12-tester, 14-day rule applies. It's a one-time $25
      fee plus identity verification.
- [ ] **Survey backend**: Firebase Firestore (recommended) or email. See
      `RELEASE_SURVEY.md` for the trade-off.
- [ ] **Tutorial artwork source**: commissioned, AI-generated then cleaned
      up, or placeholder vectors first. See `RELEASE_TUTORIAL.md`.
- [ ] **Support email** shown on the Play listing and in the app. Use a
      dedicated address such as `boombee.app@…`, not your personal inbox.
- [ ] **Privacy policy hosting**: GitHub Pages, Firebase Hosting or Google
      Sites (all free). A policy is required because analytics and the
      survey collect data.

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
- [ ] Versioning scheme (one source of truth for the version name, and
      unique versionCodes for phone vs watch).
- [ ] Upload keystore + release signing config; `bundleRelease` works for
      both modules.
- [ ] Play Console: account, create app, enable Wear OS form factor.
- [ ] First **closed testing** release (phone + watch) + recruit testers.
      **The 14-day clock starts here.**

### Stage 2 — Analytics + Crashlytics (S) → `RELEASE_ANALYTICS.md`
- [ ] Firebase project, both modules registered, `google-services.json`.
- [ ] Analytics + Crashlytics wired; screen views via NavController.
- [ ] Consent defaults + "Share anonymous usage data" toggle in Settings.
- [ ] Core events: training start / complete, coach settings.

### Stage 3 — Auto-update (S) → `RELEASE_AUTO_UPDATE.md`
- [ ] Replace the current always-IMMEDIATE check with flexible +
      priority-based immediate.
- [ ] Verified via Internal App Sharing (two builds, N → N+1).

### Stage 4 — Survey (M) → `RELEASE_SURVEY.md`
- [ ] Firestore + security rules (create-only, validated). Rules are in
      `firestore.rules`; waiting on the Firebase project + `google-services.json`.
- [x] Survey sheet (`survey/SurveySheet.kt`): 5 stars + optional text.
      Answers queue locally until Firebase is connected.
- [x] Trigger rules (first after 10 completed trainings) + "Rate &
      feedback" in Settings. Built ahead of Stages 2–3 on request.
- [ ] Analytics events.

### Stage 5 — Tutorial + mini-game (L) → `RELEASE_TUTORIAL.md`
- [ ] Pager with Next / Back / Skip, punch pages 1–6, defense, distance,
      "how the coach calls combos".
- [ ] "Tap the combo" mini-game using the recorded voice clips.
- [ ] Shown on first training start; reachable from the home screen and
      Settings.
- [ ] Final artwork swapped in.

### Stage 6 — Store readiness (M) → `RELEASE_PLAY_STORE.md` §5–§6
- [ ] Privacy policy published; Data safety, content rating, target
      audience, ads, health apps and foreground-service declarations.
- [ ] Listing text, icon, feature graphic, phone and watch screenshots.
- [ ] Full QA pass: phone (timer + coach), watch (timer + coach, ambient,
      ongoing activity), upgrade from a previous closed-test build.

### Stage 7 — Publishing automation + production (M) → `RELEASE_PLAY_STORE.md` §7
- [ ] Service account + Google Play Developer API access.
- [ ] Gradle Play Publisher configured (release notes, listing from repo,
      update priority).
- [ ] Optional: Google Play MCP server added to Claude Code.
- [ ] Production release with a staged rollout (e.g. 20% → 50% → 100%).

After Stage 7 the release flow is a single request, for example: *"release
3.0.1 to internal testing"* or *"promote 3.0.1 to production at 20%"*.
Claude bumps the versions, builds both AABs, uploads them with release
notes, and you confirm before anything reaches production.

---

## How we work through it

- One stage per session is a sensible pace. Tell Claude "continue the
  release roadmap" and it picks the first unchecked stage.
- Anything that needs your hands is marked **(you)** in the feature docs:
  consoles, payments, accounts, testing on the device.
- No production publish happens without your explicit confirmation in
  that session.
