# Goal

History is the whole training log, but the only way out of the app is the JSON backup — the app's
own restore format, not something a spreadsheet or a script can read. Add one screen-level action
to History that writes every completed set in the log to a single RFC 4180-style CSV file. Local
only: no server, no new dependency, no persisted-schema change, and the JSON backup/restore is
untouched.

The work splits three ways: a generic `toCSV` writer in `frontend/src/lib/csv.js` (which already
reads CSV), a mapper in a new `frontend/src/lib/history-csv.js` (a separate concern from history.js's
read-back), and the screen action in `History.jsx` with the TopAppBar and snackbar patterns here.

## Design notes

- `toCSV(rows, { bom = false } = {})` takes rows/cells and returns a string; it never mutates its
  input and owns quoting, escaping, CRLF and the spreadsheet formula guard.
- `historyRows(workouts, unit = 'kg')` returns header + one row per completed set;
  `historyFilename(dateIso)` returns `opengym-history-<dateIso>.csv`.
- `History.jsx` composes them in a `doExport` handler and adds one `.ab-ico` action after the
  existing plus action, exactly as `Plan.jsx` puts plus + upload in one M3 large top app bar. No
  FAB, no chip, no new modal, no new CSS.
- `mobile.js`'s `shareExport` already takes an arbitrary string (`Filesystem.writeFile` +
  `Share.share`); only its parameter name and comment say "json". Rename the parameter to `text`
  and widen the comment to "the JSON backup or the History CSV". No behaviour change.

## CSV format

Header, in this exact order (stable English, snake_case, never localized):

```
workout_id,date,start,end,workout,workout_note,entry,exercise,exercise_id,set,phase,reps,seconds,weight,unit,bodyweight,rir,rpe,pr,entry_note
```

| Column | Source | Empty when |
|---|---|---|
| `workout_id` | `w.id` | missing |
| `date` | `w.d` verbatim (`YYYY-MM-DD`) | missing |
| `start` / `end` | `w.start` / `w.end` epoch ms → ISO 8601 UTC | missing/unparseable |
| `workout` | `w.name` | missing |
| `workout_note` | `w.note` | missing |
| `entry` | 1-based index in `w.entries` | never |
| `exercise` | `EXIDX[id]?.n`, else the raw id | never |
| `exercise_id` | `entry.id` | missing |
| `set` | 1-based index in `entry.sets` | never |
| `phase` | `phaseForSet(s)` → `warmup` or `work` | never |
| `reps` | `s.r` finite number | not a rep row |
| `seconds` | `s.sec` finite number | not a timed row |
| `weight` | `s.w` finite number (0 = unloaded/bodyweight) | not recorded |
| `unit` | the `unit` argument (`kg`/`lb`) | never |
| `bodyweight` | `isBw({ ...target, id })` → `true`/`false` | never |
| `rir` / `rpe` | `s.rir` / `s.rpe` finite number | not logged |
| `pr` | `true` iff `Array.isArray(w.prs) && w.prs.includes(entry.id)` | never |
| `entry_note` | `entry.note` | missing |

Rows are oldest first: workouts sorted by `date`, then `start`, on a copy (stable, so ties keep
stored order); entries and sets keep their stored order. Only `s.done === true` sets are exported —
the test `doneUnits` and `hasCompletedWork` use; a done warm-up is exported with `phase=warmup`.
Numeric cells stay numbers in the row arrays, so no `fmtNum`, no thousands separator, no unit
suffix, dot decimal. Exercise names are the canonical catalogue `n`, never `exerciseNameFor` (no
name packs ship; the file must not change with the UI language). UTF-8 + BOM for Excel, CRLF
records, a trailing CRLF, quote-only-when-needed.

# Out of scope

- CSV import; per-workout export; date-range, exercise or filtered export (the file is always the
  whole `S.workouts` log, regardless of the History search field).
- Changing the JSON backup/restore, its filename or its shape; exporting `S.active` (an unfinished
  session is not history).
- Other screens (`Stats`, `Settings`) and any server, sync or telemetry.
- New runtime dependencies, TypeScript, persisted-state/schema changes, `index.css` edits, new CSS,
  new components, a new modal or a native `<select>/<input>`.

# Acceptance criteria

- [ ] `frontend/src/lib/csv.js` exports `toCSV(rows, { bom = false } = {})`: rows/cells in, string
  out, input never mutated. Records are joined by `\r\n` with a trailing `\r\n` when there is
  at least one record; `bom: true` prepends `\uFEFF`; `toCSV([])` is `''` even with `bom: true`.
- [ ] Cell rules: `null`/`undefined`, `NaN` and `Infinity` → empty; finite number → `String(n)`
  (dot decimal, no grouping/unit); boolean → `true`/`false`; anything else → `String(value)`; no
  trimming.
- [ ] Spreadsheet-safety guard: a **non-numeric** cell whose text starts with `=`, `+`, `-`, `@`,
  tab or CR gets a leading literal apostrophe (`'`); numeric/boolean cells never do. A cell is then
  quoted iff its final text contains `,`, `"`, `\r` or `\n`, with embedded quotes doubled. The
  writer never mutates `rows` or its cells.
