VERDICT: PASS

## Checks

- `cd frontend && npx vitest run src/lib/history.test.js src/lib/session-start.test.js src/lib/import-plan.test.js src/lib/units.test.js src/lib/plan-share.test.js src/sheets.test.jsx src/sheets.coach-import.test.jsx src/views/WeekEdit.test.jsx src/views/Workout.test.jsx` — **pass** (9 files, 339 tests).
- `cd frontend && npm test` — **pass** (114 files, 1289 tests).
- `cd frontend && npm run build` — **pass** (exit 0).
- `node scripts/check-locales.mjs` — fail (exit 1), unchanged from round 1: it also exits 1 on `main` (main and branch report the same "only here" key list) because this fork ships a single locale pack.
- `node scripts/check-source-strings.mjs --strict` — fail (exit 1), unchanged from round 1: the identical 10 keys on `main` and on the branch (626 vs 627 translated strings; the branch's one new key is translated). The branch adds no new locale/source-string failure, so AC10's "exits 0" remains unattainable in this fork without out-of-scope guard changes.
- Worktree clean at `29f2b57`; round-2 commit message is one line ("Work the round-1 review into the per-set loads"); no stray paths (`dist/`, `*.xlsx`, previews, `node_modules`).

## Findings

None.

## Notes

Round-1 findings checked against `29f2b57`:

- **Finding 1 fixed.** `frontend/src/sheets.jsx:1404-1408` now sends the empty ladder when an entry that carried one is flattened (`else if (shownLadder) patch.setWeights = []`). `saveNumbers` records `[]` because `sameSetWeights([], ladder)` is false, and `applyEntryCorrection` drops the key (`values.length` 0), so the ladder goes, `weight` stays at the uniform first load, and every movement of a complex is covered through the shared patch. The new `sheets.coach-import.test.jsx` case exercises exactly the round-1 repro (correct to 50/50/55/55 → reopen → set all four to 50 → Done → Save) and asserts both the review row (`4 × 3 · 50 kg`) and that "Add the week to my plan" stores no `setWeights`; it passes.
- **Round-1 notes all taken up.** `ComplexConfig.setSets` now writes through `writeMembers` (the -9-line simplification); `bundleFromWeek` reads `cfg.weight` off the ladder instead of the dead `entry.weight ?? …`; `setWeightsSheet` is no longer exported; the session-start "rule owns the load" test now uses an `up` prescription to 62.5 and asserts the rows take it and the target keeps no ladder.
- **The 390px wrap is gone** the way the ui review asked: `weightsLabel` joins a ladder's last load and unit with U+00A0 (`frontend/src/lib/history.js:164`), so `60/60/65/65 kg` stays together and the line can only break before the ladder. A uniform load keeps its plain space, so every pre-existing one-load line is byte-identical. The five pinned label assertions now spell `\u00a0`; the rendered string is visually unchanged, but anything that reads `.textContent` (or copies it) now sees the non-breaking space.
- **New per-member ladder normalization** (`frontend/src/sheets.jsx:1313-1330`) is a sound fix rather than a spec change: pairing does not unify set counts, and writing one ladder length to every member would have printed a 4-load ladder on a 3-set movement and broken `setWeights.length === sets`. The new test (3-set head + 4-set member → `[60,60,65]` / `[60,60,65,65]`, both keeping `setWeights[0] === weight`) covers it.
- **Shared-component change is additive.** `Stepper`'s new `ariaLabel` is optional and only the ladder sheet sets it; the field's `aria-label` reaches the input through `NumberField`'s existing prop spread, and the ± buttons only gain the set name when the caller asks. The full suite and the build are green with the shared control untouched for every other caller.
- The round-2 diff is limited to the review responses (11 files, +139/−41); no docs change was needed for them.

Lean already. Ship.
