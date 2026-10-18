APPROVE

# Decision — 005-show-best-lift-pr-badges

Approver: `approver-005`. Branch `factory/005-show-best-lift-pr-badges` at `3947ef7`
("Show the best-lift PRs on a History row, not a count"), parent/base `d64c4fe`. Reviewed the
branch tree itself (`main...HEAD`, 5 product files), not main's working copy.

**Call: APPROVE.** Every acceptance bullet in `spec.md` is met on the branch; the four round-1
reviews are PASS and their material claims held up under independent checking; the gates are green.
No blocking risk found.

## Scope and mergeability (checked, not assumed)

- `3947ef7` has one parent (`d64c4fe`) and touches exactly five files, all under `frontend/src/`:
  `lib/history.js`, `lib/history.test.js`, `m3.components.css`, `sheets.jsx`, and the new
  `sheets.workout-row.test.jsx`. No file outside `frontend/src` in the commit.
- `main` has moved past the base with two factory-only commits (`fb572ac`, `0441251`); `git diff
  d64c4fe..main -- frontend/` is empty. So the branch is **not fast-forwardable**, but it is cleanly
  mergeable: `git merge-tree --write-tree main factory/005-show-best-lift-pr-badges` wrote tree
  `03e8883` with exit 0 (no conflicts). A `--no-ff` merge is the correct landing.
- Note: `build.md` and `round-1/` are **tracked on main** (added by `fb572ac`/`0441251`), not
  untracked as the brief described. They are not in `3947ef7` and do not affect the merge.
- `git diff main...factory/005-show-best-lift-pr-badges -- frontend/src/index.css
  frontend/package.json frontend/package-lock.json` is empty: `index.css`, dependencies and the
  lockfile are untouched. No store-schema change (`w.prs` is only read).

## What I verified independently

**`prLifts` (the pure helper).** Read `history.js` on the branch; ran a direct Node probe against
the branch tree: heaviest-first `[jerk 90, clean 90, snatch 80]` with a tie keeping stored `w.prs`
order; warm-up 130 + unchecked 200 + completed 102 → `{snatch, 102}` (only the completed work set);
ghost id, zero load and duplicate id skipped; missing/non-array/empty `prs` and missing `entries`
→ `[]`; null/undefined/`42`/`'x'` inputs → `[]`; no input mutation (JSON snapshot equal); result is
a fresh array; a deep-frozen workout still returns `[a 55]`. All probes passed.

**Tests.** `cd frontend && npx vitest run src/lib/history.test.js src/sheets.workout-row.test.jsx`
→ **2 files, 115 tests passed** (112 + 3), matching build.md. `npm test` → **110 files, 1182
tests passed**. `npx vitest run src/m3.tokens.test.js src/lib/contrast.test.js` → **93 passed**.
The new suite pins each helper bullet (order/tie, completed-set weight, skips, empty cases, no
mutation) and the mount test pins badge structure under `.grow .prs`, heavy-first order, name +
`t('PR')` + `fmtNum(weight) + ' kg'`, no-PR empty state, duplicate collapse and no bare `N PR`.

**WorkoutRow and its one rendering.** Diff has a single hunk in `sheets.jsx`: the badge strip is
inside `.grow` directly after `.ss`; one `.pr` per `prLifts(w)` entry carrying trophy +
`exerciseNameFor(EXIDX[id])` in `.pr-name.capitalize` (raw id fallback) + `t('PR')` +
`fmtNum(weight) + ' ' + st.unit`. The old `{w.prs.length} PR` span is removed. `History.jsx:68`,
`Stats.jsx:437` and the day-sessions sheet (`sheets.jsx:1504`) are unmodified and pass only
`{ w, onClick }`, so the shared row reaches all three with no props. The other two `.pr` call sites
(`sheets.jsx:523`, `:1342`) keep their markup and only inherit the new look.

**Stored PR model.** `doFinishWorkout` pushes exercise ids only (`sheets.jsx:1937`) and
`buildCompletedWorkout` persists `prs` unchanged (`finish-workout.js:6,53`); the badge's load is the
session's own best via `bestWeightForEntry`, which excludes warm-up/unchecked rows. No new field.

**Copy / bilingual.** `t('PR')` is the existing key (`locales/it.js:1431` → `'PR': 'PR'`), no new
string. `node scripts/check-source-strings.mjs --strict` exits 1 on both trees: branch **619** call
sites / 10 missing keys, main baseline **618** / the same 10 keys (compared key-for-key); `PR` is
not among them.

**CSS: tokens only.** The added declarations carry no raw colour, radius, shadow or duration (scan
of added lines: only layout and the spacing `gap:6px;margin-top:4px`, which matches the existing
`.mchips` strip and DESIGN.md bans no spacing). `.pr` is re-pointed to
`var(--m3-primary-container)` / `var(--m3-on-primary-container)` / `var(--m3-shape-s)` (8px, defined
in the generated `m3.tokens.css` block), `.prs` is `display:flex;flex-wrap:wrap;gap:6px;margin-top:4px`,
and `.pr-name` clips with `min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap`;
`.pr` gets `max-width:100%`. `m3.components.css` loads after `index.css` (`main.jsx`), so the
re-point wins. `index.css` untouched.

**Contrast pair.** Re-computed WCAG ratios from the shipped token block myself: **18/18 schemes
≥ 4.5:1**, worst **7.19:1** (lime, dark), default dark **7.27:1**, default light **13.28:1** — the
pair is opaque, so the row under the badge cannot change it. `contrast.test.js:74` asserts exactly
`--m3-on-primary-container` on `--m3-primary-container` for every scheme; `m3-scheme.mjs --check`
also reports all 208 generated pairs passing.