- [ ] `frontend/src/lib/history-csv.js` (new) exports frozen `HISTORY_CSV_HEADER` (the 20 columns,
  in that order), `historyRows(workouts, unit = 'kg')` and `historyFilename(dateIso)` →
  `opengym-history-<dateIso>.csv`.
- [ ] `historyRows` returns the header first, then one row per completed set, cells exactly per the
  table: non-array `workouts` → `[HISTORY_CSV_HEADER]`; non-object workouts skipped; an entry with
  no sets or no done sets contributes no row. `start`/`end` accept epoch ms or a parseable date
  string and emit ISO 8601 UTC; anything else is blank. No field is invented, no input is mutated.
- [ ] History's TopAppBar `actions` becomes a fragment: the existing plus button first, then — only
  when `S.workouts.length > 0` — a second `<button className="iconbtn ab-ico">` with `Icon name="download"`,
  `aria-label` and `title` both `t('Export history as CSV')`, `onClick={doExport}`, inside the
  existing `.ab-acts`. No new class, no new CSS, no FAB, no chip, no modal.
- [ ] Empty-log decision: with zero workouts the export action is **not rendered**; the existing
  `t('No workouts yet.')` empty state and the plus action remain. Pinned in the screen test.
- [ ] `doExport` builds `toCSV(historyRows(S.workouts, S.unit), { bom: true })` and
  `historyFilename(todayISO())`. On `MOBILE` it awaits `shareExport(csv, name)` then toasts via
  `useUI().toast` (`components/Toast.jsx` is already `role="status" aria-live="polite"`); a
  rejection is a dismissed share sheet — no error toast, no unhandled rejection. On web it uses a
  `Blob` (`text/csv;charset=utf-8`) + `URL.createObjectURL` + `<a download>` +
  `revokeObjectURL`, exactly like `Settings.doExport`, then toasts; a thrown error toasts the
  failure key. No new feedback component.
- [ ] `frontend/src/views/History.export.test.jsx` (new, `// @vitest-environment happy-dom`) mounts
  `History` with `useNavigate` mocked and `../lib/mobile.js` mocked through `importOriginal` with
  `MOBILE: true` + a `shareExport` spy. It clicks the action and asserts the CSV payload (BOM,
  header, only done sets, CRLF) and `shareExport`'s filename, then the toast text; a second case
  asserts silence when `shareExport` rejects; a third seeds `workouts: []` and asserts the export
  button is absent while the plus action is present.
- [ ] Copy: exactly three new English keys, each with an `it.js` entry — `Export history as CSV` /
  `Esporta la cronologia in CSV`, `History exported` / `Cronologia esportata`, `Could not export
  the history` / `Impossibile esportare la cronologia`. `Backup exported` stays the JSON key (a CSV
  is not a backup). No emoji.
- [ ] Accessibility/M3: 48dp `.ab-ico` target with an accessible name and a `title`, ringed by the
  existing `.iconbtn:focus-visible` rule in `m3.components.css` (no list edit needed); meaning is
  glyph + name, never colour; two actions and the title coexist at 360px and ≥1000px, dark/light.
- [ ] `frontend/src/lib/mobile.js`'s `shareExport` parameter is renamed to `text` and its comment
  says it carries an export (JSON backup or CSV), with no behaviour change.
- [ ] No `index.css` edit, no `m3.components.css`/token edit, no dependency or lockfile change, no
  server/network code, no persisted-shape change. `node scripts/check-source-strings.mjs --strict`
  lists the same 10 pre-existing missing keys and none of the three new ones.

# Test plan

```bash
cd frontend && npx vitest run src/lib/csv.test.js src/lib/history-csv.test.js
cd frontend && npx vitest run src/views/History.export.test.jsx
cd frontend && npm test
cd frontend && npm run build
cd frontend && node scripts/check-source-strings.mjs --strict
```

- `src/lib/csv.test.js` (new) — CRLF/trailing/empty, quoting and quote doubling, an embedded
  newline, BOM, Unicode accents, the apostrophe guard for each of `= + - @ tab CR` plus a numeric
  cell that must stay unprefixed, `null`/undefined/NaN → blank, and a deep-equal input snapshot
  before/after `toCSV`.
- `src/lib/history-csv.test.js` (new) — header/widths, oldest-first order from a newest-first input,
  1-based `entry`/`set` indexes, done-only, a done warm-up with `phase=warmup`, timed vs rep,
  bodyweight, `pr` true and false, both notes, missing fields, `lb`, the header-only edge, the
  filename, no mutation, and one `parseCSV(toCSV(rows))` round-trip proving a note with a comma, a
  quote and a newline stays one cell on one logical row.
- `src/views/History.export.test.jsx` — mounts the real `History` (seeded `useStore`), clicks
  `button[aria-label="Export history as CSV"]`, and asserts `shareExport` was called once with the
  expected CSV string and `opengym-history-<today>.csv`, then `useUI.getState().toastMsg`. The
  empty-log case asserts no such button and the unchanged plus action.
- `npm test` — full vitest suite, not just the touched files; `npm run build` must be clean.
- `node scripts/check-source-strings.mjs --strict` — exits 1 today because 10 pre-existing source
  strings have no locale entry; the output must list exactly those 10 (none of the three new keys).
  Colour is not touched, so `scripts/design/m3-scheme.mjs --check` is not part of this job.
