# Per-set loads in the week editor and the coach import

> Feature: When importing a week and when creating one, for each exercise/complex the sets are considered to have the same weight, whereas in reality what happens is that my coach often gives me progressive sets (maybe 4, 2 at a weight and the other 2 heavier). Add the option to individually adjust the sets' weight.

## Context

**The plan model.** The dated plan lives in `S.weeks` (`frontend/src/lib/weeks.js`):
`{ id, startIso, name, days: [{ dow, name, ex: [...] }] }`. Each `ex` entry is the same
exercise config a routine held — `{ id, sets, reps | sec, weight, mode, warmupSets, restSec,
prog, inc, note, src, sg }`, seeded by `defaultConfig` and written by `ExConfig`
(`frontend/src/sheets.jsx`). It carries **one** `weight`; every set is built with that load
today. There is no per-set load anywhere in the model.

**Creating a week.** Plan's `+` (`frontend/src/views/Plan.jsx`) appends an empty week and
navigates to `WeekEdit` (`frontend/src/views/WeekEdit.jsx`). A day's "Add exercise" opens
`exConfigSheet`; tapping an exercise opens the same sheet with `existing`; a linked pair of
movements ("complex") draws one card whose head opens `complexConfigSheet` and writes its
`sets`/`weight` to **every** member. Rows summarise with `exLine` and complex heads with
`complexShared` (`frontend/src/lib/history.js`).

**Importing a week.** Plan's tools sheet → "Import a coach's plan"
(`frontend/src/sheets.jsx` `planToolsSheet` → `coachPlanSheet`) → the review screen
`frontend/src/components/CoachImport.jsx`. `reviewWeek` (`frontend/src/lib/import-plan.js`)
derives one entry per movement (`sets`, `weight` in kg, reps/seconds, notes); taps open
`coachEntrySheet`, whose corrections are kept in CoachImport's local state and merged back
through `applyEntryCorrection`. "Add the week to my plan" calls `bundleFromWeek`, which
converts kg → account unit with `convertedExercise` (`frontend/src/lib/plan-share.js`) and
hands the week to `mergeWeek`.

**Starting a session.** `buildSessionEntries` (`frontend/src/lib/session-start.js`) walks the
day's configs through `buildSets` (`frontend/src/lib/history.js`), which gives each work row
its `w` from the planned `weight`, the last session, or the confirmed working weight;
`applyPrescription` (`frontend/src/lib/progression.js`) rewrites those rows when a progression
rule is on. The workout's complex heading summarises the shared scheme in
`frontend/src/views/Workout.jsx` (`sharedScheme`/`schemeText`).

**Other readers of a config's weight** that must stay in step: `exLine` (week editor and coach
review rows), `plan-share.js`'s printable `scheme`, `units.js`'s `convTarget` (unit
migration), `convertedExercise` (import unit conversion), and `complexShared` (the shared
load rule). Locales: English keys are the source, Italian lives in
`frontend/src/locales/it.js`. UI conventions are in `DESIGN.md` (`Row`, `Stepper`, sheets,
44px targets); the repo has no linter and no new runtime dependencies are allowed.

## Requirements

