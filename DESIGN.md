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
20. **The coach's review shows his own row.** A ruled line under each reading carries his words and
    his numbers — minus whatever that row already prints (his phrase in the note, his name as the
    title, the load sentence in the note), and minus Excel's trailing zero, so `4.0 × 10.0` reads
    `4 × 10`. A trailing checkbox ticks a row off and the summary counts what is left — it replaced
    the "to check" chip, whose signal is on the rows themselves — and the same line travels onto the
    exercise, so the week editor shows it once the review is gone.
21. **A complex is the app's own card, in the review too.** What he wrote on one row with a `+` is
    drawn as one card — his sets, load and tip in the head, the movements numbered inside, each
    keeping only the reps that are its own — never as two loose rows that read as two exercises. The
    head there only labels, so it drops the pointer cursor the week editor's head needs.
22. **Accent — amended 2026-11-28 (§15.2).** One hue stays, but it paints one
    `--m3-primary-container` lead surface on Home and Plan (never Workout or a sheet), the routine
    `.lrow-i` rails go neutral, and one filled `--m3-primary` control sits on the lead surface.
23. **Calm surfaces — amended 2026-11-28 (§15.2, §15.7).** Calm everywhere except that one lead
    surface: the Workout's current unit is `--m3-surface-container-high`, and every sheet stays calm.
24. **Type treatment — amended 2026-11-28 (§15.4).** A role table replaces per-selector sizes:
    `headline-s` 24/500 for the lead title, `title-m` for expressive list-item titles, one
    `display-s` figure per screen where a number is the subject, and no size or weight outside the
    M3 roles.
25. **Shape scale — amended 2026-11-28 (§15.3).** The raw `20px` is retired; containers take
    `--m3-shape-l` 16 and the lead surface keeps `--m3-shape-xl` 28, so the two read as contrast. No
    seventh radius or shape token is added.
26. **Motion that only explains and the four floating surfaces — kept; the expressive component set
    — new 2026-11-28 (§15.5, §15.6, §14.18).** Motion still only explains, now split into spatial
    and fade groups; the dialog stays on shadow 3 and the timer, snackbar and wide tab bar on 2 with
    no fifth surface; the button group, expressive list item, stat tile and progress indicator are
    the app's expressive components, and there is no fifth component.

---

## 15. M3 Expressive — the direction

Agreed 2026-11-28. This section states the direction only: no screen has been restyled by it, and no
token, class or capture ships in the PR that adds it. The screen work is cut from §15.10.

### 15.1 The direction in one paragraph

OlyGym adopts M3 Expressive on five levers — colour, shape, type, motion and components — and stops
there: layout is out, and Metrolist's media-first screen structure, player patterns and mini player
are not the model, because a log of sets is not a music app. Expression belongs to the glance —
Home, Plan, Stats and History, the screens where you decide what to train — while the set — Workout,
the rest timer and every sheet, where you log — keeps the expressive surfaces and the added motion
out and allows one expression only: the current load's display figure. The expressive basics that
already ship — the spring press, the `--m3-shape-xl` hero and the wavy progress indicator — are the
floor this direction builds on, not the thing it replaces. It stays implementable under four hard
constraints: every value it names is a token role (§3), `frontend/src/index.css` is never edited, no
runtime dependency is added (React, React Router, Zustand and `@capacitor/*` remain the budget), and
the palette is still decided by the contrast audit (`scripts/design/m3-scheme.mjs`,
`frontend/src/lib/contrast.test.js`).

### 15.2 Colour

**a. One accent hue stays.** The eight seeds in `frontend/src/lib/format.js` are the palette; the
default is violet. No secondary or tertiary hue, no new seed, no gradient and no hue pair — a second
hue would repeat the mistake the one-accent rule exists to stop, only louder.

**b. The lead surface is the one saturated surface.** `--m3-primary-container` fill with
`--m3-on-primary-container` ink, at most one per screen, and only on the screens that already own a
lead surface: Home and Plan. It is not added to Workout, Stats, History, Library, Muscles,
Competitions, Settings, WeekEdit or any sheet.

**c. The second accent moment is one filled control.** One `--m3-primary` button on that lead
surface — the primary action — and no other new accent fill anywhere. A screen never carries two
accent surfaces: where the lead surface exists, its primary button is the only other accent.

**d. Routine glyph rails leave the accent.** The every-row `.lrow-i` tile moves from
`--m3-primary-container` to `--m3-surface-container-highest` with `--m3-on-surface-variant` icons.
The accent tile is left only where it marks a state — the current unit's rail — and destructive rows
keep `--m3-error-container` and `--m3-on-error-container`.

