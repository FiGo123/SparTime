# BoomBee — In-app Feedback Survey

Part of `RELEASE_ROADMAP.md` (Stage 4). Needs the Firebase project from
Stage 2.

## What the user sees

A short card or bottom sheet:

> **How's BoomBee working for you?**
> ☆ ☆ ☆ ☆ ☆  *(tap 1–5, required)*
> *What should we improve?* *(optional, multi-line, max 1000 chars)*
> **[Send]**  *Not now* · *Don't ask again*

After sending: "Thanks! 🐝" and it closes. No account, no email needed.

## Backend: Firebase Firestore (recommended) vs email

| | Firestore | Email (`mailto:` intent) |
|---|---|---|
| User effort | Tap Send, done | Their mail app opens with subject/body pre-filled, then they must press Send again |
| Does the user need your address? | No | No: it's pre-filled, but they send *from their own* address |
| Works without a mail app / offline | Yes (Firestore queues offline writes and sends later) | No |
| Completion rate | High | Low (many drop off in the mail app) |
| You read results | Firestore console / export to a spreadsheet | Your inbox |
| Cost | Free tier (thousands of writes per day) | Free |
| Can reply to the user | No | Yes |

**Recommendation:** Firestore for the survey. Also add a separate
**"Contact us"** row in Settings that opens `mailto:<support email>`
for people who want a reply. That covers both needs, and the survey
itself never asks for an email.

Optional later: the Firebase "Trigger Email" extension can email you each
new survey. It needs the paid Blaze plan, so skip it for 1.0.

## When it shows up (occasional, never annoying)

Store the state in the existing `PreferencesProvider`:
`survey_completed_trainings`, `survey_last_shown_at`, `survey_times_shown`,
`survey_submitted`, `survey_never`.

Show it on the **home screen after a completed training** (never
mid-round, never after an aborted session) only if all of these are true:
- ≥ 3 completed trainings in total
- ≥ 14 days since it was last shown
- shown fewer than 3 times in total
- not submitted for this version (`survey_submitted_version` ≠ current)
  and not "Don't ask again"

It's always available from **Settings → "Rate & feedback"**, which
ignores the rules above.

## Play review prompt: keep it separate

Google's In-App Review policy **forbids** asking "do you like the app?"
and then only sending happy users to the Play review dialog. So:
- Don't open the Play review dialog based on the star rating.
- Optionally, call the **In-App Review API** separately (e.g. after the 5th
  completed training, at most once per version). Play decides whether it
  actually appears.

## Firestore data

Collection `feedback`, one document per submission (auto-ID):

```
rating: int 1–5
comment: string (optional, ≤ 1000)
appVersion: "3.0.0"
platform: "phone"
androidSdk: 35
locale: "hr-HR"
completedTrainings: 7
createdAt: serverTimestamp
```
No name, no email, no device ID. The comment is free text, so the privacy
policy states that it's stored for product improvement and deleted on
request.

Security rules (**create-only**, validated; nobody can read or overwrite
submissions from the app):

```
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {
    match /feedback/{id} {
      allow create: if request.resource.data.keys().hasOnly(
            ['rating','comment','appVersion','platform','androidSdk',
             'locale','completedTrainings','createdAt'])
        && request.resource.data.rating is int
        && request.resource.data.rating >= 1
        && request.resource.data.rating <= 5
        && (!('comment' in request.resource.data)
            || (request.resource.data.comment is string
                && request.resource.data.comment.size() <= 1000))
        && request.resource.data.createdAt == request.time;
      allow read, update, delete: if false;
    }
  }
}
```
Optional hardening later: Firebase **App Check** (Play Integrity) so only
the real app can write.

## Steps
- [ ] **(you)** Firebase console → Firestore → create database (production
      mode, EU region if most users are in Europe).
- [ ] Deploy the rules above (console editor, or `firebase deploy` if we
      add `firestore.rules` to the repo, which is preferable).
- [ ] `firebase-firestore` dependency (via the BoM from Stage 2).
- [ ] Replace the stub `Survey.kt` / `fragment_survey.xml` (currently an
      unrelated "intensity 1–10" form) with the rating UI. Use Material
      `RatingBar` restyled in bee-yellow, `TextInputLayout` with a
      counter, and Send disabled until a star is picked.
- [ ] `SurveyRepository` (in `:app`): `submit()` → Firestore `add()`.
      Treat success as local (the write is queued offline), so there's no
      spinner.
- [ ] `SurveyTrigger`: the rules above, called by `First` when it
      resumes after a completed training.
- [ ] Settings: "Rate & feedback" + "Contact us" rows.
- [ ] Analytics: `survey_shown`, `survey_submit(rating)`,
      `survey_dismiss(reason)`.
- [ ] Test: submit on airplane mode → reconnect → the document appears.

## Watch
Not in 1.0. The watch is standalone and a text box on a watch is poor UX.
The phone survey covers users who have both.
