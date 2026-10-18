VERDICT: PASS

# Code review — 005-show-best-lift-pr-badges, round 1

Scope: `git diff main...HEAD` (merge-base `d64c4fe`, one commit `3947ef7`) — 5 product files:
`frontend/src/lib/history.js`, `frontend/src/lib/history.test.js`, `frontend/src/sheets.jsx`,
`frontend/src/m3.components.css`, `frontend/src/sheets.workout-row.test.jsx`.
`factory/jobs/005-show-best-lift-pr-badges/build.md` is an untracked factory artifact, not part of
the commit. No `package.json`/lockfile change, no `frontend/src/index.css` edit, no TypeScript, no
network/server code, no persisted-shape change.

## Correctness

- **`prLifts` is pure and total.** `frontend/src/lib/history.js:641-659` reads only
  `w.prs`/`w.entries`, builds a fresh `out`, and sorts that array; it never sorts or writes
  `w.prs`, the workout or its entries. `objectOf` (`history.js:6`) makes null/primitive/array
  inputs `{}`; a missing, non-array or empty `prs`, and a missing/non-array `entries`, all return
  `[]`. I probed it directly (command below): `undefined`, `null`, `42`, `'x'`, `[]`, `{}`,
  `prs: null`, `prs: {}`, `prs: 'snatch'`, `prs: ['a']` without `entries`, and `entries` holding
  `null`/`7` all return `[]`. A deeply frozen workout does not throw, and a second call returns the
  same result — no mutation, no hidden state.
- **Consistent with the existing PR model.** The weight is exactly `bestWeightForEntry(entry)`
  (`history.js:595-628`), which is also what `doFinishWorkout` writes back as the new best
  (`sheets.jsx:1950`), so the badge's load is the load that set the PR, not a re-run or a lifetime
  best. Warm-ups and unchecked rows stay out via `completedRowsForMode`
  (`history.js:576`), so e.g. a warm-up 130 and an unchecked 200 in the same entry are ignored.
  Ids with no matching entry, a best of 0, and non-finite loads are skipped by the `!(weight > 0)`
  guard; duplicates yield one entry (`seen`). Ordering is `weight` descending with a stable
  `Array.prototype.sort` (ES2019; the browsers this Vite 8 build targets all have it), so equal
  loads keep their stored `w.prs` order. Verified: `prs: ['a','b','a','ghost','zero']` → exactly
  `[{a,102},{b,102}]` with `a` before `b`.
- **Row rendering is correct at all three call sites.** `sheets.jsx:1566-1585` is the only
  rendering; `History.jsx:68`, `Stats.jsx:437` and the day-sessions sheet (`sheets.jsx:1504`) all
  pass only `{ w, onClick }` and were not touched, so the badge reaches all three with no props and
  no list-markup change. The strip sits inside `.grow` directly after the `.ss` metadata line, one
  `.pr` badge per helper entry in helper order, reading trophy + `exerciseNameFor(EXIDX[id])`
  (`.pr-name.capitalize`, raw id fallback) + `t('PR')` + `fmtNum(weight) + ' ' + st.unit`. The old
  trailing `{w.prs.length} PR` badge is gone, and `prs.length > 0` keeps the strip off PR-less
  rows. `EXIDX[id]` is guarded, so a deleted custom exercise renders its stored id instead of
  crashing; `p.id` is always a string from the finish flow, so the fallback is React-text safe.
  Keys are `p.id`, which is unique because the helper dedupes.
- **Accessibility keeps working.** `tappable` (`lib/use-sheet-keyboard.js:83`) still supplies
  `role="button"`/`tabIndex`/Enter-Space handling, the badge's text is real child text (icons are
  `aria-hidden`), and the word `PR` is visible, not colour- or icon-only. The mount test asserts
  both the role and that the badge text is in the row's text content.
