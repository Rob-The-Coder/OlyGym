VERDICT: CHANGES

# UX review — 008-progressive-set-weights, round 1

Branch `factory/008-progressive-set-weights`, commit `191e7bb` (`main...HEAD`, 856+/62−).
Read: `spec.md`, `build.md`, `DESIGN.md`, `AGENTS.md`, the full branch diff, `sheets.jsx`
(`SetWeightsSheet`, `ExConfig`, `ComplexConfig`, `CoachEntry`), `views/WeekEdit.jsx`,
`views/Workout.jsx`, `components/CoachImport.jsx`, the new `lib/history.js` helpers and tests.

Drove the branch like a visitor: Vite from the worktree on port 5181, headless Chromium via
agent-browser, viewport 390×844 and 360×800, fresh profile (storage cleared → starter plan). I used
the week editor, the session start, the in-session options sheet, and the coach review (opened with
a small synthetic coach week through the dev module import, the DESIGN §10 way) to reach the new
surfaces. No product code or test touched; this file is my only write.

## Findings

1. **A complex's ladder can end up the wrong length for a movement, and the week prints it anyway.**
   `frontend/src/sheets.jsx:1312-1324` (with `:1285`). `ComplexConfig` normalizes the ladder to the
   **head's** set count (`sets = first.sets || 1`) and writes that array unchanged to every member.
   Pairing two configs does not unify their set counts — only the Sets stepper does — so a member
   can carry fewer (or more) sets than its ladder.
   Reproduced: pair Front Squat (3×5) with Back Squat (4×10), open the complex head → *Weights per
   set* → 60/60/65 → Done. The week editor then prints the card head as
   `Complex 3 sets · 60/60/65 kg` and the Back Squat row as `4 × 10 · 60/60/65 kg`
   (`views/WeekEdit.jsx:137` via `weightsLabel`): four sets with three loads, so the row does not say
   what the fourth set will be. Starting the session fills that fourth row from `cfg.weight`
   (`history.js:121-125` `setWeightAt` → `buildWorkSets` `history.js:392-424`), i.e. 60 after 65 —
   a "two light, two heavy" plan ends on the light load and nothing tells the visitor.
   Fix: write the ladder per member against that member's own set count inside `openSetWeights`'s
   `writeMembers` — `normalizeSetWeights(values, Math.max(1, Math.round(row.sets) || sets), firstLoad)` —
   so every movement stores exactly one load per set (or have the ladder write the head's `sets` to
   all members, the way the Sets stepper does). A `4 × 10` line must always print four values.

2. **The per-set steppers have no accessible name.** `frontend/src/sheets.jsx:954`.
   `Stepper label={t('Set {0}', i + 1)}` renders the caption as `<span class="stp-l">`
   (`components/ui.jsx:167-178`) — it is not a `<label>` and is not referenced by the input (the ±
   buttons keep the hardcoded "Decrease"/"Increase", `ui.jsx:169,174`). In the accessibility tree the
   new sheet is therefore four or five identical "Decrease / 0 / Increase" groups: a screen-reader
   visitor cannot tell which set is which, on the one control the feature exists for. The app
   already solves this exact case in the running workout
   (`views/Workout.jsx:319`: `aria-label={t('Set {0}', num)}` on each set button).
   Fix: give `Stepper` an optional accessible label and pass it to `NumberField` (it already spreads
   `...rest` onto the `<input>`, `ui.jsx:32`), or use a real `<label htmlFor>`; the visible caption can
   stay. `SetWeightsSheet` is shared, so this one change also fixes the coach correction sheet.

## Notes

- **No unit in the ladder sheet.** It shows bare numbers under "Set 1…N" while every other weight
  control in the app names its unit ("Weight (kg)", `Weight ({0})`). A lb account, or anyone
  double-checking, only sees `kg` after leaving the sheet. `Stepper` already accepts `unit`
  (used at `ui.jsx:171`); passing it would read `Set 1 — 60 kg`.
- **Zero-load configs show an empty row.** `weightsLabel` returns `''` when there is no load, so on
  every exercise of a fresh starter plan the row reads just "Weights per set ›" with no value
  (`sheets.jsx:1097`, `:1333`, `:1425`). It looks unfinished; `0 kg` or hiding the row until a load
  exists would read better. Not blocking — the row still works and matches the spec's literal wording.
- **Long ladders ellipsise in the row.** A 6-8 set ladder of 3-digit loads will be cut by `.lrow-v`;
  that is DESIGN's stated behaviour ("the value ellipsises — do not fight that") and the week editor
  line wraps rather than clipping, so no change requested.
- **Spacing in the complex sheet.** The new row sits flush under the Weight pill
  (`sheets.jsx:1332-1335`), where `ExConfig` gives the row 12px of air below its stepper group.
  Cosmetic.
- The month-old exercise config (opening an existing week row) keeps its ladder, the running
  session's options sheet correctly hides the row and saves it back unchanged, and an old week
  without `setWeights` still prints its line and starts the same rows — all checked live.

## What I verified, and how

- **Week editor:** sets 4 / weight 60 → *Weights per set* → two steppers raised to 65 → Done → Save;
  the row reads `4 × 10 · 60/60/65/65 kg`, storage has `"weight": 60, "setWeights": [60,60,65,65]`.
  The ladder sheet's four "Set N" rows, the primary Done, and the sheet stack (Done returns to the
  exercise sheet, its row updated) all behave; 5-set ladder at 360px fits with no horizontal scroll.
- **Session:** the week's four rows read 60, 60, 65, 65; the progress ring/header unaffected; the
  complex card heading renders `4 sets · 60/60/65/65 kg` in `.ss-hd .ss-load` at 390px next to
  "Unpair" with no overflow; an old 0-load week still builds 0-load rows.
- **Requirement 9:** the in-session exercise sheet has no "Weights per set" row (checked the
  progression-settings sheet directly), and saving it unchanged keeps `target.setWeights` —
  `{"sets":4,...,"weight":60,"setWeights":[60,60,65,65]}` after Save.
- **Coach review:** a synthetic week's 4-set complex shows `Complex 4 sets · 50 kg`; correcting one
  movement via *Weights per set* (50/50/55/55) makes the card head `4 sets · 50/50/55/55 kg` and
  "Add the week to my plan" stores `setWeights` [50,50,55,55] with `weight: 50` on both movements —
  the ladder reaches the week editor card. The coach's own "Strappo · 4 × 3 · 50kg" line stays as the
  record, as DESIGN 20 wants.
- **Copy/locale:** one new string, `Weights per set` → `Pesi per serie` (`locales/it.js:117`);
  `Set {0}` and `Done` are reused as the spec asks. No new visual vocabulary: `Row` + `Stepper`
  + sheet, dumbbell icon from the existing set, no raw colour/radius, existing 44px+ row targets.
- **Errors:** `agent-browser errors` empty through the whole walkthrough.

Note on the two guard scripts in build.md criterion 10: I did not re-run them; that is the code
reviewer's ground, and the locale entry for the one new key is present.
