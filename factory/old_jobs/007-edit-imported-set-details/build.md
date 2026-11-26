STATUS: READY

# Build — 007-edit-imported-set-details

Commit: `f0ff48f` — Correct the coach's sets, reps and load in the review
Branch: `factory/007-edit-imported-set-details` (created from main at `3941b8e`)

## Files changed

- `frontend/src/lib/import-plan.js` — new exported pure helper
  `applyEntryCorrection(entry, patch)`. It returns a new entry and never mutates the one it is
  given (nor its `warns` array — a frozen entry still works): only `sets`, `reps`, `sec` and
  `weight` are read from the patch, `sets`/`reps`/`sec` are normalised to whole numbers ≥ 1
  (`0` → `1`, `4.6` → `5`), `weight` is a finite number clamped to ≥ 0, and a non-finite value
  or any other key (`id`, `name`, `note`, `src`, `tier`, `custom`, `mode`, `sg`, …) is ignored.
  A supplied `sets` drops `'sets'` from `warns`; a supplied `reps` or `sec` drops `'reps'`, so
  the chip and the number can never contradict. `reviewWeek(sheet, { aliases, corrections })`
  gains the `corrections` map (entry key → patch) and applies each matching patch through
  `applyEntryCorrection` as it builds that entry; unknown keys and untouched entries are
  unchanged, and neither the sheet, the corrections map nor an entry is mutated. The
  `reviewWeek` doc comment mentions it. `bundleFromWeek` is untouched.
- `frontend/src/components/CoachImport.jsx` — local `useState` corrections keyed by week index
  and then by entry key (session-only: never written to `S`/`localStorage`/`S.planAliases`);
  each week's `reviewWeek` call receives its own map, so corrections survive switching weeks and
  days. `groupOf(entry)` resolves the tapped row's complex through `supersetUnits`. Saving from
  a movement writes the corrected `sets`+`weight` to every member of its group and the
  `reps`/`sec` only to the tapped entry, then ticks the row as read. `Back to the coach's
  numbers` strips the shared sets/load from every movement and the tapped movement's reps/sec
  (leaving another movement's own reps correction alone) and does not un-tick the row. The row
  menu now leads with `Edit sets, reps and weight`, keeps `Pick a different exercise` / `Back to
  what it read` exactly as before, and adds the reset item only while the row/complex has a
  numeric correction. The header comment and the hint copy are updated.
- `frontend/src/sheets.jsx` — new `coachEntrySheet(entry, onSave)` / `CoachEntry`: a sheet
  (not an inline form) headed by the exercise name, with his own row (`entry.src`) printed under
  it and the existing complex line for an `sg` member; `Sets`, then `Reps` (reps mode) or
  `Seconds` 5 s steps (time mode), then `Weight ({unit})` seeded from the entry; a muted
  `Applies to this import only.`, and a primary `Save`. Weight is shown converted from the
  review's kilograms and hands back kilograms: an untouched field keeps `entry.weight` exactly
  (no kg → unit → kg round trip), a changed one goes through `convertWeight(value, unit, 'kg')`.
  Dismissing changes nothing. `coachPlanSheet` injects it as `editNumbers` next to `pick`/`menu`;
  the flow's comment now separates remembered identity corrections from this-import numbers.
- `frontend/src/locales/it.js` — three new keys with real Italian
  (`Edit sets, reps and weight`, `Back to the coach's numbers`, `Applies to this import only.`);
  the old `Tap a row to change what it was read as.` key is replaced by the new hint key.
- `docs/DATA_IMPORTS.md` — step 4 now says a row's sets, reps and weight can be corrected, that
  his row stays printed underneath, and how a complex divides the correction; step 5 separates
  the remembered exercise-name correction from the this-import number correction.
- `frontend/src/lib/import-plan.test.js` — 10 new tests: entry merge / new object / untouched
  fields; normalisation (`sets 0`→1, `-2`→1, `reps 4.6`→5, negative weight→0, `NaN`/`Infinity`/
  unknown key ignored); frozen entry and `warns`; warn removal for sets/reps/sec and other warns
  kept; a correction keys exactly one entry and leaves the rest of the week deep-equal to the
  uncorrected review; unknown key changes nothing and the inputs are not mutated; frozen
  corrections map; `bundleFromWeek` carries corrected sets/reps/weight and `sec` for a timed row,
  keeps the coach's `src`, and still converts the weight for `unit: 'lb'`.
- `frontend/src/sheets.coach-import.test.jsx` — 12 new happy-dom tests: menu offers the number
  correction first; the sheet's heading / his row / `Sets`-`Reps`-`Weight (kg)` seeds; Save
  updates the row to `2 × 5 · 55 kg`, ticks the row (`1 of 1 checked`), leaves only the review
  open and `S.planAliases` empty; dismiss changes nothing; the timed row seeds `Seconds` at 60
  and saving 45 reads `3 × 0:45`; the flagged row loses both chips and reads `4 × 6`; a complex
  save moves the head to `2 sets · 55 kg` for every movement and changes only the tapped
  `×3` (no per-movement line); `Back to the coach's numbers` restores `4 × 3 · 50 kg`, keeps the
  tick and then disappears; import writes the corrected `sets`/`reps`/`weight` and the original
  `src`; a corrected complex imports the shared prescription; in lb the sheet shows the
  converted weight, an untouched save keeps the coach's exact kilograms (51.3 kg, which would
  drift through a naive round trip), and a changed lb value reaches the week as
  `convertWeight(120, 'lb', 'kg')`.

