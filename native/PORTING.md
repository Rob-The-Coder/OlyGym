# Porting the React helpers to Kotlin

Phase 0 ported four helpers and Phase 1a ports fifteen more. This is the contract they all follow.

## One-to-one

- Source is `frontend/src/lib/<name>.js`, target is
  `native/app/src/main/java/olygym/app/lib/<Name>.kt` — same file stem, PascalCase, package
  `olygym.app.lib`. Its spec is `frontend/src/lib/<name>.test.js` →
  `native/app/src/test/java/olygym/app/lib/<Name>Test.kt`.
- **Port behaviour, never redesign.** Same exported names, same defaults, same argument order,
  same edge cases. The React suite is the specification, so every vitest case is translated
  one-to-one, with the same inputs. A case the JS gets "wrong" is still a case to reproduce, and
  the JS wins over the spec's expectation — say so in the commit rather than fixing it silently.
- A view or a component is not a helper: its behaviour is ported, but its layout is written for
  Compose rather than translated. The screens keep the web's structure and copy, and drop only what
  needs a screen this phase does not have (recorded in docs/PORT-TO-KOTLIN.md).
- Keep the comments that explain a *decision*, including the issue numbers. Drop the ones that
  narrate the obvious.
- Do not add an abstraction the JS does not have. No interfaces with one implementation, no
  options object where a parameter will do.

## JSON, not data classes

The session shapes (a set row, a workout entry, a session) are `JsonObject`, read through the
JS-shaped accessors in `data/Js.kt`: `asObj`, `asArr`, `asStr`, `asBool`, `asNum`,
`present`, `truthy`, and the object-level `obj/arr/str/num/int/bool`. Build them with
`js("w" to 100, "r" to 5)`, which drops null values the way the JS's conditional spreads do.

The reason is fidelity, not convenience. The JS is loose on purpose — `Number(x) || 0`,
`typeof x === 'string'`, `x != null` (which is false for undefined *and* null), `delete e.sg` —
and the state file is hand-edited, imported, and written by builds older and newer than this one. A
data class would have to pick a type for every field, change behaviour wherever the file disagrees,
and re-serialize every key it did not model. So the shapes stay JSON and `Js.kt` carries the JS
semantics: `asNum` is `Number(x)` and returns null where that gives NaN (so `x.asNum() ?: 0.0` is
`Number(x) || 0`), `asBool` is `x === true` (the string "true" is not true), `truthy` is `!!x`
(an empty array is true), and `asObj` / `asArr` are `objectOf` / `Array.isArray`.

The one thing they do not reproduce is absent-vs-null: both read as null. Every site that cared
used a loose check, so the two behave the same.

## Mutation

Several helpers mutate their argument in place (`cleanupSg`, `pairAdjacent`, `unpairSuperset`,
`cascadeWeight`, `insertWarmupRow`, `removeRowAt`, `rerampWarmups`, `moveSupersetUnit`).
`JsonObject` is immutable, so the port returns the new list and the caller uses the return value.
The test then asserts on the return value where the vitest case asserts on the mutated input — that
is the one place the translation is not literal, and it is noted at the function.

A screen writes the same way, through the edit combinators at the bottom of `data/Js.kt`:
`editObject`, `editArray`, `editAt`, `append`, `insertAt`, `removeObjectAt`, `mapObjects`, plus
the `with`/`without` leaf writers. An index that is gone, or a child of the wrong shape, returns the
document unchanged. There is deliberately no mutable tree: a screen that races a removal must not be
able to corrupt the profile, and every write stays a value the store can publish.

## Tests

JUnit 4 only (`org.junit.Test`, `org.junit.Assert.*`); there is no kotlin-test or kotest in the
offline cache. Names in backticks. One JUnit test per `it()`, in the same order.

`assertEquals` with two non-null `Double`s resolves to the deprecated `(double, double)` overload
and fails at runtime: compare a nullable, or pass a delta.

