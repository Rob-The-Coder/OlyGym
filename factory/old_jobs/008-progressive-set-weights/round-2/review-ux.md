VERDICT: PASS

# UX review — 008-progressive-set-weights, round 2

Branch `factory/008-progressive-set-weights`, commit `29f2b57` (`main...HEAD`, 958+/66−).
Read: `spec.md`, `build.md`, `DESIGN.md`, `AGENTS.md`, the full branch diff, and
`round-1/review-ux.md`. Drove the branch like a visitor: Vite from the worktree on port 5181,
headless Chromium via agent-browser, viewport 390×844 and 360×800, fresh profile (storage cleared
→ starter plan), plus a synthetic coach week opened through the dev module import (the DESIGN §10
way) to reach the review and correction screens. No product code or test touched; this file is my
only write.

## Findings

None. Both round-1 findings are fixed, and the round-2 changes introduce no new dead end, broken
state or unreadable label that I could reach.

## Notes

- **Zero-load configs still show the row with an empty value.** Every exercise of a fresh starter
  plan is loaded at 0, so "Weights per set ›" prints with nothing after it (round-1 note,
  unchanged). It is the spec's literal wording (`weightsLabel` is `''` with no load) and the row
  still works; hiding it until a load exists, or printing `0 kg`, would read more finished. Not
  blocking.
- **The stepper's accessible verbs are still hardcoded English.** The ladder now announces
  "Set 3", "Decrease Set 3", "Increase Set 3", so the set name is translated but the verb is not:
  on the Italian UI a screen reader hears a mixed-language name. Every stepper in the app already
  had the bare English "Decrease"/"Increase" (`components/ui.jsx:169,175`), so this is not new; if
  it is taken up, localising the verb in `Stepper` once fixes every screen.
- **A long ladder still ellipsises in `.lrow-v`.** That is DESIGN's documented value behaviour and
  it was already noted in round 1; no change requested.

## What I verified, and how

**Round-1 finding 1 — a complex's ladder could be the wrong length for a movement: fixed.**
Paired Push Jerk (4×3) with Clean Pull (3×5) in the week editor, opened the complex head →
*Weights per set* → 60/60/65/65 → Done. The card head reads `4 sets · 60/60/65/65 kg`, the 3-set
Clean Pull keeps its own line `3 × 3 · 60/60/65 kg`, and storage holds one load per set for each
member (`[60,60,65,65]` on the 4-set movement, `[60,60,65]` on the 3-set one). Started the day's
session: Push Jerk's four rows read 60, 60, 65, 65 and Clean Pull's three rows 60, 60, 65 — the
"four sets, three loads" case from round 1 can no longer be built through the UI.

**Round-1 finding 2 — the per-set steppers had no accessible name: fixed.** The *Weights per set*
sheet now exposes four fields named `Set 1`…`Set 4`, each with its unit (`kg`) beside the value,
and the ± buttons are `Decrease Set 1` … `Increase Set 4`. Keyboard order is logical — minus,
field, plus for each set, then Done — and the same sheet opened from the coach correction carries
the names too (I filled it by accessible name).

**Round-1 note — the ladder label left its unit alone on a line: fixed.** At 360 in the running
session the complex heading wraps into two line boxes only: `4 sets ·` (44px) and
`60/60/65/65 kg` (95px). The week row and the complex head measure on one line at 390 and 360;
nothing puts a bare `kg` on its own line. The coach review row behaves the same.

**The coach review now drops a flat ladder (round-2 code fix), seen live.** A synthetic coach week
with a 4×1+2 complex at 50 kg: correcting *Weights per set* to 50/50/55/55 turns the card head into
`4 sets · 50/50/55/55 kg`; reopening the same row and flattening every set back to 50 leaves
`4 sets · 50 kg` — no ladder, and the coach's own row still prints underneath as the record.

**The ladder reaches the week through the import.** The same synthetic week corrected to
50/50/55/55 and added to the plan stores `"weight": 50, "setWeights": [50,50,55,55]`, and the week
editor row reads `4 × 3 · 50/50/55/55 kg` (the standalone row, no extra surface).

**The running session's options sheet still hides the row and keeps the ladder.** ⋯ → Progression
settings shows Sets/Reps/Weight/Warm-up sets/Rest/Bar and no "Weights per set". Saving it
unchanged leaves `target.setWeights` `[60,60,65,65]`. Raising Sets 4 → 5 resizes it to
`[60,60,65,65,65]` and rebuilds the rows; touching Weight 60 → 62.5 clears the ladder and the rows
go uniform — the in-session edit cannot silently flatten a progressive plan.

**Old weeks are untouched.** The starter plan's exercises (no `setWeights`) still print their
`5 × 3` / `3 × 5` lines and start the same uniform set rows as before the change.

**Errors:** `agent-browser errors` empty through the whole walkthrough; the console only carries
Vite's connect and React's dev-tools notice. Copy: one new key ("Weights per set" → "Pesi per
serie"); the row and the sheets use the existing `Row` / `Stepper` / sheet vocabulary and the
existing `Set {0}`, `Done`, `Weight ({0})` keys.
