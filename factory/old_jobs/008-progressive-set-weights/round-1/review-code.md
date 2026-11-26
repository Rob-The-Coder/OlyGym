VERDICT: CHANGES

## Checks

- `cd frontend && npx vitest run src/lib/history.test.js src/lib/session-start.test.js src/lib/import-plan.test.js src/lib/units.test.js src/lib/plan-share.test.js src/sheets.test.jsx src/sheets.coach-import.test.jsx src/views/WeekEdit.test.jsx src/views/Workout.test.jsx` — pass (9 files, 336 tests).
- `cd frontend && npm test` — pass (114 files, 1286 tests).
- `cd frontend && npm run build` — pass (exit 0).
- `node scripts/check-locales.mjs` — fail (exit 1). **Pre-existing, not caused by the branch**: it exits 1 on `main` as well (main: 1507/1507 keys, branch: 1508/1508) because this fork ships a single locale pack, so every key is reported "only here". The one new key (`Weights per set`) is present in `it.js`.
- `node scripts/check-source-strings.mjs --strict` — fail (exit 1), with the same 10 keys on `main` and on the branch (main: 626 translated strings, branch: 627; the new key is translated). Baseline read from `/home/roberto/Android/OlyGym/frontend` and compared here.
- Worktree clean; two commits with one-line messages; no stray paths (no `dist/`, `*.xlsx`, previews or `node_modules`).

## Findings

1. `frontend/src/sheets.jsx:1397-1401` — **flattening a ladder in the coach review silently does nothing** (spec gap: requirement 10 defers to requirement 7's "all-equal values collapse back to the uniform weight"). In `CoachEntry.save`, `patch.setWeights` is only sent `if (ladder)`; when the user opens *Weights per set* on a row that already carries a ladder and makes every set equal (e.g. 50/50/55/55 → 50/50/50/50), `openSetWeights` sets the local `ladder` to `null` and `weight` to the unchanged first load. The patch that `saveNumbers` receives then carries `sets` equal to the entry's, `weight` equal to the entry's and no `setWeights`, so nothing is recorded and the review re-derives the old ladder — the row still prints `4 × 3 · 50/50/55/55 kg` right after the user flattened it. The sheet shows `50 kg` when it closes, so this is a visible contradiction, not just a lost edit.
   Fix: also send an empty ladder when the entry had one and the edited ladder collapsed to nothing, e.g. after the `if (ladder)` block add `else if (shownLadder) patch.setWeights = []`. `saveNumbers` already records it (`sameSetWeights([], ladder)` is false) and `applyEntryCorrection` already drops an empty array (`values.length` 0 → `delete out.setWeights`, weight untouched), so the correction then clears the ladder on every movement of the complex. Add a case to `sheets.coach-import.test.jsx` ("per-set loads"): correct a row to 50/50/55/55, reopen, set all four to 50, Done, Save → the review row prints `4 × 3 · 50 kg` and "Add the week to my plan" stores no `setWeights`.

## Notes

Complexity lens (ponytail-review), all non-blocking:

- `frontend/src/sheets.jsx:1297-1310`: shrink: `ComplexConfig.setSets` re-locates the week day and its members inline (13 lines) although the render already holds `first`; compute `ladder` from `first` and write it through the existing `writeMembers(row => …)`. About 9 lines shorter.
- `frontend/src/lib/import-plan.js:287-292`: shrink: `cfg.weight = entry.weight ?? cfg.setWeights[0]` — the `??` is dead, `applyEntryCorrection` guarantees `entry.weight === entry.setWeights[0]`; `cfg.weight = entry.setWeights[0]` says the invariant instead of re-deciding it.
- `frontend/src/sheets.jsx:960`: delete: the `export` on `setWeightsSheet` — no module imports it (three call sites, all in this file); drop the keyword.
- net: -9 lines possible.

Other observations:

- AC10 ("both guards exit 0") cannot be met in this fork without editing the guard scripts or ten unrelated legacy strings — out of this job's scope; the build notes record it and main is identical. The branch adds no new locale/source-string failure.
- `frontend/src/lib/session-start.test.js` "lets the rule own the load" would also pass if the ladder were simply ignored: the fixture has no history or confirmed weight, so all rows are `cfg.weight` regardless. It pins the target assertion (no `setWeights`) but not that a rule decided the load; a `first`/`up` prescription with a different weight would make it bite.
- At 390px the week-editor row wraps `4 × 10 · 60/60/65/65 kg` onto three lines (`kg` alone on the last) — cosmetic, but the UI/UX lenses may want it kept on one line.