```bash
cd native
GRADLE_USER_HOME=$PWD/.gradle-home GRADLE_RO_DEP_CACHE=$HOME/.gradle/caches \
  $HOME/.gradle/wrapper/dists/gradle-9.3.1-bin/*/gradle-9.3.1/bin/gradle :app:testDebugUnitTest \
  --offline -Pkotlin.compiler.execution.strategy=in-process
```

Add `--tests '*NameTest*'` to run one file. A build may wait on a lock if another one is running;
that is expected.

## Known debt

- Several ports carried their own private JS-truthiness helpers. Phase 1b folded the four identical
  `truthy` copies into `Js.kt` as `truthy(value)`; `Muscles.kt`'s private `jsString` is a
  different reader (a string-ish value, empty when falsy) and has one caller, so it stays local.
- `SupersetFlow.kt` flattens a JS truthiness check to `=== true` where `History.kt` keeps the
  distinction. Nothing in the shipped data tells those two apart — a stored `done` is a real
  boolean — but imported data could.
- The session screen does not implement the web's swipe between units, its scroll-to-the-actionable
  row, or the merged complex's single rounds table (a complex draws its members' own tables). The
  data written is identical; what is missing is three pieces of presentation, and the JS for each is
  in `Workout.jsx` (`onSwipePointerDown`, the two scroll effects, `RoundsTable`).
- All four tabs are live (Home, Plan, Stats, Library) and Settings has its door on Home. What is
  still stated rather than shown is the body map and the media inside the workout cards.
- The posters are hotlinked from `img.youtube.com`, which is the app's one network permission and the
  same request the web app makes. The video itself is not embedded: the badge fires `ACTION_VIEW` and
  the platform's player takes it, so `embedUrl` is deliberately not ported (see `lib/Media.kt`). The
  workout cards' minimizable media and the two Settings rows for it are still absent — the Phase 2g
  list in docs/PORT-TO-KOTLIN.md says why.
- The exercise history sheet recomputes its reading on each pass instead of memoising it as the web's
  `useMemo` does: comparing two state objects to decide whether to scan costs more than the scan.
- Competitions are their own screen, pushed the way History is, rather than the web's Plan/Competitions
  segmented switcher (`PlanTabs.jsx`) — the bottom bar's Plan tab keeps meaning the plan. The class
  picker's inline pencil became an "Edit categories" row under it, so `SelectRow` did not need an
  action slot.
- `competition.js`'s list readers return `List<JsonObject>` here, so a non-object entry in
  `S.competitions` is dropped where the JS would carry it through, and `classLists` coerces its raw
  entries to strings where the JS keeps them as they are. Neither shape occurs in a file this app
  writes.
- The Library's list is all of it — no 40-row page and no "Show more" — because a LazyColumn builds
  only what is on screen; its search field scrolls away with the list instead of sticking under the
  bar, and the By-muscle explorer is a screen of its own rather than a mode inside the picker.
- The plan editor draws a row's three actions (link, up, down) at 34dp, through a private `RowIcon` in
  `ui/plan/WeekEditScreen.kt`. The app's `IconButton` is 44dp, and four of those on a 360dp phone
  would leave the exercise's own name nothing — the same compromise the set rows' step buttons make.
- An integral number is written with its decimal: `js("sets" to 3.0)` and `with("weight", 60.0)`
  serialize as `3.0` and `60.0`, where the web writes `3` and `60`. JSON has one number type and both
  apps read it as the same value, so this is a spelling difference, not a compatibility one. Making
  them match means one numeric writer used by `toJson` *and* by the helpers that build
  `JsonPrimitive(double)` directly (`Muscles.kt`, `Units.kt`, `History.kt`).
