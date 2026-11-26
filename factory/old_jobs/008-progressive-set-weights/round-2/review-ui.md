VERDICT: PASS

Scope: job 008, round 2 — branch `factory/008-progressive-set-weights` at `29f2b57`
(`git diff main...HEAD`, 21 files) and the round-2 commit `191e7bb..29f2b57` (11 files), read
against `DESIGN.md`, the `m3-ui` references and `antislop-ui`, and against my
`round-1/review-ui.md`. Rendered the real app from the worktree on port 5182 with agent-browser at
390×844 and 360×800 client width (headless Chromium's classic scrollbar eats 15 px, so where I quote
a 390/360 layout the window was 405/375), plus 1280×900, dark and light; no page errors. I changed
no code and no test — this file is my only write.

## Findings

None.

## Round-1 finding — verified fixed

The ladder label no longer breaks between its numbers and its unit. `weightsLabel`
(`frontend/src/lib/history.js:149-165`) joins a ladder's last load and unit with U+00A0 while a
one-load label keeps the plain U+0020 — called the module directly: `weightsLabel({weight:60})` is
`60 kg` (char codes …32,107,103) and a 4-set ladder is `60/60/65/65 kg` (…160,107,103). Measured
on the real render with character-level `Range` rects:

- Week editor row (`frontend/src/views/WeekEdit.jsx:117`) at 390×844 and 360×800:
  `4 × 5 ·` is line 1 and `60/60/65/65 kg` line 2 (`.ss` 36.1 px = two lines) — no lone `kg`.
- Workout complex heading (`frontend/src/views/Workout.jsx:410,726`) at 360×800:
  `.ss-hd .ss-load` (139.1 px, 34.8 px = two lines) reads `4 sets ·` / `60/60/65/65 kg`; it is
  one line at 390×844 as before.
- Coach review: a corrected standalone row prints `4 × 3 · 50/50/55/55 kg` on one line at 390 and
  at 360, and the complex head `4 sets · 50/50/55/55 kg` on one line at 360.

## Round-2 visible changes — checked

- **The unit in the ladder sheet.** Each `Set N` stepper now reads `60 kg` (150×44 pill, 70 px
  value cell). `102.5` keeps `clientWidth 51 / scrollWidth 51` and the unit stays inside the pill;
  no horizontal scroll at 390/360 (`scrollWidth === clientWidth`), desktop sheet 640 px wide,
  dark and light. Same in the coach correction sheet, where the unit is the display unit.
- **Accessible names.** The field is named `Set 1` and the buttons `Decrease Set 1` /
  `Increase Set 1`; `Stepper`'s new `ariaLabel` is optional and only the ladder sheet passes it,
  so no other stepper changed visually or semantically.
- **Per-member ladder length** (the round-1 UX fix): with a 3-set head + 4-set member the ladder
  sheet shows three steppers, Done stores `[60,60,65]` / `[60,60,65,65]`; the card head reads
  `3 sets · 60/60/65 kg`, the 3-set movement keeps `×5` and the 4-set one prints
  `4 × 3 · 60/60/65/65 kg` on one line. The rendering holds at 390.
- **Flatten-to-uniform in the coach review:** 50/50/55/55 → reopen → all four 50 → Done/Save turns
  the row back into `4 × 3 · 50 kg` (plain space) and drops the ladder.
- **Running session:** the options sheet still has no "Weights per set" row (Sets, Reps, Weight,
  Warm-up sets, Rest, Bar) and the session's four rows read 60, 60, 65, 65.
- **Nothing else moved:** `git diff 191e7bb..29f2b57 -- '*.css'` is empty (no token, radius,
  shadow or colour touched), no new locale key (the round-2 diff only reuses `Set {0}`; `it.js`
  unchanged), and the round-2 visuals are exactly the ladder sheet's unit and the set names above.

## Notes

- **Long ladders, measured but not a finding.** A 4-set ladder of 3-digit loads
  (`102.5/102.5/107.5/107.5 kg`) is one unbreakable 172 px token, wider than the row's text column
  (106 px in the 390 layout, 76 px at 360). It wraps to a second line that runs to x≈288, across
  the x-range of the row's action buttons, but 3 px *below* the icon glyphs (icons 592–610 /
  line 613–629 at 390; 606–624 / 627–643 at 360) and inside the row and card — no clipping, no glyph
  overlap, `documentElement.scrollWidth` unchanged. The same 3-digit ladder in the workout heading
  at 360 makes the header content overflow `.ss-hd` by 19 px and the Unpair button box by 11 px
  past the card while the label itself ends at the card edge and the page does not scroll. Both are
  honest edge cases I measured rather than failures; a tighter format or a clamp would only be
  needed if such plans become normal.
- **Zero-load configs still show the row with an empty value** (round-1 note, unchanged): the spec
  defines `weightsLabel` as `''` with no load, so a fresh starter-plan exercise prints just
  "Weights per set ›". It works; `0 kg` or hiding the row until there is a load would read more
  finished, but that is a spec call, not a defect.
- **Cross-lens note for the code owner (not a UI finding).** `ComplexConfig.setSets`
  (`frontend/src/sheets.jsx:1298-1310`) writes the head's resized ladder to every member but leaves
  each member's `weight` untouched, so a member whose uniform weight was edited on its own row can
  end with `setWeights[0] !== weight`. Reproduced with the UI alone on a 2×4-set complex: create the
  card ladder 60/60/65/65 → open the second movement's row and set Weight 70 (it clears that
  member's own ladder) → change the card's Sets to 5 → that member stores
  `{ sets: 5, weight: 70, setWeights: [60,60,65,65,65] }`. Invisible in the surfaces I checked (the
  card leads with the head and the member prints its own ladder line), but it breaks requirement 1's
  invariant; writing `weight: ladder[0]` when a ladder is written there would keep it. Present in
  the round-1 code as well, unchanged by round 2.
- **Anti-slop:** unchanged from round 1 — no gradient, glass, glow, emoji, decorative badge, invented
  number or dead control; every new visual is the app's existing `.lrow` / `Stepper` / sheet
  vocabulary in the existing tokens.