- **CSS is token-only and clips.** `m3.components.css:238-255` is in the Lists section next to the
  row rules; `.pr` is re-pointed to `--m3-primary-container` / `--m3-on-primary-container` /
  `--m3-shape-s` (loaded after `index.css`, so equal specificity wins — `main.jsx:5-10`), gets
  `max-width:100%`, and `.pr-name` gets `min-width:0;overflow:hidden;text-overflow:ellipsis;
  white-space:nowrap`. `.grow` already has `flex:1;min-width:0` (`index.css:440`), so the badge
  wraps/clips inside the text column. The 360×800 and 1200×900 screenshots in `oly-previews/`
  show the heavy-first badges, the 62-character carry ellipsised ("Snatch Grip Overhead Ca… PR
  60 kg"), a one-PR row, the chevron intact and no title overlap; the History "PRs" tile
  (`History.jsx:45`) is untouched and still shows the count, as the spec requires.
- **Copy.** Only the existing `t('PR')` key (`locales/it.js:1431`); no new string. The strict
  source-string check lists exactly the same 10 pre-existing missing keys as main, and `PR` is not
  among them.

## Tests

Satisfies the pure-helper-plus-test rule (helper in `lib/history.js`, tests beside it in
`lib/history.test.js`; the component rendering test is the spec-named
`sheets.workout-row.test.jsx`). The new suite is 5 tests (`history.test.js:945-1024`) and the
weaker assertions are backed by behaviour: removing the sort fails the order test (stored order is
snatch/clean/80-102), an unstable tie sort fails the tie test (stored order is jerk before clean
with reversed entries), including the warm-up 120 fails the completed-set test, dropping the
missing/zero/duplicate guards fails the skip test, and any input mutation fails the
deep-equal-before/after test. The mount test fails if the old `N PR` count reappears, if the strip
is not directly under `.ss` inside `.grow`, if order is wrong, or if a badge loses its name/load;
its duplicate test pins the collapse end to end.

## Blocking findings

None. Every acceptance bullet is met; the one declaration beyond the spec's CSS list
(`white-space:nowrap`) is declared in build.md and adds no raw colour/radius/shadow/duration.

## Non-blocking notes

- `frontend/src/lib/history.test.js:977-988` — the unchecked row is 90 kg while the best is 102,
  so that assertion would still pass if unchecked rows stopped being filtered; only the warm-up
  row (120) makes the test fail. The `done === true` filter is pre-existing and separately pinned
  (`history.test.js:559-567`, timed entry with a `done:false` 40 kg row expecting 25), so this is a
  sharpening opportunity, not a hole in this change: raising the unchecked row above 102 would
  pin the acceptance bullet literally.
- `frontend/src/sheets.workout-row.test.jsx:55-72` — the loads are integers, so the `fmtNum`
  assertion cannot distinguish `fmtNum(120)` from `String(120)`; one decimal load (e.g. 102.5)
  would actually pin "via `fmtNum`".
- `frontend/src/sheets.jsx:1580` — the raw-id fallback for an id missing from `EXIDX` is
  implemented but never rendered in a test (the helper's `ghost` id has no entry and is skipped
  before rendering). String-safe, optional follow-up.
- `frontend/src/lib/history.js:641-659` — `Array.prototype.find` per id makes it O(prs × entries);
  bounded device-local data, no new import path, informational only.

## Commands run

```bash
cd frontend && npx vitest run src/lib/history.test.js
# ✓ 1 file, 112 tests passed (main has 107 → +5 new prLifts tests)

cd frontend && npx vitest run src/sheets.workout-row.test.jsx
# ✓ 1 file, 3 tests passed (new file; total new tests 8)

cd frontend && npm test
# ✓ 110 files, 1182 tests passed (7.12 s)

cd frontend && npm run build
# exit 0 — ✓ built in 302 ms, dist/assets/index-*.css 89.50 kB, index-*.js 817.41 kB

cd frontend && node scripts/check-source-strings.mjs --strict
# exit 1 (expected): 619 translated call sites, exactly the same 10 "defined in no locale pack"
# keys as main; "PR" is not among them.
# Compared with main (git archive main frontend/src frontend/scripts → /tmp, same script):
# 618 call sites, the same 10 keys — only delta is the new t('PR') use of an already-defined key.

node --input-type=module -e '<prLifts totality/purity probe>'
# malformed/null/non-object/non-array-prs/non-array-entries → []; deep-frozen input →
# [{a,102},{b,102}] (tie in stored prs order); duplicate/missing/zero skipped; warm-up 130 and
# unchecked 200 excluded; repeat call identical; input untouched.
```

Also inspected: `git diff --check` clean, `git status` shows only untracked factory artifacts,
`m3.tokens`/`contrast` suites pass inside `npm test`, and the 360/1200 screenshots in
`oly-previews/` match build.md's measurements.
