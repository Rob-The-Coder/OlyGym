# The OlyGym exercise catalogue

`frontend/src/lib/exercises-data.js` is **generated**, not written by hand: 624 Olympic-weightlifting
exercises imported from [Catalyst Athletics](https://www.catalystathletics.com/exercises/). This
directory holds everything it is built from.

```bash
node scripts/oly-catalogue/build-oly-catalogue.mjs           # rebuild the catalogue
node scripts/oly-catalogue/build-oly-catalogue.mjs --check   # fail if it is out of date (CI runs this)
```

## Inputs

| File | What it is | How it changes |
|---|---|---|
| `catalyst-exercises.csv` | The Catalyst exercise list: name, category, YouTube URL, Catalyst page URL, description. | Re-scraping. Upstream data — never edited by hand. |
| `exercise-tags.json` | This fork's curated metadata, keyed by catalogue id: `eq` (equipment) and `tg`/`sm` (muscle tags). | By hand, deliberately. |

`eq` is not cosmetic: it decides whether the app asks for a weight at all (a `body weight` movement
is reps-only by default), which bar weight the per-side plate maths starts from (`barbell` 20 kg,
`ez barbell` 10 kg, `smith machine` 9 kg, `trap bar` 25 kg — see `frontend/src/lib/bar.js`), and
which equipment chips the exercise appears under. `tg`/`sm` drive the muscle map and the recovery
model; an exercise with neither falls back to its movement family in `muscles.js` (`BY_BODYPART`).

## Media

An entry carries no `img` and no `gif`: `yt` is the only picture source, and the app hotlinks the
video's poster frame from `img.youtube.com` at runtime (`frontend/src/lib/media.js`, sizes and the
fallback chain live there). Nothing is downloaded, committed or bundled — the terms are in
`NOTICE.md`.

## What the build guarantees

- The id is `wl` + the Catalyst exercise-page id parsed from the URL, so every entry stays traceable
  to the page it came from (`src`) and ids never shift under a saved workout.
- Entries are written in the Catalyst **section order** (Snatch → Clean → Jerk → General Exercises →
  Trunk → Jumping & Plyometrics → Accessory — Lower → Accessory — Prep → Accessory — Upper →
  Carries) and, inside a family, in the CSV's own order.
- The build fails on: a CSV row whose URL has no exercise id, two rows sharing an id, a row with no
  entry in `exercise-tags.json`, and an unknown category (which would need a `BY_BODYPART` fallback).
  An id in the sidecar that is no longer in the CSV is a warning, not a failure.
- `--check` rebuilds in memory and compares byte for byte, so a stale committed catalogue fails CI
  with the first differing line rather than shipping quietly.

## Adding or fixing an exercise

1. Re-scrape the CSV if the exercise list itself changed.
2. Add or edit the entry in `exercise-tags.json` (that is where the equipment and muscle tags live).
3. `node scripts/oly-catalogue/build-oly-catalogue.mjs`, then `npm test` in `frontend/`.

Two examples of why the sidecar exists, both from the OlyGym swap: push-ups, pull-ups, chin-ups and
dips arrived tagged `barbell` (so the app asked for a weight and started the plate maths from a 20 kg
bar) and the EZ-bar movements were `barbell` too (20 kg instead of 10). Both are one-line edits here.

## After the catalogue changes

`api/coach/core/library-data.js` is a second, deliberately separate copy of the catalogue for the
Coach server, which has no shared build step with the frontend. Regenerate it too:

```bash
node scripts/build-coach-assets.mjs            # writes api/coach/core/library-data.js
node scripts/build-coach-assets.mjs --check    # CI runs this as well
```
