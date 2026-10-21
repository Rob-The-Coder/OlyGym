VERDICT: PASS

# Security review — 005-show-best-lift-pr-badges

Branch: `factory/005-show-best-lift-pr-badges`, commit `3947ef7` (`main...HEAD`).
Scope: product diff only — `frontend/src/lib/history.js`, `frontend/src/lib/history.test.js`,
`frontend/src/sheets.jsx`, `frontend/src/m3.components.css`,
`frontend/src/sheets.workout-row.test.jsx`. `factory/*` excluded by instruction.

## Findings

No blocking findings.

Informational (not blocking, no action required):

- `frontend/src/sheets.jsx:1580` — the name lookup `EXIDX[p.id]` is a prototype-bearing plain
  object, so an id equal to an inherited key (`__proto__`, `constructor`, `toString`, …) returns a
  truthy non-exercise. It is still safe: `exerciseNameFor` (`frontend/src/lib/i18n-core.js:69`)
  returns `''` for such a value (it reads only `ex.id` / `ex.n`), and the name packs are disabled
  for every shipped language (`EXERCISE_NAME_LANGS = []`, so `exerciseNames` stays `null`). The
  worst case is a badge with an empty name span, not a crash or an injection. Only an in-state id
  having such a literal value could reach it, and real ids are catalogue ids written by the finish
  flow.
- `frontend/src/lib/history.js:641-659` — for a pathological imported backup `prLifts` is O(prs ×
  entries) (`Array.prototype.find` per id). Bounded by the device-local data the user imported
  themselves; no new import path is added by this change. Informational only.

## What I verified

- **Secrets/credentials:** none in the diff; no tokens, keys, URLs, endpoints or config values
  added.
- **Read-only over stored state / no data loss:** `prLifts` only reads `w.prs` and `w.entries`; it
  builds a new array (`out`) and sorts that, never the stored array, and the new test deep-compares
  the input before/after (`frontend/src/lib/history.test.js:1010-1020`). Nothing in the diff calls
  `useStore.setState`, `localStorage`, `nativeSave` or any store writer — the only `setState` in the
  diff is test seeding in `frontend/src/sheets.workout-row.test.jsx:46-47`.
- **No destructive mutation:** the rendered row is pure render over an immutable snapshot; no
  `.prs` write-back, no backfill, no migration (spec out-of-scope items respected).
- **No injection / unsanitised input:** all badge text is rendered as React text children
  (auto-escaped); no `dangerouslySetInnerHTML`, `eval`, `new Function`, `innerHTML` or DOM string
  building anywhere in the diff (grep over `git diff main...HEAD`). The lift name comes from the
  trusted catalogue (`EXIDX`); the only fallback is the raw stored id, which React escapes as text.
  `t('PR')` comes from the static locale pack (`frontend/src/locales/it.js:1431`), no interpolation
  of state into translated strings that could be re-parsed.
- **No network/server code:** no `fetch`, `XMLHttpRequest`, `WebSocket`, `http(s)://` or new
  endpoint in the diff.
- **No new runtime dependency:** `git diff main...HEAD --name-only` is the five frontend files;
  `package.json` and the lockfile are untouched. No import outside the existing modules
  (`prLifts` is a new named export of the already-imported `history.js`).
- **No persisted-schema change:** the change adds no field and no migration; `w.prs` keeps its
  stored shape (`{ id, weight }` objects are in-memory only). `buildCompletedWorkout` /
  `doFinishWorkout` and the other PR readers are untouched (only `.pr` colours inherit).
- **No unsafe localStorage writes:** the diff never touches `gym_state_v1`; only reads.
- **Malformed legacy data cannot crash the row:**
  - missing/empty/non-array `prs` → `[]` (`history.js:643-644`);
  - missing/non-array `entries` → `[]` (`:644`);
  - null/primitive/non-object workout → `objectOf` → `{}` (`:6`, `:642`);
  - entry with missing id / null element → `e?.id === id` optional chaining skips it (`:649`);
  - PR id with no matching entry, best load 0, `NaN`/`undefined` load, and duplicate ids are all
    skipped (zero/duplicate covered by `frontend/src/lib/history.test.js:992-1000`; the
    `!(weight > 0)` guard also rejects `NaN`);
  - name resolution is null-safe (`exerciseNameFor` returns `''` rather than throwing);
  - `bestWeightForEntry` guards `entry`/`sets`(non-array)/non-finite `w` and falls back to `0`
    (`frontend/src/lib/history.js:595-628`).
- **Rendered text is a function of trusted data only:** weight comes from the session's own entry
  via the existing `bestWeightForEntry`; name from `EXIDX`; unit from store state; the word from
  the locale pack. No user free-text field is newly rendered beyond the existing row fields.
- **CSS change:** `frontend/src/m3.components.css:238-255` adds only token references
  (`--m3-primary-container`, `--m3-on-primary-container`, `--m3-shape-s`) and layout
  (`max-width`, `white-space`, flex/gap/margin, ellipsis). No `url()`, `@import`, external resource,
  raw colour/radius/duration, or `index.css` edit.
- **Tests run (read-only verification):** `npx vitest run src/lib/history.test.js
  src/sheets.workout-row.test.jsx` — 115 tests passed (112 + 3), matching the build.md claim.
- **Bilingual copy:** uses the existing `PR` key, no new locale string; no missing-translation
  vector introduced.
