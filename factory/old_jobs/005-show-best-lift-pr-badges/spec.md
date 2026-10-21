# Goal

The History list marks a session that set a PR with a trophy and a count — `WorkoutRow` in
`frontend/src/sheets.jsx` renders `{w.prs.length} PR`. That says how many, not which lift and not
the load. Put the session's best-lift PRs on the row instead: one M3 badge per lift the session
recorded a PR for, naming the lift and the load it hit, heaviest first — e.g. "Snatch PR 102 kg".

The model already exists; do not invent another one. `doFinishWorkout` (`sheets.jsx`) writes
`w.prs` as an array of exercise **ids** whose best completed work load beat the previous best
(`bestWeightFor`); `buildCompletedWorkout` (`frontend/src/lib/finish-workout.js`) persists that
array unchanged. `w.prs` holds no weights and no names. The badge's load is the session's own best
for that entry, `bestWeightForEntry(entry)` — the reader Stats and the exercise-history sheet
already use for "this session's best" — never the lifetime best and never a re-run of the PR test.
`w.prs` is read, not extended: no persisted-state schema change.

The `w.prs` → ordered `{ id, weight }` mapping is a pure helper, `prLifts(workout)`, in
`frontend/src/lib/history.js` (the module CLAUDE.md names as the home of best-lift PRs), with its
unit tests in `frontend/src/lib/history.test.js` beside it, per CONTRIBUTING.md.

`WorkoutRow` is shared (History, Stats' recent list, the day-sessions sheet), so the badges reach
all three from one rendering — intended, and the reason there is no per-screen prop.

Styling stays on tokens. The existing `.pr` marker is re-pointed in `frontend/src/m3.components.css`
(a second badge class would give the app two PR looks) from `index.css`'s legacy yellow and 6px
radius to `--m3-primary-container` / `--m3-on-primary-container` / `--m3-shape-s`. That text pair is
AA for 12px in every generated scheme — the contrast suite already asserts `>= 4.5:1`, measured worst
7.19:1 (lime); the badge is opaque, so the row under it does not change the pair. `index.css` is
never edited.

Copy: the badge's one word is the existing `PR` key (`t('PR')`, `frontend/src/locales/it.js`), so
there is no new string and no missing translation.

# Out of scope

- Recomputing or backfilling PRs for sessions that did not record them. `w.prs` is written only by
  a finished session, and a backfilled session deliberately records none (`doFinishWorkout` skips
  the past). Legacy, imported or backfilled workouts without the field keep their current
  badge-less row: no migration, no new stored field, no "first reached" derivation.
- Changing PR detection or its other readers. `doFinishWorkout`, `bestWeightFor`,
  `bestWeightForEntry` and the History "PRs" tile keep their semantics. `WorkoutDetail`'s
  per-entry badge and the exercise-history sheet's session marker keep their markup and only
  inherit the new `.pr` colours; the finish summary's PR chips are `.chip`s and are untouched.
- A lifetime-best badge (the session that holds the all-time best for a lift) — the
  exercise-history sheet already marks that; this is per-session PRs only.
- Any new locale key, new runtime dependency, network/server code, `index.css` edit, TypeScript or
  tooling.

# Acceptance criteria

- [ ] `frontend/src/lib/history.js` exports `prLifts(workout)`, returning a new array of
  `{ id, weight }`, one per recorded PR, without mutating the workout or its entries:
  - `weight` is `bestWeightForEntry(entry)` for the workout entry with that id (the existing reader
    still excludes warm-up and unchecked rows);
  - ids with no matching entry, and lifts whose best load is 0, are skipped; a duplicate id yields
    one entry;
  - ordered by `weight` descending; equal loads keep their order in `workout.prs`;
  - `[]` when `workout.prs` is missing/not an array/empty, or when `workout.entries` is missing.
- [ ] `frontend/src/lib/history.test.js` gains a `prLifts` suite pinning: heaviest-first order and
  tie order, the completed-work-set weight (warm-up and unchecked rows excluded), skipped
  missing/zero/duplicate ids, the empty and missing-`prs` cases, and no input mutation.
- [ ] `WorkoutRow` (`frontend/src/sheets.jsx`) renders the badges: a `.prs` strip inside the row's
  text column, below the metadata line, holding one `.pr` badge per `prLifts(w)` entry in helper
  order. A badge reads trophy + exercise name (`exerciseNameFor(EXIDX[id])` in a `.capitalize`
  span, raw id as fallback) + `t('PR')` + `fmtNum(weight) + ' ' + st.unit` — e.g.
  "Snatch PR 102 kg". The old trailing `{w.prs.length} PR` badge is gone, and a workout with no
  recorded PRs renders no `.prs` strip.
- [ ] The one row rendering reaches History, the Stats recent list and the day-sessions sheet with
  no new props, and no other list markup changes.
- [ ] `frontend/src/sheets.workout-row.test.jsx` (`// @vitest-environment happy-dom`) mounts
  `WorkoutRow` and asserts: two PRs with different loads produce two `.pr` badges under
  `.grow .prs`, the heavier first, each naming the lift and the load via `fmtNum`; a workout with
  no `prs` renders no `.prs`; no bare "N PR" count remains.
- [ ] `frontend/src/m3.components.css` re-points `.pr` (Lists section, next to the row rules) to
  `background:var(--m3-primary-container)`, `color:var(--m3-on-primary-container)`,
  `border-radius:var(--m3-shape-s)` and adds `.prs`
  (`display:flex;flex-wrap:wrap;gap:6px;margin-top:4px`). Long names are clipped inside the
  badge with a `max-width:100%` on `.pr` and `min-width:0;overflow:hidden;text-overflow:ellipsis;
  white-space:nowrap` on the name span (it may get its own class). No raw colour, radius, shadow
  or duration is added; `index.css` is untouched, and the existing `.pr` markup elsewhere in
  `sheets.jsx` is unchanged.
- [ ] A badge never widens or overflows the row: at 360px, a very long lift name (the catalogue's
  62-character carry, or a long custom exercise) truncates with an ellipsis inside its badge, and
  the row's chevron and full tap target stay intact.
- [ ] Layout holds at 360px and at >=1000px, dark and light, for a row with one and with three
  PRs: badges wrap inside the text column, never overlap the title, and the badge text is part of
  the row's accessible name (the row keeps `role="button"`; `PR` is a visible word, not colour or
  icon alone).
