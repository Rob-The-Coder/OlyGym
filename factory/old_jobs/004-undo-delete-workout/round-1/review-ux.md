VERDICT: CHANGES

## Blocking

1. **The Undo action cannot be tapped.** `#toast` still carries `pointer-events:none` from
   `frontend/src/index.css:1259-1267`. `frontend/src/m3.components.css:959-978` restyles the
   snackbar but never re-enables pointer events, and `pointer-events` inherits, so `.toast-act`
   computes to `none` too. On a phone every tap on Undo passes through the snackbar to whatever
   sits behind it (on History, a workout row) and the snackbar stays up until its timer runs out.
   The happy-dom flow test cannot catch this: happy-dom applies no CSS, and `.click()` bypasses
   hit-testing, so the test is green while the feature is dead on device.
   Fix in `m3.components.css`: add `pointer-events:auto` to `#toast .toast-act` (not to
   `#toast.show`, or the 520px bar swallows taps on the list underneath for the whole window).
   Because no DOM test can catch a hit-test problem, add a disk-reading CSS assertion in the
   style of `m3.tokens.test.js` / `contrast.test.js`.

2. **Undo is ~37px tall, under the 44px target.** `font-size:14px`, the `#toast` `line-height:1.35`
   inherited through `button{font:inherit}`, `padding:9px 12px` → 18.9px of text + 18px of padding
   = 36.9px box height (about 60px wide). DESIGN.md §8 sets 44px as the minimum for anything a thumb
   presses, and this is the one control that lives in the hardest-to-reach strip of a phone screen.
   The `.dlg-btn` precedent does not apply: a dialog action inside a 320px modal and a floating
   one-hand Undo are different targets. Add `min-height:44px` (or `padding:13px 14px`) to the same
   rule.

## Checked and fine

- **Confirm dialog.** Delete still goes through `confirmSheet` (`frontend/src/sheets.jsx:98-100`),
  which opens `kind:'center'` and renders `.mback` + `.center` from
  `frontend/src/components/Modals.jsx:112-118`. The flow test asserts `kind === 'center'`; there is
  no `window.confirm()` / `alert()` anywhere in `frontend/src`. The new copy "This removes it from
  your history." is in both locales.
- **Window and reach.** `bottom: calc(96px + var(--sab))`, full width minus 32px, action on the right
  edge: right thumb, clear of the floating tab bar. 10000ms sits inside M3's 4–10s window for an
  action snackbar and well above the 2200ms message default. Once finding 1 is fixed this is the
  right shape.
- **Empty stack.** `takeUndo([])` returns `{ workout: null }` and `undoDelete` returns before
  touching `S`. No crash, no false "Workout restored". It is unreachable through the UI (the
  action only exists after a push), so it is defensive only.
- **Double-tap Undo.** The first tap calls `dismissToast()` and the button unmounts before a second
  discrete click can land, so no double restore. One hardening: `undoDelete` pops the top of the
  stack rather than the workout the toast was created for, so if it ever ran twice it would restore
  a different, older deletion. Bind the action to `w.id` (and no-op unless it is still on top).
- **"Workout restored" closes the loop.** Tapping Undo dismisses the action snackbar and replaces it
  with a message-only 2.2s toast; `toastAction` goes null, so no dangling Undo. Deletion promises the
  undo, tap delivers it, the restored workout is back chronologically in History. It does not say
  where it went, which is acceptable and avoids a new string.
- **Copy and tests.** `Undo` reuses the existing `it.js` key ("Annulla"); "Workout restored" is added
  in both locales; no "for good" remains. Targeted tests (`undo.test.js`, `sheets.undo-delete.test.jsx`)
  pass, 12/12; full suite 109 files / 1168 tests green. None of those tests can see findings 1–2, which are CSS/hit-test problems.

## Non-blocking

- A second delete inside the 10s window replaces the first Undo: the older workout stays on the
  stack but nothing can reach it (cap 5, no paging). The spec allows this, but entries 2–5 are dead
  state — either accept it explicitly or drop an entry when its snackbar is replaced.
- The 10s timer does not pause on focus: a keyboard or screen-reader user who reaches Undo late
  loses focus when the snackbar clears. Material pauses the timeout on focus/hover.
- `role="status"` wraps the button (`frontend/src/components/Toast.jsx:10-12`). TalkBack/VoiceOver
  announce "Workout deleted, Undo" as one status; whether the action reads as actionable needs a
  device check, and a separate live region for the message plus the button outside it is the safer
  shape.
