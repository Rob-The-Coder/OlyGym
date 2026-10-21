# Build — 006-export-history-csv

Commit: 0a91b0f — Export the whole History log as CSV
Branch: factory/006-export-history-csv (created from main at f126cb3)

## Files changed

- frontend/src/lib/csv.js — added toCSV(rows, { bom = false } = {}) beside the unchanged
  parseCSV. Records are joined with CRLF and end with one when there is at least one record;
  toCSV([]) is an empty string even with bom: true, and bom: true otherwise prepends U+FEFF.
  Cells: null/undefined, NaN, Infinity and -Infinity become empty; finite numbers become
  String(n); booleans become true/false; anything else becomes String(value) with no trimming.
  A text cell starting with =, +, -, @, tab or CR gets a leading literal apostrophe (numbers and
  booleans never do). The final text is quoted only when it contains a comma, a double quote,
  CR or LF, with embedded quotes doubled. The input rows and cells are read only. parseCSV is
  byte-for-byte unchanged.
- frontend/src/lib/history-csv.js (new) — frozen HISTORY_CSV_HEADER with the 20 spec columns in
  order; historyRows(workouts, unit = 'kg'); historyFilename(dateIso) →
  opengym-history-<dateIso>.csv. historyRows returns the header first, then one row per
  set.done === true; workouts are filtered to objects and sorted oldest-first by date then start
  on a copy, with an explicit original-index tie-break (stable; unknown starts stay after known
  starts on the same date). Entries and sets keep stored order and are numbered from 1.
  start/end accept finite epoch ms or a parseable date string and emit toISOString(); anything
  else is blank. exercise is EXIDX[id]?.n with the raw id as fallback; bodyweight is
  isBw({ ...entry.target, id: entry.id }); pr is Array.isArray(w.prs) && w.prs.includes(entry.id);
  both notes pass through. reps, seconds, weight, rir and rpe are included only when they are
  finite numbers, so numeric cells stay numbers. Non-array workouts returns
  [HISTORY_CSV_HEADER]; non-object workouts, non-object entries and malformed/non-array sets are
  skipped. Nothing is mutated.
- frontend/src/views/History.jsx — added doExport, which builds
  toCSV(historyRows(S.workouts, S.unit), { bom: true }) and historyFilename(todayISO()). On
  MOBILE it awaits shareExport(csv, name) and toasts t('History exported'); a rejection is
  swallowed as a dismissed share sheet (no error toast, no unhandled rejection). On web it
  creates a Blob with text/csv;charset=utf-8, an object URL and an <a download>, clicks and
  revokes it, then toasts success; a thrown error toasts t('Could not export the history'). The
  TopAppBar actions prop is now a fragment with the existing plus action first and a second
  button.iconbtn.ab-ico (Icon name="download", matching aria-label/title from
  t('Export history as CSV'), onClick={doExport}) only when S.workouts.length > 0. No new CSS,
  component, FAB, chip or modal.
- frontend/src/lib/mobile.js — shareExport's first parameter is renamed json → text, and the
  comment now says it carries an export (the JSON backup or the History CSV). No behaviour change.
- frontend/src/locales/it.js — exactly three new entries:
  'Export history as CSV': 'Esporta la cronologia in CSV',
  'History exported': 'Cronologia esportata',
  'Could not export the history': 'Impossibile esportare la cronologia'.
  Backup exported is untouched.
- frontend/src/lib/csv.test.js (new) — 10 tests: empty/CRLF/trailing CRLF, BOM, quote-only when
  needed and doubled quotes, embedded newlines and CR, no trimming, Unicode accents,
  null/undefined/NaN/±Infinity/number/boolean cells, the apostrophe guard for each trigger
  (=, +, -, @, tab, CR), numeric/boolean exemption, guard-plus-quote, a deep-equal no-mutation
  snapshot, and a parseCSV(toCSV(...)) read-back of the quoting cases.
- frontend/src/lib/history-csv.test.js (new) — 15 tests: frozen header and 20-column widths,
  header-only for empty/absent/non-array input, oldest-first order from a newest-first input with
  a stable tie, 1-based entry/set indexes with skipped entries still counted, done-only rows, done
  warm-ups (explicit phase and legacy warmup) as warmup, timed vs rep rows, weight 0, finite-only
  rir/rpe, canonical names and raw-id fallback, bodyweight from target and from the catalogue, PR
  true/false and missing/non-array prs, both notes with commas/quotes/newlines, epoch and
  parseable start/end → ISO UTC, missing fields blank, lb, no mutation of nested data or order,
  and one parseCSV(toCSV(rows)) round-trip proving a note with a comma, a quote and a newline
  stays one cell on one logical row. historyFilename is pinned too.
