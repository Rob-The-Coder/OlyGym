VERDICT: PASS

# Security review — 004-undo-delete-workout, round 2

Scope: the round-2 diff `git diff 8e6f3d1..62d8dac` (5 files: `frontend/src/sheets.jsx`,
`frontend/src/m3.components.css`, and three test files) plus the full product diff
`chore/factory...factory/004-undo-delete-workout` (10 files, all under `frontend/`; factory docs
and `board.json` excluded). No `frontend/package.json` or lockfile change. Read `SECURITY.md`.

## Blocking

None. The round-1 data-loss bug is fixed and pinned by a regression test that provably fails on
the old code.

## Round-1 blocking finding — verified fixed

**Delete now snapshots the live `S` entry, not the stale `w` prop.**
- `frontend/src/sheets.jsx:45` (`const S = () => useStore.getState().S`) was already the live
  store read; `sheets.jsx:1373` now does `const current = S().workouts.find(x => x.id === w.id) || w`
  inside `onConfirm`, before the filter, and pushes `current` with `pushUndo`. The note is the
  only mutable field in this sheet, and the `saveNote`/`onBlur` write lands in the deep-cloned
  `S` (`store/useStore.js:140-143`), so the captured object carries the edit.
- The regression test
  (`frontend/src/sheets.undo-delete.test.jsx:120-147`) types a new note through the real textarea,
  dispatches the `focusout` the real ⋯ tap produces (`blur`, line 129), asserts `S` holds the new
  text while the `w` prop still holds `"felt strong"` (lines 130-131), then drives the real
  ⋯ → Delete workout → confirm flow, taps the real `Toast` `.toast-act`, and asserts the restored
  workout has the edited note (line 146).
- I checked the test is a genuine pin rather than a tautology: on a detached worktree at
  `8e6f3d1` (round 1), the same test file fails with
  `expected 'felt strong' to be 'NEW note typed before delete'`. The double-fire test also fails
  there (it restores `w2` on the second fire). Cleaned the worktree afterwards.

**No re-inserted duplicate.** `undoDelete` (`sheets.jsx:1360-1373`) binds the action to the deleted
workout's id — it no-ops unless that id is still on top of the stack — and the `update` producer
returns early if `S.workouts` already has an entry with that id (re-import path). The new
double-fire test (`sheets.undo-delete.test.jsx:149-164`) deletes two workouts, fires the same
action twice, and asserts the second fire restores nothing and does not pop the older entry. The
duplicate guard means "Undo" can never produce two history rows for one id.

## New data-loss paths — checked, none found

- **Capture order.** `current` is read from `S` before `update` filters it, so the pushed object is
  the pre-delete snapshot, not the filtered state. `update` clones `current`'s container, so the
  snapshot is not mutated by the subsequent filter/persist.
- **Unmount note flush.** `WorkoutDetail`'s cleanup (`sheets.jsx:1312-1332`) already guards
  `if (!rec) return` — after the delete filter it cannot resurrect the deleted record or write a
  stale note over the restored one. The flush that was designed to rescue a dismissed sheet cannot
  fire into a deleted workout.
- **Same-session re-delete.** Delete → Undo → delete again pushes a fresh snapshot from the current
  `S`; the old toast action is unmounted when the action toast is replaced, so no stale closure is
  reachable in the UI. Residual theoretical edge: a stale same-id action fired after a re-delete
  would pop the new stack entry and then no-op on the duplicate guard; unreachable, and even then
  it loses only the undo pointer, not data.
- **Re-import / reset while the snackbar is up.** If a backup import has already re-added the same
  id, Undo short-circuits on the duplicate guard, so it cannot overwrite the imported copy with the
  older snapshot. The stack entry is dropped and "Workout restored" still shows — cosmetic only.

## Undo stays ephemeral — verified

`undo: []` and `toastAction: null` live only in `store/useUI.js:84-85`; that store has no persist
middleware and no `localStorage`/`nativeSave` write (`grep` over `useUI.js` and the whole `src`
tree finds no persistence of either field). The snapshots are dropped on reload, exactly as the
spec requires; the deletion itself still persists immediately and the snapshot adds no new on-disk
copy.

## Secrets, network, server — verified absent

- No credentials, keys or tokens added. The only diff matches for "token" are the existing Google
  Drive import and the filename `m3.tokens.test.js`; this feature does not touch either.
- No `fetch`/XHR/endpoint, no new runtime dependency, no server code. Everything is local
  in-memory state plus existing `update`/`persist`.
- No `eval`, no `dangerouslySetInnerHTML`. The snackbar label comes from `t('Undo')` and renders as
  a React text node (`components/Toast.jsx:10-12`); note text is React-escaped like before and is
  still trimmed/capped by `NOTE_MAX` in `saveNote`.
- `UNDO_MAX = 5` bounds retained snapshots; nothing grows without limit.

## Verification run

- `npx vitest run src/lib/undo.test.js src/sheets.undo-delete.test.jsx src/m3.tokens.test.js
  src/lib/contrast.test.js` → 4 files / 107 tests pass.
- `cd frontend && npm test` → **109 files / 1174 tests pass** (matches the build note).
- New tests fail on `8e6f3d1` (worktree experiment above), so the regression and double-fire
  protections are real.

## Non-blocking

- **In-flight note not captured.** `current` comes from `S`; a note that is still only in the
  textarea (typed but never blurred) would not be in the snapshot. Today there is no route to
  Delete that does not move focus out of the textarea first — the ⋯ button must be tapped, and its
  `focusout` saves the note — so this is not reachable in the UI, but merging the component's
  `note` state into the snapshot (or calling `saveNote()` first) would make it airtight.
- **`|| w` fallback.** If the workout is somehow absent from `S` at confirm time, the stale prop is
  pushed and Undo would resurrect that older snapshot. No UI path gets there while `S` lacks the
  record (sheets block navigation), and it cannot lose data that still exists, but it is the one
  remaining path back to a stale object.
- **"Workout restored" on a short-circuited restore.** When the duplicate guard fires (re-import),
  the toast still says restored though nothing changed. Cosmetic.
- **Undo is still time-boxed to the 10 s snackbar** and the new copy no longer says so; per spec's
  "ephemeral, latest delete only" scope. Entries 2-5 on the stack are unreachable, as before.
- **pointer-events:auto on .toast-act** (round-2 CSS change) is scoped to the action only; the
  520 px bar keeps index.css's pass-through `pointer-events:none`, so no new full-width hit target
  intercepts list taps. Verified by reading both sheets and the new token assertions.

## Checks that pass

- Fix for the round-1 data loss, with a regression test that fails on the old commit.
- No new data-loss path; no duplicate restoration; no stale-note overwrite.
- Undo ephemeral (no `localStorage`, no native file mirror, no `S` field).
- No secrets, network calls, server code, dependency or package-lock change.
- XSS surface unchanged (React text nodes only); inputs are internal objects, never parsed/eval'd.
- Full suite green at HEAD (109 files / 1174 tests).