**Badge is non-interactive; row keeps its semantics.** The badge is a plain `<span className="pr">`
with no `role`, no `tabIndex`, no handlers or state; there is no `.pr:hover/:active/:focus/cursor`
rule and `.pr` is not in the shared `:focus-visible` selector list. The row is still
`{...tappable(onClick)}` → `role="button"`, `tabIndex=0`, Enter/Space; `.item:focus-visible` is in
the ring list (`m3.components.css:495-496`); `.list .item` keeps its 68px minimum target and the
chevron stays. The trophy `Icon` is `aria-hidden` (`Icon.jsx:146`), and the badge's lift name,
visible word `PR` and load are real text children, so the meaning is not colour/icon-only and the
badge words are part of the row's accessible name (HTML-AAM name from contents; no `aria-label`
overrides it).

**Real render evidence.** Inspected the committed captures at 360×800 dark/light and focus, and
1200×900 dark: badges sit in the text column below the metadata line, heaviest first, wrap inside
the column, the 62-character carry ellipsises ("Snatch Grip Overhead Ca… PR 60 kg"), the chevron and
full-row tap target are intact, and the keyboard focus ring paints around the whole row while the
badges stay unfocused. This matches build.md/UX/UI measurements.

**Build.** `cd frontend && npm run build` → exit 0, clean (`✓ built in 194ms`,
`dist/assets/index-*.js 817.41 kB`). No network/server code, no new dependency.

## Method limits (stated, not assumed)

- The pixel coordinates, `scrollWidth === innerWidth`, computed-style and AX-tree readings in
  `build.md` and the reviews came from their live Vite/headless-Chrome runs. I did **not** re-run a
  dev server/browser in this session; I verified the underlying DOM/CSS and inspected their captures
  rather than re-measuring the page. Nothing in the branch contradicts those figures.
- The Italian live render was not re-run (UX reviewer also notes this); the same `max-width`/
  ellipsis rules and existing `PR` key apply structurally.
- The two secondary `.pr` consumers (WorkoutDetail marker, exercise-history marker) were verified as
  unchanged markup inheriting the palette; they were not separately re-rendered.

## Non-blocking notes carried forward

1. **Test sharpening (code review):** the unchecked 90 kg row in the new `prLifts` test sits below
   the 102 best, so that assertion would not catch a regression in unchecked-row filtering (the
   warm-up 120 row does fail such a regression). Raising that unchecked row above 102 would pin the
   bullet literally.
2. **Test sharpening (code review):** the mount test uses integer loads, so
   `fmtNum(120) === String(120)`; one decimal load (e.g. 102.5) would actually pin "via `fmtNum`".
3. **Untested fallback (code/security):** the raw-id fallback for an id missing from `EXIDX` is
   implemented but not exercised by the tests (ghost ids are skipped before rendering); string-safe.
4. **Prototype-key edge (security):** `EXIDX[p.id]` is a plain object, so an id equal to
   `__proto__`/`toString` would be truthy and render an empty name span; `exerciseNameFor` returns
   `''` and no app flow can write such an id — safe, informational.
5. **Worst case O(prs × entries)** in `prLifts`; bounded by device-local data, no new import path.
6. **360px density (UX):** a 3-PR row is ~144px vs 68px PR-less, so PR-heavy History scrolls more;
   this is the spec's chosen one-badge-per-lift trade (title/date still lead).
7. **Truncation (UX/UI):** at 360px the long carry clips to "Snatch Grip Overhead Ca…" as specified;
   the lift family and load still read, and the full name stays in the accessible name/detail.
8. **Spacing/type (UI):** `.prs` uses raw 4/6px spacing consistent with `.mchips` (no spacing-token
   family exists), and `.pr` inherits 12px/500 metrics from `index.css`; both intentional.
9. **Global `.pr` coupling (UI):** one rule is now shared by the row, WorkoutDetail and the
   exercise-history marker; a future restyle must touch this one rule (deliberate: one PR look).
10. **Pre-existing, out of scope (UX):** a workout with `prs` but no `entries` throws in the row's
    metadata readers before the strip; reproduced as independent of this diff, and `prLifts` returns
    `[]` for it. Spec only guarantees legacy rows without `prs` (they render fine, no strip).
11. **Disclosed spec deviation (build.md):** `.pr` also sets `white-space:nowrap`; layout-only,
    prevents "PR 130 kg" wrapping under the name, and is not a banned raw value.
12. **Per-row count is now implicit** (count badges); the History "PRs" aggregate tile is untouched
    and still reports the total.

None of these block: they are test sharpening, measurement method limits, or deliberate spec trades.

## Merge

Merged `factory/005-show-best-lift-pr-badges` into `main` with `--no-ff` (no force, no rebase),
keeping the merge commit. Merge description: the shared `WorkoutRow` now shows one M3 badge per
recorded PR (lift name, `PR`, session-best load, heaviest first) instead of the bare "N PR" count;
`prLifts(workout)` in `lib/history.js` maps the stored `w.prs` ids to the session's best completed
work load, `w.prs` is read not extended, no schema/locale/dependency change, `index.css` untouched.
Not pushed to any remote; `decision.md` is not committed; `board.json`/`backlog.md` untouched.
Merge commit: `e472618` (parents `0441251` + `3947ef7`); branch `factory/005-show-best-lift-pr-badges`
is now an ancestor of `main`, `git diff factory/005-show-best-lift-pr-badges main -- frontend/` is
empty, and `git status --short` shows only this untracked decision file. Post-merge re-run of the
two targeted suites: 115 tests passed.
