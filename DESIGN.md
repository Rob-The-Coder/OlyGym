# DESIGN.md — OlyGym's UI

Read this before adding, changing, or reviewing **any** screen, sheet, or control. It is the
system the app already follows and the loop that has been landing UI changes on it. `CLAUDE.md`
says what the app *is*; this says how it *looks and behaves*.

If a request conflicts with something here, say so and ask — do not quietly break a rule below.

---

## 1. The product, in one paragraph

OlyGym is a **local-only, offline** Olympic weightlifting tracker. One user, one device, no
backend, no accounts. React 19 + Vite, shipped as a Capacitor Android app and a PWA from the
same source. Dark is the default theme; light ships too. Mobile-first, with a wide layout at
**≥1000px**. Two locales (English keys + `it.js`). No TypeScript, no linter, no new runtime
dependencies.

Two screens are the job: **the set you are on right now** (Workout) and **what you did** (Stats,
History). Everything else feeds them. Design for a glance between sets and for a phone in one
hand.

---

## 2. The three stylesheets — the one structural invariant

```
frontend/src/index.css          upstream's.  NEVER EDIT IT. (1,432 lines, 0 changes in this fork.)
frontend/src/m3.tokens.css      roles only. No component styles.
frontend/src/m3.components.css  components. Loads last, wins over both.
```

Loaded in that order from `frontend/src/main.jsx`. Equal-specificity `:root` rules in
`m3.tokens.css` beat `index.css`; `m3.components.css` beats both. **A change that would need to
edit `index.css` is an override in one of the other two.**

- `m3.components.css` has sections (Primitives, Home, Sheets/dialogs/picker, Type, Plan, Stats,
  Motion, Complex…). Put a new rule in **the section it belongs to**, not at the end of the file.
- The **colour block at the bottom of `m3.tokens.css` is generated**. Never hand-edit it:
  ```bash
  node scripts/design/m3-scheme.mjs          # rewrite the colours
  node scripts/design/m3-scheme.mjs --check  # print the contrast audit
  ```
  `frontend/src/lib/contrast.test.js` re-checks the written CSS, so an unreadable palette cannot
  ship. If a change touches colour, run both.

---

## 3. Values come from tokens, never from the rule

`m3.tokens.css` holds, in sections: **shape** (`--m3-shape-xs|s|m|l|xl|full`), **type** (the two
M3 scales — weight and tracking per role), **motion** (`--m3-ease-*`, `--m3-dur-short|medium|long`),
**state layers** (`--m3-state-hover|focus|press|drag` = 8/10/10/16%), **elevation**
(`--m3-shadow-0|1|2|3`), and the app's own legacy names (`--label`, `--label-2`, `--surface`,
`--acc`, `--sep`…) aliased onto those roles.

**A raw colour, radius, shadow or duration in `m3.components.css` is a bug.** New work uses the
`--m3-*` roles; the legacy aliases are for restyling the classes `index.css` already defines.

Colour roles in use: `--m3-surface`, `--m3-surface-container{,-low,-high,-highest}`,
`--m3-on-surface`, `--m3-on-surface-variant`, `--m3-on-surface-disabled`, `--m3-outline`,
`--m3-outline-variant`, `--m3-primary` / `--m3-on-primary` / `--m3-primary-container` /
`--m3-on-primary-container`, `--m3-error` / `--m3-error-container` / `--m3-on-error` /
`--m3-on-error-container`.

Rules that were decisions, not defaults:

- **One accent.** Eight seeds, default `violet` (`lib/format.js`). The accent is for the one
  deliberate moment — the primary action, the selected state, a live figure. An accent everywhere
  is no accent.
- **Two text levels plus a decorative third**: `--label` (primary), `--label-2` (secondary),
  `--label-3` (a hint, never load-bearing).
- **A press is a state layer in the container's own on-colour** —
  `color-mix(in srgb, var(--m3-on-primary) var(--m3-state-press), var(--acc))` — **never**
  `brightness()`, and never a second hue standing in for "darker".
- **Tone separates surfaces before shadow does.** Elevation shadows are only the cast-shadow half;
  the tonal half is the `surface-container` roles. Use the role, not a shadow, to lift a surface.
