# BoomBee — Play Store Release & Publishing Automation

Part of `RELEASE_ROADMAP.md` (Stages 1, 6, 7). Items marked **(you)** need
a human: accounts, payments, console clicks, device testing.

> Play policies change every year (target SDK, tester rules, forms). Every
> number below was correct when written. Re-check the linked Play Console
> page at the moment you do each step.

---

## §1 Release blockers found in the current project

| Blocker | Where | Fix |
|---|---|---|
| ~~`applicationId "com.example.boombee"`. Play rejects `com.example.*`~~ ✅ fixed | `app/build.gradle`, `wear/build.gradle` | Now `com.filipgolovic.boombee` (§2) |
| ~~Watch uses a different ID (`com.example.boombee.wear`)~~ ✅ fixed | `wear/build.gradle` | Same ID as the phone |
| `targetSdk 34`. Play requires new apps and updates to target a recent API (35 since Aug 2025; the yearly bump to 36 was due Aug 2026) | all modules | Raise to the current requirement (§3) |
| AGP 8.1.4 / Kotlin 1.9.10 can't compile against API 35/36 | root `build.gradle` | Toolchain upgrade (§3) |
| Phone and watch both use `versionCode 24` | both | Every uploaded artifact needs a unique versionCode (§4) |
| No release signing config | both | Upload keystore (§4) |
| ~~`local.properties` tracked in git~~ ✅ fixed | repo | Untracked in `491d09b` |
| `jcenter()` in `settings.gradle` | settings | Remove (shut down) |
| Watch uses a `specialUse` foreground service | `wear/AndroidManifest.xml` | Needs a Foreground Service declaration in Play Console (§5) |

Keep the `namespace` values (`com.example.boombee`, …). They're only the
Kotlin/R package and don't need to match the applicationId. Changing only
`applicationId` avoids touching every import.

---

## §2 Package name

- [x] **(you)** Pick the ID: **`com.filipgolovic.boombee`** (2026-09-26).
      It is permanent.
- [x] `applicationId "com.filipgolovic.boombee"` in `app/build.gradle`
      **and** `wear/build.gradle`. The watch no longer uses the `.wear`
      suffix.
- [ ] Update `ExampleInstrumentedTest`, which still asserts
      `com.example.boombee`.
- [ ] Uninstall the old `com.example.*` builds from the phone and watch.
      The new ID installs as a separate app, and old local history isn't
      carried over, which is fine before release.

## §3 Toolchain + target SDK

- [ ] Check the current requirement: Play Console → Policy → *Target API
      level requirements*. Plan for **API 36**.
- [x] Use Android Studio's **AGP Upgrade Assistant** to move AGP and the
      Gradle wrapper to a version that supports that compileSdk. Kotlin
      goes to 2.x.
- [x] With Kotlin 2.x, Compose (`:wear`) switches from
      `composeOptions.kotlinCompilerExtensionVersion` to the
      `org.jetbrains.kotlin.plugin.compose` Gradle plugin.
- [ ] Raise `compileSdk` / `targetSdk` in `app`, `wear` and `core`
      (`core` only needs compileSdk).
- [ ] Bump the Compose BOM / Wear Compose to versions built for that SDK.
- [ ] **Behaviour changes to test on the phone:**
  - Android 15 (API 35) forces **edge-to-edge**. Every fragment layout
    can draw under the status and navigation bars. Apply window insets on
    `First`, `Second`, `Rest`, `Settings`, `HistoryTraining`, `Survey` and
    the new tutorial.
  - Android 16 (API 36): predictive back is on by default. Check that the
    back gesture during a running round doesn't silently kill the timer.
- [ ] Watch: re-run Phase 6 checks from `WEAR_OS_IMPLEMENTATION.md`
      (foreground service survives screen-off, ambient, ongoing activity).

## §4 Versioning + signing

**Versioning.** The version name currently lives in three places
(`app/build.gradle`, `wear/build.gradle`, `AppVersion.kt`). Move it to one:

- [ ] `gradle.properties`: `boombeeVersionName=3.0.0`, `boombeeVersionCode=30000`.
- [ ] Phone `versionCode = boombeeVersionCode`; watch
      `versionCode = 1_000_000 + boombeeVersionCode` (unique, and still
      ordered).
- [ ] Generate `AppVersion.VERSION` from `BuildConfig`/`buildConfigField`
      instead of hand-editing it.
- 1.0 on the store = versionName `3.0.0` (continuing from 2.9.6) or reset
  to `1.0.0`. Either works; users only ever see the store build.

**Signing**
- [ ] **(you)** Create an upload keystore once:
      `keytool -genkeypair -v -keystore ~/keys/boombee-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`
- [ ] Store it **outside the repo**, and back up the file and password to
      a password manager. Play App Signing holds the real app signing key;
      a lost upload key can be reset through Play support, but it takes
      days.
- [ ] `keystore.properties` (gitignored) with path, alias and passwords;
      `signingConfigs.release` in both modules reads it.
- [ ] `./gradlew :app:bundleRelease :wear:bundleRelease` produces two
      signed AABs.
