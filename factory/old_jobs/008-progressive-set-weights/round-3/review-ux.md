VERDICT: PASS

# UX review — 008-progressive-set-weights, round 3

Branch `factory/008-progressive-set-weights`, commit `2282365` (`main...HEAD`, 975+/66−; the
round-3 rework is 19+/2− in `frontend/src/sheets.jsx` and `frontend/src/sheets.test.jsx`).
Read: `spec.md`, `build.md`, `DESIGN.md`, `AGENTS.md`, `round-2/review-ux.md`, both previous UX
reviews, `round-2/review-ui.md`, and the round-3 diff. Drove the branch like a visitor: Vite from
the worktree on port 5181, headless Chromium via agent-browser (isolated session), viewport
390×844 and 360×800, fresh profile, starter plan loaded, then a two-movement complex seeded into
a day and the whole rework path done through the real UI. No product code or test touched; this
file is my only write.

## Findings

None. Round 2 had no blocking UX findings, and the round-3 rework closes the one cross-lens
defect without opening a new dead end or disagreeing surface.

## Notes

- **The card's Sets step now re-asserts the card's load, silently.** With a ladder on the complex
  head, changing the card's Sets writes `weight: ladder[0]` to every member
  (`frontend/src/sheets.jsx:1302-1313`, the new write at `:1308`). In the rework path a movement
  whose own row had been set to 70 (which clears its ladder) ends at weight 60 when the card's
  Sets goes 4 → 5 — its `4 × 3 · 70 kg` own line disappears and nothing asks about the 70. This is
  the fix working as intended: the sheet says "Sets and load are the same for every exercise in
  the complex", and the round-2 alternative was worse — a stored weight of 70 that the built set
  rows (60/60/65/65/65) silently ignored. Not a finding; recorded so the behaviour is a decision
  rather than a surprise. If member-specific loads inside a complex are ever wanted, that needs
  its own design.
- **Carried from round 2, still open, still non-blocking.** Zero-load configs print the
  "Weights per set" row with an empty value (the spec defines `weightsLabel` as `''` for no load);
  the `Stepper` ± verbs are still hardcoded English on the Italian UI (pre-existing in every
  stepper); a long ladder still ellipsises in `.lrow-v` (DESIGN's documented value behaviour).
- **The 5-set heading fits.** The complex head now carries one more load than the 4-set case
  round 2 measured: `.cx-load` is 165×18px, one line, at both 390 and 360, and
  `document.scrollWidth` is 375 / 345 against the viewport — no horizontal scroll, no orphaned
  `kg`.

## What I verified, and how

**The round-2 defect path, live.** Seeded two movements in a complex (`sets: 4, weight: 60`),
opened the card → *Weights per set* → 60/60/65/65 → Done: both members stored the ladder. Then
opened the second movement's own row, set Weight 70 → Save: the member stored `{ sets: 4,
weight: 70 }` with no ladder (the exact round-2 repro state), and the week editor showed the
disagreement the old code produced — card head `4 sets · 60/60/65/65 kg`, member row its own
`4 × 3 · 70 kg`. Reopened the card, raised Sets 4 → 5, Done: both members stored
`{ sets: 5, weight: 60, setWeights: [60,60,65,65,65] }`, the card head reads
`5 sets · 60/60/65/65/65 kg`, and the member's own line is gone because it now agrees with the
card. The invariant `setWeights[0] === weight` holds for every member, so print, progression and
backups see the same plan the set rows will build.

**The week → session flow still adds up.** Set the day to today's weekday, started it from Home:
the complex heading read `5 sets · 60/60/65/65/65 kg` and the five set rows read 60, 60, 65, 65,
65. Starting without weighing in produced no errors.

**The running session's options sheet still hides the row and keeps the ladder** (requirement 9):
*Progression settings* showed Sets/Reps/Weight/Warm-up sets/Rest/Bar and no *Weights per set*;
saving it unchanged left `target.setWeights` at `[60,60,65,65,65]` and the set rows unchanged.
The round-3 fix only runs when the complex card's Sets changes, so this surface is untouched.

**Old weeks are untouched.** The starter days (stored without `setWeights`) still print their
plain `5 × 3`, `4 × 3`, `3 × 3`, `3 × 5` lines in the week editor, with no ladder and no new
control.

**Errors and copy.** `agent-browser errors` empty through the whole walkthrough; the console only
carries Vite's connect and React's dev-tools notice. Round 3 added no user-facing string — one
new key ("Weights per set" → "Pesi per serie") from round 1, and the existing `Row` / `Stepper` /
`Done` vocabulary, 44px targets, no new visual pattern.

**Test evidence.** `npx vitest run src/sheets.test.jsx` → 12 passed, including the new case
"writes the ladder's first load as the movement's weight" (head 60/60/65/65 + member `{ weight: 70 }`,
card Sets → 5, both members `{ weight: 60, setWeights: [60,60,65,65,65] }`).
