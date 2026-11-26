VERDICT: PASS

# Security review — 007-edit-imported-set-details

Scope: `git diff main...HEAD` — commit `f0ff48f` on top of main `3941b8e`, 7 files
(`frontend/src/lib/import-plan.js`, `frontend/src/components/CoachImport.jsx`,
`frontend/src/sheets.jsx`, `frontend/src/locales/it.js`, two test files,
`docs/DATA_IMPORTS.md`). Read `spec.md`, `build.md`, `SECURITY.md`, `AGENTS.md`/`CLAUDE.md`.
Working tree clean. Re-ran the tests and probed the new helper directly; no security finding.

## Verified

**No secrets, credentials or config added.** I scanned every added line of the product diff for
keys/tokens/passwords/bearer headers/URLs/emails — none. The diff is the UI + one pure helper +
Italian copy + docs; no `.env`, key, certificate or CI file is touched. The only literal URL in
the worktree is the pre-existing GitLab issues link in `SECURITY.md`.

**No network or server code introduced.** No `fetch`/`XMLHttpRequest`/`WebSocket`/`sendBeacon`/
`EventSource`/`navigator.*` and no new `import()`/endpoint in the added lines. The feature is
entirely device-local: corrections live in a React `useState` (`frontend/src/components/CoachImport.jsx:46`)
and the only outbound flow is the pre-existing, user-initiated Google Drive import, untouched.

**No new runtime dependency, and no persisted-schema change.** `package.json` and
`package-lock.json` have a zero-line diff. No new store field, no migration, no writer: numeric
corrections never reach `S`/`localStorage`/`nativeSave` — they are merged into the derived review
by `reviewWeek` (`frontend/src/lib/import-plan.js:232`) and only reach the plan through the existing
confirm path (`bundleFromWeek` → `mergeWeek`, unchanged). A grep of the whole diff for
`localStorage`/`sessionStorage`/`gym_state_v1`/`nativeSave`/`useStore.setState` shows only a
comment and the two test seeds; the diff's only state writers are
`setCorrections`/`setChecked` in the component. The new test asserts `S.planAliases` is untouched
by a numeric save (`frontend/src/sheets.coach-import.test.jsx:305`).

**Input validation is whitelisted and normalising.** `applyEntryCorrection`
(`frontend/src/lib/import-plan.js:135-158`) reads only `sets`/`reps`/`sec`/`weight` from the
patch, rejects non-finite values, rounds counts to whole ≥ 1 and clamps weight to ≥ 0, and copies
the entry instead of mutating it (frozen entry/warns and frozen corrections map are covered at
`frontend/src/lib/import-plan.test.js:294-335`). Everything else on the row — `id`, `name`,
`note`, `src`, `tier`, `custom`, `mode`, `sg` and the unrelated warnings — is preserved, so a
correction cannot rewrite which exercise is imported or the coach's record row. The patch built by
the sheet is numbers only: `NumberField.commit` strips everything but digits and a decimal point
and clamps at 0 (`frontend/src/components/ui.jsx:37-45`), so no coach-file text becomes a number
through this path.

**No prototype pollution / object-injection path.** The patch keys are iterated from the fixed
`CORRECTION_NUMBERS` list plus an explicit `weight`, and the merge target is a fresh object spread
from the entry; `entry.key` is generated as `${day}:${row}:${i}` from sheet integers
(`frontend/src/lib/import-plan.js:200`), never an attacker-chosen string, and the corrections map is
component state, not file content. I imported the helper in Node and fed it a patch parsed from
`{"__proto__":{"polluted":true},"constructor":{"prototype":...},"sets":2,"weight":55}`: the entry
came back with `sets 2 / weight 55`, an ordinary prototype, and `({}).polluted` stayed
`undefined`. I also froze the entry and its `warns` array: `applyEntryCorrection` returned a new
object, the frozen input was unchanged.

**Data-loss safety.** This is the part the feature could get wrong and it does not:
- Dismissing the sheet changes nothing (`close()` without `onSave`; pinned by
  `frontend/src/sheets.coach-import.test.jsx:316-324`), and cancelling the review leaves no trace —
  the corrections are local state and are dropped with the sheet.