- R8 minification: leave `minifyEnabled false` for 1.0 (less risk). Turn
  it on in a later release.

## §5 Play Console setup (you + Claude)

- [ ] **(you)** Developer account ($25, identity verification). Note the
      account type (see the roadmap).
- [ ] **(you)** Create app: name "BoomBee", app (not game), free.
- [ ] **(you)** Advanced settings → **Form factors → add Wear OS**. Watch
      builds then go to the Wear OS tracks, and Wear OS is reviewed
      separately against the Wear app quality guidelines. It's stricter,
      so allow extra days.
- [ ] Upload the first phone AAB **manually** in the console. The API
      can't create the first release of a new app.
- [ ] **Closed testing** track: create a tester list (Google Group or
      emails), add 12+ testers, and share the opt-in link. Testers must
      stay opted in for 14 days (personal accounts only).
- [ ] App content forms. Claude drafts the answers and you submit them:
  - Privacy policy URL (drafted from `RELEASE_ANALYTICS.md` and
    `RELEASE_SURVEY.md` data lists)
  - **Data safety**: app activity (analytics), app info and performance
    (crash logs), user-generated content (survey text), rating. No data
    sold. Encrypted in transit. No account, so no deletion flow is
    needed, but describe how to request deletion by email.
  - Ads: **no ads**
  - Content rating (IARC questionnaire): no violence toward people
    beyond sport instruction; expect Everyone / PEGI 3–7
  - Target audience: **13+ or 18+**. Avoid under-13, which brings in the
    Families policy.
  - Health apps declaration: fitness / activity category, no medical
    claims
  - **Foreground service declaration** for the watch's `specialUse`
    service: describe it as a boxing round timer that must keep running
    with the screen off. Play may ask for a short screen-recording video
    of the feature.
  - Advertising ID: declare "no" and remove the permission Firebase
    merges in (see `RELEASE_ANALYTICS.md`)

## §6 Store listing

- [ ] Short description (≤ 80 chars), full description (≤ 4000). Claude
      drafts them.
- [ ] App icon 512×512 PNG; feature graphic 1024×500.
- [ ] Phone screenshots: 2–8 (home, running round, coach mode, history,
      tutorial).
- [ ] Wear OS screenshots: at least 1, square 1:1, ≥ 384×384, the UI only
      with no device frame. Take them from the emulator for clean
      captures.
- [ ] Keep all listing text in the repo under `app/src/main/play/` (§7) so
      it's versioned and can be uploaded by command.

## §7 Publishing automation (last stage)

Goal: you say *"build and release 3.0.1 to internal"* and Claude does it
end to end, then waits for your OK before production.

### Recommended backbone: Gradle Play Publisher (GPP)

GPP is a Gradle plugin (`com.github.triplet.play`) that talks to the
official Google Play Developer API. Claude can run it from the terminal
like any other Gradle task, so no extra MCP is needed for it to work.

- [ ] **(you)** Google Cloud console → new project → enable the **Google
      Play Android Developer API**.
- [ ] **(you)** Create a **service account** → JSON key → save it to
      `~/keys/boombee-play.json` (outside the repo, never committed).
- [ ] **(you)** Play Console → Users and permissions → invite the
      service-account email → grant for the BoomBee app only:
      *release to testing tracks*, *manage store listing*. Leave
      **production release** off to begin with, so production pushes stay
      a manual click. You can grant it later.
- [ ] Apply GPP to `app` and `wear`, `serviceAccountCredentials` pointing
      at the key, `defaultToAppBundles = true`, wear with
      `track = "wear:internal"`-style Wear OS tracks.
- [ ] Put listing + release notes in `src/main/play/`
      (`release-notes/en-US/default.txt`, `listings/en-US/…`).
- [ ] Try it end to end: `./gradlew publishBundle --track internal`, then
      `promoteArtifact --from-track internal --promote-track production --release-status inProgress --user-fraction 0.2`.
- [ ] `updatePriority` (0–5) can be set per release **only through the
      API**. This drives the auto-update behaviour in
      `RELEASE_AUTO_UPDATE.md`.

### Optional: Google Play MCP server

An MCP server lets Claude call Play actions as tools (list tracks, read
review and vitals data, promote releases) without Gradle. When we reach
this stage:

- [ ] Check whether Google publishes an official Play Developer MCP server
      by then. If not, pick a maintained open-source one that wraps the
      same Android Publisher API.
- [ ] **Read its source before giving it the key.** It receives the same
      service-account credentials, which have publishing rights.
- [ ] `claude mcp add …` with the JSON key path via env var, project scope.
- The main reason to add it is **reading**: new reviews, crash and ANR
  rates, rollout status. GPP stays the tool for building and uploading.

### Release checklist Claude follows once this is set up

1. Clean working tree, on `master`, and CI/`./gradlew test` green.
2. Bump `boombeeVersionName` / `boombeeVersionCode`; write release notes.
3. `bundleRelease` for both modules.
4. `publishBundle` to **internal** (phone + Wear tracks).
5. You install it from the internal track and confirm.
6. On your explicit "go": promote to production at 20%, then raise the
   rollout in later steps.
7. Tag `vX.Y.Z` and commit.