## Validation

> **BASELINE-RED, NOT CAUSED BY THIS JOB.** Both copy guards fail identically on the base
> commit `3941b8e` (verified by running them in the main checkout):
>
> - `node scripts/check-locales.mjs` → EXIT 1. `it.js` is the only locale file in this
>   checkout, so the script's cross-locale "only here" rule marks every key and always fails. On
>   the base commit: EXIT 1.
> - `node scripts/check-source-strings.mjs --strict` → EXIT 1 with 10 strings "defined in no
>   locale pack". The base commit has the same 10 keys (623 translated strings there vs 626
>   here). None of the 10 is from this job: the three new keys are present in `it.js`, the new
>   hint key is present, and the old hint key is gone (623 − 1 + 4 = 626).
>
> This job does not change either script or the missing keys; acceptance criterion 4 is red on
> the base branch and cannot be made green without an unrelated locale-script/locale-debt fix.

Everything else is green:

```bash
cd frontend && npx vitest run src/lib/import-plan.test.js
# 1 file, 32 tests passed   (22 before)

cd frontend && npx vitest run src/sheets.coach-import.test.jsx
# 1 file, 24 tests passed   (12 before, the existing swap/import tests unchanged and green)

cd frontend && npm test
# 113 files, 1242 tests passed   (the 22 new tests are exactly the +22)

cd frontend && npm run build
# ✓ built in 225ms (dist/assets/index-*.js 824.76 kB, index-*.css 89.78 kB)
```

`git diff --check` is clean.

## Real render evidence

The Vite dev server for this job is running on **port 5170** (worktree `frontend`). The agent
browser's bundled Chrome crashes in this sandbox, so the click-through used the system Chrome
headless with CDP on a workspace `--user-data-dir` and `agent-browser connect`; every
interaction below was a real browser click/typing against the dev server at 390×844.

1. `Plan → Share your plan → Import a coach's plan`, uploaded a temp CSV written to
   `COACH_COLUMNS` (`Giorno 1,Strappo,,,,,3.0,4.0,50kg` and a `Plank / 1 minuto / 3.0` day).
   The review showed `snatch 4 × 3 · 50 kg` with his row `Strappo · 4 × 3 · 50kg` under it.
2. Tapped the row: the menu showed `Edit sets, reps and weight` first, then `Pick a different
   exercise`, and no reset item.
3. The sheet showed heading "Snatch", his row, steppers `Sets 4 · Reps 3 · Weight (kg) 50`,
   `Applies to this import only.` and Save. Filled 2 / 5 / 55 and saved: the row became
   `snatch 2 × 5 · 55 kg`, his row stayed `Strappo · 4 × 3 · 50kg`, and the checkbox ticked.
4. Reopened the menu: `Back to the coach's numbers` was there; choosing it restored
   `4 × 3 · 50 kg` with the tick still on and removed the item from the next menu.
5. Redid the 2 / 5 / 55 correction and pressed `Add the week to my plan`. `S.weeks` got the
   corrected config (`sets 2, reps 5, weight 55, src 'Strappo · 4 × 3 · 50kg'`) and the timed
   Plank day kept `mode 'time'`. The Plan screen listed the new week; `Edit this week` showed
   `Snatch 2 × 5 · 55 kg` with `Strappo · 4 × 3 · 50kg` underneath, and `Plank 3 × 1:00`.
6. Imported the same CSV a second time: the review showed `4 × 3 · 50 kg` again (number
   corrections are not remembered), and `Pick a different exercise` on the row still worked
   (`2 position power snatch`, row re-labelled and ticked); Cancel left the persisted state with
   one week and only the identity alias `planAliases.strappo` written.

## Deviations / judgement calls

