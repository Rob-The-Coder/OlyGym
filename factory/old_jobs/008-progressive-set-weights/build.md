STATUS: READY

# Build: Per-set loads in the week editor and the coach import

## Summary

A plan config may now carry an optional `setWeights` ladder — one load per planned set, same unit
as `weight`, with `setWeights[0] === weight` — so a coach's "two at 60, two at 65" can be planned
and imported instead of flattened to one number. Absent, empty or all-equal means today's single
weight; no storage version, no migration, and a uniform config builds byte-for-byte what it did.

The week editor, the complex config, the coach's number correction and the workout's complex card all
read and print the ladder through helpers in `lib/history.js`; a running session's own options sheet
does not show the row (each set row owns its load) but still preserves and resizes a ladder it was
opened with.

Round 2 closed the round-1 review. Round 3 (rework) keeps the `setWeights[0] === weight` invariant
when the complex card's Sets stepper writes its ladder to every movement: a member whose own weight
was edited (which clears its ladder) takes the ladder's first load with it.

Files changed:

- Domain: `frontend/src/lib/history.js` (+ tests), `session-start.js` (+ tests),
  `import-plan.js` (+ tests), `plan-share.js` (new `plan-share.test.js`), `units.js` (+ tests)
- UI: `frontend/src/sheets.jsx`, `views/WeekEdit.jsx`, `views/Workout.jsx`,
  `components/CoachImport.jsx`, `components/ui.jsx` (+ their tests)
- Copy/docs: `frontend/src/locales/it.js` (`Weights per set`), `docs/DATA_IMPORTS.md`

Round 3 touched only `frontend/src/sheets.jsx` (`ComplexConfig.setSets`) and
`frontend/src/sheets.test.jsx`.

## Acceptance criteria

Ladder labels are written below with a normal space; in the app a ladder's unit is joined by a
non-breaking space (round-2 UI fix), and the tests pin it with `\u00a0`.

1. [x] `npx vitest run src/lib/history.test.js` — 121 passed. New cases cover `setWeightAt`
   falling back to `weight`, `normalizeSetWeights` truncating/repeating/collapsing,
   `weightsLabel` (`60 kg`, `60/60/65/65 kg`, `''`, `+10/10/15/15 kg`, a junk entry dropped),
   `complexShared.load` false when two members' ladders differ (the card still heads with the
   head's ladder), and
   `buildSets({ sets: 4, weight: 60, setWeights: [60,60,65,65], warmupSets: 3 }, { useTarget: true })`
   returning work rows 60/60/65/65 with warm-ups 30/45/52.5.
2. [x] `npx vitest run src/lib/session-start.test.js` — 11 passed. With progression off the day
   builds rows 60/60/65/65 and `target.setWeights` equals the ladder; with `autoProg` on the rows
   are uniform and the target has no `setWeights`. A second case gives the rule a different load
   (an `up` prescription to 62.5) so the test proves the rule owns the rows rather than passing on
   a first-session no-op.
3. [x] `npx vitest run src/sheets.test.jsx` — **12 passed** (round 3 adds one case; the other 11
   unchanged). A 4-set `exConfigSheet` renders the "Weights per set" row; opening it shows four
   steppers labelled `Set 1`…`Set 4` whose fields and ± buttons carry the set name (`Set 1`,
   `Decrease Set 1`) and the unit; setting 3 and 4 to 65, Done, Add to routine calls `onSave` with
   `{ sets: 4, weight: 60, setWeights: [60,60,65,65] }`; changing Sets to 3 first saves
   `[60,60,65]`; the uniform Weight stepper clears the payload's `setWeights`; with
   `{ hideSetWeights: true }` the row is absent but saving unchanged keeps the given ladder (and
   Sets resizes it).
