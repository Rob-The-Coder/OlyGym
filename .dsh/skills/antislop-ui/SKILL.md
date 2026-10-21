---
name: antislop-ui
description: >
  UI review lens that filters generic AI-generated UI patterns ("AI slop"):
  default blue-purple gradients, glassmorphism everywhere, pill-everything,
  decorative glow and pulse, bento grids, emoji in copy, invented dashboard
  numbers, filler feed rows, dead links. Tell / Why / Fix per pattern, with a
  closing checklist. Use when reviewing a diff or a page for generic AI design
  tells. Adapted for the OlyGym factory from miqdadbadjuber/anti-slop
  (skills/antislop-ui, MIT).
---

Filter generic AI-generated UI patterns. Every pattern has the same shape:
**Tell** (what it looks like), **Why** (why it reads as generated), **Fix**
(what to require instead). Repo conventions win: in this repo `DESIGN.md`
(M3 tokens/roles) is the authority on color, type, radius, shadow and theme —
this skill adds the "does it look agent-generated?" lens on top, it does not
replace the token check.

## Visual & color

- **Generic blue-purple gradient** — Tell: blue-to-purple/cyan/pink gradients as the primary treatment, full-page glows. Why: the most over-represented treatment in training data; signals "no brand identity". Fix: palette from `DESIGN.md` tokens only; a gradient needs a hierarchy reason.
- **Glassmorphism everywhere** — Tell: backdrop blur on nav, cards, modals at once. Fix: glass on 1–2 elements max; everything else solid.
- **Excessive radius** — Tell: pill-shaped buttons, inputs, cards, badges, modals all at once. Fix: the small radius set from the token file, applied deliberately.
- **Overly soft shadows / glow everywhere** — Tell: every component floats; glow on cards, buttons, icons, borders simultaneously. Fix: elevation only where an element genuinely lifts; glow on at most one focal element.
- **Too many colors / accent spread thin** — Tell: 5+ colors, or one accent on buttons, icons, badges, links and backgrounds at once. Fix: 2–3 core colors + 1 accent, accent at the key moment only.
- **Sterile default** — Tell: flat white, thin grey borders, generic font, no identity. Fix: that is a direction problem — require the design tokens, not more rules.

## Layout & components

- **Template layout** — Tell: hero → subtitle → 2 CTAs → feature grid → testimonials → FAQ → footer, every time. Fix: structure follows the product's actual content; sections that only fill a template get cut.
- **Copy-paste cards / bento mosaic** — Tell: identical feature cards, or a tile-grid of assorted cards by default. Fix: layout reflects content hierarchy; uniform content gets a plain grid or list.
- **Uniform spacing and rhythm** — Tell: identical padding/margins/gaps everywhere, every section "centered title + card grid". Fix: a spacing scale used with intent.
- **Decorative status dot / AI capsule badge / eyebrow badge above the H1** — Tell: glowing pulsing dots, "AI Powered" pills, a badge that repeats the headline. Fix: dots and badges only when they mark a real state or carry information the surrounding text does not.

## Copy & icons

- **Emoji as decoration** — Tell: 🚀 in headlines, ✅ beside bullets, 🔥 on CTAs. Fix: none in UI text; a real icon with a reason, or nothing.
- **Generic AI icons** — Tell: sparkle/star/magic/lightning/robot glyphs, one uniform thin-stroke icon library everywhere. Fix: icons relevant to the feature, or none — the label does the work.
- **AI typography** — Tell: monospace headings as an aesthetic, extreme-tracked uppercase labels, default-roster fonts with no reason. Fix: type from `DESIGN.md`; a choice with a reason beats a trend.

## App & dashboard (OlyGym is an app — these matter most)

- **Default dashboard shell** — Tell: sidebar + top bar + four stat cards + chart + table, whatever the screen is for. Fix: build the screen around the one decision the user makes there.
- **Invented numbers** — Tell: stat cards reading 12,483 / 94.2% with "+12% this week" deltas that no real series backs. Fix: real data wired up, or the one real metric, or an honest placeholder; a delta only with a named comparison period.
- **Filler feed / fake data** — Tell: "Sarah Chen updated a document, 2 hours ago", `John Doe`, `johndoe@example.com`, "Let's build something" in fields. Fix: empty cells stay empty with honest placeholders; feeds show real events or don't ship.
- **Charts without a question** — Tell: "Overview" donut placed because space looked bare. Fix: the chart title states the question it answers ("Tonnage per week, last 8 weeks"); otherwise a sentence.
- **Placeholder states that say nothing** — Tell: "No data available" + spinner for every case. Fix: empty/loading/error each name the cause and the next action; first-run, filtered-to-nothing and permission-denied read differently.

## Structural

- **Dead navigation / non-functional controls** — Tell: links to nowhere, buttons that do nothing. Fix: every control works or carries a visible "Coming soon"; anything else comes out of the diff.

## Checklist (run on every screenshot and on the diff)

- [ ] Palette, type, radius, shadow all come from `DESIGN.md` tokens, not defaults?
- [ ] Accent used at the key moment only; no gradient/glass/glow page-wides?
- [ ] No decorative emoji, no generic AI glyphs, no meaningless dots or badges?
- [ ] Every interactive element has real behavior or a visible "Coming soon"?
- [ ] Numbers, feed rows and table cells real or honestly placeholder — never invented?
- [ ] Empty/loading/error states say why and what to do next?
- [ ] Page holds at phone and desktop widths, light and dark, with readable contrast?

## Factory integration (OlyGym)

When a factory reviewer loads this skill, the skill changes *what to look for*
— never the output contract:

- `.dsh/agents/reviewer-ui.md` owns the output format: first line exactly
  `VERDICT: PASS` or `VERDICT: CHANGES`, one file
  (`round-<N>/review-ui.md`), no edits to code.
- CHANGES only for visible, fixable slop in the branch's own diff (per the
  agent file: "visible inconsistencies or breakage"). Slop that predates the
  branch, and matters of taste, go under Notes.
- If the branch has no visual change, still write PASS and say so — do not
  invent findings to justify the lens.

Source: adapted from https://github.com/miqdadbadjuber/anti-slop
(skills/antislop-ui, MIT), distilled for reviewing an existing M3 app; the
upstream repo has the full 38-rule system with Delivery Gate if you want it.
