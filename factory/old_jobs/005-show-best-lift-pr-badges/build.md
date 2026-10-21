# Build — 005-show-best-lift-pr-badges

Commit: `3947ef7` — Show the best-lift PRs on a History row, not a count
Branch: `factory/005-show-best-lift-pr-badges` (created from `main` at `d64c4fe`)

## Files changed

- `frontend/src/lib/history.js` — new pure helper `prLifts(workout)`: maps the stored `w.prs`
  exercise ids to an ordered array of `{ id, weight }`. The weight is the session's own best for
  that entry via the existing `bestWeightForEntry` (so warm-ups and unchecked rows stay out);
  ids with no entry and lifts whose best load is 0 are skipped, a duplicate id yields one entry,
  and the result is sorted heaviest-first with equal loads keeping their stored `prs` order
  (stable sort). `[]` when `prs` is missing / not an array / empty or `entries` is missing.
  Reads only — no mutation, no backfill, no schema change.
- `frontend/src/lib/history.test.js` — new `prLifts` suite (5 tests): heaviest-first order and
  tie order, best completed work set with warm-up and unchecked rows excluded, skipped
  missing/zero/duplicate ids, empty/non-array/missing `prs` and missing `entries`, and a
  deep-equal before/after check that the workout is not mutated.
- `frontend/src/sheets.jsx` — imports `prLifts`; `WorkoutRow` now renders a `.prs` strip inside
  `.grow`, below the `.ss` metadata line, with one `.pr` badge per `prLifts(w)` entry: trophy +
  `exerciseNameFor(EXIDX[id])` in a `.pr-name.capitalize` span (raw id as fallback) + `t('PR')` +
  `fmtNum(weight) + ' ' + st.unit`. The old trailing `{w.prs.length} PR` badge is gone and a
  workout with no recorded PRs renders no `.prs`. No new props, so History, the Stats recent
  list and the day-sessions sheet all get it from this one rendering.
- `frontend/src/sheets.workout-row.test.jsx` (new) — `// @vitest-environment happy-dom`, mounts
  `WorkoutRow` with the store seeded (`unit: 'kg'`, empty `workouts`). 3 tests: two PRs of
  different loads produce two `.grow .prs .pr` badges, heavier first, each naming the lift and
  the load via `fmtNum` (strip sits below the metadata line, no bare "N PR" remains); a workout
  with no `prs` renders no `.prs`; a duplicate id collapses to one badge.
- `frontend/src/m3.components.css` — in the Lists section, next to the row rules: `.pr` is
  re-pointed to `background:var(--m3-primary-container)`, `color:var(--m3-on-primary-container)`,
  `border-radius:var(--m3-shape-s)`, with `max-width:100%` so it can never widen the row; `.prs`
  is `display:flex;flex-wrap:wrap;gap:6px;margin-top:4px`; `.pr-name` gets
  `min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap`. No raw colour,
  radius, shadow or duration is added. `index.css` is untouched, and the existing `.pr` markup
  elsewhere in `sheets.jsx` (WorkoutDetail's per-entry marker, the exercise-history session
  marker) is unchanged — it only inherits the new look.

Not changed: `package.json` / lockfile, the persisted store shape (`w.prs` is read, not
extended), PR detection or its other readers, the finish summary's `.chip` PR chips.

## How I tested it

```bash
cd frontend && npx vitest run src/lib/history.test.js
# ✓ 1 file, 112 tests passed

cd frontend && npx vitest run src/sheets.workout-row.test.jsx
# ✓ 1 file, 3 tests passed

cd frontend && npx vitest run src/m3.tokens.test.js src/lib/contrast.test.js
# ✓ 2 files, 93 tests passed   (CSS was touched)

cd frontend && npm test
# ✓ 110 files, 1182 tests passed

cd frontend && npm run build
# ✓ built in 284ms (dist/assets/index-*.css 89.50 kB, index-*.js 817.41 kB)

cd frontend && node scripts/check-source-strings.mjs --strict
# EXIT:1 — exactly the same 10 pre-existing "defined in no locale pack" keys as on main
# (618 → 619 translated call sites: the badge adds one use of the already-defined t('PR'),
#  and "PR" is not among the missing keys)
```

### Real-render check

No browser was running, so `oly-previews/shot.mjs` was driven with a local Vite dev server
(port 5173) and headless Chrome on the CDP port 9222, with state seeded through
`useStore.setState` (`oly-previews/factory-005-seed.js`): a one-PR row and a three-PR row whose
third PR is the catalogue's 62-character carry (`wl891`, "snatch grip overhead carry overhead
squat with hanging weights"). Captured at 360×800 and 1200×900, dark and light.

Measured at 360px, dark:

- `document.documentElement.scrollWidth === innerWidth === 360` — the badges never widen the
  page or overflow the row.
- The 3-PR strip runs left 78 → right 301 inside a row at 16 → 344 (the title line occupies the
  same 78 → 301 column), and the strip's top (369px) is below the metadata line's bottom (345px):
  it wraps inside the text column, never over the title.
- The long carry badge ends at x=301 with its chevron and full 344px row/tap target intact; its
  `.pr-name` reports `scrollWidth > clientWidth` and paints "Snatch Grip Overhead Ca… PR 60 kg".
  At 1200px the name fits whole (432px text column) and the first two badges share one line.
- The row keeps `role="button"` and `tabIndex=0`; Tab reaches it with `:focus-visible` true and
  the shared focus layer painted, and the accessible name is the row text including
  "clean PR 130 kg", "snatch PR 102 kg" and "snatch grip … PR 60 kg".
- Computed `.pr`: `background-color: rgb(76, 56, 137)` (`--m3-primary-container` dark),
  `color: rgb(233, 221, 255)` (`--m3-on-primary-container`), `border-radius: 8px`
  (`--m3-shape-s`), `max-width: 100%`; computed `.prs`: `display:flex; flex-wrap:wrap; gap:6px`.

Screenshots in the gitignored capture dir: `oly-previews/factory-005-history-360-dark.png`,
`factory-005-history-360-light.png`, `factory-005-history-360-focus.png`,
`factory-005-history-1200-dark.png`, `factory-005-history-1200-light.png`.

Both themes show the same layout; in light the pair is the light `--m3-primary-container`
(#e9ddff) / `--m3-on-primary-container` (#150d56), in dark the dark pair (#4c3889 / #e9ddff).

## Spec deviations

- `.pr` also sets `white-space:nowrap`, which is not in the spec's declaration list. Without it a
  squeezed badge can break the "PR 130 kg" text onto a second line under the name; the badge is
  meant to be one line with the name clipping. It is not a raw colour, radius, shadow or
  duration, and no other rule changed.
- Nothing else: `w.prs` is read only, there is no migration or new stored field, no new locale
  key, no new dependency, and `index.css` / `package.json` / the lockfile are untouched.
