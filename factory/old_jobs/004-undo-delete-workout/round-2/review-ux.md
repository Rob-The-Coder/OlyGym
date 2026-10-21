VERDICT: PASS

# Review — 004-undo-delete-workout, round 2 (UX)

Scope: round-2 diff `8e6f3d1..62d8dac` and the full product diff
`chore/factory...factory/004-undo-delete-workout`, product files only (`frontend/`); factory
docs and `board.json` ignored. DESIGN.md read. Round-1 blockers re-checked in a **real browser
engine** (headless Chrome against the three shipped stylesheets), because happy-dom applies no CSS
and `.click()` bypasses hit-testing — the exact reason round 1's dead Undo shipped green.

## Blocking (round 1) — both fixed

1. **Undo is tappable now.** `m3.components.css:975-980` sets `pointer-events:auto` on
   `#toast .toast-act` only. The bar keeps index.css's pass-through
   `#toast{pointer-events:none}` (`index.css:1266`) and nothing in the component sheet overrides
   it, so the rule is on the child and only on the child. Measured in Chrome at a 360px and a
   320px viewport: computed `pointer-events` = `auto` on the button, `none` on the bar;
   `document.elementFromPoint` at the button centre returns the button, while a point on the bar's
   left (over the page text behind it) returns the element underneath. Taps on Undo land; taps on
   the 328px bar still pass through to the list.
2. **44px target, bar not ballooning.** The button measures **61.1 × 44.0px** at 320, 360 and
   500px viewports (`min-height:44px` + `box-sizing:border-box` + `padding:12px 14px`, text
   14px/1.35). The bar measures **328 × 56px** at 360 (`288 × 56` at 320, `468 × 56` at 500):
   the 44px flex item plus 6px padding, over the 48px floor. Round 1's geometry was a 36.9px action
   in a ~63px bar; this is +8px on the bar, not the ≥70px balloon I warned about. Message-only
   toasts are 48px — the action adds 8px, it does not resize the bar.

The colour change the fix came with is also clean: the label is `--m3-on-surface`, measured
against the real snackbar surface at **8.92:1 dark** (resting) and **13.30:1 light**, and
**10.95:1** on the light 10%-press layer — AA for a 14px label, where the old primary hit 4.11:1.
The action now has hover (in the shared `@media (hover:hover)` block, `m3.components.css:468`),
press, and a visible focus ring (`outline:2px solid`, seen in Chrome at 360px). No raw colour,
radius or shadow was introduced.

## States

- **Workflow.** `.center` + `.mback` confirm → delete → 10s snackbar with Undo → tap → restore in
  chronological order → "Workout restored" (message-only, action cleared). Exercised by the real
  `⋯ → Delete workout → Delete → Toast button` flow in `sheets.undo-delete.test.jsx:75-118`;
  the toast's own click handler dismisses before firing (`components/Toast.jsx:12`), so the
  "restored" toast cannot leave a dangling Undo (`useUI.js:107-116`).
- **Empty stack.** `undoDelete` peeks `undo.at(-1)` and returns before touching `S`
  (`sheets.jsx:1360-1366`), so a stray fire does nothing and shows nothing. Unreachable through
  the UI (the action only exists after a push); the `takeUndo([])` path is unit-tested.
- **Double-tap / stale action.** The guard binds to the deleted workout's id and the duplicate
  check stops a second history row (`sheets.jsx:1360-1372`). The new test (`…test.jsx:149-164`)
  deletes two workouts, fires the same action twice, and proves the second fire restores nothing and
  leaves the older stack entry alone — the round-1 hole where a second fire would restore a
  different deletion is closed.
- **Note edited before deleting.** `del` now snapshots the live `S` entry
  (`sheets.jsx:1373`) instead of the stale `w` prop, and the regression test
  (`…test.jsx:120-147`) types through the real textarea, blurs it as the ⋯ tap does, then
  restores the edited note. That was not a round-1 UX blocker but it is the right fix.
- **Copy.** "for good" is gone; both locales carry "This removes it from your history." and
  "Workout restored" (`locales/it.js:156,158`), and Undo reuses the existing key
  (`…:771`). The restore confirmation closes the loop.

Tests: `npx vitest run` on the four touched suites → 107 pass; full `npm test` → **109 files /
1174 tests**; `npm run build` → clean (428ms). All match the build note.

## Non-blocking

- **The action now floats over an open sheet.** `#toast` is z-index 200 (`index.css:1261`) and
  `#modal-root` is 100 (`index.css:1050`), so for the 10s window a 61×44 live target sits above
  a sheet's scrim and can overlap the bottom of a bottom sheet. Before this round the same region
  was dead, so the fix is what makes it a live target. Cheap follow-up: `dismissToast()` (or at
  least drop `toastAction`) in `openSheet`, or render the snackbar under `#modal-root`. The
  delete flow itself always ends with no sheet open, so nothing this job promises is affected.
- **Actions are still replaced, not queued.** Any other toast inside the 10s window (rest over, a
  favourite added) clears `toastAction` and strands the deleted workout on the stack — entries 2-5
  remain unreachable. Same trade round 1 accepted; now at least a stale action can never restore the
  wrong workout.
- **Auto-dismiss can still eat focus.** At 10s the focused action unmounts and focus drops to
  `<body>`. Material pauses the timer on focus/hover; not addressed this round, and no worse than
  other toasts in the app.
- **`role="status"` still wraps the button** (`Toast.jsx:10-12`). TalkBack/VoiceOver will announce
  "Workout deleted, Undo" as one status; whether the action reads as actionable needs a device
  check. Unchanged from round 1, cosmetic for sighted use.
- **Affordance trade-off.** Putting the label on `--m3-on-surface` removes the accent cue: the
  action is now the same colour as the message, separated by weight (500 vs 400), the right edge
  and a 12px gap. Contrast was the hard constraint and this satisfies it; if the app ever gains a
  darker accent role for light themes, revisiting is worthwhile.

Nothing here blocks. The two round-1 UX defects are fixed, the fix sits on the action alone, the
bar grew 8px rather than ballooning, and the workflow, empty and double-fire states all behave.