1. Add an optional per-set load to a planned exercise config: `setWeights`, an array of numbers in the same unit as `weight`, one per planned set, with `setWeights[0] === weight`. Absent means today's one weight for every set. Never store an all-equal array, `[]` or an explicit `undefined` (remove the key); add no storage version and no migration — a state without `setWeights` reads and behaves exactly as today.
2. Add pure helpers in `frontend/src/lib/history.js`, with tests beside them: `setWeightAt(cfg, i)` (0-based, falls back to `cfg.weight`), `normalizeSetWeights(values, sets, fallback)` (returns an array of length `sets`, repeating the last value when short and truncating when long, or `undefined` when every value is equal), `weightsLabel(cfg, unit)` (`''` when there is no load, `60 kg` when uniform, `60/60/65/65 kg` when a ladder, keeping the leading `+` that `exLine` prints for bodyweight), and make `complexShared` compare and return the whole ladder, not just the head's `weight`.
3. `exLine` prints the ladder through `weightsLabel`, so the week editor row and the coach review row show progressive loads with no extra surface. The week editor's complex head shows the shared ladder as `4 sets · 60/60/65/65 kg`, and a member whose ladder differs still prints its own line.
4. `buildSets`/`buildWorkSets` build planned rows from the ladder when the session uses the plan's own target (`useTarget`, i.e. automatic progression is off): row *i* gets `setWeightAt(cfg, i)`, including a 0 when the ladder says 0; only a uniform `weight` of 0 keeps today's fallback to the last session's load. A uniform config builds byte-for-byte what it builds today. Planned warm-ups keep ramping toward the first work set's weight.
5. `buildSessionEntries` (`frontend/src/lib/session-start.js`) puts `setWeights` on an entry's `target` only when the rows were built from the planned ladder (`plan.kind === 'off'`); when a prescription decides the load, the target carries no ladder. `freestyleConfig` drops a ladder it would otherwise copy from a past target.
6. The workout's complex heading (`sharedScheme`/`schemeText` in `frontend/src/views/Workout.jsx`) summarises a ladder shared by every member as `4 sets · 60/60/65/65 kg`, and keeps today's `3 sets · 30 kg` for a uniform complex.
7. In the exercise config sheet (`ExConfig`, `frontend/src/sheets.jsx`), a config planning more than one set shows a `Row` titled "Weights per set" whose value is `weightsLabel`. The row opens a sheet titled "Weights per set" with one `Stepper` labelled `Set {0}` per set, seeded from the ladder when present and from the uniform weight otherwise, and a primary "Done". Saving writes `setWeights` when the values are not all equal and sets `weight` to the first value; all-equal values collapse back to the uniform weight. Editing the uniform Weight (or Added, for bodyweight) stepper clears the ladder. Changing the Sets count resizes the ladder with `normalizeSetWeights`.
8. The complex config sheet (`ComplexConfig`, `frontend/src/sheets.jsx`) has the same row and sheet. Saving a ladder there applies it to every movement of the complex exactly as `sets`/`weight` are applied today, and changing Sets or the uniform Weight leaves the members' ladders equal and normalized.
9. A running session's config sheet does **not** show the per-set row: the `exConfigSheet` callers that edit `s.active` (`frontend/src/views/Workout.jsx` progression settings and add-exercise, `swapActiveWorkoutExercise` in `frontend/src/sheets.jsx`) pass an option that hides it — a session already edits one weight per set row. The hidden row must still preserve a ladder the edited target already carries: it is resized when Sets changes and cleared when the uniform Weight changes, so an in-session edit cannot silently flatten a progressive plan. The week editor's callers keep the row.
10. Coach import: `reviewWeek` entries accept a `setWeights` correction, and `applyEntryCorrection` applies it as an array of finite non-negative numbers normalized to the entry's `sets`, sets `weight` to the first ladder value, collapses all-equal values to the uniform weight, and clears the ladder when a `weight` correction is applied. `CoachEntry` (`frontend/src/sheets.jsx`) has the same row/sheet as requirement 7, in the display unit; an untouched value keeps its exact kilogram value and only changed values are converted, as the single weight does today.
11. In the coach review, a ladder correction is shared by every movement of a complex, like the sets and load today: `CoachImport`'s `saveNumbers`, `hasNumbers`, `resetNumbers`, row display and complex head all understand it.
12. `bundleFromWeek` writes the ladder into the week (converted to the account unit) with `setWeights[0]` equal to the written `weight`; `convertedExercise` (`frontend/src/lib/plan-share.js`) and `convTarget` (`frontend/src/lib/units.js`) convert `setWeights` wherever they convert `weight`; the printable plan's `scheme` prints the ladder.
13. Every new user-facing string is an English key with an Italian entry in `frontend/src/locales/it.js`; reuse existing keys where one already says it (`Set {0}`, `Done`, `Weight ({0})`). Follow `DESIGN.md`: existing `Row`/`Stepper`/sheet vocabulary only, no new visual pattern, no raw colours, radii or durations, 44px targets.
14. Update `docs/DATA_IMPORTS.md` (the coach's plan section, point 4): each set's load can be corrected in the review, a complex's per-set loads apply to every movement, and a load cell that lists several numbers is not split into a ladder — the first number stays the weight and the rest stay in the note.

## Acceptance criteria

1. `cd frontend && npx vitest run src/lib/history.test.js` passes, including new cases: `setWeightAt` falls back to `weight`; `normalizeSetWeights` truncates, repeats the last value and collapses all-equal values to `undefined`; `weightsLabel` gives `60 kg` uniform, `60/60/65/65 kg` for a ladder, `''` for no load, and the `+` prefix for bodyweight; `complexShared.load` is false when two members' ladders differ; `buildSets` with `{ sets: 4, weight: 60, setWeights: [60, 60, 65, 65], warmupSets: 3 }` and `useTarget: true` returns work rows `w` 60/60/65/65 and warm-ups below 60 that ramp toward the first work set.
2. `cd frontend && npx vitest run src/lib/session-start.test.js` passes, including: with automatic progression off, a 4-set ladder day builds an entry whose set rows read 60, 60, 65, 65 and whose `target.setWeights` equals the ladder; with the rule on, that entry's rows are uniform at the first value and `target` has no `setWeights`.
3. `cd frontend && npx vitest run src/sheets.test.jsx` passes, including: `exConfigSheet` for a 4-set config renders a "Weights per set" row; opening it shows four steppers labelled `Set 1`…`Set 4`; setting sets 3 and 4 to 65, pressing Done, then Save calls `onSave` with `{ sets: 4, weight: 60, setWeights: [60, 60, 65, 65] }`; the same sheet opened with the option from requirement 9 renders no "Weights per set" row but saving unchanged keeps the ladder it was given; changing Sets to 3 before Done saves `[60, 60, 65]`; touching the uniform Weight clears `setWeights` from the payload.
4. `cd frontend && npx vitest run src/sheets.test.jsx` also shows `complexConfigSheet` writing the same `setWeights` to every member of a two-movement complex, and normalizing it when Sets changes.
5. `cd frontend && npx vitest run src/views/WeekEdit.test.jsx` passes, including: an entry `{ sets: 4, reps: 10, weight: 60, setWeights: [60, 60, 65, 65] }` renders `4 × 10 · 60/60/65/65 kg`; a uniform entry still renders its current line; a complex card head renders `4 sets · 60/60/65/65 kg`.
6. `cd frontend && npx vitest run src/views/Workout.test.jsx` passes, including: two complex entries whose `target` carries the same ladder heading show `4 sets · 60/60/65/65 kg` in `.ss-hd .ss-load`, and the existing `3 sets · 30 kg` case is unchanged.
7. `cd frontend && npx vitest run src/lib/import-plan.test.js` passes, including: `applyEntryCorrection(entry, { setWeights: [60, 60, 65, 65] })` gives the entry that ladder and `weight === 60`; a later `{ sets: 3 }` truncates it to `[60, 60, 65]`; `{ setWeights: [60, 60, 60, 60] }` leaves no ladder and weight 60; `{ weight: 70 }` clears a ladder; `bundleFromWeek` writes the ladder into the week, converts it with the account unit, and keeps `weight === setWeights[0]`.
8. `cd frontend && npx vitest run src/sheets.coach-import.test.jsx` passes, including: after correcting a 4-set row, the review row shows the ladder; correcting one movement of a complex gives every member the same ladder; "Add the week to my plan" stores a week entry with `setWeights` and a matching first `weight`; "Back to the coach's numbers" removes the ladder again.
9. `cd frontend && npx vitest run src/lib/units.test.js src/lib/plan-share.test.js` passes (the second file is new): `convertStateUnit` converts a week's `setWeights` alongside `weight`; `planPrintHTML` contains `60/60/65/65 kg` for a week carrying that ladder and still `60 kg` for a uniform week.
10. `cd frontend && node scripts/check-locales.mjs && node scripts/check-source-strings.mjs --strict` exits 0, and `frontend/src/locales/it.js` has an Italian entry for every new key (at least `Weights per set`).
11. `docs/DATA_IMPORTS.md`'s coach-plan section describes correcting each set's load in the review, that a complex's per-set loads apply to every movement, and that a load cell listing several numbers is not split into a ladder.
12. `cd frontend && npm test` is green (whole suite) and `npm run build` completes without errors.
13. Browser, dev server on the job's assigned port, viewport 390×844: from a fresh profile (clear storage, reload, take a starter plan so a week covering today exists), open Plan → *This week* → Edit this week, add a day or use one, add an exercise with Sets 4 and Weight 60, open "Weights per set", set sets 3 and 4 to 65, Done, Save — the week row reads `4 × 10 · 60/60/65/65 kg`; set that day's weekday to today's, start the session from Home and its four set rows read 60, 60, 65, 65; `agent-browser errors` is empty; `agent-browser storage local gym_state_v1` shows the config with `"weight": 60` and `"setWeights": [60,60,65,65]`.
14. Browser, same server: during a running session, the exercise options sheet shows no "Weights per set" row; an old week stored without `setWeights` still shows its row line and still starts the same session rows as before the change.

## Out of scope

- Reading several loads out of one coach load cell ("90,95 95") into a ladder; the parser keeps today's behaviour (the first number is the weight, the rest stay in the note).
- Per-set reps, seconds, notes, warm-ups or drop-sets — only each working set's load.
- Making automatic progression ladder-aware; with a rule on, the rule keeps owning the load.
- A per-set editor inside the running workout's exercise options sheet, or any change to how a session's set rows are edited, cascaded or saved.
- Migrations, backup-format/version changes, starter plan contents, legacy `S.routines`, or plan-share JSON (there is none).
- Changes to the coach sheet reader (`lib/coach-sheet.js`), alias matching, the review's exercise picker, or the import's day scheduling.
- New dependencies, new stylesheets or `index.css` edits, new modal/picker vocabulary, or reworking the complex (`sg`) model or its pairing UI.

## Assumptions

- A ladder belongs to the plan config, not to a session or a logged set; a session still stores one weight per set row as today.
- `weight` stays the first set's load, so every older reader (progression, print, exports, backups) keeps seeing a sensible number.
- The single Weight control means "the same for every set", so touching it clears the ladder — one control, one meaning.
- All-equal per-set values collapse to no ladder, so a uniform plan stays byte-identical to today's.
- Automatic progression wins when enabled and the planned ladder is used only while the rule is off (the default) — the same way the planned `weight` already behaves.
- The display format is `60/60/65/65 kg` (unit once, after the list) and a uniform load keeps today's `60 kg`, so one helper formats every surface.
- Changing the Sets count resizes the ladder by truncating or repeating the last value rather than resetting it — the least surprising edit.
- The coach import does not guess a split for a multi-number load cell: silently rewriting the coach's plan is worse than a manual correction, which is the requested option.
- The editor is a `Row` opening a sheet of `Stepper`s (DESIGN's vocabulary) rather than a new inline control; it is hidden in a running session's options sheet because each set row already carries its own load. If the approver treats the exact placement or labels as a product decision, escalate — the behaviour above does not depend on the copy.
