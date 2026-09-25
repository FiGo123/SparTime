# BoomBee — Google Analytics (Firebase) + Crashlytics

Part of `RELEASE_ROADMAP.md` (Stage 2). Needs the final applicationId
from Stage 1.

## Approach

"Google Analytics" for an Android app means **Google Analytics for
Firebase**, which is GA4. It's the simplest option: one Gradle plugin, a
config file, and automatic sessions, first_open, retention, devices and
countries. Custom events are one-liners. **Crashlytics** comes from the
same Firebase project for almost no extra work, and crash reports are the
most useful data to have for a first release.

The same Firebase project is reused by the survey (Firestore). That's why
this stage comes first.

## Steps

### Firebase project (you)
- [ ] console.firebase.google.com → Add project "BoomBee" → **enable
      Google Analytics** (creates the GA4 property).
- [ ] Add an Android app with the new applicationId → download
      `google-services.json` into `app/`. The watch has the same
      applicationId, so a copy goes in `wear/` when watch analytics is
      added.
- `google-services.json` isn't secret (it only identifies the project),
  so committing it is normal.

### Gradle
- [ ] Root: `com.google.gms.google-services` and
      `com.google.firebase.crashlytics` plugins.
- [ ] `app`: apply both plugins;
      `implementation platform('com.google.firebase:firebase-bom:<latest>')`,
      `firebase-analytics`, `firebase-crashlytics`. BoM 34+ dropped the
      `-ktx` artifacts, so use the plain names.

### Screen tracking
The phone is a single Activity with Navigation fragments, so automatic
screen tracking only sees `MainActivity`. Add one listener:

- [ ] `navController.addOnDestinationChangedListener` →
      `logEvent(SCREEN_VIEW, screen_name = destination.label)`.
      Also rename the `android:label`s in `nav_graph.xml`
      (`fragment_first` → `home`, `fragment_second` → `round`, …) so the
      reports are readable.

### Events (one small `Analytics` object in `:app`)

| Event | Params | When |
|---|---|---|
| `training_start` | `mode` (timer/coach), `difficulty`, `voice_style`, `rounds`, `round_min`, `warmup` | Start pressed |
| `training_complete` | same + `duration_sec` | Workout-complete |
| `training_abort` | `round_reached` | Stop pressed mid-session |
| `tutorial_begin` / `tutorial_complete` | `source` (first_run / menu) | GA4 recommended events |
| `tutorial_skip` | `page` | Skip pressed |
| `combo_game_result` | `correct`, `total` | Mini-game end |
| `survey_shown` / `survey_submit` / `survey_dismiss` | `rating` | Survey |
| `settings_change` | `setting`, `value` | Save in Settings |

User properties (≤ 25): `training_mode`, `difficulty`, `voice_style`,
`has_watch_app` (later).

- Watch analytics: skip for 1.0 and add the same events to `:wear` in a
  later release. Watch sessions still show up through Play Console
  statistics.

### Consent / privacy (keep it simple, no ads)
- [ ] Manifest meta-data in `app`:
  - `google_analytics_default_allow_ad_storage`,
    `…_ad_user_data`, `…_ad_personalization_signals` = **false**
    (no ads, ever)
  - `google_analytics_adid_collection_enabled` = **false**
  - Remove the merged `com.google.android.gms.permission.AD_ID`
    permission (`tools:node="remove"`) so you can declare "no Advertising
    ID" in Play.
- [ ] Analytics storage: if you distribute in the EU/EEA/UK, ask once on
      first launch with a short "Share anonymous usage data to improve
      BoomBee?" prompt (Yes / No), then call `setConsent(ANALYTICS_STORAGE, …)`.
      Default to denied until they answer.
- [ ] Settings → toggle **"Share anonymous usage data"**. It controls
      `setAnalyticsCollectionEnabled` and Crashlytics collection.
- [ ] Never log free text (the survey comment) or anything personal to
      Analytics.

### Verify
- [ ] `adb shell setprop debug.firebase.analytics.app <applicationId>` →
      Firebase console → **DebugView** shows the events live.
- [ ] Force a test crash in a debug-only menu item → it appears in
      Crashlytics.
- [ ] Standard reports take up to 24 hours to fill in. That's normal.

## Data safety mapping (for `RELEASE_PLAY_STORE.md` §5)
Collected: app interactions, crash logs, diagnostics, device IDs
(Firebase installation ID). Purpose: analytics, app functionality. Not
shared with third parties for their own use, not sold, encrypted in
transit, and users can opt out in Settings.