- [ ] Copy stays bilingual with no new key: the badge uses `t('PR')` (`locales/it.js` already
  defines it), and `node scripts/check-source-strings.mjs --strict` reports exactly the same 10
  pre-existing "defined in no locale pack" keys, nothing from this change.
- [ ] `cd frontend && npm test` passes and `cd frontend && npm run build` is clean; no
  `package.json`/lockfile change, no network/server code, and the store's persisted shape is
  unchanged.

# Test plan

```bash
cd frontend && npx vitest run src/lib/history.test.js
cd frontend && npx vitest run src/sheets.workout-row.test.jsx
cd frontend && npm test
cd frontend && npm run build
cd frontend && node scripts/check-source-strings.mjs --strict
```

- `src/lib/history.test.js` — the new `prLifts` suite: rows with completed, unchecked and warm-up
  sets; two PR ids of different loads (heaviest first) and a tie (stored `prs` order kept); an id
  with no entry, a zero-load PR and a duplicated id; a workout with no `prs` and one whose `prs`
  is not an array; the input objects are deep-equal before and after the call.
- `src/sheets.workout-row.test.jsx` — mounts `WorkoutRow` with `useStore` seeded (`unit: 'kg'`,
  empty `workouts`) and a normal workout record (`d`, `start`, `end`, `name`, `vol`, `entries`)
  whose `prs` names two lifts with different best loads. Asserts
  `[...host.querySelectorAll('.grow .prs .pr')]` has those two, in load order, each containing
  `exerciseNameFor(EXIDX[id])`, `t('PR')` and `fmtNum(weight) + ' kg'`; a second workout with no
  `prs` renders no `.prs`.
- `npm test` — the full vitest suite gate.
- `npm run build` — the production build must be clean.
- `node scripts/check-source-strings.mjs --strict` — exits 1 today because 10 pre-existing source
  strings have no locale entry; the output must list exactly those 10, with `PR` not among them.
