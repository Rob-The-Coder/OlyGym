VERDICT: PASS

# UI / Material Design 3 review — 005-show-best-lift-pr-badges, round 1

Branch `factory/005-show-best-lift-pr-badges`, commit `3947ef7`, `main...HEAD` from `d64c4fe`.
M3 lens (SKILL.md rules + self-audit) and OlyGym's `DESIGN.md` role/token system.

## Scope

- `frontend/src/m3.components.css` — the `.pr` re-point and the new `.prs` / `.pr-name` rules
  (`m3.components.css:238-254`, Lists section).
- `frontend/src/sheets.jsx` — `WorkoutRow` PR strip (`sheets.jsx:1573-1583`) and the two other
  `.pr` call sites it affects (`sheets.jsx:523`, `sheets.jsx:1342`).
- `frontend/src/sheets.workout-row.test.jsx` (new, 3 tests) and the `prLifts` helper it renders
  (`frontend/src/lib/history.js:641`) plus its suite (`frontend/src/lib/history.test.js:945`).
- `frontend/src/index.css` is untouched (`git diff main...HEAD -- frontend/src/index.css` is empty),
  no store-schema change, no new locale key, no new component or dependency.

I modified no product code and no test; this file is my only write.

## Blocking findings

None.

## Non-blocking notes

1. `.prs` uses raw spacing (`gap:6px;margin-top:4px`, `m3.components.css:253`). DESIGN.md §3 bans
   a raw **colour, radius, shadow or duration**, not spacing, and the system has no spacing-token
   family; the declaration is a verbatim copy of the existing `.mchips` strip (`index.css:1235`),
   so it is consistency rather than a new one-off. If a spacing scale lands later, promote these.
2. `.pr`'s type metrics (12px / 500, no tracking token) are inherited from the legacy
   `index.css:1251` rule; the new rule deliberately re-points colour, radius and overflow only.
   DESIGN.md §6 says element sizes live per selector and the app's own sizes/weights stay, so this
   is correct — but a future type pass that re-roles chips/labels must include `.pr`.
3. The fill is `--m3-primary-container`, exactly the pair the row's leading icon tile already uses
   (`.lrow-i`, `m3.components.css:695`). The badge therefore reads as part of the row's existing
   tonal identity, not a new louder accent, and PR rows are exceptional — the emphasis is earned.
   The thing to watch (DESIGN.md §3, "one accent") is only if History ever lists many PR rows at
   once; no change needed now.
4. The badge is non-interactive and must stay so: computed `cursor:auto`, no `role`, `tabIndex:-1`,
   no hover/press rule, and not in the shared `:focus-visible` ring list
   (`m3.components.css:495-496`). The row keeps `role="button"`, `tabIndex=0`, its 68px target and
   the chevron; selected/disabled/loading states are not applicable to a static status label.
5. Accessible name is text, not an `aria-label` workaround: the AX tree exposes the row as role
   `button` with the badge words in its name (measured: "… Clean PR 130 kg Snatch PR 102 kg …").
   Badges are separated only by whitespace in that name — tolerable, no separator needed.
6. Ellipsis is the agreed trade (spec): at 320 CSS px the 62-character carry clips (name client
   100px vs `scrollWidth` 348px) so the chevron and tap target survive; the full name stays in the
   accessible name and on the session detail. `white-space:nowrap` on `.pr`
   (`m3.components.css:249`) is a disclosed spec deviation; it is layout-only (not a banned raw
   value) and prevents "PR 130 kg" wrapping under the name.
7. No duplication on the History screen: the old trailing "N PR" count is gone, and the "PRs" stat
   tile is a summary where the badges are the detail. The finish-summary `.chip`s are untouched
   and the two other `.pr` markers (`sheets.jsx:523` exercise-history, `sheets.jsx:1342`
   WorkoutDetail) keep their markup and inherit the one badge look on purpose — one PR look, not
   two, and no second control.
8. `.pr` becoming a global rule that sheets also consume is a coupling worth remembering: it sits
   in the Lists section (spec-mandated) and its comment (`m3.components.css:238-243`) records that
   the sheet markers inherit it; a restyle of either consumer has to touch this one rule.

## Evidence

- **Tokens/contrast.** `cd frontend && npx vitest run src/m3.tokens.test.js src/lib/contrast.test.js`
  → 2 files, 93 tests pass. Across the 18 generated schemes (default + 8 accents, dark + light)
  `--m3-on-primary-container` on `--m3-primary-container` measures **7.19:1 worst (lime)**, 7.27:1
  default dark, 13.28:1 default light — ≥ 4.5:1 for 12px, matching the spec's figure. The fill is
  opaque, so the row surface under the badge cannot change the pair.
