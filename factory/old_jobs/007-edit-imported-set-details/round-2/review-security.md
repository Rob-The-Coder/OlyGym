VERDICT: PASS

# Security review — 007-edit-imported-set-details, round 2

Scope: `git diff f0ff48f..83ac684` (round-2 commit on top of round 1's `f0ff48f`; main is
`3941b8e`). 5 files, all under `frontend/src`: `CoachImport.jsx`, `sheets.jsx`,
`import-plan.js`, and the two test files. Read round-1 `review-security.md`, `spec.md`,
`build.md` (Round 2 section), `SECURITY.md`. Working tree clean, `git diff --check` clean,
`package.json`/`package-lock.json` untouched. Re-ran the suite, the build, and the helper probe.

## Round-1 findings — status

All four notes from round 1 are addressed; none was a blocker then, and the two that were real
weaknesses are now fixed with tests.

1. **Helper coercion was looser than its documented contract — FIXED.**
   Round 1: `Number(raw)` accepted `true`/`[7]` and threw on a `Symbol`. Round 2 makes the
   merge number-only: counts require `typeof raw === 'number' && Number.isFinite(raw)` and weight
   requires `typeof weight === 'number' && Number.isFinite(weight)`
   (`frontend/src/lib/import-plan.js:144-151`); the doc comment says why (`:127-131`). New lib
   test `accepts a number as a count and nothing else` (`frontend/src/lib/import-plan.test.js:281`).
   I re-probed the helper directly: `{sets:'2'}`, `{sets:true}`, `{reps:[7]}`, `{weight:'60'}`
   and `{sets:Symbol('n')}` are all ignored (no throw, entry fields unchanged and `warns`
   intact); finite numbers still merge and normalise. The UI's only caller already sends numbers,
   so no product behaviour changes.
2. **Complex reset item could become a permanent no-op — FIXED.**
   Round 1: `hasNumbers` asked the whole complex "has anything?", so after reps-only fixes on two
   movements the sibling's menu kept an item that reset could not remove. Round 2 records only the
   fields that actually differ (`frontend/src/components/CoachImport.jsx:101-118`) and
   `hasNumbers` now offers reset only when it can remove something: `reps`/`sec` on the tapped
   movement, or `sets`/`weight` anywhere in its group (`:86-94`). I traced the round-1 scenario
   against the new code: a reps-only save on one movement records no `sets`/`weight`, so the
   sibling's menu does not offer reset, and resetting the corrected movement removes its `reps`
   and the item. Pinned by the new `resetting across a complex` suite, 2 tests
   (`frontend/src/sheets.coach-import.test.jsx:366-429`).
3. **Saving without changing a field still recorded the numbers — FIXED.**
   Round 2 writes a key only when the field differs from the entry's current value
   (`frontend/src/components/CoachImport.jsx:107-112`; an empty patch deletes the key,
   `:113-114`), so a no-op Save records nothing and no reset item appears, while the row is still
   ticked. Pinned by `does not claim a correction when Save changed nothing`
   (`frontend/src/sheets.coach-import.test.jsx:431-444`). The round-1 "deviation" note no longer
   applies.
4. **Round-1 browser click-through was not re-run by me — evidence now supplied; still not a
   security blocker.** `build.md`'s Round 2 live-render section documents the 360px weight-field
   measurement and the sibling/movement reset-menu checks against a real Chrome. I did not re-run
   the browser (same sandbox/Chrome constraint); the security-relevant logic is covered by the
   tests and probes here, and the browser evidence is for the approver to weigh.

## Round-2 security checks (new commit only)

- **No new secrets, network/server code, persistence or XSS sink.** Scanning the added lines of
  `f0ff48f..HEAD` for secrets, `fetch`/XHR/WebSocket/`sendBeacon`/URLs,
  `localStorage`/`sessionStorage`/`gym_state_v1`/`nativeSave`/`useStore.setState`, and
  `dangerouslySetInnerHTML`/`innerHTML`/`eval`/`new Function` finds nothing. The diff touches
  no package/lockfile, locale, docs, native or CI file.
- **The validation change is a hardening, not a loosening.** The stricter `typeof` guard means a
  correction can only ever be a finite number; there is no new coercion path, no throw on hostile
  values, and no prototype-pollution route (patches are still iterated from the fixed
  `CORRECTION_NUMBERS` list plus explicit `weight`, merged onto a fresh spread entry). My JSON
  `__proto__`/`constructor` probe still leaves `Object.prototype` clean and the frozen input
  unchanged.
- **Data-loss safety is unchanged or improved.** `saveNumbers` now writes fewer keys and never
  claims a shared correction from an untouched field; corrections are still component state only
  (no store writer in the added lines), the reset path still only deletes from `corrections`, and
  the import path (`reviewWeek` + before it `bundleFromWeek`/`mergeWeek`) is untouched. The
  coach's `src` stays on the entry; the sheet change is layout only (`sheets.jsx:1265-1276` —
  the Weight stepper moved to its own row, same props, same `onSave` contract).
- **Only the new reset/recording logic changed in the screen**, and it reads/writes the same local
  `corrections` map; no new object shape reaches `S` or the import bundle.
- **Verification re-run:** `npm test` → 113 files / 1247 tests pass (round-1 1242 + 5 new,
  matching build.md); `npm run build` → `✓ built` (index 825.06 kB) with a clean tree afterwards;
  `git diff --check` clean. Copy guards are unchanged from round 1: the round-2 diff adds no source
  literal and does not touch `it.js`, and both guards remain baseline-red for the documented
  pre-existing reasons.

## Non-blocking residual

- **Restoring the coach's own value by typing it back still records a no-op correction.** With a
  previous correction in place (say sets 4 → 2), typing 4 again compares against the *corrected*
  entry's 2, so `{sets: 4}` is kept in local state; the row shows the coach's number again but
  "Back to the coach's numbers" lingers until used. Purely cosmetic: no persistence, no data loss,
  and the imported numbers are identical either way. Not worth another round unless the code
  reviewer wants the comparison against the sheet's original.
- The `hasNumbers`/`saveNumbers` logic still relies on `corrections` being app-written objects
  (no external caller can reach it); the helper's type guard is the backstop, and it now validates.

No blocking security finding in round 2; the round-1 notes are fixed or acknowledged, and the new
commit adds no secret, network, persistence, injection or data-loss surface.
