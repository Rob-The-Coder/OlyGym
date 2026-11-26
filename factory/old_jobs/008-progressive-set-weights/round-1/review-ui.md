VERDICT: CHANGES

Scope: job 008 branch `factory/008-progressive-set-weights` (191e7bb), product diff
`git diff main...HEAD` (frontend + docs/DATA_IMPORTS.md), read against DESIGN.md, the
`m3-ui` references and the `antislop-ui` lens. Rendered the real app from the worktree on
port 5182 with agent-browser at 390×844 and 360×800 (dark and light) and 1280×900; no page
errors (`agent-browser errors` empty). I changed no code and no test — this file is my only write.

## Findings

1. **The ladder label breaks between its numbers and its unit, leaving `kg` alone on a line.**
   `frontend/src/views/WeekEdit.jsx:117` — on the phone the row's line is 127 px wide (the thumb
   plus the three `.iconbtn` actions take the rest), 36.1 px / two lines tall; measured with
   `Range.getClientRects()` on the real render at 390×844 dark:
   line 1 = `4 × 5 · 60/60/65/65`, line 2 = `kg`. Same at 360×800, and the branch's own capture
   `oly-previews/factory-008-week-row.png` already shows the break inside the complex card.
   `frontend/src/views/Workout.jsx:410,726` — the workout's complex heading does it too at
   360×800: `.ss-hd .ss-load` is a 139 px box, 34.8 px / two lines, line 1 =
   `4 sets · 60/60/65/65`, line 2 = `kg` (at 390 it is one line, so this is DESIGN §8's 360 px
   check failing on the new surface).
   Every other plan line (`4 × 5 · 60 kg`, `5 × 3`) is one line, so the row/card now reads as
   broken beside its neighbours even though nothing overflows (document scrollWidth = viewport).
   Fix: keep the unit with the last load so the label can only break *before* the ladder — a
   non-breaking space before the unit in `weightsLabel` (`frontend/src/lib/history.js:149`,
   with the pinned expectations updated) or an unbreakable load group at those two render sites.
   Verified in the live DOM: with `60/60/65/65\u00a0kg` the week row wraps as line 1
   `4 × 5 ·` / line 2 `60/60/65/65 kg` (still two lines, no orphan; the 86 px load fits the
   127 px line). `white-space:nowrap` on the whole label instead would overflow the row at 390.

## Notes

- The new surface is existing vocabulary only: one `.lrow` (`Row` with `icon="dumbbell"` and
  a chevron) opening a sheet of the shared `Stepper`s with a primary `Button`, and the ladder
  printed through the existing `.ss`/`.cx-load`. No stylesheet is touched:
  `git diff main...HEAD -- frontend/src/index.css frontend/src/m3.tokens.css
  frontend/src/m3.components.css` is empty, so no raw colour, radius, shadow or duration and no
  new pattern. `Weights per set` has its Italian entry (`it.js:117`); `Set {0}`, `Done` and
  `Weight ({0})` are reused.
- Worked end to end in the browser, not just read: exercise config → **Weights per set** →
  four `Set 1`…`Set 4` steppers → 60/60/65/65 → Done → Save stores
  `"weight": 60, "setWeights": [60,60,65,65]` and the week row prints the ladder; starting
  today's session gives set rows 60, 60, 65, 65; the running session's options sheet shows no
  "Weights per set" row (and saving unchanged keeps the ladder). Coach review: the corrected
  complex head reads `4 sets · 50/50/55/55 kg`, an untouched row keeps its old line, the
  correction sheet edits in the display unit. No dead controls, no invented numbers or filler.
- Week editor at 390: the complex head keeps `4 sets · 60/60/65/65 kg` on one line and moves
  the three `.iconbtn`s to a second row. That is the documented `.cx-head` fallback
  (`m3.components.css:608-611`), so not a finding — it just makes that card head two rows tall
  with a long ladder. Same at 1280, where day cards sit two to a row.
- 360: the ExConfig row title wraps to `Weights per` / `set`; DESIGN §4 allows a long row title
  to wrap and the value fits (110/110 px), so not a finding. A 5+-set or 3-digit ladder will
  ellipsise in `.lrow-v` (max-width 55%) while the sheet keeps the full list — the documented
  value behaviour.
- On a config with weight 0 the row's value is blank (the spec's "no load → ''"), while the row
  still opens a ladder seeded at 0. Intentional per spec, but it is the one state where the row
  reads as a title with no value; a follow-up could seed it from the exercise's last load.
- Accessibility: the row is a real `<button class="lrow">` whose accessible name is
  "Weights per set 60/60/65/65 kg"; the sheet adds no icon-only control and its steppers are the
  shared control (324×44 at 360, above the 44 px floor). Colour is never the only signal, no
  motion was added, and the focus ring is the shared one. Contrast is the existing
  `--label-2`/on-surface roles in both themes, not a new pair.
- Anti-slop checklist: no gradient/glass/glow, no emoji or sparkle glyphs, no decorative dots or
  badges, no bento/card mosaic, no dead links. Passes the lens apart from the wrap above.