- **Token/role use (computed, real render).** `.pr` at 360 light: `background-color:
  rgb(233, 221, 255)` (`--m3-primary-container`), `color: rgb(21, 13, 86)`
  (`--m3-on-primary-container`), `border-radius: 8px` (`--m3-shape-s`), `max-width: 100%`,
  `white-space: nowrap`; at 360 dark: `rgb(76, 56, 137)` / `rgb(233, 221, 255)`. No raw colour,
  radius, shadow or duration added by the diff. `.prs` computes `display:flex; flex-wrap:wrap;
  gap:6px; margin-top:4px`; `.pr-name` computes `min-width:0; overflow:hidden;
  text-overflow:ellipsis; white-space:nowrap; text-transform:capitalize`.
- **Real render** (I started Vite on 5173 and headless Chrome on CDP 9222, seeded a no-PR workout,
  a 1-PR workout and a 3-PR workout whose third lift is the catalogue's 62-char carry):
  - 360 light: `document.documentElement.scrollWidth === innerWidth === 360`; row 16→344, text
    column 78→301, metadata bottom 365, `.prs` top 369, badge right ≤ 301, chevron 315→330 — the
    badges never reach the chevron; the long name paints ellipsis (client 140 / scroll 348). Three
    PRs wrap to three lines (row 144px); the no-PR row renders **no** `.prs` (row 68px).
  - 320 light (reflow floor): `scrollWidth === innerWidth === 320`; long name client 100 / scroll
    348; metadata wraps to two lines; chevron and row edge intact; no page overflow.
  - 1200 dark: `scrollWidth === innerWidth === 1200`; two-column list, 432px text column; first two
    badges share a line (top 393), the third wraps (top 420); title bottom 369 → no overlap.
  - Keyboard: Tab lands on an `.item` with `:focus-visible` true and the shared focus layer painted
    (`background-image` gradient); `.pr` is not focusable and not in the ring list.
  - AX: role `button`, name "Competition week Sat 28 Mar · 1h 0m · 3 sets · 900 kg Clean PR 130 kg
    Snatch PR 102 kg Snatch Grip Overhead Carry Overhead Squat With Hanging Weights PR 60 kg".
- **DOM/unit tests.** `npx vitest run src/sheets.workout-row.test.jsx src/lib/history.test.js`
  → 115 tests pass (3 + 112): strip structure, heaviest-first order, session-best load, no-PR empty
  state, duplicate collapse, and the removal of the "N PR" count. The new DOM test also asserts
  `role="button"` and that `.grow .ss` is directly followed by the strip.
- **Visual.** Viewed the committed captures `oly-previews/factory-005-history-360-dark.png` and
  `…-360-light.png` (mtime 20:55, commit `3947ef7` at 20:55:06) plus my own re-render of the same
  code: the badge reads as a small tonal **label** — same container tone as the row icon tile, no
  state layer, no chevron, and the meaning is a visible word "PR" plus the trophy, not colour alone.
  Light/dark read the same layout; light paints the light pair, dark the dark pair.

## M3 self-audit

| Category | Score | Why |
|---|---|---|
| Task clarity | 2 | The row now answers which lift and what load where it used to say "2 PR"; the old count is gone, no dead control. |
| Information hierarchy | 2 | Badges sit under the metadata line inside the title column; measured no overlap with title or chevron at 360/320/1200. |
| Component semantics | 2 | Static status label, not a button/chip: no role, no tab stop, no cursor, no state layer; the whole row is the single control; reuses the existing `.pr` class, no component invented. |
| Token discipline | 2 | Role pair + `--m3-shape-s`, no raw colour/radius/shadow/duration added; the only raw values are 4/6px spacing matching `.mchips`, with no spacing-token family existing (note 1). |
| Adaptive behavior | 2 | 360/320 wrap and ellipsis inside the column; 1200 shares lines; no horizontal overflow at any measured width, dark and light. |
| States & feedback | 2 | Badge has no hover/press/selected/disabled states because it is not interactive; row focus-visible unaffected; empty state is the absence of the strip for workouts without `prs` (legacy/backfilled rows stay bare). |
| Accessibility | 2 | Contrast 7.19:1 worst; row AX name includes the badge text; trophy is `aria-hidden`; "PR" is a visible word (meaning not colour-only); 68px row target and chevron intact; keyboard focus unchanged; full lift name preserved in the accessible name when visually clipped. |
| Expressive restraint | 2 | One tonal container for the milestone, the pair the row icon already uses; no extra glyphs, colours or motion. |

No `0`s and no category below `2`, so nothing blocks approval on the M3 self-audit.

## Method limits

- A second process later reused the shared dev browser and replaced the seeded store between runs;
  the figures above are from contiguous runs against `3947ef7`, and I re-checked the settled page
  for overflow (no element past the viewport; `documentElement.scrollWidth === innerWidth === 360`).
- The WorkoutDetail per-entry marker and the exercise-history session marker were inspected
  statically (unmodified markup at `sheets.jsx:523` and `sheets.jsx:1342`; they inherit this one
  `.pr` look by design) rather than separately re-rendered; the finish-summary `.chip`s are
  untouched and out of this diff.