**e. Existing accent uses stay as states.** The selected segment, the `.tag.acc` "Current", the
done-set marker, PR badges and the coach-count figure are states and stay exactly that; they are not
multiplied into surfaces, and none of them becomes a fill behind a whole row or card.

**f. Error stays M3 baseline red.** `--m3-error` and its container roles are not restyled. The effort
tint, the muscle heat and the body-weight delta colours are data, not accent, and are unchanged.

**g. Tone before shadow.** No new shadow role and no tonal ladder: separation still comes from the
`surface-container` roles first, and the four floating surfaces keep the existing `--m3-shadow-1|2|3`
scale (§14.18).

**h. The Workout has no `primary-container` surface.** The current unit's card is
`--m3-surface-container-high`; the accent there is the live figure and the existing state markers
only. The set screen is the calm core, and a saturated card would trade the logging surface for
expression.

### 15.3 Shape

Six roles, from `m3.tokens.css`; each control takes one of them:

- `--m3-shape-full` (999px) — `.btn`, `.iconbtn`, the segmented track and thumb, the Start disc.
- `--m3-shape-xl` (28px) — the lead surface (`.hero`), the dialog (`.center`) and the sheet's top
  corners.
- `--m3-shape-l` (16px) — containers: cards (`.sect-b`, `.card`), list groups, `.item` rows, tiles
  (`.tile`), the `.coach-cta` banner, the wide floating `#tabbar` and the weekday tile (`.wday`).
- `--m3-shape-m` (12px) — fields.
- `--m3-shape-s` (8px) — chips and tags. Kept at 8 so a chip does not read as a pill button.
- `--m3-shape-xs` (4px) — compact badges.

**The raw `20px` is retired.** `.sect-b`/`.card` and `.tile` (`m3.components.css:87`, `:463`) go to
`--r-card`, the legacy alias of `--m3-shape-l`; `.wday` (`:208`), the wide `#tabbar` (`:140`) and
`.coach-cta` (`:361`) follow to the same role. The wide tab bar's indicator follows at the container
radius minus the 5px gap (so 11px beside 16). No seventh radius and no new shape token is added.

**Where a control may change shape.** Only two: the Start FAB's press, whose disc rounds to
`--m3-shape-xl` (the rule that ships today), and the exercise poster's expand, which grows to full
width on `--m3-shape-l` (also shipping). Every other control keeps one shape and answers a press
with a state layer, never a morph — the shape does not move because a finger landed on it.

### 15.4 Type

Nine roles, and the house tracking tokens they carry:

| Role | Size / weight | Tokens | Where it lands |
|---|---|---|---|
| `headline-l` | 32 / 500 | `--m3-type-headline-l-weight-emphasized`, `--m3-type-headline-l-track` | The app bar title — the screen's one large headline. |
| `headline-s` | 24 / 500 | `--m3-type-headline-s-weight-emphasized`, `--m3-type-headline-s-track` | The lead surface's title (`.hero-t`) and History's month headings. |
| `title-m` | 16 / 500 | `--m3-type-title-m-weight`, `--m3-type-title-m-track` | Card headings and expressive list-item titles. |
| `body-l` | 16 / 400 | `--m3-type-body-l-weight`, `--m3-type-body-l-track` | Calm row titles. |
| `body-m` | 14 / 400 | `--m3-type-body-m-weight`, `--m3-type-body-m-track` | Row subtitles. |
| `label-m` | 12 / 500 | `--m3-type-label-m-weight`, `--m3-type-label-m-track` | Section labels. |
| `label-l` | 14 / 500 | `--m3-type-label-l-weight`, `--m3-type-label-l-track` | Buttons and chips. |
| `label-s` | 11 / 500 | `--m3-type-label-s-weight`, `--m3-type-label-s-track` | Tab labels. |
| `display-s` | 36 / 500, tabular | `--m3-type-display-s-weight`, `--m3-type-display-s-weight-emphasized`, `--m3-type-display-s-track` | One display figure, where a number is the subject. |

**At most one display figure per screen, and only where a number is the subject:** the Workout's
current load, Home's body weight, Stats' best total. Never a title, never in a list, never in a
sheet. No size or weight outside the M3 roles — no 600/700 body type and no one-off sizes.

