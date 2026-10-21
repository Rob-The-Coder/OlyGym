VERDICT: CHANGES

# Security review — 004-undo-delete-workout, round 1

Scope: the 8 product files in `git diff chore/factory...factory/004-undo-delete-workout`
(`frontend/` only; factory docs and board excluded). No `frontend/package.json` or lockfile
change. Read `SECURITY.md`.

## Blocking

- **Data loss: Delete pushes a stale snapshot, so a note edited before deleting is reverted by Undo.**
  `frontend/src/sheets.jsx:1367` pushes the `w` prop — the object captured when the sheet was
  opened by `workoutDetailSheet(w)` (`sheets.jsx:1412`) — not the workout as it is in `S` at
  delete time. The session note is edited in this same sheet through `saveNote` / the unmount
  flush (`sheets.jsx:1312-1332`), which writes into a **deep-cloned** `S`
  (`store/useStore.js:140-143`; `clone = JSON.parse(JSON.stringify(o))` at line 76). After any
  note save, `w` is a stale snapshot, so Undo re-inserts the pre-edit object.
  - Reproduced: open a workout whose note is "old note", save "NEW note typed before delete" (the
    exact store write `saveNote` does), delete through the real ⋯ → Delete workout → confirm
    flow, tap Undo. `useUI.undo[0].note` and the restored `S.workouts[0].note` are both
    `"old note"`; the edit is gone.
  - The flow test misses it because it never edits the workout before deleting
    (`sheets.undo-delete.test.jsx:62-100`), and the note is the only mutable field in this sheet.
  - Fix: capture the current entry from `S` at delete time and push that, e.g.
    `const current = S().workouts.find(x => x.id === w.id) || w` at the top of `onConfirm`, then
    filter and `pushUndo(..., current)` (or assign it inside the `update` producer before the
    filter). Add a regression test: edit the note, delete, undo, assert the new text survives.

## Non-blocking

- Undo is only reachable from the 10 s snackbar. Once it expires, is replaced by another toast, or
  the app reloads, the delete is permanent even though up to 5 workouts may still be in memory.
  That matches the spec's "ephemeral, latest delete only" scope, but the new confirm copy
  ("This removes it from your history.") no longer hints that undo is time-boxed; consider wording
  that does, or accept as designed.
- `undoDelete` (`sheets.jsx:1360-1366`) restores without checking for an existing workout with
  the same id. No reachable path today (backup import/reset replace the toast action), but a
  duplicate id would yield two history rows with the same React key; a cheap
  `if (s.workouts.some(x => x.id === workout.id)) return` guard would be defensive.
- `UNDO_MAX = 5` with a single-slot snackbar makes entries 2–5 unreachable; they only bound
  memory and silently discard the oldest. Per spec, fine — no user-facing benefit.

## Checks that pass

- **Secrets:** none added. No credentials, keys or tokens; the only `token` references in
  `sheets.jsx` are the pre-existing Google Drive import, untouched by this diff.
- **Network/server:** none introduced. No `fetch`/XHR/endpoint and no new dependency. Undo is
  device-local, in-memory UI state (`store/useUI.js:84-85`), never written to the persisted
  `S`/`localStorage` or the Android file mirror.
- **Input validation:** the stack only ever receives internal `S.workouts` objects and internal
  toast callbacks; no user string is evaluated or parsed. `pushUndo`/`takeUndo` are pure,
  handle the empty stack, and a double-tap is a no-op because the pop happens first
  (`lib/undo.js:17-22`).
- **XSS:** the snackbar label and message render as React text nodes (`components/Toast.jsx:10-12`);
  no `dangerouslySetInnerHTML`, no HTML/URL injection path.
- **On-disk exposure:** unchanged. The delete still persists immediately; the undo copy adds no new
  file or `localStorage` copy and is dropped on reload.
- **Tests:** `npx vitest run src/lib/undo.test.js src/sheets.undo-delete.test.jsx` → 12/12 pass.