- The lb screen test confirms the import in a kg account. `plan-share.js`'s `mergeWeek` accepts
  kg only (`PLAN_UNITS = new Set(['kg'])`, and today's profiles are pinned to kg), so a full lb
  import throws there beyond this job's scope; the test saves in lb, switches to kg and confirms
  the import, which reads the exact kilograms the sheet handed over. The sheet's own lb → kg
  contract is also pinned directly through `coachEntrySheet(entry, onSave)`.
- `applyEntryCorrection` clamps a finite negative weight to 0 (consistent with `sets 0 → 1`);
  the spec only pins the non-finite case. The sheet itself cannot emit a negative weight
  (`NumberField` clamps at 0).
- Saving the sheet without touching any field still records the numbers for the row, so it ticks
  the row and the reset item appears; the spec's "has a numeric correction" wording was read as
  "has saved numbers", and the behaviour is what requirement 5's Save contract asks for.
- No changes to the parser/matcher, `bundleFromWeek`, `mergeWeek`, weekday mapping, WeekEdit,
  the workout flow, the store shape, or dependencies.

## Round 2

Round-1 reviews: security PASS (non-blocking notes only); ux, ui and code CHANGES. Every rendering
finding is addressed below; code note 2 and note 4 were taken as small hardening/tests too.

Commit: `83ac684` — Reset only what a complex movement can reset, and give the load its own row
(on top of round 1's `f0ff48f`).

### Fixed from the reviews

- **ux finding 1 / code finding 1 — the reset item could become a permanent no-op on a complex
  movement.** `saveNumbers` now records only the fields that actually differ from the entry's
  current numbers: a reps-only fix writes `reps` on the tapped movement and **no** `sets`/`weight`
  anywhere, so it never claims a shared correction on its siblings; a genuinely changed sets/load
  is still written to every movement of the complex. `hasNumbers` now asks whether the reset can
  remove something — a `sets`/`weight` correction on the group, or `reps`/`sec` on the tapped
  movement — so the item is not offered on a sibling that has nothing of its own and it disappears
  once the tapped row is clean. New happy-dom suite `resetting across a complex` (2 tests): a
  reps-only fix leaves the sibling's menu without the item while the corrected movement's menu has
  it and reset clears it; a shared-sets fix is resettable from either movement, keeps the first
  movement's own reps fix, and that movement's menu still offers reset until it is cleared.
  `does not claim a correction when Save changed nothing` pins the related case the round-1 report
  flagged as a deviation: an untouched field now records nothing (the round-1 "saving without
  touching still records the numbers" deviation no longer applies; Save still ticks the row).
- **ui finding 1 — a five-character load clipped at 360px.** In `coachEntrySheet` the Weight
  stepper moved to its own `.cfgrow` row (DESIGN §4's "a stepper alone on its line is a row", as
  `complexConfigSheet` already does); Sets + Reps/Seconds stay on the row above. Other sheets are
  untouched (out of scope).
- **code note 2 / security note 1 — helper coercion.** `applyEntryCorrection` now accepts only a
  `typeof raw === 'number'` finite number: a hand-built string/boolean/array patch is ignored, and a
  `Symbol` is ignored rather than throwing. Doc comment and a new lib test
  (`accepts a number as a count and nothing else`) pin it. The UI's only caller already sends
  numbers, so no product behaviour changes.
- **code note 4 — week switching had no test.** New happy-dom test
  `keeps each week's corrections when switching weeks and back`: correct week 1, switch to the
  second sheet (which shows its own uncorrected line), switch back (correction and tick still
  there).
- Code note 3 is the no-op-save behaviour above; code note 5 is the worktree cleanup below.
  The ux/ui notes about the shared `Stepper`'s missing accessible names, the legacy `.dim`
  contrast, and a stepper displaying 0 below 1 are app-wide existing components; left untouched
  as both reviews recommended.

### Files changed in round 2

- `frontend/src/components/CoachImport.jsx` — diff-only recording, reset-visibility rule, comments.
- `frontend/src/sheets.jsx` — Weight on its own row in `CoachEntry`.
- `frontend/src/lib/import-plan.js` — number-only guard and doc comment.
- `frontend/src/lib/import-plan.test.js` — 1 new test (33 total).
- `frontend/src/sheets.coach-import.test.jsx` — 4 new tests (28 total).

### Validation

```bash
cd frontend && npx vitest run src/lib/import-plan.test.js src/sheets.coach-import.test.jsx
# 2 files, 61 tests passed   (33 lib + 28 screen)

cd frontend && npm test
# 113 files, 1247 tests passed   (+5 over round 1's 1242)

cd frontend && npm run build
# ✓ built in 216ms (dist/assets/index-*.js 825.06 kB, index-*.css 89.78 kB)

cd frontend && git diff --check
# clean
```

The two copy guards are unchanged from round 1 and still baseline-red for the documented
pre-existing reasons: `check-source-strings.mjs` reports 626 strings with the same 10 keys
defined in no pack, and `check-locales.mjs` exits 1 because `it.js` is the only locale file. No
new source literal was added in round 2.

### Live render check (round 2)

Vite dev server on **port 5170** at 390×844 and 360×740, real system Chrome over CDP. The
temporary profile/socket dirs were deleted and the dev server, Chrome and the leftover
agent-browser daemon were stopped afterwards: the worktree is clean, no `.tmp*` remains and ports
5170/9333 are free.

- A complex row whose coach load is `102.5kg`: the correction sheet renders Sets and Reps on one
  row with Weight alone below it; the Weight input measured `clientWidth 86 / scrollWidth 86` with
  `value 102.5` (round 1 measured 39px and clipped it), and
  `document.documentElement.scrollWidth === innerWidth === 360`.
- Corrected only the second movement's reps to 6: the head stayed `4 sets · 102.5 kg`, `×reps`
  became `×1 / ×6`; the first movement's menu listed only `Edit sets, reps and weight` and
  `Pick a different exercise` (no reset item), while the second movement's menu had
  `Back to the coach's numbers`; resetting it restored `×2`, kept the head and the row tick, and
  the item was gone from the reopened menu.