**`display-small` is the one M3 role the token layer skips.** `m3.tokens.css` carries display-large
and display-medium only, which is why `.card .big` borrows display-large's weight and tracking at
34px while the app's own type table names that role display-small. The type-foundations follow-up
(§15.10 #2) adds `--m3-type-display-s-weight` (400), `--m3-type-display-s-weight-emphasized` (500)
and `--m3-type-display-s-track` (-.012em), matching display-medium; this document does not add them.

**Three corrections follow.** `.hero-t` at 24/600 with a literal `-.02em` (`m3.components.css:897`)
becomes `headline-s` 24/500 — the size stays, the weight and tracking come from the role.
`.card .big` at 34px with the display-large tokens (`:341`) becomes `display-s` 36px with the
display-small role. Stats' `Weight 30d` tile stops setting `fontSize: 22` inline (`Stats.jsx:348`)
and takes the stat tile's `title-l` role.

### 15.5 Motion

**Spatial — travel or morph.** The sheet rising and the dialog popping (both `--m3-dur-long` +
`--m3-ease-emphasized-decelerate`), the week editor's day reveal, the segmented thumb travelling, the
tab bar's indicator growing, the Start FAB compressing and rounding, the exercise poster expanding,
and the Workout's progress fill travelling to its new value on `--m3-dur-long` +
`--m3-ease-emphasized-decelerate`.

**Fade or state layer only.** The view change (`#app.vfade` stays a fade), toasts, content swaps and
list filtering, theme changes, chart redraws, the rest countdown (1s linear) and the update download's
small increments (`--m3-dur-medium` + `--m3-ease-standard`).

**Invariants.** No new duration and no new curve — the four curves and three durations in
`m3.tokens.css` are the whole scale. The spring is for press and reveal only. The only looping
animation is the wavy progress (plus
its existing drift): no count-up, no parallax, no scroll effect. And **no motion is added while a set
is in progress** — the press state layer and the progress wave keep running, nothing else enters,
staggers or bounces. `prefers-reduced-motion` keeps `index.css`'s blanket rule, and nothing in the
direction may `!important` around it.

### 15.6 Components

Exactly four. Each replaces something already shipping, and none of them renames an existing
primitive to claim it.

**Button group.** Two to three connected buttons in one `--m3-shape-full` container, one filled at
most, for peer actions on the same object or line — Stats' Goal/Log, Workout's Move up/Move down. It
replaces the loose `.row` of separate `.btn`s. It lands on Stats and Workout first. It must never be
navigation, never a segmented control (a group acts; a segment selects), and never destructive beside
primary.

**Expressive list item.** The loop screens' row: a 48px neutral leading glyph, a `title-m` title, one
meta line and a trailing affordance, on the `--m3-shape-l` container. It replaces `.item` on Plan's
weeks, History's sessions and Library's exercises, with Competitions after the loop. It lands on
those four screens in that order. It must never appear in pickers or sheets (they keep `.lrow`), and
there is never a second row style for the same content.

**Stat tile.** One `label-m` label, one `title-l` figure (the single lead figure may be `display-s`),
one context line, on `--m3-shape-l`. It replaces the `.tile` variants on Home, Stats, History and
Competitions. It must never be a tile per number with an accent fill, and never a sparkline.

**Progress indicator.** The existing 6px wavy fill (the mask already in `m3.components.css`) as the
app's one determinate progress and loading treatment, used by the rest timer, the workout header and
the update download. It replaces the bespoke inline bar in `DownloadProgress` (`Settings.jsx`). It
must never be a spinner and never a third bar treatment; the `.mrow .bar` in the muscle balance is a
data bar and stays straight.

**Rejections.** No split button — the only candidate, Home's Start + "choose another", would
duplicate the start screen, which §13 forbids. No floating toolbar — it would be a fifth floating
surface. No new FAB, modal, picker or menu. A component is one primitive reused: a second way to do
the same thing is not adopted.

### 15.7 Screens and order

1. **Home** — the lead surface's title on `headline-s` and its one `--m3-primary-container` colour,
   the stat row as stat tiles, the one `display-s` body weight. Its meet row stays a calm `Row`.
2. **Plan** — the same hero and `headline-s` title, plus week rows as expressive list items.
3. **Workout** — the current unit's `display-s` load in a `--m3-surface-container-high` card (no
   `primary-container` surface anywhere), the button group for Move up/Move down, and no new motion.
4. **Stats** — one display figure, the stat tiles, and the `Weight 30d` tile on a role instead of an
   inline size.
5. **History** — month headings at `headline-s` 24/500 and session rows as expressive list items.
6. **Library** — the expressive list item and the stat tile.
7. **Muscles** — only the type and legend roles; no new component, surface or motion.
8. **Competitions** — the expressive list item and the stat tile.
9. **Settings** — calm rows, neutral `.lrow-i` rails, and the shared progress indicator where a bar
   already exists.
