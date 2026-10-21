# Build — 004-undo-delete-workout

Commit: `8e6f3d1` — Undo deleting a logged workout

## Files changed

- `frontend/src/lib/undo.js` (new) — pure helper: `pushUndo`, `takeUndo`, `restoreWorkout`
  (reuses `insertChronological` from `lib/backfill.js`); `UNDO_MAX = 5`.
- `frontend/src/lib/undo.test.js` (new) — unit tests for cap/order/no-mutation,
  empty/non-empty pop, and chronological re-insertion.
- `frontend/src/store/useUI.js` — added `undo: []` and `toastAction: null` ephemeral fields
  (never persisted in `S`); `toast(msg, opts)` now takes an optional `{ action, duration }` and
  gained `dismissToast()`.
- `frontend/src/components/Toast.jsx` — renders the optional snackbar action (`Undo`) in the
  slot that was previously unused; running it dismisses the toast then fires the callback.
- `frontend/src/sheets.jsx` — `WorkoutDetail`'s delete now removes the workout, pushes it via
  `pushUndo`, closes the sheet, and shows the snackbar with an Undo action (10s window);
  `undoDelete` pops and re-inserts via `restoreWorkout`; confirm copy no longer says "for good".
- `frontend/src/locales/it.js` — replaced the "for good" line with "This removes it from your
  history." and added "Workout restored" ("Allenamento ripristinato"); reuses existing "Undo".
- `frontend/src/m3.components.css` — added `#toast .toast-act` (M3 text button, tokens only)
  and registered it in the shared `:focus-visible` ring list.
- `frontend/src/sheets.undo-delete.test.jsx` (new) — happy-dom flow test: open workout → ⋯ →
  Delete workout → confirm, assert the workout leaves `S.workouts`, the snackbar shows Undo,
  tapping it restores the workout intact in chronological order and clears the stack.

## How I tested it

```bash
cd frontend && npx vitest run src/lib/undo.test.js
# ✓ 1 file, 10 tests passed

cd frontend && npx vitest run src/sheets.undo-delete.test.jsx
# ✓ 1 file, 2 tests passed

cd frontend && npm test
# ✓ 109 files, 1168 tests passed
```

Also verified, outside the spec's three commands:

```bash
cd frontend && npm run build
# ✓ built cleanly in 267ms

cd frontend && node scripts/check-source-strings.mjs --strict
# 10 missing keys, all pre-existing (none introduced here); the three new/reused
# keys ("This removes it from your history.", "Workout restored", "Undo") are all defined.
```

No new runtime dependencies; no network/server code. Undo is ephemeral UI state only.

---

## Round 2

Commit: `62d8dac` — Harden the undo action after review round 1

### Files changed (this round)

- `frontend/src/sheets.jsx` — `del` captures the current `S` entry at delete time
  (`S().workouts.find(x => x.id === w.id) || w`) and pushes that instead of the stale `w` prop,
  so a note edited before deleting survives Undo. `undoDelete` is bound to the deleted workout's
  id (no-op unless it is still on top) and guards against re-inserting a duplicate id.
- `frontend/src/m3.components.css` — `#toast .toast-act` now sets `pointer-events:auto` (the bar
  keeps index.css's pass-through `none`), grows to `min-height:44px`, paints the label
  `var(--m3-on-surface)` (was `--m3-primary`, 4.11:1 on the press layer), and joins the
  `@media (hover:hover)` hover-layer block. `#toast` padding trimmed to `6px` with a
  `min-height:48px` so the bar stays ~48–56px instead of ballooning past ~70px.
- `frontend/src/m3.tokens.test.js` — disk-reading assertions: the action re-enables
  `pointer-events:auto` while the bar does not, sizes at 44px, paints on-surface, and is in the
  hover block.
- `frontend/src/lib/contrast.test.js` — asserts `--m3-on-surface` stays >= 4.5:1 over the 10%
  press layer on the snackbar surface, per scheme, and quotes the measured ratio.
- `frontend/src/sheets.undo-delete.test.jsx` — added the note-edit regression (edit → delete →
  undo → edited text survives) and the double-fire binding test (a second fire restores its own
  workout, not an older deletion). Now 4 tests.

### How I tested it (this round)

```bash
cd frontend && npx vitest run src/lib/undo.test.js
# ✓ 1 file, 10 tests passed

cd frontend && npx vitest run src/sheets.undo-delete.test.jsx
# ✓ 1 file, 4 tests passed

cd frontend && npx vitest run src/m3.tokens.test.js src/lib/contrast.test.js
# ✓ 2 files, 93 tests passed

cd frontend && npm test
# ✓ 109 files, 1174 tests passed

cd frontend && npm run build
# ✓ built cleanly in 215ms
```
