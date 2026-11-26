# Correct sets, reps and weight in the coach-plan review

> Feature: When importing a week and deciding whether an exercise is correct or not, as of today I
> only have the option to swap to a different exercise, I have no agency in correcting the sets, reps
> and weight of the exercises. Therefore, this should be added.

## Context

The coach's spreadsheet is read from **Plan → the upload icon ("Share your plan") → Import a coach's
plan** (a local `.xlsx`/`.csv`, or Google Drive). Nothing is written until the review sheet is
confirmed; the review is where the user decides whether the reading is right.

- `frontend/src/components/CoachImport.jsx` is that review screen. It derives the review with
  `reviewWeek(sheets[week], { aliases })` and renders one row per entry: the exercise name, the
  `exLine(...)` summary (sets × reps · load), the coach's own row under it (`.coach-src`), chips for
  the reading's warnings (`no sets given`, `no reps given`, `New exercise`, …), and a tick. Rows of
  one coach row form a complex card whose head carries the shared sets and load
  (`complexShared(...)`). Tapping a row opens `menuSheet` via the injected `menu` prop; today that
  menu offers **Pick a different exercise** (writes `S.planAliases` keyed by the coach's words) and
  **Back to what it read**. Confirming the review runs `bundleFromWeek(review, { unit })` and
  `mergeWeek`. The screen's header comment says "Tapping a row picks a different exercise".
- `frontend/src/lib/import-plan.js` owns the reading. `reviewWeek(sheet, { aliases })` builds the
  entries: each has `key` (`day:row:component`, stable within a sheet), `sets`, `reps`, `mode`
  (`'reps'`|`'time'`), `sec`, `weight` **in kilograms** (the sheet is always kilos), `note`, `src`
  and `warns`. `bundleFromWeek` turns those entries into the week's exercise configs and converts the
  weight to the account's unit. The review is derived, never stored: correcting an alias re-reads the
  week.
- `frontend/src/sheets.jsx` holds the sheets and injects the flows into the screen:
  `coachPlanSheet(sheets)` passes `pick` and `menu` into `CoachImport`. The existing
  `exConfigSheet` is the plan editor's exercise form (media, bodyweight, bar, progression, warm-up and
  rest) and it seeds itself from a catalogue/custom exercise **id** — a review row can be a custom
  exercise that has no id yet (tier 3, e.g. "Pogo jump"), so it is not the right control here. The
  controls to reuse are `Stepper`/`Button` from `components/ui.jsx`, `convertWeight` from
  `lib/units.js`, `exLine`/`complexShared`/`supersetUnits` from `lib/history.js`, and the existing
  copy key *Sets and load are the same for every exercise in the complex — each one keeps its own
  reps.*
- Tests already in place: `frontend/src/lib/import-plan.test.js` (`reviewWeek`, `bundleFromWeek`) and
  `frontend/src/sheets.coach-import.test.jsx` (`// @vitest-environment happy-dom`; renders the top
  sheet, drives the row menu and the picker, asserts the imported week). Copy is English-source with
  Italian in `frontend/src/locales/it.js`, guarded by `scripts/check-locales.mjs` and
  `scripts/check-source-strings.mjs --strict`.
- `docs/DATA_IMPORTS.md` documents this flow ("Tap a row …", step 4) and is the doc to update.

## Requirements