- Confirming writes exactly the corrected numbers and keeps the coach's `src`
  (`bundleFromWeek` untouched; tested at `frontend/src/lib/import-plan.test.js:339-355` and
  `frontend/src/sheets.coach-import.test.jsx:433-441`). The coach's own row is displayed, never
  rewritten.
- `Back to the coach's numbers` restores the original line without un-ticking the row
  (`frontend/src/components/CoachImport.jsx:106-121`).
- The kg→display-unit→kg drift is explicitly avoided: an untouched weight hands back the original
  kilogram value, a changed one is converted (`frontend/src/sheets.jsx:1243,1250-1256`), pinned by
  the 51.3 kg round-trip test (`frontend/src/sheets.coach-import.test.jsx:462-490`).
- The lb confirm path is unchanged and still never rewrites state: `mergeWeek` throws on a non-kg
  account *before* `addCustomEx`/`pushWeek` (`frontend/src/lib/plan-share.js:102-107`), and
  `update` mutates only a clone that is persisted after the producer returns
  (`frontend/src/store/useStore.js:152-156`), so a throw persists nothing. It is also unreachable
  in the shipped app: a stored `unit: 'lb'` profile is converted to kg on load
  (`frontend/src/store/useStore.js:111`). This is pre-existing, not introduced here.

**No XSS/injection sink added.** The new sheet renders the exercise name, the coach's row and the
Italian/English labels as React text children; the diff adds no `dangerouslySetInnerHTML`,
`innerHTML`, `eval`, `new Function`, `document.write` or string-built HTML. The only new
dynamic text is `entry.name`/`entry.src`, which the review screen already renders as text
(`frontend/src/components/CoachImport.jsx:162,165`), and the new sheet does the same.

**Verification I re-ran.** `npx vitest run src/lib/import-plan.test.js
src/sheets.coach-import.test.jsx` → 56/56 pass (32 + 24, matching build.md's counts);
`npm test` → 113 files / 1242 tests pass; `git diff --check` clean. The copy guards are red exactly
as build.md documents and for pre-existing reasons: `check-locales.mjs` fails because `it.js` is
the only locale file (every key is then "only here"), and `check-source-strings.mjs --strict` re-
ports 626 translated strings with the same 10 pre-existing keys defined in no pack, none of them
from this job. All four new source keys are present in `frontend/src/locales/it.js:603,608-610`
with real Italian, and the old hint key is gone.

**SECURITY.md posture unchanged.** No new at-rest data (corrections are never stored), no new
network surface, no new export or sharing path. The feature only edits data the document already
describes as unencrypted local state, on the same screen that already reads the coach's file.

## Non-blocking notes

1. **The pure helper's coercion is looser than its contract for hand-built patches.**
   `Number(raw)` accepts booleans/arrays (`true` → sets 1, `[7]` → reps 7) and throws on a
   `Symbol` (`Number(Symbol())` throws), where the doc comment says "a number that is not finite is
   ignored". Not reachable from the UI (the sheet only ever hands over numbers), so no user/security
   impact today; a `typeof raw === 'number'` guard would make the documented behaviour true for any
   future caller and is a one-line hardening.
2. **Complex reset edge (flagging for the code reviewer, not a security issue).**
   `hasNumbers` asks whether *any* movement of the complex has a correction
   (`frontend/src/components/CoachImport.jsx:84-87`), but `resetNumbers` only removes the tapped
   movement's reps (`:106-121`). If reps were corrected on two movements of the same complex, then
   after resetting from one of them the menu item stays but clicking it again removes nothing (the
   other movement's reps correction is still there) — the spec's "the item disappears once there is
   nothing to reset" is not met for that row. Cosmetic, review-only state; no persistence or data
   loss involved. The single-row and ordinary-complex paths are correct and tested.
3. **Saving the sheet without touching a field still records the row's numbers.** Noted in build.md
   as a judgement call; it makes the row tick and the reset item appear even for a no-op save. No
   security consequence (nothing persisted; reset restores; the numbers are what the review already
   showed), just worth confirming as intended product behaviour.
4. **The build's browser click-through was not re-run in this review.** The security-relevant logic
   is covered by the unit/screen tests and the probes above; the acceptance-criteria 7 evidence
   remains build.md's, for the approver to weigh.

No secrets, no new network/server code, no new persistence, no injection path, and no data-loss
path were found in this diff.
