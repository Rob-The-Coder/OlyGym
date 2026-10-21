VERDICT: CHANGES

Scope: product diff `chore/factory...factory/004-undo-delete-workout` (8 files; `factory/*` excluded),
checked against DESIGN.md and M3 roles. Two release blockers, one contrast regression in the new
interactive states.

## Blockers

1. **The Undo button cannot be tapped.** `index.css:1266` sets `#toast{pointer-events:none}` so a
   hidden snackbar passes taps through, and `#toast.show` never restores it. `pointer-events` is
   inherited, so the new `.toast-act` inherits `none`. Measured on a replica of the exact new
   rules in headless Chrome: `getComputedStyle(.toast-act).pointerEvents === "none"` and
   `document.elementFromPoint()` at the button centre returns `HTML`, not the button. Keyboard
   Enter still works, and `sheets.undo-delete.test.jsx` passes because `.click()` bypasses
   hit-testing — the touch/mouse path, the one the snackbar exists for, is dead.
   Fix: `#toast .toast-act{pointer-events:auto}` (keep the bar itself pass-through), then verify
   with a real pointer, not `.click()`.

2. **Action target is ~37px tall**: `line-height:1.35` on 14px is 18.9px of text + 18px of
   padding = 36.9px (a headless replica without the app's `button{font:inherit}` measured 34 × 57.8).
   Under the 44px floor in DESIGN §8 and M3's 48dp snackbar action. Add `min-height:44px` (48 for
   M3) and trim `#toast`'s 13px block padding so the bar does not balloon to ~74px.

## Major — state-layer contrast

Base contrast passes: `--m3-primary` on `--m3-surface-container-highest` is 4.97–5.02:1 in light
and 6.73–6.83:1 in dark across all eight seeds. The state layer breaks it: the 10% `--m3-on-surface`
layer over the light surface is `#d0cdd6`, and primary on that is **4.11:1** — under AA 4.5 for the
14px label, and this is the persistent focused state, not just the transient press. A primary-based
layer is no better (4.40:1 at 10%), so reducing the alpha will not fix it. Dark is fine (5.17:1).
Fix: keep the focused/pressed label ≥4.5:1 — e.g. `--m3-on-surface` for the action label (13.3
light / 8.9 dark) with the accent kept for the ring, or a layer that lightens the bar — and quote
the measured ratio.

## Minor

- **No hover state.** `.toast-act` is missing from the `@media (hover:hover)` block at
  `m3.components.css:467` that gives `.btn/.iconbtn/.chip/.lrow/.item/.tile` theirs; on the PWA /
  wide layout the action has no pointer feedback. M3 text buttons have a hover layer.
- **Auto-dismiss can eat focus.** The 10s timer unmounts `toastAction` while the button can hold
  focus (focus drops to `<body>`, and a screen reader may be mid-announcement). Clear/pause
  `toastTm` on focus/pointerenter, or leave the action until dismissed.

## Passes

- **Tokens only.** The new rules use `--m3-primary`, `--m3-on-surface`, `--m3-state-press`,
  `--m3-shape-full`, `--m3-type-label-l-weight/-track`; no raw colour/radius/shadow/duration. The
  only literal is `font-size:14px`, which DESIGN §6 allows (sizes live per selector) and which
  matches `.btn`. The rule sits in the snackbar section, not at the file tail.
- **M3 text-button anatomy is right**: transparent container, primary label, label-l weight and
  tracking, full shape. Since the bar is `surface-container-highest` (not M3's `inverseSurface`,
  which this token set does not define), `--m3-primary` is the correct role; `inversePrimary`
  would be wrong here.
- **Focus ring present**: global `:focus-visible` outline (`index.css:128`, 2.5px accent) plus the
  10% layer via the shared list. Accent ring vs bar surface is 4.99/6.74:1 (>3:1).
- **Dark and light both covered**: same semantic surface, measured above.
- **Destructive confirm reads as error, not accent**: `danger` renders `.dlg-ico` with
  `error-container`/`on-error-container` and `.dlg-btn.err` uses `--m3-error`. Correct.
- **Copy/locales**: the "for good" line is gone, "Workout restored" is added to `it.js`, and "Undo"
  reuses the existing key (`it.js:771`). No missing new key.
- A normal toast clears `toastAction` (`null` default), so a stale Undo cannot reappear after an
  unrelated message.
