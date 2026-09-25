# BoomBee — Auto-update (Play In-App Updates)

Part of `RELEASE_ROADMAP.md` (Stage 3).

## What "auto-update" means here

- Play already auto-updates apps in the background for most users. That
  needs no code.
- **In-App Updates** (`com.google.android.play:app-update`) adds an
  in-app prompt for people who have auto-update off or haven't reached a
  Wi-Fi charging window yet. This doc is about that prompt.
- **Wear OS:** the In-App Updates API isn't supported on watches. The
  watch app updates through the Play Store on the watch, which is fine.

## Current state (`app/.../MainActivity.kt`)

The dependency and a check already exist, but:
- It forces **IMMEDIATE** (a full-screen, blocking update) for *every*
  update, even a typo fix. That's annoying and makes people uninstall.
- It uses the deprecated
  `startUpdateFlowForResult(info, type, activity, 123)`. The
  `updateLauncher` it registers is never used.
- It shows "Update failed!" when the user just tapped *Not now*.
- There's no `onResume` handling, so an interrupted immediate update isn't
  resumed.
- `app-update-ktx` is redundant with 2.1.0.

## Target behaviour (simplest good approach)

| Situation | Behaviour |
|---|---|
| Normal update (priority 0–3) | **Flexible**: downloads in the background while they train. When finished, the home screen shows a Snackbar **"Update ready · Restart"** → `completeUpdate()` |
| Critical update (priority 4–5, set when publishing) | **Immediate**: blocking update screen |
| Flexible update ignored for ≥ 7 days | Upgrade to immediate |
| During a round / rest | **Never** prompt or restart. Only surface it on the home screen (`First`) |

Priority can only be set through the Play Developer API (Gradle Play
Publisher `updatePriority`, see `RELEASE_PLAY_STORE.md` §7), not in the
console UI. Without automation every release is priority 0, which means
flexible, which is the right default anyway.

## Steps
- [ ] Move update logic out of `MainActivity` into a small
      `UpdateController` (lifecycle-aware) in `:app`.
- [ ] Use `AppUpdateOptions.newBuilder(type)` with the registered
      `ActivityResultLauncher<IntentSenderRequest>`.
- [ ] Choose FLEXIBLE or IMMEDIATE from `updatePriority()` and
      `clientVersionStalenessDays()`.
- [ ] `InstallStateUpdatedListener` → on `DOWNLOADED` publish a "ready"
      state; `First` observes it and shows the Snackbar.
- [ ] `onResume`: if `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS` → resume
      the immediate flow; if `DOWNLOADED` → show the Snackbar again.
- [ ] Unregister the listener in `onDestroy`.
- [ ] Remove the Toasts on cancel and failure; log `update_prompt` /
      `update_accepted` analytics events instead.
- [ ] Drop `app-update-ktx`.

## Testing (the only reliable way)
The API only returns updates for **Play-installed** builds.
- [ ] Unit logic: `FakeAppUpdateManager`.
- [ ] Real: **Internal App Sharing**. Upload build N and install it from
      the link, then upload N+1 (higher versionCode) and open the link
      without installing. The installed N now sees an update. Test both
      flexible and immediate (immediate can be forced by a debug-only
      flag).
- [ ] Also check it on the internal testing track once §7 automation
      exists.
