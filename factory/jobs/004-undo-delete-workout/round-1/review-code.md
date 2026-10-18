VERDICT: PASS

# Review — 004-undo-delete-workout, round 1

Scope reviewed: the 8 product files in `git diff chore/factory...factory/004-undo-delete-workout`
(factory docs/board excluded). No `frontend/package.json` or lockfile change: no dependency added.

## Findings

- **`frontend/src/lib/undo.js` — correct and pure.** No React/store imports; the only import is the
  existing `insertChronological` from `lib/backfill.js`. `pushUndo` builds a new array and trims from
  the front, `takeUndo` slices and returns `null` on empty, `restoreWorkout` delegates ordering to
  the helper that already owns it (and is already covered by `backfill.test.js`). No input is mutated.
- **`frontend/src/lib/undo.test.js` — covers the required matrix.** 10 tests: append order, cap at
  `UNDO_MAX` dropping the oldest, custom max, no mutation for `pushUndo`; pop non-empty, empty, no
  mutation for `takeUndo`; chronological insertion by date, same-day `start` ordering, no mutation
  for `restoreWorkout`.
- **`frontend/src/store/useUI.js` — undo stays ephemeral.** `undo: []` and `toastAction: null` live in
  the Zustand UI store, which has no persist middleware and never writes into `S`/localStorage.
  `toast(msg, opts)` remains backward compatible (every other caller passes one argument) and both
  the timeout and `dismissToast` clear `toastMsg` and `toastAction` together.
- **`frontend/src/sheets.jsx` — wiring is correct.** Delete still goes through `confirmSheet`
  (`kind: 'center'`, `.mback`), then filters `S.workouts`, pushes via `pushUndo`, closes the detail
  sheet and shows the snackbar with the `t('Undo')` action and a 10s window. The action closure only
  uses module-level `update`/`toast`/`t` and the UI store, so it keeps working after `WorkoutDetail`
  unmounts. Undo pops with `takeUndo`, re-inserts with `restoreWorkout`, and confirms with
  `t('Workout restored')`. `takeUndo` returning `null` makes a second tap a no-op.
- **`frontend/src/components/Toast.jsx` + `m3.components.css` — one token-only action.** The button
  is rendered only when `toastAction` exists, and it dismisses before firing the callback. Styling
  uses `--m3-primary`, `--m3-shape-full`, `--m3-type-*` and state tokens only (no raw colour, radius
  or shadow); it is in the shared `:focus-visible` list and `#toast` has no `pointer-events` block.
- **Copy — bilingual and consistent.** English source key `'This removes it from your history.'` is
  used in `sheets.jsx` and translated in `locales/it.js`; `'Workout restored'` is new in both
  (key + it) and `'Undo'` reuses the existing key (`it.js:771`). `check-source-strings.mjs` does not
  report any of the three keys as missing.
- **Tests and build.** `npx vitest run src/lib/undo.test.js src/sheets.undo-delete.test.jsx`: 12/12
  pass. `npm test`: 109 files / 1168 tests pass. `npm run build`: clean. The flow test mounts the real
  `Toast`, so the snackbar action is exercised, not just the store.

## Non-blocking notes

- Entries stay on the `undo` stack after the 10s snackbar expires (bounded at `UNDO_MAX = 5`). They
  are unreachable from the UI (only the latest delete is offered) and invisible to the user, so this
  is only a small retained-memory detail; clearing the stack when the action's toast is dismissed
  would make "tied to the snackbar's window" literal. Not required by the acceptance criteria.
- `sheets.undo-delete.test.jsx`'s cap test is named "... without mutating the stored workouts" but
  only asserts the capped stack order. Helper-level no-mutation is covered by `undo.test.js`.