10. **WeekEdit and the sheets** — no new components, no new shape, no new motion; the day reveal
    stays a spatial transition and sheets keep their behaviour.

**The calm list.** No added motion while a set is in progress. Sheets keep their behaviour — no close
button, scrim, drag, Escape and Android back, and a locked sheet swallows all four. Reduced motion is
honoured. Both themes are designed, not darkened later. 44px targets stay 44px. Every screen is
checked at 360px and at ≥1000px. Both locales read the result, and no new string is needed to ship it.

**The constraint.** The direction is implementable as-is: every value it names is a token role,
`index.css` stays untouched, no dependency is added, the contrast audit still decides the palette, and
no new string is needed.

### 15.8 The audit

Rendered from this worktree against the real store: a starter plan, five logged sessions across
three months, five body-weight entries, two meets and one active session, captured with
`scripts/agent-browser` at 390×844 in dark (plus 360, 1200 and light for Home). The captures live in
the job folder (`factory/jobs/014-m3-expressive-design/`), never in this repository; the values below
are the ones the worktree actually has.

| Surface | Today | Shortfall against this direction | Evidence |
|---|---|---|---|
| Home | `.hero` on `--m3-primary-container` and `--m3-shape-xl`; `.hero-t` 24/600; `.big` 34px with `--m3-type-display-l-*`; `.tile` at `border-radius:20px` (`m3.components.css:87`, `:341`, `:463`, `:897`). | The title becomes `headline-s` 24/500, the body weight the one `display-s` figure, and the three tiles stat tiles on 16px. | `shot-home-390-dark.png`, `shot-home-360-dark.png`, `shot-home-wide-dark.png`, `shot-home-390-light.png` |
| Plan | The same hero; week rows as `.item` inside a 20px card; `.coach-cta` at 20px (`:361`). | Hero title to `headline-s`, week rows to expressive list items, banner and cards to 16px. | `shot-plan-390-dark.png` |
| Workout | A 6px `.wprog` wave, set rows with accent done markers, no display figure for the current load. | The current unit's load becomes the screen's one `display-s` figure in a `--m3-surface-container-high` card; no primary-container surface and no new motion. | `shot-workout-390-dark.png` |
| Stats | `.tile` at 20px with `.tile .v` 30px on `--m3-type-display-m-*` (`:463`, `:465`); `Weight 30d` inline `fontSize: 22` (`Stats.jsx:348`); no single lead figure. | Tiles to stat tiles, one lead figure to `display-s`, and the inline 22 replaced by a role. | `shot-stats-390-dark.png` |
| History | `.mlabel` months at 12px uppercase (`:961`); `.item` session rows; `.hist-tiles .tile .v` at 17px (`:958`). | Months to `headline-s` 24/500, sessions to expressive list items, summary tiles to stat tiles. | `shot-history-390-dark.png` |
| Library | `.list .item` rows led by a poster thumbnail (`:232`); no 48px neutral glyph rail. | The expressive list item, after the loop screens. | `shot-library-390-dark.png` |
| Muscles | The body map and its `.chip` legend; no card, tile or accent surface. | None — keep as is, with only the type and legend roles corrected. | `shot-muscles-390-dark.png` |
| Competitions | `.tile` at 20px and `.meet-tiles .tile .v` at 22px (`:463`, `:459`); meet rows as `.item`. | Tiles to stat tiles, rows to the expressive list item, after the loop. | `shot-competitions-390-dark.png` |
| Settings | Every `.lrow-i` paints `--m3-primary-container` (`:713`); card groups at 20px. | Rails to `--m3-surface-container-highest` with `--m3-on-surface-variant` icons, groups to 16px. | `shot-settings-390-dark.png` |
| WeekEdit | `.wday` at 20px (`:208`) inside 20px day cards; rows inline-styled. | Weekday tiles and day cards to 16px; the day reveal stays the only motion. | `shot-weekedit-390-dark.png` |
| Buttons | Every `.btn` is a `--m3-shape-full` pill (`:32`), set in a loose `.row` (Home's Goal/Log, Workout's Move up/Move down). | Peer actions become one connected button group with one filled button at most. | `shot-home-390-dark.png`, `shot-workout-390-dark.png` |
| Cards and shape | `.sect-b`/`.card` and `.tile` at raw `border-radius:20px` (`:87`, `:463`); `.wday` `:208`, `.coach-cta` `:361`, wide `#tabbar` `:140`. | Raw 20 retired for `--m3-shape-l`; the lead surface keeps 28 so l/xl reads as contrast. | `shot-settings-390-dark.png`, `shot-home-wide-dark.png` |
| Type | `.hero-t` 24/600 (`:897`), `.card .big` 34px with `--m3-type-display-l-*` (`:341`), `.tile .v` 30px display-m (`:465`), Stats inline 22; `m3.tokens.css` has no display-small role. | The role table replaces one-off sizes; one display figure; display-small arrives with the type follow-up. | `shot-home-390-dark.png`, `shot-stats-390-dark.png` |
| Motion | Spring press, spring tab indicator, emphasized-decelerate sheet/dialog and `viewfade`; the download bar animates width at `.2s`. | The spatial/fade split, no motion during a set, no new curve, and the download on the shared scale. | `shot-workout-390-dark.png`, `shot-home-390-dark.png` |
| Progress | `.wprog` and `#timer .bar` are the 6px wave; `DownloadProgress` draws its own 6px bar with `border-radius:3`, `--fill-3`/`--acc` and `transition: width .2s` (`Settings.jsx:484–505`). | One progress indicator — the wave — used by the update download too. | `shot-workout-390-dark.png`, `shot-settings-390-dark.png` |
| Navigation | The wide `#tabbar` floats at 20px with a 15px indicator (`:140–141`); the phone bar runs full width. | Container to 16px and indicator to 11px; no fifth floating surface. | `shot-home-wide-dark.png`, `shot-home-390-dark.png` |

