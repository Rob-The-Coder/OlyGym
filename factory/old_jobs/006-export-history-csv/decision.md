APPROVE

# Decision — 006-export-history-csv

## Call

**APPROVE.** Merge `factory/006-export-history-csv` (0a91b0f) into `main`. All four round-1
reviews are PASS, and every finding they carry is explicitly non-blocking. I re-ran the evidence
independently and found no blocking defect: the change is additive, local-only, matches the spec
column for column, and does not touch the persisted shape, the JSON backup, the dependency set or
the stylesheets. The remaining notes are test-completeness, documentation and file-format
semantics follow-ups — not reasons to hold a local-only, single-user feature.

## Scope and mergeability checked

- Branch `factory/006-export-history-csv` at **0a91b0f** ("Export the whole History log as CSV");
  its parent and the merge base are `main` **f126cb3**, and `main` had not moved.
- The commit contains exactly the **8 product files**, all under `frontend/src`:
  `lib/csv.js`, `lib/csv.test.js`, `lib/history-csv.js`, `lib/history-csv.test.js`,
  `lib/mobile.js`, `locales/it.js`, `views/History.export.test.jsx`, `views/History.jsx`.
  No factory artifact, test evidence, board file or preview is in it.
- No dependency, schema or style change: `git diff --name-only f126cb3..0a91b0f --`
  `frontend/package.json frontend/package-lock.json frontend/src/index.css`
  `frontend/src/m3.tokens.css frontend/src/m3.components.css frontend/src/store frontend/android`
  is empty.
- Working tree before the merge held only `M factory/board.json` plus untracked
  `factory/jobs/006-export-history-csv/{build.md,round-1/}`; they are not in the commit and were
  deliberately left uncommitted, as was this decision file.
- Merge was `git merge --no-ff` (ort strategy), no squash, rebase or force, clean with no
  conflicts. `main` is checked out and nothing was pushed.

## Verified independently

| Check | Result |
|---|---|
| `cd frontend && npx vitest run src/lib/csv.test.js src/lib/history-csv.test.js src/views/History.export.test.jsx` | 3 files, **28 tests passed** (10 + 15 + 3) |
| `cd frontend && npm test` (before merge) | **113 files, 1210 tests passed** |
| `cd frontend && npm run build` (before merge) | **clean**, exit 0 |
| `cd frontend && node scripts/check-source-strings.mjs --strict` | **exit 1**, 622 translated strings, the same 10 pre-existing missing keys, **none of the three new keys** |
| Same check against a fresh `git archive main frontend/src frontend/scripts` export | **exit 1**, 619 strings, the same 10 keys; key-set diff vs the branch is **identical** |
| `git show --name-status 0a91b0f` | exactly the 8 files above |
| Independent Node probe of `toCSV`/`historyRows` | 20-column frozen header; empty table `''` with and without BOM; formula guard for `= + - @ TAB CR` with numeric `-5`/`0` and booleans unprefixed; two-workout log → 5 rows oldest-first, only done sets, done warm-up `phase=warmup`, timed row `seconds=45, weight=0, bodyweight=true`, per-entry PR flags; BOM `EF FE FF`, CRLF with trailing CRLF; `parseCSV` round-trip of a note containing a comma, a quote and a newline; inputs unmutated; non-array log → header only |
| `git diff --check f126cb3..HEAD` | clean (exit 0) |
| Five captures in `oly-previews/` | inspected: 360 dark and light show two distinct 48dp actions (plus then download) beside the title, 1200 dark keeps them together at the right, the empty-log capture shows the plus alone with the unchanged "No workouts yet." state, and the exported capture shows the "History exported" snackbar |

I also read the full diff and the pre-existing helpers it composes — `phaseForSet`,
`isBw`, `todayISO`, `parseCSV` — and the acceptance criteria map one-to-one onto the code and
tests; no field is invented and no input is mutated.

## Non-blocking notes carried forward

1. **Web failure branch untested.** `Could not export the history` (Blob/`createObjectURL` throw)
   has no automated test; the spec's plan pins the mobile path and the empty log. The branch is
   simple and mirrors `Settings.doExport`. A `MOBILE: false` case with a throwing
   `URL.createObjectURL` would pin it later.
2. **Unknown-start ordering unpinned.** A workout with no parseable `start` sorts after known
   starts on the same date, with stored order among unknowns. Deterministic and harmless; the spec
   only says "then start".
3. **Search independence not proven by the screen test.** The test never types a query, but the
   implementation reads `S.workouts` directly, so the file is always the whole log, and filtered
   export is explicitly out of scope.
4. **Silent mobile rejection.** Every share rejection is swallowed as "sheet dismissed" per the
   spec; a genuine native write/share failure is indistinguishable from a cancel. No error toast
   and no unhandled rejection, as specified.
5. **`SECURITY.md` does not mention the CSV.** It still says "Exported backups are plain JSON".
   The CSV is another unencrypted, device-local full-log export; the at-rest bullet already sets
   the expectation. Documentation follow-up when convenient.
6. **Icon-only discoverability.** There is no visible "CSV" word; the download glyph matches the
   app's existing Settings export vocabulary, and the accessible name/title carry the meaning.
7. **Format semantics worth documenting.** `pr` is per entry (all its sets, warm-ups included),
   and `bodyweight=true` means `weight` is added load. Both follow the spec's fixed 20-column
   mapping.
8. **Local date vs UTC start.** `date` is the stored local training day while `start`/`end`
   are UTC ISO; a session around local midnight can show two adjacent calendar days. Spec-mandated.
9. **Leading LF quoted, not apostrophe-guarded.** The guard covers the spec's `CR` but not a
   leading `LF`; the cell is quoted, so no formula is evaluated after the line break.

## Method limits

- I did not run the real Capacitor share sheet on a physical Android device; the native path is
  covered by the mocked screen test, code inspection and the builder's real web-click evidence.
- I inspected the five PNGs but did not re-run the live headless-Chrome layout/focus measurements
  myself; the UI reviewer's measured values and the images agree with the code.
- I did not open the generated file in Excel/LibreOffice; CSV correctness is verified through the
  helpers' unit tests, the independent Node probe and the `parseCSV` round-trip.
- I did not run the APK/CI pipeline or `m3-scheme.mjs --check` (no colour or token change), and I
  did not push anywhere.

## Merge and post-merge verification

- Merge commit: **6e5735ee22ffe7a2c40059a08085fd8f3def8d60**
  ("Merge factory/006-export-history-csv: export History as CSV")
- Parents: **f126cb3434bfe480a958bbbde7fbc514a1eecc50** (`main`) and
  **0a91b0fdd112df6c3e6d63903eab267d5f833119** (`factory/006-export-history-csv`)
- Post-merge `cd frontend && npm test` → **113 files, 1210 tests passed**
- Post-merge `cd frontend && npm run build` → **clean**, exit 0
- `main` is checked out; the working tree still has only the expected factory artifacts
  (`M factory/board.json`, untracked job files and this `decision.md`). Nothing pushed.
