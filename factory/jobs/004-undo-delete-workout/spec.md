# Goal

Make deleting a logged workout undoable. Deleting already asks for confirmation through a
`.center` + `.mback` modal (via `confirmSheet` in `frontend/src/sheets.jsx`). After the
user confirms, instead of removing the workout for good, the app moves it onto a small in-memory
undo stack and offers an M3 snackbar action (through `useUI().toast`) that restores it. The
stack itself is a pure, framework-free helper in `frontend/src/lib/undo.js` with a unit test
beside it, per CONTRIBUTING.md.

# Out of scope

- Undo for anything except deleting a logged workout (weigh-ins, exercises, routines,
  days/weeks, competitions keep their current behaviour).
- Undo that survives a reload or app restart — the stack is ephemeral, lives in `useUI`
  (not persisted `S` / `localStorage`), and is tied to the snackbar's window.
- A browsable undo-history UI: the snackbar offers undo for the most recent delete only; the
  stack exists to bound and queue, not to page through.
- Any server call, sync, or new runtime dependency (React, Router, Zustand and
  `@capacitor/*` remain the whole budget).

# Acceptance criteria

- [ ] `frontend/src/lib/undo.js` exports pure helpers that never mutate their inputs and
  always return new arrays/objects:
  - `pushUndo(stack, workout, max = UNDO_MAX)` appends the deleted workout (most recent
    last) and drops the oldest beyond `UNDO_MAX` (a small constant, e.g. 5).
  - `takeUndo(stack)` returns `{ stack, workout }` popping the most recent entry, or
    `{ stack, workout: null }` when the stack is empty.
  - `restoreWorkout(workouts, workout)` returns `workouts` with the workout re-inserted in
    chronological order (reusing `insertChronological` from `lib/backfill.js`), so History's
    reversed view puts it back in the right month.
- [ ] `frontend/src/lib/undo.test.js` covers: `pushUndo` cap/order and no-mutation,
  `takeUndo` empty/non-empty and no-mutation, and `restoreWorkout` chronological
  re-insertion.
- [ ] The undo stack is ephemeral UI state: a new `undo: []` field in `store/useUI.js`,
  never added to the persisted `S` object.
- [ ] Confirming "Delete workout" in `WorkoutDetail` (`sheets.jsx`) removes the workout from
  `S.workouts`, pushes it via `pushUndo`, closes the sheet, and shows the snackbar with an
  Undo action — all still behind the existing `.center` + `.mback` confirm modal (never
  `confirm()` / `alert()`).
- [ ] `useUI().toast` and `components/Toast.jsx` gain an optional action (the documented
  "room for one action on the right, which nothing uses yet"): an M3 text button using M3 tokens
  only (no raw colour/radius/shadow) that runs the action and dismisses the toast. The undo
  snackbar stays visible long enough to tap Undo (not the message-only 2200ms window).
- [ ] Tapping Undo pops the workout via `takeUndo` and re-inserts it via `restoreWorkout`,
  restoring its entries, sets, PRs and note at the correct chronological position; a
  "Workout restored" toast confirms it.
- [ ] Copy stays bilingual: the confirm message no longer claims "for good" (English source key
  + `locales/it.js`), the Undo action reuses the existing `Undo` key, and the restore
  feedback is a new key in both locales.
- [ ] `cd frontend && npm test` passes (full vitest run); no new runtime dependencies and no
  network/server code.

# Test plan

```bash
cd frontend && npm test
cd frontend && npx vitest run src/lib/undo.test.js
cd frontend && npx vitest run src/sheets.undo-delete.test.jsx
```

- `src/lib/undo.test.js` — unit-tests the pure helper: `pushUndo` (cap at `UNDO_MAX`,
  most-recent-last, input not mutated), `takeUndo` (empty vs non-empty, input not mutated),
  and `restoreWorkout` (chronological re-insertion). Run with
  `npx vitest run src/lib/undo.test.js`.
- `src/sheets.undo-delete.test.jsx` (`@vitest-environment happy-dom`) — mounts
  `workoutDetailSheet`, opens `⋯ → Delete workout` → the confirm dialog, confirms, then
  asserts: the workout leaves `S.workouts`, the snackbar shows an Undo action, tapping it
  restores the same workout (id, entries/sets/PRs intact) in chronological order and clears the
  stack. Run with `npx vitest run src/sheets.undo-delete.test.jsx`.
- Full suite gate: `cd frontend && npm test`.