- The plan editor has no swipe-to-delete on a row (that is the web's); a row is removed through the
  config sheet's own "Remove from routine".
- `PlanScreen` no longer gates on `Profile.fileExists`. Phase 0 showed a notice there because it had
  nothing that could write a file; an empty plan is now a plan you can fill, which is also the first
  run on a phone that has never had this app's file.
- `Catalogue` is module-level, so a test that reads through it — `defaultIncrement`, a name lookup —
  must not assume whether another test class has installed a catalogue yet: assert against the same
  function rather than against a value that depends on it.
- The charts' maths is lifted out of the components into lib/ (`ChartMath`, `Activity`, `Progress`),
  which is not where the web keeps it — LineChart.jsx, Heatmap.jsx and Stats.jsx hold it inline. It
  lives in lib/ here because it is what the curves *mean*, and the porting contract puts those
  decisions where a test can reach them. The drawing is Compose and is not tested.
- The body map (`components/BodyMap.jsx` and its ~90 KB `lib/body-paths.js`) is not ported. The
  balance and fatigue readings it draws are shown as ranked bars with the same ramp and the same
  state words; the silhouette needs the path blob shipped as an asset and an SVG-path renderer.
- The line chart has no hover tooltip (no pointer, on a phone), and the heatmap's day tap falls
  through when several workouts share a date, because the sheet it would open (the calendar) is not
  ported yet.
- The backfill writes its `backfill` block through `js()`, which drops null keys: the web writes
  `replaceId: null` explicitly and this writes no key at all. `completeBackfill` reads both the same
  way, so only the file's spelling differs.
- `resetState()` is an empty JSON object, not a copy of the web's `DEF`. The defaults live in code
  here (`Persisted`'s own and `StateStore.derive`), and the React app merges its DEF over whatever it
  finds — so both apps see the same fresh profile, from a different mechanism.
- The date picker is the web's month grid rather than the platform's `<input type="date">`, and the
  time picker is the app's steppers and presets rather than `<input type="time">` — which is what the
  web itself does, having replaced both for the same reason.
- `mergeWeek` returns null when the account is not in kilos instead of throwing the web's
  `unitError`: a screen cannot catch a throw from inside the store's update, so the same signal comes
  back as a value and the import sheet toasts it. Everything else about the merge is the port.
- The coach import reads .xlsx only. The web's Settings row promises "An Excel, CSV or Google Sheets
  week" and the code behind it is the same xlsx reader, so the native subtitle says Excel; the Drive
  door is its own piece of work.
- The review screen shows the "N to check" line and the per-row marks, but the `ignored` fragments
  `matchName` reports (a `+` component with nothing before it) are not listed anywhere.

## What is already ported

`Format`, `I18nCore`, `Weeks`, `MigrateWeeks` (Phase 0), the rest of the day-one closure plus
`WorkoutControls`, `ProgressionCopy`, `NumInput`, `Favourites`, `Equipment`, `LibraryFilter` and
`Usage` (Phase 1a/1b/1c), `PlanEdit` and `Starter` (Phase 2a), `Recovery`, `ChartMath`, `Activity`
and `Progress` (Phase 2b), `HistoryView` (Phase 2c) and `Settings` (Phase 2d — the option lists the
screen offers, and what a reset leaves behind), the By-muscle reads in `LibraryFilter` (Phase 2e —
`muscleWeightOf` and `muscleCounts`, over the one `exerciseJson` that the progression, recovery and
explorer reads now share), `ExerciseHistory` (Phase 2f), `Media` (Phase 2g — the video id, the
poster URL and the chain, without `embedUrl`, which the platform player replaces) and `Xlsx`,
`CoachSheet`, `PlanAliases` and `ImportPlan` (Phase 3a — the coach's workbook read, matched and
reviewed), `PlanShare` (Phase 3a's merging half and Phase 3c's printable page) and `Competition`
(Phase 3b). Read `WorkoutModel.kt` and
`WorkoutModelTest.kt` first if you are porting another one; they are the pattern, including how the
JS's null checks and `Number()` coercions are written.
