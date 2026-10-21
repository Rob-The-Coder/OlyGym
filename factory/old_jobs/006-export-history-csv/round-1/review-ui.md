VERDICT: PASS

# UI / M3 review — job 006 (Export History as CSV)

Commit 0a91b0f, product diff f126cb3..0a91b0f (8 files, all under frontend/src). Read spec.md,
build.md, DESIGN.md, CLAUDE.md, CONTRIBUTING.md, SECURITY.md, the full diff, and the five captures.

## What I verified

### Where the action lives
`History.jsx:75` adds the download button to the TopAppBar `actions` fragment, after the plus
(`History.jsx:74`) and only when `S.workouts.length > 0`; `TopAppBar.jsx:53` wraps the fragment
in `.ab-acts`. That is the same two-action shape as `Plan.jsx:116-119` (plus + upload) and the
M3 large top app bar's trailing-action slot. It is a screen-level action on the whole log, not a
row action and not the screen's create action, so this is the right home: not a FAB (the plus is
already the bar's primary action), chip, card or modal. No new component, class, CSS or inline
style is introduced.

### Tokens / visual grammar
`git diff f126cb3..0a91b0f -- frontend/src/index.css frontend/src/m3.tokens.css
frontend/src/m3.components.css` is empty, and no raw colour, radius, shadow or duration is added
anywhere in the diff. The button reuses `.iconbtn .ab-ico` and inherits the 48×48 box,
`--m3-shape-full`, `--m3-on-surface` and the 24px glyph from `m3.components.css:781`. The
`download` glyph already exists (`Icon.jsx:103`) and is already the app's export glyph
(`Settings.jsx:325`, "Export backup (JSON)"), so this is neither a new icon nor a new visual
idiom.

### Accessibility (live DOM, 360×800 and 1200×900, headless Chrome)
- Both bar actions measure 48×48 CSS px: plus 258→306, export 308→356, top 16→64 at 360; plus
  1038→1086, export 1088→1136, top 40→88 at 1200. Glyphs 24×24, stroke 1.9.
- Accessible name: `aria-label="Export history as CSV"` with a matching `title`; the SVG is
  `aria-hidden="true"`, so the name is exactly the label (no "download" noise). Italian:
  "Esporta la cronologia in CSV" (`it.js:344`).
- Keyboard: Tab reaches the button, `el.matches(':focus-visible')` is true, and the computed style
  is the app's existing focus treatment — accent outline (rgb(210,187,255) dark / rgb(103,80,164)
  light) plus the `.iconbtn:focus-visible` state layer (`m3.components.css:495`; outline from
  `index.css:128`), at `border-radius: 999px`. The 64dp row does not clip it (row 8→72, ring
  12→68).
- Meaning is glyph + accessible name, never colour; the glyph colour is the inherited on-surface
  role, which the repo's contrast test covers.
- The button is absent on an empty log: the empty capture shows the plus alone, and the screen
  test's third case pins that.

### Layout and hierarchy (captures)
Read all five: 360 dark, 360 light, 1200 dark, empty 360 dark, 360 dark exported.
- 360: `innerWidth === scrollWidth === 360`; the title "History" sits on its own line below the
  64dp action row, so there is no crowding with either action; the pair sits at the right edge
  with the 4dp inset.
- 1200: `innerWidth === scrollWidth === 1200`; the same pair sits together at the right, beside
  the title, with no stretch or overflow. I re-measured this live rather than trusting the report.
- The download glyph (arrow into a tray) and the plus are distinct at 24px, same weight and
  colour; the bar still reads as one M3 app bar rather than two competing controls.

### Snackbar and states
- The exported capture shows the existing tokenized `#toast` with "History exported";
  `Toast.jsx:10` keeps `role="status" aria-live="polite"` with the text in its own `<span>`, so
  it is announced; the surface is `--m3-surface-container-highest` on `--m3-on-surface`.
- I clicked the real web action in the dev build: the captured Blob was
  `text/csv;charset=utf-8`, 1001 bytes, header-first records, and the toast text became
  "History exported". The click completed without throwing. (The BOM is pinned by `csv.test.js`;
  `Blob.text()` strips it, so that byte is not visible from the click.)
- Empty log: action absent (capture + test). Success: toast. No loading state is needed for a
  synchronous local write. The web failure path toasts "Could not export the history", and a
  dismissed mobile share sheet is deliberately silent — both per spec.

### Checks run
- `npx vitest run src/lib/csv.test.js src/lib/history-csv.test.js src/views/History.export.test.jsx`
  → 3 files, 28 tests passed.
- `npm test` → 113 files, 1210 tests passed.
- `npm run build` → clean (exit 0).
- `node scripts/check-source-strings.mjs --strict` → exit 1, 622 translated strings, exactly the
  10 pre-existing missing keys, none of the three new ones.

## Non-blocking notes

1. The action is icon-only, so a sighted touch user gets no visible "CSV" word; the download glyph
   carries the meaning until the toast confirms. That is the spec's copy and it matches the app's
   existing download = export vocabulary (`Settings.jsx:325`) and Plan's plus + upload, so it is
   not a defect.
2. `build.md` ships no focus capture for this action. I verified the ring live via the DevTools
   protocol instead; the gap is in the report's evidence, not in the product.