1. **Pure numeric corrections in `lib/import-plan.js`.** Export a pure helper
   `applyEntryCorrection(entry, patch)` that returns a new entry (never mutates its input) with only
   the prescription keys merged:
   - `sets`: whole number ≥ 1;
   - `reps` and `sec`: whole numbers ≥ 1 (only the one that matches the entry's `mode` is ever sent);
   - `weight`: finite kilograms ≥ 0 (the review keeps weights in kg);
   - any other key in `patch`, and any non-finite value, is ignored; `id`, `name`, `note`, `src`,
     `tier`, `custom`, `mode` and `sg` are never changed by a correction.

   When the patch supplies `sets`, the returned entry's `warns` no longer contains `'sets'`; when it
   supplies `reps` or `sec`, no longer `'reps'` (the chip and the number must never contradict).
2. **`reviewWeek` accepts corrections.** `reviewWeek(sheet, { aliases = {}, corrections = {} } = {})`
   gains `corrections`, a map from an entry's `key` to a patch as in 1. Every matching entry is
   corrected through `applyEntryCorrection` before it is returned; entries without a matching key are
   unchanged, an unknown key changes nothing, and nothing in `sheet`, `corrections` or an entry is
   mutated. `aliases` and everything else about `reviewWeek` stays as it is today; `bundleFromWeek`
   needs no change and must carry corrected numbers into the week (still converting kg to the
   account's `unit`).
3. **Ephemeral correction state on the review screen.** `CoachImport` keeps numeric corrections in
   local `useState` (never in `S`/`localStorage`, never in `S.planAliases`), keyed by week index and
   the entry `key`, so each week's corrections survive switching weeks and days within the sitting and
   are passed to that week's `reviewWeek` call. Numeric corrections are not remembered across imports;
   only exercise-identity corrections keep going through `S.planAliases`.
4. **Row menu.** Tapping a review row keeps opening the menu with the same title/subtitle, now with:
   - `Edit sets, reps and weight` first, which opens the correction sheet (requirement 5);
   - the existing `Pick a different exercise` (and its conditional `Back to what it read`) unchanged;
   - when that row (or, in a complex, its whole complex) has a numeric correction, a
     `Back to the coach's numbers` item that removes the corrections for the row (for a complex: the
     shared sets/load on every movement and the tapped movement's reps/seconds). The item disappears
     once there is nothing to reset. Resetting does not un-tick the row.
5. **The correction sheet.** Add `coachEntrySheet(entry, onSave)` in `sheets.jsx` (opened by the row
   menu through an injected `editNumbers` prop from `coachPlanSheet`, the same injection pattern as
   `pick`/`menu`). It is a sheet, not an inline form:
   - heading is the exercise name the row shows (catalogue name, or `entry.name` for a custom exercise);
     the coach's own row (`entry.src`) is printed under it when present, as the reference;
   - a complex member also prints the existing *Sets and load are the same for every exercise in the
     complex — each one keeps its own reps.* line;
   - steppers: `Sets`, then `Reps` for a reps-mode entry or `Seconds` (5 s steps) for a time-mode entry,
     then `Weight ({unit})`, seeded from the entry's current values; the weight is shown in the
     account's unit (catalogue weight is kg, converted with `convertWeight`), sets ≥ 1, reps/seconds
     ≥ 1, weight ≥ 0;
   - a muted line *Applies to this import only.*; a primary `Save`;
   - saving closes the sheet and hands `onSave` `{ sets, weight, reps }` or `{ sets, weight, sec }`
     with the weight converted back to kilograms. Saving a weight that was not changed must keep the
     entry's existing kilogram value exactly (no kg → unit → kg drift); a changed weight is converted
     with `convertWeight`;
   - dismissing the sheet without saving changes nothing.
6. **Complex semantics.** Sets and load are what the coach wrote once for the whole complex, so saving
   from any movement of a complex applies the corrected sets and weight to every movement of that
   complex, while reps/seconds apply only to the movement that was tapped. A one-movement row is its
   own unit. After saving, the complex card's head shows the corrected sets/load and its movements
   still share them (no new per-movement disagreement line); the corrected movement's `×reps`/`×sec`
   changes. All of this matches how `complexConfigSheet` and `exConfigSheet` divide the same job in the
   week editor.
7. **What the row shows after saving.** The row's existing summary re-derives from the corrected
   review: `.ss` (or the complex head) shows the corrected sets/reps/weight, the coach's `.coach-src`
   is unchanged (his row stays the record of what he wrote), the warnings the correction answered
   (`no sets given`, `no reps given`) disappear, and the row is marked checked (the same
   "looked at it" tick that picking an exercise gives). `Back to the coach's numbers` restores the
   original summary.
8. **Import.** Confirming the review imports the corrected numbers: `S.weeks` gets the corrected
   `sets`/`reps`/`mode`/`sec`/`weight` (kg) for the corrected rows, corrected complex sets/load on
   every member, and the unchanged `src` on each config. Nothing about day/weekday scheduling,
   custom-exercise creation, checked counts or the `Added the week to your plan` toast changes.
9. **Copy and logging stay bilingual.** New English source keys (added to `locales/it.js` with real
   Italian) are `Edit sets, reps and weight`, `Back to the coach's numbers` and
   `Applies to this import only.`; the header hint changes from `Tap a row to change what it was read
   as.` to `Tap a row to change the exercise it was read as, or fix its sets, reps and weight.`
   (replace the old key in `it.js`, don't leave a stale one). Reuse existing keys for `Sets`, `Reps`,
   `Seconds`, `Weight ({0})`, `Save` and the complex line.
10. **Comments and docs.** Update the header comment of `CoachImport.jsx` (it currently says tapping a
    row picks a different exercise) and the `reviewWeek` doc comment to mention the corrections; update
    `docs/DATA_IMPORTS.md` step 4 to say a row's sets, reps and weight can be corrected in the review,
    that the coach's row stays printed underneath, and that number corrections apply to the week being
    imported while identity corrections remain remembered.
11. **No new runtime dependencies, no server calls, no persistence of review-only state**, and no
    changes to the import parser/matcher, the weekday mapping, `WeekEdit`, or the workout flow.

## Acceptance criteria

1. `cd frontend && npx vitest run src/lib/import-plan.test.js` passes with new tests that pin:
   - `applyEntryCorrection` returns a new entry with `sets`/`reps`/`sec`/`weight` merged and the
     rest untouched; values are normalised (e.g. `sets: 0` → 1, `reps: 4.6` → 5, a `NaN` or an
     unknown key ignored); the input entry and `warns` array are not mutated (frozen input still
     works);
   - correcting `sets` removes `'sets'` from `warns` and leaves other warnings alone; correcting
     `reps` (or `sec`) removes `'reps'`;
   - `reviewWeek(week, { corrections: { [key]: patch } })` corrects exactly that entry, leaves the
     other entries of the same day and the other days equal to an uncorrected review, and an
     unknown key changes nothing;
   - `bundleFromWeek` of the corrected review carries the corrected `sets`/`reps`/`weight` (and
     `sec` for a timed row) into the week, keeps the coach's `src`, and still converts the weight
     for `unit: 'lb'`.
2. `cd frontend && npx vitest run src/sheets.coach-import.test.jsx` passes with new happy-dom tests
   that pin the screen:
   - tapping a row shows `Edit sets, reps and weight` in the menu; choosing it opens a sheet whose
     `Sets`/`Reps`/`Weight (kg)` fields hold the row's current numbers; Save updates the row's
     `.ss` line (e.g. a 4 × 3 row corrected to 2 sets, 5 reps and 55 kg reads `2 × 5 · 55 kg`) and
     the checked count goes up; only the review sheet is left open and `S.planAliases` is unchanged;
   - the time-mode row (`Plank … 1 minuto`) opens with a `Seconds` field seeded at 60, and saving
     45 makes the row read `3 × 0:45`;
   - in the complex test week, correcting from one movement's sheet changes the card head to the
     corrected sets/load for the whole complex (all movements still share; no extra per-movement
     line) and changes only that movement's `×reps`;
   - a row that was flagged `no sets given`/`no reps given` loses the chips it was corrected for;
   - after a correction the menu shows `Back to the coach's numbers`; choosing it restores the
     original line and the item disappears;
   - with `S.unit = 'lb'`, the sheet shows the converted weight and saving it unchanged imports the
     coach's original kilogram value; a changed lb weight imports its kg conversion
     (`convertWeight(value, 'lb', 'kg')`);
   - confirming the review with a corrected row writes the corrected `sets`, `reps` and `weight`
     into `S.weeks[0].days[…].ex[…]` and keeps the original `src` — the existing import test still
     asserts the uncorrected sheet imparts the sheet's numbers.
3. `cd frontend && npm test` passes (whole suite), including the existing coach-import tests, which
   must keep passing unchanged for the swap flow.
4. `cd frontend && node scripts/check-locales.mjs` and
   `cd frontend && node scripts/check-source-strings.mjs --strict` both pass; every new `t(...)`
   literal is present in `frontend/src/locales/it.js` and the old hint key is gone.
5. `docs/DATA_IMPORTS.md` step 4 no longer reads as if the only possible correction is swapping an
   exercise; it states that sets, reps and weight can be corrected in the review and that the
   correction applies to that import (the coach's row stays printed).
6. `cd frontend && npm run build` succeeds.
7. Click-through on the job's dev server (phone viewport, e.g. `agent-browser set viewport 390 844`):
   `Plan → upload icon → Import a coach's plan`, upload a temporary CSV whose columns follow
   `COACH_COLUMNS` in `frontend/src/lib/coach-sheet.js` (e.g. `Giorno 1` in A, `Strappo` in B, reps
   `3.0` in G, sets `4.0` in H, `50kg` in I); in the review tap the row, choose `Edit sets, reps and
   weight`, change the numbers, Save, watch the row and the coach's row side by side, then
   `Add the week to my plan` and open the new week from Plan — its exercise shows the corrected
   numbers. The same click-through confirms the normal swap flow still works.

## Out of scope

- Editing the coach's note, cue or `src` row in the review, and editing which exercise a row is
  (that stays the picker's job).
- Remembering numeric corrections for future weeks (see Assumptions) — no new persisted field, no
  alias-like number memory.
- Changing the parser, matcher, complex splitting, weekday scheduling, custom-exercise creation, the
  checked-count rules, or any sheet other than the review.
- Switching a row between reps and time in the review, or offering any plan-editor field (warm-ups,
  rest, bodyweight, bar, progression) on the review row — `exConfig` is deliberately not reused.
- Editing imported exercises after import (`WeekEdit`/its sheets already do that).
- New runtime dependencies, server calls, or changes to the workout flow.

## Assumptions

- **Number corrections live for this import only.** They are not remembered per coach phrase the way
  exercise identity is, because the same phrase comes back with different numbers (deloads, weeks of
  5s): silently overriding a future week from an old fix would be worse than retyping it. Flagged
  for the approver as a product decision.
- **In a complex, sets and load are one prescription** the coach wrote once for the whole row, so they
  are corrected for every movement at once, while reps (the only per-movement number) are corrected
  on the tapped movement. This mirrors the week editor's `complexConfigSheet`/`exConfigSheet` split.
- **The correction sheet always shows the weight**, including 0, because the request includes weight
  and a bodyweight movement can carry added load; 0 keeps meaning "no planned load".
- **A correction does not rewrite the coach's row.** `entry.src`/the config's `src` stay as he wrote
  them, so the week editor can still show the source next to what it became.
- **The account's display unit is honored** even though today's profiles are pinned to kg (the
  pre-2026 lb profiles are migrated): the sheet uses the same `unit` the review row prints, so a
  legacy/test lb state cannot show one number in the row and another in the sheet.
