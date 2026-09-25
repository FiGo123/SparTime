# BoomBee Voice Recording — Tier 1 Script

Read each line below out loud, pausing about 1 second of silence between
lines (that pause is what Audacity's Silence Finder uses to split the
recording into separate files). Record the whole thing as **one
continuous take** in Audacity (File > New Project, then the red Record
button) — don't stop and restart per line.

Read it the way you'd actually coach: punchy, energetic, consistent
volume and mic distance throughout.

## 1. Numbers (Numbers voice style)
1. One
2. Two
3. Three
4. Four
5. Five
6. Six

## 2. Punch names (Words voice style)
7. Jab
8. Cross
9. Left hook
10. Right hook
11. Left uppercut
12. Right uppercut

## 3. Tactical phrases
13. Hands up
14. Move more
15. Make distance
16. Close distance
17. Slip
18. Duck under

## 4. Freestyle lines
19. Now go freestyle, max power and speed
20. Pick up the speed
21. Power now

## 5. Session moments
22. Warm up starting now
23. Round
24. Rest
25. Workout complete, well done

---

## After recording: splitting in Audacity

1. **Effect > Silence Finder** (or Analyze > Silence Finder in some
   versions) on the whole track — creates a Label Track with a region per
   line, split at your pauses.
2. Check the split points look right (zoom in on the label track if a
   pause got missed or a line got split in two) — drag label edges to fix.
3. **Rename each label** to match the filename it should become (double-
   click the label text), in order:
   `number_1`, `number_2`, `number_3`, `number_4`, `number_5`, `number_6`,
   `word_1`, `word_2`, `word_3`, `word_4`, `word_5`, `word_6`,
   `phrase_hands_up`, `phrase_move_more`, `phrase_make_distance`,
   `phrase_close_distance`, `phrase_slip`, `phrase_duck_under`,
   `phrase_freestyle_start`, `phrase_pick_up_speed`, `phrase_go_power`,
   `phrase_warmup_start`,
   `misc_round`, `misc_rest`, `misc_workout_complete`
4. Select all labels, **File > Export > Export Multiple** — split by
   labels, "Use Label/Track Name", format MP3 or WAV, export to a folder.
5. Optional but recommended: **Effect > Normalize** on the whole track
   *before* splitting, so every clip ends up at a consistent volume.

Drop the exported files straight into
`core/src/main/assets/audio/` in the project (create that folder) —
the app will pick up whichever ones exist automatically and use your
voice for those, falling back to the built-in TTS for anything not
recorded yet. No code changes needed to add more later.

## Watch out for: sentences with a comma

A phrase with an internal comma — "Now go freestyle**,** max power and
speed", "Workout complete**,** well done" — often gets a real pause at
that comma. If that pause is as long as your between-phrase pauses,
Silence Finder will split it into *two* labels instead of one. Keep both
halves in **one** label/file rather than exporting them separately — a
tool splitting on silence has no idea where a sentence is "supposed" to
end, it only sees gaps.

## Watch out for: trimming leading silence

If there's dead air before you start reading (getting set up, waiting to
hit record), it's tempting to just trim off "the first N seconds." Do
this carefully — trimming a fraction of a second too far *into* your
first word instead of stopping just before it will quietly eat that
word, and shift every phrase after it into the wrong label. When in
doubt, trim conservatively short of where you think speech starts and
let Silence Finder find the real boundary from there, rather than
guessing the cut point precisely yourself.