- **Error is M3 baseline red**, not the accent rotated. `--m3-error` for destructive and error.

---

## 4. Component vocabulary — what to reach for

| Need | Use | Never |
|---|---|---|
| Label above a group of cards | `.sech` (12px uppercase overline) | a heading |
| Label inside a card | `h4.sec` (13px sentence case) | a second `.sech` |
| A settings/action line | `Row` / `SelectRow` / `MultiSelectRow` → `.lrow` | a hand-built flex row |
| A tappable piece of content | `.list > .item` | a `.lrow` |
| A single choice in a row | `SelectRow` (opens a sheet) | a native `<select>` |
| Several choices in a row | `MultiSelectRow` (toggle, then Done) | chips |
| 2–5 exclusive short options | `Segmented` (`.seg-inline` inside a row) | tabs |
| A number with −/+ | `Stepper` | a native number input |
| A filter set | `.chip`, or `.chips.wrap` when the set is the sheet's body | a scroll strip that hides half the options |
| An action beside a row | `.iconbtn` (36px, state layer) | a 48dp button in a list |
| Help for a line that names itself | `.fieldhelp` | `.helpbtn` |
| Help for an icon alone | `.helpbtn` + `aria-label` | text next to `.helpbtn` (its glyph sits on the text) |
| A sheet | `.sheet` — no close button; scrim, drag and Android back close it | an X in the corner |
| A modal question | `.center` + the blurred 40% `.mback` scrim | a browser `confirm()` |
| "Done" feedback | `useUI().toast` (`#toast`, M3 snackbar) | an alert |

**Rows.** `Row` renders `.lrow` with `.lrow-i` (icon rail), `.lrow-m` (`.lrow-t` title,
`.lrow-s` subtitle), an optional control as `children`, and `.lrow-v` for the value —
`accessory="chevron"` or `"check"` for the trailing glyph. `.lrow` rows are transparent and
divided by a hairline; the surface comes from the parent. Use `danger` for destructive rows.
Long titles wrap; the value ellipsises — do not fight that.

**Steppers.** A stepper **alone on its line is a row**: label left, a 150px pill right. Two or
three sharing a `.cfgrow` keep the centred caption over their own control, because that is what
makes them one group. `:has(.stp-w + .stp-w)` decides; do not add a class at the call site.

**Segmented** supports `{ value, label, ariaLabel }` per option. When the visible label is a bare
number (a day, a week), always pass `ariaLabel` so the screen reader hears "Giorno 2".

**Chips.** `.chip` carries `text-transform: capitalize`; `.lrow-t` does not. When a raw id
(`upper legs`) has to read as a label in a row, use `capWords` from `lib/format.js` — and if a
value has no option in the list, add it to the options rather than printing it raw.

**Overlays.** A sheet is one job, entered deliberately. The scrim is a blurred 40% black; sheets
have no close button (`Modals.jsx` closes on drag, backdrop, Escape and Android back). A locked
sheet swallows all four on purpose (`bwSheet({ required: true })`).

---

## 5. Motion and elevation

- Duration scale: **short 150 / medium 250 / long 400ms**. Expressive curves:
  `--m3-ease-standard`, `--m3-ease-emphasized`, `--m3-ease-emphasized-decelerate`,
  `--m3-ease-spring`. One scale — do not introduce a fifth duration or a bespoke curve.
- Motion explains a state change, a spatial relationship, or a response. It does not decorate.
- Shadow scale is **1–3 only**: `--m3-shadow-1` for a raised button, `--m3-shadow-2` for floating
  chrome (rest timer, snackbar, the wide tab bar), `--m3-shadow-3` for the dialog. **No
  hand-written `box-shadow` anywhere.** On dark, shadows barely read — tone and the scrim carry
  the separation.

---

## 6. Type

The two M3 scales ship as tokens (baseline and Emphasized). Emphasized changes **weight only** —
it is a drop-in that never reflows a layout, not a second design. Element sizes and leading live
in `m3.components.css` per selector; **weight and tracking are what a role carries**. Uppercase
labels use `--m3-type-overline-weight` / `-track`. The app's tracking is negative where M3's is
positive — that is house style, not a mistake.

---

## 7. Copy, locales, and language

