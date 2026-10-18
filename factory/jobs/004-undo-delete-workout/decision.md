APPROVE

# Decision — 004-undo-delete-workout (round 2)

Final commit reviewed: **62d8dac** on `factory/004-undo-delete-workout` ("Harden the undo action
after review round 1"), the branch tip. Product diff `chore/factory...62d8dac` is 10 files, all
under `frontend/`; no `package.json`/lockfile change.

## Why approve

All four round-2 reviews are PASS (security, ux, ui, code). Round 1 was security/ux/ui CHANGES and
code PASS; the round-2 commit closes every blocking finding, and the fixes are pinned by tests that
provably fail on the round-1 commit (`8e6f3d1`), not just green at HEAD:

- **Data loss fixed.** Delete snapshots the live `S` entry (`sheets.jsx:1373`), not the stale `w`
  prop captured when the sheet opened, so a note edited before deleting survives Undo. A
  note-edit regression drives the real textarea → blur → ⋯ → Delete → confirm → Undo flow.
- **Undo is tappable on a device.** `pointer-events:auto` is scoped to `#toast .toast-act`
  (`m3.components.css:979`) while the 520px bar keeps the pass-through `pointer-events:none` from
  `index.css`; the action measures a 44px-high target (61.1 × 44.0 in a real engine at 320/360/500px),
  the bar grows to 56px, and the label is `--m3-on-surface` with 10.95:1 light / 6.84:1 dark on the
  press layer (round 1's primary was 4.11:1). Hover and focus states exist; tokens only.
- **Action targeting hardened.** `undoDelete` no-ops unless the deleted workout's id is still on
  top of the stack and the duplicate-id guard prevents a second history row for one id; a
  double-fire test proves the second fire cannot restore an older deletion.
- **Scope held.** Undo stays ephemeral (`useUI`, never `S`/localStorage/nativeSave), the delete still
  goes through the `.center` + `.mback` confirm (no `confirm()`/`alert()`), copy is bilingual
  (`"This removes it from your history."` + `"Workout restored"` with `it.js`; `"Undo"` reuses the
  existing key), no new runtime dependency, no network/server code.

## Verification I ran

- `cd frontend && npm test` → **109 files / 1174 tests pass** (matches build.md round 2).
- Read the product diff myself: `lib/undo.js` is pure and delegates ordering to
  `insertChronological`; `useUI.js` adds `undo`/`toastAction` plus `dismissToast`; `Toast.jsx`
  renders the action only when present and dismisses before firing; `sheets.jsx` captures `current`
  from `S` before filtering and closes the sheet after the push; `it.js` keys match
  `sheets.jsx`; the CSS block uses tokens and no raw colour/radius/shadow.
- Confirm the unmount note flush cannot resurrect a deleted workout (`if (!rec) return`), so
  delete → Undo → restore is safe.

## Residual non-blocking notes (recorded, not gating)

1. **In-flight note not captured.** `current` comes from `S`; a note still only in the textarea at
   confirm time would not be in the snapshot. No UI path reaches Delete without blurring the
   textarea first (the ⋯ tap moves focus), so it is unreachable today. A follow-up could call
   `saveNote()` at the top of `onConfirm` to make it airtight.
2. **`|| w` fallback.** If the workout were already absent from `S`, the stale prop is pushed;
   unreachable while the sheet blocks navigation, but it is the last path back to a stale object.
3. **Short-circuited restore still toasts "Workout restored".** When the duplicate-id guard keeps a
   re-imported copy, the stack entry is consumed and the message is cosmetic (security/code both
   logged it).
4. **Snackbar floats over open sheets.** `#toast` z-index 200 > `#modal-root` 100, so for the 10s
   window the now-live action can sit above an open sheet's scrim. The delete flow ends with the
   sheet closed, so this job's promise is unaffected; a cheap follow-up is dropping `toastAction`
   in `openSheet`.
5. **Auto-dismiss can eat focus** at 10s if the action holds focus (Material pauses the timer on
   focus/hover; not addressed).
6. **Entries 2–5 of the bounded stack are unreachable** and the stack is not cleared when the
   action snackbar is replaced/expires. Per the spec's "latest delete only" scope, but worth
   remembering if the snackbar ever supports more actions.
7. **Test/comment polish.** `contrast.test.js` hard-codes `--m3-on-surface` rather than reading the
   declared colour out of `#toast .toast-act` (it is paired with the token test that pins the
   role); `m3.tokens.test.js` checks the *component* sheet for the bar's absent
   `pointer-events` rather than `index.css`'s `none`; the `.toast-act:hover` assertion just
   matches the selector text; the duplicate-id guard has no test; `m3.components.css`'s comment
   says ">=8:1 in both themes" where the dark press layer is 6.84:1; `del` in `sheets.jsx` is a
   long one-liner.

None of these changes the verdict: the job's acceptance criteria are met, the round-1 blockers are
genuinely fixed, the full suite and build are green, and the change is device-local and ephemeral
as specified.

## Merge

Merging `factory/004-undo-delete-workout` into `main` with `--no-ff` (merge commit, no force).
