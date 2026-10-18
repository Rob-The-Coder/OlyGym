VERDICT: PASS

# UI review — 004-undo-delete-workout, round 2

Scope: the round-2 diff `8e6f3d1..62d8dac` (5 files) and the full product diff
`chore/factory...factory/004-undo-delete-workout` (10 files, all under `frontend/`; factory docs
and `board.json` excluded), read against DESIGN.md. All four round-1 UI findings are fixed; no new
blocker. Measurements below come from a headless-Chrome replica that loads the three real
stylesheets in `main.jsx` order (`index.css` → `m3.tokens.css` → `m3.components.css`) and the
real `Toast` markup, in dark and light.

## Round-1 findings — verified fixed

### 1. The Undo action is tappable again
`#toast .toast-act{pointer-events:auto}` (`m3.components.css:979`) is the only new
`pointer-events` declaration; `#toast` keeps `index.css:1266`'s `pointer-events:none` and
`m3.components.css` adds none. Rendered: `getComputedStyle(.toast-act).pointerEvents` is `auto`,
`#toast` is `none`, and `document.elementFromPoint()` at the button centre returns
`BUTTON.toast-act` in both themes (round 1 returned `HTML`). The bar's tap-through is intact; the
only hit target it grows is the 61.1 × 44 button.

### 2. The target is 44px
`min-height:44px` (`m3.components.css:978`), border-box from `index.css:109` → rendered
**61.1 × 44.0** in light and dark (round 1: ~36.9 tall). `#toast` was trimmed to
`min-height:48px; padding:6px 16px` (`m3.components.css:965`), so the bar renders **520 × 56**,
not the ~70px the old 13px padding would give with a 44px child. Width is also above 44.

### 3. Label contrast on the press layer
Label = `--m3-on-surface` (`m3.components.css:976`). Computed from the shipped tokens for all 18
dark/light accent schemes, with the 10% on-surface press layer over
`--m3-surface-container-highest`:

- light default: **10.95:1** (`#1d1a21` on `#d0cdd6`)
- dark default: **6.84:1** (`#e4e1ea` on `#4c4950`)
- worst accent: **6.81:1** (lime, dark)

Round 1's 4.11:1 is gone. The persistent focus layer is also 10% (tokens: focus = press = 10%), so
the same numbers apply there; a combined hover+press layer is 9.48 light / 5.58 dark.
`contrast.test.js:98-112` now pins ≥4.5:1 on the press layer for every scheme.

### 4. Hover state exists
`.toast-act:hover` joined the `@media (hover:hover) and (pointer:fine)` state-layer block
(`m3.components.css:468`), which composites `var(--label)` (aliased to on-surface at
`m3.tokens.css:151`) at 8%. Verified with a real mouse move in headless Chrome with fine-pointer
media forced: computed `background-image` goes from `none` to
`linear-gradient(color(srgb 0.894118 0.882353 0.917647 / 0.08), …)` — the on-surface hover layer.
The focus-visible layer is in the shared list too (`m3.components.css:478`).

## Tokens and themes

- **Tokens only.** New rules spend `--m3-on-surface`, `--m3-state-press`, `--m3-state-hover`,
  `--m3-shape-full`, `--m3-type-label-l-weight`, `--m3-type-label-l-track`. No raw colour,
  radius, shadow or duration. The literals are sizes (44/48px, 6/12/14/16px padding, 14px type)
  plus `border:0`/`transparent`, which DESIGN §3/§6 allow (sizes live per selector, as `.btn`
  and `.dlg-btn` already do). The rule sits in the snackbar section, not the file tail.
- **Dark and light** both measured from the shipped values: on-surface `#1d1a21` / `#e4e1ea`,
  surface-container-highest `#e4e1ea` / `#3b383f`.
- **Anatomy** stays an M3 text button: transparent container, shape-full (999px), label-l weight
  500, tracking .006em. The bar is `surface-container-highest`, so on-surface is the correct role
  (`inverseSurface` is not defined in this token set). The accent remains the focus ring
  (`index.css:128`) at 4.99/6.74:1 against the bar on the default scheme (≥4.97/6.73 across accents).
- **Keyboard:** a real `<button className="toast-act">` with visible text (`Toast.jsx:12`), in the
  focus-visible list, reachable and activatable.

## Verification

- `npx vitest run src/m3.tokens.test.js src/lib/contrast.test.js src/sheets.undo-delete.test.jsx
  src/lib/undo.test.js` → 4 files / 107 tests pass.
- `npm test` → 109 files / 1174 tests pass.
- `npm run build` → clean.
- The new token tests (`m3.tokens.test.js:162-180`) assert pointer-events scope, 44px, on-surface
  and the hover block; `contrast.test.js` pins the ratio.

## Residual, non-blocking

- **Auto-dismiss can still drop focus** (round-1 minor, unchanged): the 10s `toastTm`
  (`useUI.js:111`) unmounts the action while it can hold focus, sending focus to `<body>`.
  Pausing the timer on focus/pointerenter, or leaving the action until dismissed, would remove it.
- **One comment overstates the number** (`m3.components.css:972`): "on-surface stays >=8:1 in
  both themes" is the base layer (8.92 dark / 13.30 light); on the pressed layer the dark minimum
  is 6.81:1. Still passing — the comment should quote the state-layer figure if that is its point.