4. [x] Same file: `complexConfigSheet` writes `[60,60,65,65]` to both movements of a two-movement
   complex, and changing Sets to 3 leaves both members `[60,60,65]`; changing Weight clears both and
   sets 70 on each. A member with a different set count gets a ladder of its own length (head 3 sets
   → 3 loads, member 4 sets → 4 loads). Round 3's case: head `{ sets: 4, weight: 60,
   setWeights: [60,60,65,65] }` + member `{ sets: 4, weight: 70 }` (the state after that member's
   own row was edited), card Sets → 5, and both members store
   `{ sets: 5, weight: 60, setWeights: [60,60,65,65,65] }` — the ladder's first load is the
   member's weight. The case fails on the round-2 code (`expected [60,60], received [60,70]`),
   checked by stashing `sheets.jsx` and re-running the file.
5. [x] `npx vitest run src/views/WeekEdit.test.jsx` — 24 passed. A `{ sets: 4, reps: 10, weight: 60,
   setWeights: [60,60,65,65] }` row renders `4 × 10 · 60/60/65/65 kg`, a complex card head renders
   `4 sets · 60/60/65/65 kg`, and a member whose ladder differs keeps its own line.
6. [x] `npx vitest run src/views/Workout.test.jsx` — 88 passed. Two complex entries whose targets
   carry the same ladder show `4 sets · 60/60/65/65 kg` in `.ss-hd .ss-load`; differing ladders show
   nothing; the existing `3 sets · 30 kg` case is unchanged. The mock-based tests also pin the
   `{ hideSetWeights: true }` option on both the progression-settings and add-exercise calls.
7. [x] `npx vitest run src/lib/import-plan.test.js` — 39 passed.
   `applyEntryCorrection(entry, { setWeights: [60,60,65,65] })` gives that ladder and weight 60; a later
   `{ sets: 3 }` truncates to `[60,60,65]`; `{ setWeights: [60,60,60,60] }` leaves no ladder and weight
   60; `{ weight: 70 }` clears the ladder; `bundleFromWeek` writes the ladder (copied, not aliased),
   converts it with the account unit (lb) and keeps `weight === setWeights[0]`.
8. [x] `npx vitest run src/sheets.coach-import.test.jsx` — 33 passed. A corrected 4-set row prints
   `4 × 3 · 50/50/55/55 kg`; correcting one movement of a complex gives every member the same ladder
   (card head `4 sets · 50/50/55/55 kg`, no disagreeing lines) and the import stores it on both;
   "Add the week to my plan" stores `setWeights` with a matching first weight; "Back to the coach's
   numbers" removes it again; flattening every set to 50 drops the ladder and the review prints
   `4 × 3 · 50 kg` (round-2 code-review fix).
9. [x] `npx vitest run src/lib/units.test.js src/lib/plan-share.test.js` — 8 + 4 passed.
   `convertStateUnit` converts a week's ladder alongside `weight`; `planPrintHTML` contains
   `60/60/65/65 kg` for a ladder week and still `60 kg` for a uniform one.
10. [~] **Guards still exit 1, identically on `main` — this branch introduces neither failure.**
    `node scripts/check-locales.mjs`: exit 1 on main and on this branch (`it.js: 1507/1507` main,
    `1508/1508` here). The script treats a key as "only here" unless a second locale carries it, and
    this fork ships a single locale file (e.g. `e97bdca` dropped the others), so *every* key is an
    orphan; the union/keyset invariant is trivially satisfied. `node scripts/check-source-strings.mjs
    --strict`: exit 1 on main (626 translated strings, 10 keys in no pack) and on this branch
    (627, the same 10 — listed below). The one new key `Weights per set` is present in `it.js`
    (`Pesi per serie`), and reuses existing `Set {0}`, `Done`, `Weight ({0})`. Fixing either means
    editing a guard script or ten unrelated strings, which is outside this job's scope; recorded here
    rather than papered over. Round 3 changed no strings, so the guard output is unchanged.
    Pre-existing source-string gaps (same list on main): `Day options`, `How the whole workout went.
    Editable here any time afterwards.`, `No workouts planned this week.`, `Put your week on paper.`,
    the long ramp-up sentence, `Rest runs after each set of this exercise. Leave it at 0 to use your
    default rest timer.`, `Warm-ups and rest`, `Week options`, `What do warm-ups and rest do?`,
    `Workout options`.
11. [x] `docs/DATA_IMPORTS.md` point 4 now says each set's load can be corrected under **Weights per
    set**, that a complex's ladder applies to every movement like its sets and load, and that a load
    cell listing several numbers (`90,95 95`) is not split into a ladder — the first number stays the
    weight and the rest stay in the note.
12. [x] `npm test` — 114 files, **1290 tests** passed (round 3: +1 case). `npm run build` — built in
    246ms, exit 0.
13. [x] Browser, dev server on port 5180, viewport 390×844, fresh profile (`storage local clear` +
    `reload`), starter plan "Snatch / Clean & Jerk / Squat" loaded: an exercise added with Sets 4 /
    Weight 60, its Weights-per-set sheet edited to 60/60/65/65 (four steppers labelled `Set 1`…`Set
    4`). The week row reads `4 × 10 · 60/60/65/65 kg`; Home starts today's session; the ladder
    entry's four set rows read 60, 60, 65, 65; `agent-browser errors` is empty. Screenshots:
    `oly-previews/factory-008-week-row.png`, `oly-previews/factory-008-session-rows.png`.
14. [x] Browser, same server: during a running session, ⋯ → Progression settings renders
    Steppers Sets/Reps/Weight/Warm-up sets/Rest (s)/Bar and the rows Bodyweight, No bar, Rule — no
    "Weights per set" row — and saving unchanged keeps `target.setWeights` `[60,60,65,65]` and the
    rows at 60/60/65/65. An untouched starter exercise (old week, no `setWeights`) still prints its
    `5 × 5` line on the week editor row and starts rows that are uniform with no ladder on the
    target. Round 3's live repro of the rework sits under Validation.

## Validation

```bash
cd frontend
npx vitest run src/sheets.test.jsx              # ✓ 12 tests (round 3)
npx vitest run src/lib/history.test.js          # ✓ 121 tests
npx vitest run src/lib/session-start.test.js    # ✓ 11 tests
npx vitest run src/lib/import-plan.test.js      # ✓ 39 tests
npx vitest run src/lib/units.test.js src/lib/plan-share.test.js  # ✓ 8 + 4 tests
npx vitest run src/sheets.coach-import.test.jsx # ✓ 33 tests
npx vitest run src/views/WeekEdit.test.jsx      # ✓ 24 tests
npx vitest run src/views/Workout.test.jsx       # ✓ 88 tests
npm test                                        # ✓ 114 files, 1290 tests
npm run build                                   # ✓ built in 246ms, exit 0

