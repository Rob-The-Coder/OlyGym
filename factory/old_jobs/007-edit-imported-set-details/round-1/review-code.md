VERDICT: CHANGES

# Code review — 007-edit-imported-set-details

Scope: `git diff main...HEAD` — one commit `f0ff48f` on main `3941b8e`, 7 files (the pure
helper and its tests, the review screen, `sheets.jsx`, the Italian copy, `docs/DATA_IMPORTS.md`,
the screen test). Read `spec.md`, `build.md`, `AGENTS.md`/`CLAUDE.md` and `docs/DATA_IMPORTS.md`;
ran the validation commands myself rather than trusting build.md.

## Checks

- `cd frontend && npx vitest run src/lib/import-plan.test.js src/sheets.coach-import.test.jsx`: pass — 56/56 (32 lib + 24 screen), matching build.md's counts.
- `cd frontend && npm test`: pass — 113 files, 1242 tests.
- `cd frontend && npm run build`: pass — vite build, `dist/assets/index-*.js` 824.76 kB.
- `cd frontend && node scripts/check-locales.mjs`: fail — baseline-red, see Notes 1.
- `cd frontend && node scripts/check-source-strings.mjs --strict`: fail — baseline-red, see Notes 1.
- `cd frontend && git diff --check main...HEAD`: pass.
- Commit hygiene: one commit, one-line subject, only the 7 expected files; no dependency, parser, matcher, `WeekEdit` or workout-flow change.

## Findings

1. **`frontend/src/components/CoachImport.jsx:84-87` — the reset item can become a permanent no-op on one movement of a complex.** `hasNumbers` shows `Back to the coach's numbers` when *any* correction exists anywhere in the complex, but `resetNumbers` (`:106-121`) only removes the shared sets/weight plus the tapped movement's reps/seconds. Reproduce the simplest case: correct the reps of movement A (a save also records the shared sets/weight on both movements), then tap movement B and choose `Back to the coach's numbers` — the shared sets/weight are removed and A keeps its reps correction; tap B again and the item is back, but clicking it changes nothing (A's `{ reps }` survives the reset), and the dead item stays on B for the rest of the sitting. The same happens on A after resetting a doubly-corrected complex from A. That contradicts requirement 4's "The item disappears once there is nothing to reset". Fix: base visibility on what the reset can actually remove — a `sets`/`weight` correction on any movement of the group, or a `reps`/`sec` correction on the tapped entry — instead of `Object.keys(...).length` over the whole group. Add a screen test for that sequence (correct one movement's reps, reset from the other: the item goes on that row and remains on the movement that still carries a reps correction; resetting it there clears it too).

## Notes

1. **The two copy guards are red on base and on the branch, identically — not a regression.** I extracted `git archive 3941b8e frontend/scripts frontend/src` into a temp dir and ran both there: `check-locales.mjs` exits 1 on base and on the branch because `it.js` is the only locale file, so the cross-locale check flags every key "only here"; `check-source-strings.mjs --strict` exits 1 with the same 10 pre-existing keys defined in no pack on base and on the branch (the two missing-key lists are identical), none of them from this job. This branch's copy obligation is met: the new hint key and the three new keys are in `locales/it.js`, the old `Tap a row to change what it was read as.` key is gone, and no key added here is missing from the pack. Criterion 4 cannot be made green from this branch without unrelated locale-debt work or a spec amendment; build.md already documents it, and it should not be chased in the fix round.
2. `applyEntryCorrection`'s `Number(raw)` also coerces booleans/arrays and throws on a `Symbol`; unreachable from the sheet (it only ever hands over numbers), so this is hardening only if another caller appears — same point as the security review's note 1.
3. Saving the sheet without touching a field still records the row's numbers (and ticks it). That is build.md's documented reading of requirement 5's Save contract; no action, just confirm it is the intended product behaviour.
4. Requirement 3's "corrections survive switching weeks" has no test. The state path (`corrections[week]` plus the memo dependency) is correct by inspection; an optional screen test would pin it.
5. Worktree hygiene: the click-through left untracked `.tmp/` (~218 MB), `.tmp-chrome/` (~134 MB), `.tmp-ab/` and `.tmp-coach.csv` in the worktree. They are not in the commit, but they make the worktree dirty, so `git worktree remove` will refuse at merge — delete them (or remove with `--force`) when the round is done.