- frontend/src/views/History.export.test.jsx (new, // @vitest-environment happy-dom) — mounts the
  real History with the real store seeded through useStore.setState, useNavigate mocked and
  ../lib/mobile.js mocked through importOriginal with MOBILE: true plus a shareExport spy.
  3 tests: the click shares the expected CSV (BOM, header, exactly the done sets, trailing CRLF)
  and opengym-history-<today>.csv, then History exported; a rejected shareExport leaves the toast
  empty; an empty log has no export button but keeps the plus action and No workouts yet.

## How I tested it

(Indented command/output pairs)

    cd frontend && npx vitest run src/lib/csv.test.js src/lib/history-csv.test.js
    # 2 files, 25 tests passed

    cd frontend && npx vitest run src/views/History.export.test.jsx
    # 1 file, 3 tests passed

    cd frontend && npm test
    # 113 files, 1210 tests passed   (baseline on main: 110 files, 1182 tests;
    #  +3 files and +28 tests are exactly the new suites)

    cd frontend && npm run build
    # built in 246ms (dist/assets/index-*.css 89.50 kB, index-*.js 820.09 kB)

    cd frontend && node scripts/check-source-strings.mjs --strict
    # EXIT:1 — 622 translated strings, exactly the same 10 "defined in no locale pack" keys
    # as main (baseline extracted from git archive main: 619 translated strings, the same 10).
    # None of the three new keys is missing: 619 + 3 = 622. Exit 1 is the pre-existing state.

The strict-check baseline was produced from a throwaway "git archive main frontend/src
frontend/scripts" export, not from a modified working tree. Both runs list:
"Day options", "How the whole workout went. Editable here any time afterwards.",
"No workouts planned this week.", "Put your week on paper.", the long ramp-up sentence,
"Rest runs after each set of this exercise. Leave it at 0 to use your default rest timer.",
"Warm-ups and rest", "Week options", "What do warm-ups and rest do?", "Workout options".

Colour was not touched, so "node scripts/design/m3-scheme.mjs --check" was not run (the spec
excludes it for this job).

## Real render evidence

The Vite dev server on 5173 and headless Chrome on 9222 were already running. State was seeded
through the app's own persistence path (localStorage key gym_state_v1 plus a reload) because a
dynamically imported useStore resolved to a different Vite module instance than the mounted app;
the seed/measure scripts are gitignored oly-previews/factory-006-seed.js and
oly-previews/factory-006-measure.js. The two seeded workouts are "Snatch day" (a done warm-up, a
done 102 kg single, a done bodyweight set, notes, one PR) and "Clean & jerk + holds" (a done
130 kg single, a done 45-second bodyweight hold, an unchecked set, a note, one PR).

Captures (gitignored oly-previews/, a reviewer can open them):

- oly-previews/factory-006-history-360-dark.png — 360×800, dark.
- oly-previews/factory-006-history-360-light.png — 360×800, light.
- oly-previews/factory-006-history-1200-dark.png — 1200×900, dark.
- oly-previews/factory-006-history-empty-360-dark.png — 360×800, dark, empty log.
- oly-previews/factory-006-history-360-dark-exported.png — 360×800, dark, after a real click.

Measured from the live DOM:

- 360px, dark and light: innerWidth === document.documentElement.scrollWidth === 360 — no
  horizontal overflow. Two .ab-acts .ab-ico actions, 48×48 each: plus at left 258 → right 306,
  top 16 → 64; CSV export at left 308 → right 356, top 16 → 64. Both carry the glyph, an
  aria-label and a matching title; the export is "Export history as CSV". Title "History",
  subtitle "2 workouts", rows "Snatch day" and "Clean & jerk + holds".
- 1200px, dark: innerWidth === scrollWidth === 1200; plus at left 1038 → right 1086, export at
  left 1088 → right 1136, both top 40 → 88, 48×48. The two icons sit together at the right of the
  wide bar, beside the title, in the same order as the phone.
- Empty log, 360px dark: only one action remains (plus, left 308 → right 356, 48×48);
  button[aria-label="Export history as CSV"] is absent, while
  button[aria-label="Log a past workout"] and the "No workouts yet." empty state (with its
  "Log a past workout" button) are present. Subtitle "0 workouts".

I looked at the PNGs: in dark and light the download glyph (down arrow into a tray) and the plus
are distinct and unobstructed by the "History" title at 360px; the 1200px dark capture keeps the
pair together at the right edge. The empty capture shows the plus alone with the unchanged empty
state.

A real click on the web build (probe script stubs the anchor click and object URL, then reads the
Blob) produced:

- filename opengym-history-2026-10-18.csv (today from todayISO()),
- type text/csv;charset=utf-8, 1001 bytes,
- first bytes [239, 187, 191] = the UTF-8 BOM,
- 6 records: the 20-column header plus 5 done sets (the unchecked set is absent),
- first data row w-006-a,2026-03-27,2026-03-27T16:30:00.000Z,... and the timed row shows
  ...work,,45,0,kg,true,2,,false,slow and controlled — seconds 45, weight 0, bodyweight true,
  the entry note, no reps,
- the snackbar rendered "History exported" (visible in the exported capture).

## Not changed

- Persisted store shape (gym_state_v1) — no new or renamed field; the export only reads
  S.workouts, S.unit and entry.target.
- JSON backup/restore, its filename and its "Backup exported" copy.
- frontend/src/index.css, frontend/src/m3.tokens.css, frontend/src/m3.components.css and any
  colour/token value. No new class is introduced, so the existing .iconbtn:focus-visible ring and
  the .ab-acts layout apply as shipped.
- package.json, the lockfile and the dependency set; no network/server code; no TypeScript.

## Deviations

None. The implementation follows the spec; the only judgement calls inside its wording are:

- An unknown start sorts after known starts on the same date (the spec only says "then start";
  both rows still preserve stored order when neither has a start).
- historyRows treats entry.target (an object) as the config spread into isBw; an absent target
  reads as {}, so catalogue bodyweight detection still works.
- No deviations from the listed copy, files, UI shape, or test plan.