node scripts/check-locales.mjs                  # exit 1 — pre-existing, see criterion 10
node scripts/check-source-strings.mjs --strict  # exit 1 — pre-existing, same 10 keys as main
```

Guards baseline was read from `/home/roberto/Android/OlyGym/frontend` (main checkout, same
commands): `it.js: 1507/1507 keys` exit 1; `626 translated strings … 10 defined in no locale pack`
exit 1. This worktree: `1508/1508` and `627 translated strings` with the same 10 keys.

Round-2 browser measurements (same dev server, agent-browser, character-level `Range` rects):

- Week editor at 390: a standalone ladder row wraps `4 × 10 ·` / `60/60/65/65 kg` — two lines, the
  second is the whole ladder plus unit, never a lone `kg`.
- Workout complex heading at 390: one line, `.ss-hd .ss-load` 143px wide. At 360: two lines,
  `4 sets ·` / `60/60/65/65 kg` (was `…/65` / `kg` before the fix). Screenshot:
  `oly-previews/factory-008-round2-heading-360.png`.
- Ladder sheet in the live app: three steppers for a 3-set head, inputs named `Set 1`…`Set 3`,
  ± buttons named `Decrease Set 1`…, unit `kg` beside each value.
- Complex config in the live app with a 3-set head and a 4-set member: Done wrote
  `ladder: [60,60,65]` / `ladder: [60,60,65,65]` to storage — one load per set for each movement.

Round-3 browser check (dev server from the worktree on 5180, agent-browser at 390×844, fresh
profile, starter plan loaded, complex pair `{ sets: 4, weight: 60 }` × 2 seeded as the starting
point):

1. Card → "Weights per set" → Set 3 and Set 4 = 65 → Done. Storage: both members
   `{ sets: 4, weight: 60, setWeights: [60,60,65,65] }`.
2. Second movement's own row → its config sheet → Weight 70 → Save. Storage: head keeps
   `[60,60,65,65]`, member stores `{ sets: 4, weight: 70 }` (its ladder cleared) — the round-2
   reviewer's repro state.
3. Reopen the card → Sets 4 → 5. Storage: **both** members
   `{ sets: 5, weight: 60, setWeights: [60,60,65,65,65] }`; the card head reads
   `5 sets · 60/60/65/65/65 kg`; `agent-browser errors` is empty.
   Screenshot: `oly-previews/factory-008-round3-card.png`.

## Log

### Round 1

Built the feature end to end.

- `history.js`: `setWeightAt`, `normalizeSetWeights`, `sameSetWeights`, `weightsLabel`;
  `exLine` prints through `weightsLabel`; `complexShared` compares and returns the ladder;
  `buildWorkSets`/buildSets use it only under `useTarget`; `freestyleConfig` drops it.
- `session-start.js`: `target.setWeights` only when `plan.kind === 'off'`.
- `import-plan.js`: ladder in `applyEntryCorrection` (normalize to sets, first value as weight,
  collapse all-equal, clear on a weight correction) and in `bundleFromWeek`.
- `plan-share.js`/`units.js`: convert and print the ladder.
- `sheets.jsx`: one shared `SetWeightsSheet`; `ExConfig` row + draft handling (resize on Sets,
  clear on uniform Weight/bodyweight-on) plus the `hideSetWeights` option; `ComplexConfig` writes
  the ladder to every member and keeps them equal; `CoachEntry` edits it in the display unit and
  preserves exact kilos when untouched.
- `CoachImport.jsx`: `saveNumbers`/`hasNumbers`/`resetNumbers`, row display and complex head.
- `WeekEdit.jsx`, `Workout.jsx`: ladder in the row/card heading; session callers pass the option.
- Locale key + docs.

During browser verification the progression-settings call had the option in the `initial` slot
(sixth argument) instead of the options slot, so the row still appeared in a running session. Fixed
the call and added assertions in `Workout.test.jsx` that both session callers pass
`{ hideSetWeights: true }` — the mock-based test could not see this before.

The two guard scripts fail identically on `main` (see criterion 10); no unrelated strings or guard
code were changed.

### Round 2

Round-1 verdicts: security PASS (no findings); code, ui and ux CHANGES. Each finding:

1. **code 1 — flattening a ladder in the coach review did nothing.** `CoachEntry.save` only sent
   `setWeights` when a ladder was left, so setting every set to the same load changed no number the
   correction could see and the old ladder was re-derived. It now sends an empty ladder when the
   entry had one and the edit collapsed it; `saveNumbers` records it and `applyEntryCorrection`
   deletes the ladder. New test in `sheets.coach-import.test.jsx`: build 50/50/55/55, reopen,
   flatten to 50, Done, Save → row prints `4 × 3 · 50 kg` and the imported week has no
   `setWeights`.
2. **ux 1 — a complex's ladder could be the wrong length for a movement.** `ComplexConfig` wrote
   the head's ladder unchanged to every member, so a 4-set movement paired with a 3-set head carried
   three loads. The writer now normalizes against each member's own set count. New test in
   `sheets.test.jsx` (3-set head + 4-set member → `[60,60,65]` and `[60,60,65,65]`), plus a live
   check in the same 3+4 configuration through the real complex sheet.
3. **ui 1 — the ladder label broke before its unit, leaving `kg` alone.** `weightsLabel` now joins
   a ladder's last load and unit with a non-breaking space (a single load keeps its plain space), so
   the line can only break before the ladder. Measured at 390 and 360 on the real render (see
   Validation); the pinned label expectations in the five test files were updated to `\u00a0`.
4. **ux 2 — the per-set steppers had no accessible name.** `Stepper` takes an optional `ariaLabel`
   used for the field and its ± buttons; `SetWeightsSheet` passes `Set {0}` and the unit, so each
   row reads "Set 3", "Decrease Set 3", "65 kg" instead of four identical groups. Asserted in
   `sheets.test.jsx` and checked in the live app.
5. **ux note — no unit in the ladder sheet.** Fixed with the same `unit` prop (visible in the live
   sheet).
6. **code notes (taken up).** `ComplexConfig.setSets` now goes through `writeMembers` (about nine
   lines shorter); the dead `entry.weight ?? setWeights[0]` fallback is gone in favour of the
   invariant; the `setWeightsSheet` export was removed because nothing imports it. The
   session-start "rule owns the load" test was strengthened with an `up` prescription to 62.5 —
   the old fixture would have passed even if the ladder were ignored.
7. **security notes (taken up).** `weightsLabel` filters non-finite ladder entries (a corrupt state
   cannot print "NaN kg"), and `bundleFromWeek` copies the ladder instead of aliasing the review
   entry's array. No injection sink was added — the print view still escapes the whole scheme.
8. **Not changed (deliberately).** The zero-load row still shows an empty value: the spec defines
   `weightsLabel` as `''` when there is no load, and the UI reviewer flagged it as a follow-up, not
   a defect. A long ladder still ellipsises in `.lrow-v` — DESIGN's documented value behaviour. The
   audit's "complex row sits flush under the Weight pill" was addressed before the review was read
   (`.sect-b` with a 12px top margin, commit 191e7bb).

Round-2 validation: whole suite 114 files / 1289 tests, build clean, browser re-check on 5180
(errors empty, session rows 60/60/65/65, in-session sheet still hides the row).

### Round 3 (rework)

Rework note: *"Fix ComplexConfig.setSets to write weight: ladder[0] for every member and add a
sheets.test.jsx case."*

- `frontend/src/sheets.jsx` (`ComplexConfig.setSets`): when the resized ladder is written to the
  members it now writes the ladder's first load as each member's `weight` too
  (`Object.assign(out, { weight: ladder[0], setWeights: ladder.slice() })`), and the comment says
  why. A member whose own row was edited (which clears its ladder and sets its own `weight`) can no
  longer end with `setWeights[0] !== weight`, so the older readers of `weight` (progression, print,
  backups) agree with the planned rows. The uniform path (head has no ladder) is unchanged.
- `frontend/src/sheets.test.jsx`: new case "writes the ladder's first load as the movement's
  weight" — head 60/60/65/65, member `{ weight: 70 }`, card Sets → 5; both members end
  `{ sets: 5, weight: 60, setWeights: [60,60,65,65,65] }`. Verified red on the round-2 code
  (`expected [60, 60], received [60, 70]`) by stashing `sheets.jsx` and re-running the file, then
  green with the fix.
- Browser, full UI path on 5180 (see Validation): created the card ladder through the sheet, edited
  the second movement's own Weight to 70, then raised the card's Sets to 5 — storage and the card
  heading both show the fixed, invariant-preserving state; `agent-browser errors` empty.
- Everything else from round 2 is unchanged: the whole suite is 114 files / 1290 tests, the build
  is clean, and the copy guards still fail only in the pre-existing, main-identical way recorded in
  criterion 10 (unchanged by this round; not this job's to fix).
