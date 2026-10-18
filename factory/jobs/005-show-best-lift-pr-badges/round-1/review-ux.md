VERDICT: PASS

# UX review — 005-show-best-lift-pr-badges, round 1

Branch `factory/005-show-best-lift-pr-badges`, commit `3947ef7` (`main...HEAD`, base `d64c4fe`).
Read: spec, build.md, DESIGN.md, CLAUDE.md, the full branch diff, `WorkoutRow`
(`frontend/src/sheets.jsx:1565-1587`), `prLifts` (`frontend/src/lib/history.js:641`), the CSS
(`frontend/src/m3.components.css:244-254`) and the three call sites
(`frontend/src/views/History.jsx:68`, `frontend/src/views/Stats.jsx:437`,
`frontend/src/sheets.jsx:1504`).
I modified no product code and no test; this file is my only write.

## Scope

- The shared `WorkoutRow` PR badge strip: does the History list, Stats' recent list and the
  day-sessions sheet (one rendering, no new props) stay scannable, tappable and readable at a
  glance between sets.
- Empty/no-PR and malformed legacy rows; long-name clipping; focus/tap target/one-hand use;
  wording and capitalisation; 360px and ≥1000px, dark and light; the removed "N PR" count.
- `frontend/src/index.css` is untouched, the store shape unchanged, no new locale key, no new
  dependency.

## Blocking findings

None. Every UX acceptance criterion I checked is met; the two acceptance criteria most at risk
were verified explicitly:

- "The row's chevron and full tap target stay intact" (`spec.md:81-83`) — measured intact at 360,
  1200 and inside the day-sessions sheet; a badge never widens the row.
- "Badges wrap inside the text column, never overlap the title … the badge text is part of the
  row's accessible name" (`spec.md:84-87`) — measured below the metadata line, no title or chevron
  overlap, row still `role="button"` with the badge words in its name.

## Non-blocking notes

1. **A 3-PR session is 144px tall at 360px** (vs 68px for a PR-less row; measured, `WorkoutRow`
   at `sheets.jsx:1578-1583`). On a 360×800 screen that is ~4 list rows instead of ~7, so a
   PR-heavy History scrolls more. This is the spec's chosen trade (one badge per lift, no summary
   strip) and the title/date still lead, so no change is requested. Only worth revisiting if
   PR-dense History becomes common.
2. **At 360px the 62-character carry truncates the distinguishing tail** — the badge paints
   "Snatch Grip Overhead Ca… PR 60 kg"; the lift family and the load still read, and the full name
   stays in the row's accessible name and in the session detail. This is exactly the specified
   ellipsis behaviour (`spec.md:81-83`) and the name/PR/load order keeps "PR 60 kg" outside the
   clipped span; no action.
3. **The per-row PR count is now implicit** (count the badges) rather than a trailing "3 PR".
   Measured a row with three badges; the History "PRs" summary tile still reports the aggregate
   (screenshotted at 11), so the count information survives at screen level. The one case where a
   recorded PR now renders no badge at all is malformed `w.prs` whose ids resolve to no entry or
   to a zero load (verified: a "ghost ids" workout renders no strip); that cannot be produced by
   the app itself (`doFinishWorkout`, `sheets.jsx:1934-1938`, only records ids whose best completed
   load is > 0 and whose entry is in the same workout), so no real user loses a count.
4. **Pre-existing, not introduced here:** a workout that has `prs` but no `entries` still throws
   in the History row through the metadata readers (`setsDone` / `workoutVolume`, called at
   `sheets.jsx:1577`) before the strip is reached — I reproduced the row crash and proved both
   readers throw on such a shape independently of this diff; `prLifts` itself returns `[]` for it
   (`history.test.js` covers the missing-`entries` case). Out of scope for 005 (legacy rows without
   `prs` are the stated case, and they render fine), but since the task asked about malformed
   legacy workouts I am recording it rather than implying the helper crashes.

## What I verified, and how

**Diff contract, read not imagined.** The strip sits inside `.grow` below `.ss`, one `.pr` per
`prLifts(w)` entry with `exerciseNameFor(EXIDX[id])` in `.pr-name.capitalize` (raw id fallback),
`t('PR')` and `fmtNum(weight) + ' ' + st.unit`; the old `{w.prs.length} PR` is gone; no strip when
the helper returns `[]`. `prLifts` is read-only and `w.prs` is not extended.

