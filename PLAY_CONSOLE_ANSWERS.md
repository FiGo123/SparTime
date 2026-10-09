# BoomBee — Play Console answers (draft)

Copy-paste answers for Play Console → **Policy and programs → App content**
and the store listing. Part of `RELEASE_PLAY_STORE.md` §5–§6. Re-check each
form's wording when you fill it in; Google changes them.

Listing text lives in `app/src/main/play/listings/en-US/` (title, short and
full description), release notes in `app/src/main/play/release-notes/`.

---

## Create app
- App name: **BoomBee: Boxing Timer & Coach** (≤ 30 chars)
- Default language: English (United States)
- App or game: **App** · Free or paid: **Free**
- Declarations: Developer Program Policies ✔, US export laws ✔

## Privacy policy
- URL: **https://boombee.web.app/privacy.html** (source: `hosting/privacy.html`,
  deployed with Firebase Hosting)

## App access
- **All functionality is available without special access** (no login).

## Ads
- **No**, the app does not contain ads.

## Content rating (IARC questionnaire)
- Category: **All Other App Types** (fitness / utility)
- Violence: **No** (sport technique instruction, no depictions of violence
  against people)
- Sexuality, language, controlled substances, gambling: **No**
- User interaction: users can't chat or share content with each other → **No**
- Shares location: **No** · Digital purchases: **No**
- Expected result: Everyone / PEGI 3

## Target audience and content
- Age groups: **18 and over** (keeps the app out of the Families and
  teen-specific requirements; anyone can still install it)
- Appeals to children: **No**

## News app
- **No**

## Health apps
- Category: **Fitness** (activity / workout tracking). No medical features,
  no health data from sensors.

## Government app / Financial features
- **No** / **None**

## Foreground service permissions (Wear OS build)
- Type: **Special use** (`FOREGROUND_SERVICE_SPECIAL_USE`)
- Description: *"BoomBee's watch app is a boxing round timer. During a
  workout a foreground service counts down rounds and rest periods and
  plays the round bell, 10-second warning and voice coach calls, with a
  vibration at each change. It has to keep running while the watch screen
  is off or the user lowers their wrist, otherwise the timer would stop
  mid-round. The service starts only when the user taps Start and stops
  when the workout ends or they tap Stop."*
- Impact if deferred/interrupted: the workout timer stops and the user
  misses round/rest changes.
- Video: record the watch: tap Start → screen turns off → bell/vibration
  still arrive at the round change → ongoing activity on the watch face.

## Advertising ID
- Does the app use an advertising ID? **No** (the `AD_ID` and ad-services
  permissions are removed in `app/src/main/AndroidManifest.xml`).

## Data safety
**Overview**
- Does the app collect or share user data? **Yes** (collects; does not share)
- Is all collected data encrypted in transit? **Yes**
- Do you provide a way for users to request data deletion? **Yes**, by email
  (privacy policy → "Your choices and rights")
- Account creation: **No accounts**

**Data types collected** (none are *shared*; none are required for the app
to work → mark optional where asked)

| Type | Collected | Optional? | Purposes | Processed ephemerally? |
|---|---|---|---|---|
| App activity → App interactions | Yes | Optional (opt-in) | Analytics | No |
| App activity → Other user-generated content (survey comment) | Yes | Optional | App functionality (feedback), Analytics | No |
| App activity → Other actions (survey star rating) | Yes | Optional | Analytics | No |
| App info and performance → Crash logs | Yes | Optional (opt-in) | Analytics | No |
| App info and performance → Diagnostics | Yes | Optional (opt-in) | Analytics | No |
| Device or other IDs (Firebase installation ID) | Yes | Optional (opt-in) | Analytics | No |

Not collected: location, personal info, financial info, health & fitness
data (training history stays on the device), messages, photos, audio,
files, calendar, contacts, web history.

## Store listing assets (still to make)
- App icon 512 × 512 PNG
- Feature graphic 1024 × 500
- Phone screenshots (2–8): home, running round with coach, tutorial punch
  page, combo game, history
- Wear OS screenshots (≥ 1, square, UI only): setup, timer, combo game
- App category: **Health & Fitness** · Tags: boxing, workout timer
- Contact email: the support address (also in the privacy policy)
