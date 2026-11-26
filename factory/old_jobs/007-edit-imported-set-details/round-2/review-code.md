VERDICT: PASS

# Code review — 007-edit-imported-set-details (round 2)

Scope: round-2 commit `83ac684` on top of round-1 `f0ff48f` (5 files, +161/−19: `CoachImport.jsx`,
`import-plan.js`, its test, the screen test, `sheets.jsx`). Read the round-2 section of
`build.md`, checked each of my round-1 findings against the new code, and re-ran every validation
command myself.

## Round-1 findings

- **Finding 1 (the reset item could become a permanent no-op on a complex movement): fixed.**
  `saveNumbers` (`CoachImport.jsx:101-120`) now records only the fields that differ from the derived
  entry, so a reps-only fix no longer claims shared sets/load on the complex's siblings;
  `hasNumbers` (`:86-94`) now asks whether the reset can actually remove something — a
  `sets`/`weight` patch on the group, or `reps`/`sec` on the tapped movement. My exact repro is
  pinned by the new `resetting across a complex` tests (`sheets.coach-import.test.jsx:366`):
  correcting one movement's reps leaves the sibling's menu without the item, the corrected
  movement's menu has it and reset clears it; the shared-sets variant is resettable from either
  movement, keeps a sibling's own reps fix, and that sibling's menu still offers reset until it is
  cleared there. I also re-walked both round-1 traces against the new code.
- **Note 1 (copy guards): unchanged — still baseline-red** (see Checks/Notes). Round 2 adds no
  source literal and touches neither locale file.
- **Note 2 (`Number()` coercion): fixed.** `applyEntryCorrection` now accepts only
  `typeof === 'number'` finite values (`import-plan.js:137-151`); boolean/array/string patches are
  ignored and a `Symbol` no longer throws, pinned by `accepts a number as a count and nothing else`
  (`import-plan.test.js:281`).
- **Note 3 (a no-op Save recorded the numbers): addressed by the same diff-only recording.** An
  untouched Save stores nothing but still ticks the row (`does not claim a correction when Save
  changed nothing`, `sheets.coach-import.test.jsx:431`). Requirement 5's sheet→screen handoff is
  intact: the sheet still hands `{ sets, weight, reps }` / `{ sets, weight, sec }`.
- **Note 4 (week-switch persistence had no test): added** (`keeps each week's corrections when
  switching weeks and back`, `sheets.coach-import.test.jsx:446`): week 1 corrected, switch to week 2
  (its own uncorrected line), switch back (correction and tick intact); it also pins that one
  week's numbers do not leak into the other.
- **Note 5 (leftover temp dirs in the worktree): cleaned.** `git status --short` is empty and the
  `.tmp*` browser-profile artifacts are gone.

## Checks

- `cd frontend && npx vitest run src/lib/import-plan.test.js src/sheets.coach-import.test.jsx`: pass — 61/61 (33 lib + 28 screen), matching build.md's counts.
- `cd frontend && npm test`: pass — 113 files, 1247 tests.
- `cd frontend && npm run build`: pass — vite build, `dist/assets/index-*.js` 825.06 kB.
- `cd frontend && node scripts/check-locales.mjs`: fail — baseline-red (only `it.js` exists, so every key is "only here"); same failure as base `3941b8e`, verified in round 1.
- `cd frontend && node scripts/check-source-strings.mjs --strict`: fail — baseline-red; still 626 strings with the same 10 pre-existing keys defined in no pack, none from this job; round 2 adds no literal.
- `cd frontend && git diff --check main...HEAD`: pass.
- Commit/worktree: two commits with one-line subjects, round 2 touches the 5 expected files, no dependency/parser/matcher/store/doc drift, clean worktree.

## Findings

None.

## Notes

1. The copy guards remain baseline-red for the same pre-existing reasons as round 1; this branch's copy obligations hold (new keys present, old hint key gone, no new literal). Acceptance criterion 4 still needs a spec amendment or a separate locale-debt change, not work on this branch.
2. With the diff-only recording, a partially corrected row keeps the warning chip for a field the user did not edit (e.g. changing only reps on a `no sets given` row leaves that chip). That is the honest reading of "the warnings the correction answered", and the acceptance case edits both fields; noted so the behaviour is not mistaken for a regression.
3. The new Weight-on-its-own-row layout in `CoachEntry` (`sheets.jsx:1265-1276`) is the UI reviewer's call; from the code side it keeps the required Sets → Reps/Seconds → Weight order, every field-driven screen test still passes, and no other sheet is touched.