**Automated tests (independently run).**
- `npx vitest run src/sheets.workout-row.test.jsx` → 3 passed; the test asserts two `.pr` badges
  under `.grow .prs`, heavier first, lift + `fmtNum(weight) + ' kg'`, no bare "N PR", and no
  `.prs` for a workout without `prs`.
- `npx vitest run src/lib/history.test.js src/m3.tokens.test.js src/lib/contrast.test.js` → 205
  passed (112 + 16 + 77).
- `npm test` → **110 files, 1182 tests passed**.
- `node scripts/check-source-strings.mjs --strict` → exit 1 with exactly the 10 pre-existing
  "defined in no locale pack" keys; `PR` is not among them. `t('PR')` is the existing key
  (`frontend/src/locales/it.js:1431` → `'PR': 'PR'`), no new string.

**Real render (my own, not the build's captures).** I ran Vite on 5174 with headless Chrome on CDP
9222, seeded `useStore` and measured the DOM at 360×800 (dark + light) and 1200×900 (dark + light):
a 3-PR workout whose third lift is the catalogue's 62-char carry (`wl891`), a 1-PR workout, a
legacy workout with no `prs`, one with `prs` as a string, and one with ghost/zero-load ids.
Measured, at 360 dark and light and at 1200 dark:

- `document.documentElement.scrollWidth === innerWidth` (360/360, 1200/1200): no page or row
  overflow; every badge's right edge ≤ the strip's right edge.
- `.prs` top ≥ `.ss` bottom and ≥ title bottom: the strip never overlaps the title or metadata.
- Row keeps `role="button"`, `tabIndex=0`, its chevron, and a full-width target (328px × 90/144px
  at 360; 328px × 68px for PR-less rows). One-hand use is unchanged: nothing moved out of the
  row/column flow and the whole row, not the badge, is the target.
- Long name: `.pr-name` has `scrollWidth > clientWidth` and paints an ellipsis; "PR 60 kg" stays
  visible. At 1200 the 62-char name fits whole (badge 432px) and the first two badges share a line.
- Computed `.pr`: `background-color: rgb(76, 56, 137)` / `color: rgb(233, 221, 255)` (dark),
  `rgb(233, 221, 255)` / `rgb(21, 13, 86)` (light), `border-radius: 8px`, `max-width: 100%`,
  `white-space: nowrap`; computed `.prs`: `display: flex; flex-wrap: wrap; gap: 6px`. Contrast
  measured 7.27:1 dark and 13.28:1 light at 12px.
- Keyboard: Tab reaches the row, `:focus-visible` is true and the shared focus layer paints; the
  badge itself is not focusable (correct — it is not a control).
- **Badge is not mistakable for a tappable chip**: computed `cursor: auto`, no `role`, no tab stop,
  no hover/press state, and it is not in the focus-ring list; it uses `--m3-shape-s` (8px) against
  `.chip`'s 99px pill on the surface tone. No chip sits in the three lists it appears in, and
  clicking a badge opens the session (the row's tap target is not split into dead zones).
- **Day-sessions sheet, opened live** (calendar day with two sessions at 360): sheet 360 wide,
  two rows 324px wide, 3-PR badges wrap inside the column, long name clipped, chevron and
  `role="button"` intact, no sheet horizontal scroll.
- **Stats recent list**: the same shared row renders in "Recent workouts" at 1200 and 360 with the
  same badge layout (screenshot inspection).

**Empty/no-PR/malformed handling.** Legacy row without `prs`, `prs: null`, `prs: []`, `prs` as a
string → 68px row, no strip, no crash. Ghost ids (no matching entry + a zero-load entry) → no
strip, no crash. Unknown catalogue id → raw-id fallback "gone-lift PR 55 kg" (Title-Cased on
screen), no crash. Warm-up/unchecked rows and duplicates are excluded by `prLifts` (unit tests)
and duplicates collapse to one badge (happy-dom test).

**Wording/capitalisation.** The badge reads "<Lift> PR <load> <unit>" ("Clean PR 130 kg",
"Snatch PR 102 kg"); "PR" is the visible word (not colour alone), uppercase as in `it.js`; the
lift name uses the app's existing `.capitalize` convention for exercise names in list rows, and
the load matches the `fmtNum` + unit form used by the tiles. Italian `t('PR')` is `PR`
(`locales/it.js:1431`), so the badge word needs no translation; Italian exercise names can be
longer (`exerciseNameFor` appends the English name) and would clip through the same
`max-width`/ellipsis CSS — I checked that structurally, not in a live Italian render.