- **English keys are the source.** Write `t('Add the week to my plan')`; the key *is* the English
  string. Placeholders are `{0}`, `{1}`… and work on the English fallback too.
- **Every new key needs an entry in `frontend/src/locales/it.js`.** A missing one silently ships
  English in the Italian UI.
- Prefer **no new string** over a new string. If an existing key says it, use it.
- **No emoji anywhere in the chrome.** Icons are the `Icon` component's 24×24 stroke set — a new
  glyph is drawn on that grid, never imported from a library.
- Empty states say **why** and the one action that fills them ("Nothing in this day." → what to
  do next). Errors say what failed and how to recover.

---

## 8. Accessibility — a release requirement, not polish

- Hit targets: **44px minimum** for anything a thumb presses; `.iconbtn` is the app's 36px in-row
  action, and it is 36 because a row is 56–68 tall and the whole row is the target.
- Every icon-only button has an `aria-label`. Every segmented option that shows a bare number
  gets an `ariaLabel`.
- Focus is visible: the shared `:focus-visible` ring list in `m3.components.css` — **add a new
  interactive class to it**.
- Never encode meaning in colour alone. A deleted weigh-in, a missed muscle group, a guessed
  number each carry a word or a glyph, not just a hue.
- Check the change at **360px** and at **≥1000px**, in dark and light, with the keyboard.

---

## 9. The loop — how a UI change lands in this repo

Follow it in order. Steps 1–3 are not optional: **no UI change is applied before the user has
answered a question about it.**

1. **See the real current state.** Render it, do not imagine it. Capture the screen(s) involved
   with the preview harness (§10) or, where the sheet needs state, seed the state first.
2. **Build a mockup.** Prefer a real render. For a layout/decision, a hand-drawn HTML mockup that
   links the three real stylesheets is acceptable and often faster — but it must use the real
   class names and real token values, or it will lie.
3. **Ask, with clickable options.** Use `ask_user_question` with 2–4 concrete options, the
   recommended one first and labelled "(Recommended)", each with a one-line tradeoff. Present the
   mockup in the same turn. Wait for the answer.
4. **Apply in the right file** (§2), in the section that owns it, using tokens (§3).
5. **Verify for real.**
   - `cd frontend && npx vitest run` — the whole suite, not just the file you touched.
   - `npm run build` — must be clean.
   - Re-capture the screen and **look at it**.
   - If the change is a token map, **measure the computed style** and quote it back
     (`getComputedStyle(el).boxShadow`) instead of asserting it looks right.
   - If you claim something is unchanged, **prove it**: an identical file hash between the before
     and after capture is proof; "I didn't touch it" is not.
   - Add or extend a test when the behaviour is assertable in `happy-dom` (§11).
6. **Commit** with the *why*: an imperative subject, a blank line, then prose. See §12.
7. **`graphify update .`** at the end of the task.

---

## 10. The preview harness

Real-render screenshots through the DevTools protocol. Headless Chrome must already be listening
on **9222**, and the dev server on **5173**:

```bash
node oly-previews/shot.mjs <out.png> <url> <w> <h> [scrollY] [@script.js]
```

- `@file.js` runs a script in the page before the shot. Use it to open a sheet
  (`await import('/src/sheets.jsx')` then call the exported sheet function) or to seed state
  (`useStore.setState(...)`, `localStorage`).
- **Give every shot a unique URL** (`?r=42`): navigating to the same URL does not reload the page,
  and sheets/state pile up between shots.
- Screens that only exist in the mobile build need `VITE_MOBILE=1 npx vite --port 5174` and a URL
  pointed at it. Stop that server when done.
- **After editing, verify the server is serving the new transform** before trusting a capture:
  `curl -s http://127.0.0.1:5173/src/sheets.jsx | grep -c '<marker>'`. Vite's watcher has silently
  stopped invalidating a file before; when in doubt, restart the dev server.
- The capture directory (`oly-previews/`) is gitignored. Put seeds and mockups there, not in the
  app.

---

## 11. Tests

- Two kinds, both required where they apply: **domain logic** → a pure helper in `frontend/src/lib/`
  with a `*.test.js` beside it; **screen behaviour** → a `*.test.jsx` with
  `// @vitest-environment happy-dom`, mounting the component and asserting the rendered DOM.
