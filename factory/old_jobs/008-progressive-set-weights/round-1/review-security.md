VERDICT: PASS

## Findings
None.

## Notes
- This change runs entirely on-device (localStorage, no network, no endpoints, no auth, no
  payments), so there is no access-control, abuse or data-exposure surface to review. No new
  dependencies or config files are added (`git diff --stat` touches no `package.json`/lockfile,
  no `.env`, no keys, no native plugin), and no secret-like strings appear in the diff.
- The only raw-HTML sink the feature reaches is the print view. `plan-share.js` builds
  `scheme()` from the new ladder and still passes the whole scheme through `esc()` at
  `frontend/src/lib/plan-share.js:144`, so a ladder cannot inject markup there; every other
  consumer (week editor row, workout complex heading, coach review row, the "Weights per set"
  sheet) renders through React JSX, which escapes. No `innerHTML`, `dangerouslySetInnerHTML`,
  `eval` or URL interpolation is introduced.
- Ladder values arriving from the coach-review correction path are validated where it matters:
  `applyEntryCorrection` (`frontend/src/lib/import-plan.js:160-174`) accepts only arrays, keeps
  only finite numbers, clamps negatives to 0, and re-normalizes the length to the entry's set
  count; the review state that feeds `bundleFromWeek` therefore cannot carry a malformed ladder
  into `S.weeks`. The coach sheet parser is untouched, so no new untrusted-file parsing is added.
- Hardening, non-blocking: `weightsLabel` (`frontend/src/lib/history.js:149-153`) formats ladder
  entries with `fmtNum` without the `Number.isFinite` guard that `setWeightAt` and
  `normalizeSetWeights` use, so a hand-edited/corrupt `gym_state_v1` or an old backup with a
  non-numeric `setWeights` entry would print `NaN`/`0` in a plan row and the printout. Not
  injectable (all sinks escape) and device-local, so not blocking; filtering finite values in
  `weightsLabel` would make it as defensive as its neighbours.
- Hardening, non-blocking: `normalizeSetWeights` allocates an array of `sets` entries, and the
  Sets `Stepper` text field has no upper bound, so an absurd set count in a config or a review
  correction allocates a matching ladder. That is local, self-inflicted and the same class as the
  pre-existing "build that many session rows" behaviour, so it does not block.
- `bundleFromWeek` (`frontend/src/lib/import-plan.js:290-292`) stores the ladder by reference
  (`cfg.setWeights = entry.setWeights`) and `pushWeek` shallow-copies the exercise, so the review
  entry and the stored week share one array. No current writer mutates a ladder in place (all copy
  or re-normalize), so this is only an aliasing fragility worth a `slice()` if the file is touched
  again.
- The two i18n guard-script failures recorded in build.md criterion 10 are pre-existing on
  `main` and unrelated to this review; they are not a security matter.
