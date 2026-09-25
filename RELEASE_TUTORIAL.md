# BoomBee — Tutorial + "Tap the Combo" Mini-game

Part of `RELEASE_ROADMAP.md` (Stage 5), the biggest feature for 1.0.

## Goal

A new user who has never boxed should understand what the coach means by
"1-1-2", "3", "Slip" or "Close distance" before their first coached round.
Show it once automatically and let them replay it any time.

## Flow

```
[Start training] pressed
   └─ tutorial_seen == false ?  ──yes──►  Tutorial  ──(Finish or Skip)──►  training starts
                                 no ───►  training starts
Home screen "?" / "Learn the punches"  ──► Tutorial ──► back to home
Settings → "Tutorial"                  ──► Tutorial ──► back to Settings
```

- `tutorial_seen` is stored in `PreferencesProvider` and set on **Finish
  or Skip**. Skipping still counts as seen, so it's never forced twice.
- When it opens from Start, finishing continues straight into the training
  they asked for, with no extra tap.
- Every page has **Back**, **Next** and **Skip** (top-right), plus page
  dots. Swiping also works.

## Pages

| # | Page | Content | Audio |
|---|------|---------|-------|
| 1 | Welcome | "Your coach calls punches by number. 2 minutes to learn them." | – |
| 2 | Stance & guard | Orthodox stance: left foot forward, hands up by the cheeks. One line on southpaw: "Left-handed? Mirror everything." | `phrase_hands_up` |
| 3 | **1 · Jab** | Lead (left) straight. Illustration + one cue: "fast, snap it back" | `number_1`, `word_1` |
| 4 | **2 · Cross** | Rear (right) straight, rotate the hip | `number_2`, `word_2` |
| 5 | **3 · Lead hook** | Left hook, elbow at shoulder height | `number_3`, `word_3` |
| 6 | **4 · Rear hook** | Right hook | `number_4`, `word_4` |
| 7 | **5 · Lead uppercut** | Left uppercut | `number_5`, `word_5` |
| 8 | **6 · Rear uppercut** | Right uppercut | `number_6`, `word_6` |
| 9 | Summary card | All 6 on one fighter figure: odd = left hand, even = right hand. This is the key idea to remember | – |
| 10 | Defense | Slip, Duck under, Hands up (one small image or icon each) | `phrase_slip`, `phrase_duck_under`, `phrase_hands_up` |
| 11 | Distance | Close distance, Make distance, Move more | `phrase_close_distance`, … |
| 12 | How combos sound | "1-2" = jab then cross. Numbers vs Words voice style (matches the setting) | plays a combo via `ClipPlayer` |
| 13 | **Mini-game** | see below | |
| 14 | Done | "You're ready. Coach mode recommended: Beginner." → **Start** / **Finish** | – |

Every punch page has a 🔊 button that plays the recorded clip. All the
clips already exist in `core/src/main/assets/audio/`, so nothing new
needs recording. The speaker plays the number or word depending on the
user's voice-style setting.

## Mini-game: "Tap the combo"

**Layout:** a front-facing fighter silhouette with 6 big tap targets
placed where the punches land. The left-hand side has 1 (jab), 3 (hook)
and 5 (uppercut); the right-hand side has 2, 4 and 6. That teaches the
odd = left, even = right pattern.

**Round:** the coach calls a combo, and the user taps the punches in
order.
- Correct tap → the target flashes yellow, with a short haptic.
- Wrong tap → it flashes red, the correct one pulses, and the combo
  replays.
- Combo finished → ✓ and the next combo.

**Progression (8 combos, about 60–90 s):**

| Level | Shows | Combos (picked from `coach_combos_general.txt`) |
|---|---|---|
| 1 (×3) | Text + voice | 1-2, 1-1-2, 1-3 |
| 2 (×3) | Voice only | 1-2-3, 2-3-2, 1-6-3-2 |
| 3 (×2) | Voice only, faster | random 3–4 punch combos from `ComboLibrary` |

- The end screen shows a score ("7/8 🐝") + **Play again** / **Continue**.
- "Skip game" is always visible. The game is optional and never blocks.
- Reuse `ComboLibrary.combosOfLength()` and `ClipPlayer.playCommand(keys, isCombo = true)`.
  It's the same timing as the real coach, so the game sounds exactly like
  training.
- Filter out defense tokens (`slip`, `duck`) from game combos in v1, or
  map them to a "↓ duck / ↔ slip" button row in v2.

## Artwork

**"Like in pictures"** means one illustration per punch in a consistent
style: the fighter shown from the front or at ¾ view, with the punching
arm highlighted in bee-yellow and a motion arc. That's about 10 images
(stance, 6 punches, slip, duck, distance) plus 1 mini-game silhouette.

| Option | Cost / time | Notes |
|---|---|---|
| **A. Placeholder vectors first** (recommended to start) | Built by Claude with the code, as VectorDrawables | A simple silhouette + a highlighted arm + an arrow. Unblocks all the code; good enough for closed testing |
| B. AI-generated images, cleaned up | Hours, free to cheap | Consistency between images is hard. Needs a fixed prompt and style; export WebP |
| C. Commission an illustrator (e.g. Fiverr) | ~1 week, modest fee | Best quality and consistency. Give them the page table above + brand colours |

Plan: build with **A**, get **B or C** started in parallel on day 1 of the
roadmap, and swap the files in (same resource names) before production.
If you have reference pictures of the look you want, drop them in
`design/tutorial-refs/` and they'll guide both the placeholders and the
final art.

Specs: WebP, square, transparent background, ~1024 px source → exported
at `drawable-nodpi` or 2–3 densities. It should read well on both dark
and light surfaces.

## Implementation

- [ ] `TutorialFragment` hosting a `ViewPager2` + `TabLayout` dots, in
      `nav_graph.xml` with actions from `first` and `settings`, and an
      argument `source` = `first_run` | `menu`.
- [ ] Pages are data-driven: `TutorialPage(titleRes, bodyRes, imageRes, clipKeys)`
      rendered by one generic page layout. Only the mini-game and the
      summary have custom layouts.
- [ ] `ComboGameFragment` (or a custom page view) with the 6 targets. Game
      logic lives in a plain `ComboGame` class (combo queue, expected
      index, score) so it can be unit-tested without UI.
- [ ] One `ClipPlayer` owned by the tutorial and stopped when a page
      changes or the tutorial closes. Never let audio overlap across pages.
- [ ] `First`: Start checks `tutorial_seen`; add a "?" icon button next to
      the header title → tutorial (`source = menu`).
- [ ] `Settings`: "Tutorial" row → tutorial.
- [ ] Respect edge-to-edge insets (see `RELEASE_PLAY_STORE.md` §3) and
      test on a small phone.
- [ ] Analytics: `tutorial_begin(source)`, `tutorial_skip(page)`,
      `tutorial_complete(source)`, `combo_game_result(correct, total)`.
- [ ] Unit tests for `ComboGame` (correct tap, wrong tap → replay, score).
- [ ] All strings in `strings.xml`, ready for later translation.

## Watch

Not in 1.0. The watch keeps working on its own, and a watch-only user
learns from the phone app or the Play listing screenshots. Possible later
addition: a 3-card "punch numbers" cheat sheet on the watch Setup screen.