- **Class names and `data-` attributes are API.** A test may assert on them; do not rename one
  without updating its tests.
- Some tests read CSS from disk (`m3.tokens.test.js`, `contrast.test.js`, `m3.empty-icon.test.js`).
  A deleted token or class can fail a test that never renders anything.
- Screen tests render sheet functions directly: `coachPlanSheet([week])` then mount
  `useUI.getState().sheets.at(-1).render(close)`. Copy the existing harness rather than inventing
  one.

---

## 12. Commits

```
Short imperative subject, no full stop

Why this exists, in prose. What was wrong, what the new shape is, and the decision behind it
if there was one. Name the evidence: a measured value, an unchanged hash, a test that did not
exist before.
```

`git commit -a` misses new files — `git add` a new test or component explicitly. Do not commit
`frontend/dist`, the Android build tree, `oly-previews/`, or `graphify-out/`.

---

## 13. Do-not list

- Do not edit `index.css`.
- Do not put a raw colour, radius, shadow or duration in `m3.components.css`.
- Do not add a runtime dependency. React, React Router, Zustand and `@capacitor/*` are the budget.
- Do not use chips as buttons or as navigation; do not use a FAB for a minor or destructive action.
- Do not wrap every section in a card, or give every element the same radius, or a shadow.
- Do not add a **native** `<input type="date">`, `type="time"` or `<select>` — they ignore the
  theme. The app has its own date picker, time picker and `SelectSheet`; the last native control
  was removed in `3c77fc7`.
- Do not add a second way to do something that exists (`window.confirm`, a new modal, a new picker).
- Do not ship a UI change without step 3 of §9.
- Do not inflate a review with praise. State the concrete issue and the correction, or say it is
  fine.

---

## 14. The decisions this stands on

A short record, so a future feature does not relitigate them. Ordered by when they were agreed.

1. **Surfaces** are the M3 neutral tint (chroma 0.10). Dark keeps M3 tone 6.
2. **Accent** defaults to violet; eight seeds. Error roles are M3 baseline constants.
3. **Foundations**: one press value in the container's own on-colour (8/10/10/16), one duration
   scale, **a shadow scale only** — no hand-written shadows.
4. **Type**: both M3 scales as tokens; the app's own sizes and weights stay.
5. **Section labels**: the two-level system (`.sech` above a group, `h4.sec` inside a card) is
   kept; outliers are brought onto it, not removed.
6. **Top app bar** is M3 large flexible.
7. **Filters** are a "Filters · N" chip that opens a sheet, not an always-open row.
8. **Overlays**: M3 basic dialog + M3 snackbar, the blurred 40% scrim kept, no close button on
   sheets.
9. **Plan**: the current week leads as a card; the 7-option weekday Segmented stays but shrinks;
   destructive actions go behind `⋯`.
10. **Weight sheets**: keep the curve, drop the ± chips, keep the today/goal tiles and the moving
    goal line; the goal's explanation sits behind an ⓘ.
11. **History**: tappable sessions, Best/Last tiles, the standing note read-only.
12. **Calendar / backfill**: the app's own date grid and time stepper. A past empty day opens the
    backfill dated to it; the same-day choice is a menu.
13. **Week editor**: all three row actions stay inline as 36px `.iconbtn` — reordering is one tap on
    the planning screen — and a collapsed day shows a one-line exercise preview.
14. **Picker rows** (exercise picker, custom-exercise form, equipment profile): `SelectRow` /
    `MultiSelectRow`, never a horizontal scroll strip. The equipment checklist wraps
    (`.chips.wrap`).
15. **Help** that names itself is `.fieldhelp`, not `.helpbtn`.
16. **The coach's review** leads with its rows: the day strip is numbers with the day's name as the
    overline above its rows, a one-line summary instead of four stat cards, warning-coloured chips
    for numbers the coach never wrote, and his "Amrap"/"Trova 5RM" kept in the note.
17. **A lone stepper is a row**; 2–3 in a `.cfgrow` keep the centred caption.
18. **The four floating surfaces** sit on the shadow scale (dialog 3; timer, snackbar, wide tab bar
    2).
19. **No native date/time input remains.** The reminder time opens the app's own time picker.