### 15.9 §14 reconciled

| Rule (where) | Call | Date | Reason |
|---|---|---|---|
| One accent (§3) | **amended** | 2026-11-28 | One hue kept, but it gets hierarchy back: one lead surface, neutral routine rails, one filled primary. |
| Calm surfaces (§5) | **amended** | 2026-11-28 | Calm everywhere except the one lead surface; the Workout and every sheet stay calm. |
| Motion that only explains (§5) | **kept** | 2026-11-28 | The spatial/fade split says the same thing more precisely; no set motion and no new curve. |
| The type treatment (§6, §14.4) | **amended** | 2026-11-28 | A display-s figure, title-m item titles and the corrected hero/card roles, all on M3 roles. |
| The shape scale (§3) | **amended** | 2026-11-28 | The raw 20px is retired; six roles with the l/xl contrast instead. |
| The four floating surfaces (§14.18) | **kept** | 2026-11-28 | Dialog 3; timer, snackbar and wide tab bar 2; no fifth surface. |

### 15.10 Follow-ups

Sixteen small PRs, in this order. Each is one lever or one screen, and each screen job follows §9's
loop — capture, mockup, ask the user before applying. None of them is in this PR.

1. **Shape foundations** — retire the raw 20px and move `.sect-b`/`.card`, `.tile`, `.wday`, the wide
   `#tabbar` and `.coach-cta` onto `--m3-shape-l` (and the tab indicator to 11px) in
   `frontend/src/m3.components.css`.
2. **Type foundations** — add the display-small roles to `frontend/src/m3.tokens.css` and move
   `.hero-t`, `.card .big` and the one-off sizes onto roles in `frontend/src/m3.components.css`.
3. **Progress indicator** — make the wave the one progress treatment and replace the inline bar in
   `DownloadProgress` (`frontend/src/views/Settings.jsx`).
4. **Button group** — add the connected group primitive to `frontend/src/components/ui.jsx` and
   `frontend/src/m3.components.css`, used first by Stats and Workout.
5. **Expressive list item** — add the row to `frontend/src/components/ui.jsx` and
   `frontend/src/m3.components.css`, replacing `.item` where the loop screens use it.
6. **Stat tile** — add the tile primitive to `frontend/src/components/ui.jsx` and
   `frontend/src/m3.components.css`.
7. **Home** — `frontend/src/views/Home.jsx`.
8. **Plan** — `frontend/src/views/Plan.jsx`.
9. **Workout** — `frontend/src/views/Workout.jsx`, including the current-load figure and the button
   group.
10. **Stats** — `frontend/src/views/Stats.jsx`, including the `Weight 30d` role.
11. **History** — `frontend/src/views/History.jsx`, including the `headline-s` month headings.
12. **Library** — `frontend/src/views/Library.jsx`.
13. **Muscles** — `frontend/src/views/Muscles.jsx`, type and legend only.
14. **Competitions** — `frontend/src/views/Competitions.jsx`.
15. **Settings** — `frontend/src/views/Settings.jsx`, calm rows and neutral rails.
16. **WeekEdit and the sheets** — `frontend/src/views/WeekEdit.jsx` and `frontend/src/sheets.jsx`,
    shape only, with the sheet and dialog behaviour untouched.
