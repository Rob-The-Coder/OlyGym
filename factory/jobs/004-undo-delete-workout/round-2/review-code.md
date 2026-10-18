VERDICT: PASS

# Review — 004-undo-delete-workout, round 2 (code)

Scope: the round-2 diff `8e6f3d1..62d8dac` and the full product diff
`chore/factory...factory/004-undo-delete-workout`, `frontend/` only (factory docs and
`board.json` ignored). Five product files changed this round: `sheets.jsx`,
`m3.components.css`, `m3.tokens.test.js`, `lib/contrast.test.js`, `sheets.undo-delete.test.jsx`.
No `package.json`/lockfile change: no dependency added. `CONTRIBUTING.md` read.

## Verified

- `cd frontend && npm test` → **109 files / 1174 tests pass** (matches the build note).
- `npx vitest run src/sheets.undo-delete.test.jsx src/m3.tokens.test.js src/lib/contrast.test.js`
  → 3 files / 97 tests pass (4 flow + 93 CSS).
- `npm run build` → clean (244 ms); `node scripts/check-source-strings.mjs --strict` → the same
  10 pre-existing missing keys, none of the three keys this job added (exit 1 is the script's
  normal answer for those pre-existing keys).
- Meaningfulness check instead of trusting the green run: I ran the round-2 test files against a
  clean `git archive` of `8e6f3d1` (round 1). **5 assertions fail there** — the three new
  `m3.tokens.test.js` snackbar tests, the note regression, and the double-fire regression. They
  are pins, not tautologies.

## Round-2 fixes — correct

**Current-`S` capture.** `frontend/src/sheets.jsx:1373` reads
`const current = S().workouts.find(x => x.id === w.id) || w` inside `onConfirm`, before the filter
and before `close()`, then pushes `current`. `update` deep-clones `S`
(`store/useStore.js:140-143`), so the `w` prop really is stale after an edit; the regression test
proves the edited note now survives (and fails with the old `pushUndo(..., w)`, checked above).
The snapshot is the pre-delete object, so the subsequent filter cannot mutate it.

**Id binding + duplicate guard.** `undoDelete` (`sheets.jsx:1360-1373`) peeks
`undo.at(-1)` and no-ops unless that id is the workout the action was created for; the
`update` producer returns early when `S.workouts` already holds the id. So a stale or
double-fired action cannot pop a different, older deletion, and Undo cannot create a second history
row. The double-fire test (`sheets.undo-delete.test.jsx:149-164`) fails on the round-1 code (it
restores `w2` there), so the binding is genuinely pinned. `at(-1)` is already the idiom in this
repo (all the `sheets.*` tests use it); no compatibility issue.

**CSS blockers from round-1 UI review.** `#toast .toast-act` now sets `pointer-events:auto`
(`m3.components.css:975-980`) while the bar keeps index.css's pass-through
`#toast{pointer-events:none}` (`index.css:1266`) — the rule is on the child, so it neither
inherits `none` nor turns the 520 px bar into a tap trap. `min-height:44px` with the global
`box-sizing:border-box` (`index.css:109`) gives a real 44 px target; the bar is
6+44+6 = 56 px with the action, 48 px without, as claimed. The label is
`--m3-on-surface`; I measured it over the 10% press layer: **10.95:1 light, 6.84:1 dark**
(old `--m3-primary`: 4.11:1 light — the comment's number is right), and still
5.67:1 / 9.47:1 with the hover layer under the press layer. The hover layer is now in the
shared `@media (hover:hover)` block (`m3.components.css:468`, added this round; the
`:focus-visible` list at `:478` was already there from round 1), using `--label` =
`var(--m3-on-surface)`, so no raw colour/radius/shadow was introduced.

**No new deps, pure-helper rule holds.** The only new logic outside the view is the id/duplicate
check, which is view wiring; ordering still lives in `lib/undo.js` (pure, tested) and reuses
`insertChronological`. No network, no persistence of `undo`/`toastAction`.

## Non-blocking findings

1. **The contrast test alone does not pin the fix it names.** `lib/contrast.test.js:98-110` hard-codes
   `--m3-on-surface` and measures it over the press layer. It passes on `8e6f3d1` too (the label
   there was primary), because the tokens did not change — only the component rule did. It is a real
   token invariant (it would catch a scheme that lowers on-surface), and the pair with
   `m3.tokens.test.js:172-177` does pin the actual label role, so the fix is covered. If the intent
   is "this test fails if the label goes back to the accent", read the declared colour out of the
   `#toast .toast-act` block and compute against that variable instead of the literal
   `--m3-on-surface`; it would then have failed at 4.11:1 in light. Not a blocker.
2. **Two of the new CSS assertions are weaker than their names.** `m3.tokens.test.js:168` asserts
   the *component* sheet's `#toast` block has no `pointer-events`, but the pass-through it protects
   lives in `index.css:1266`; the same file already reads `base`, so
   `expect(decls(base, '#toast')).toContain('pointer-events:none')` would cover the real half.
   `m3.tokens.test.js:179` matches `/\.toast-act:hover/` anywhere in the file (comments included),
   not that the hover rule has a body; `decls(components, '.toast-act:hover')` would assert the
   gradient. Neither changes the verdict — both would still fail if the rules were removed.
3. **The duplicate-id guard has no test.** `sheets.jsx:1368` is the only new branch this round and
   nothing exercises it; the double-fire test exercises the id binding, which returns before the
   guard can be reached. A cheap addition to the flow test (delete `w2`, re-add it to `S`, fire the
   action: one `w2` in `S`, stack emptied) would pin it.
4. **"Workout restored" on a short-circuited restore.** When the guard fires (re-import), the stack
   entry is consumed and the toast still says the workout was restored even though the imported copy
   was kept. Cosmetic; the security review logged the same.
5. **Auto-dismiss can still eat focus** (raised as minor in round-1 `review-ui.md`, not addressed by
   this round): at 10 s `toastAction` is cleared and a focused action button unmounts, dropping
   focus to `<body>` mid-announcement. Not blocking, but worth a follow-up if the snackbar is ever
   used for more than this one action.
6. **Comment precision (`m3.components.css:972-974`).** "on-surface stays >=8:1 in both themes" reads
   as if it covers the pressed layer; there it is 6.84:1 in dark (still above AA). At rest it is
   13.30/8.92. One word ("at rest") would make the comment match the measurements.
7. **Style:** `del` (`sheets.jsx:1373`) is now a ~700-character one-liner; the round-1 version was
   already a long one-liner, but the capture makes it harder to read than the rest of the file. Not
   a convention violation, just a readability cost.

Residual edge (already noted by the security review): `current` comes from `S`, so a note that is
still only in the textarea at confirm time would not be captured; in the tested path the ⋯ tap
blurs the textarea first, and the unmount flush cannot save it after the record is filtered out.

## Regression tests — good

The note test (`sheets.undo-delete.test.jsx:120-147`) types through the real textarea, asserts `S`
holds the new text while the `w` prop still holds `"felt strong"`, drives the real
⋯ → Delete workout → confirm flow and then clicks the real `Toast` `.toast-act`. The double-fire
test drives the same UI twice and asserts both the restored set and the remaining stack after the
second fire. Both are flow tests through the shipped wiring, not store-only probes, and both fail on
the pre-fix commit. Test placement (`src/sheets.undo-delete.test.jsx`, happy-dom) and the
`afterEach` unmount cleanup match the existing `sheets.*` suite.

Nothing blocking: round 1's behaviour still holds, the round-2 fixes are correct and pinned, and the
full suite/build are green.
