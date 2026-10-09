# Porting OlyGym to Kotlin

**This document is the brief for a fresh session.** It is self-contained: everything needed to
start the port is here, and nothing needs re-deriving.

---

## Start here

The first task of a new session is **Phase 0**:

1. Create a top-level directory **native/** in this repo holding a Gradle + Jetpack Compose
   Android project, package **olygym.app.dev** (the .dev suffix matters, see Data
   compatibility).
2. Port **frontend/src/lib/format.js** and **frontend/src/lib/i18n-core.js** to Kotlin,
   each with JVM unit tests translated from its behaviour. Neither has a test today, so read
   them and write the tests as you port.
3. Write a **StateStore** that reads and parses the existing file
   **/data/data/olygym.app/files/opengym-state.json** into the app's state model, and port
   **frontend/src/lib/migrate-weeks.js** so an older file shape still loads.

Milestone for Phase 0: the native app boots and renders the list of workouts from the real
file. Nothing else.

Do not touch anything under frontend/. The React app is the shipping app until cutover.

## Ground rules

- Port behaviour, do not redesign it. The React test suite is the specification.
- Local only: no server, no account, no sync, no telemetry.
- No iOS. No PWA.
- Keep the React app working and shippable for the whole port.
- Dependency light. Compose, Navigation and kotlinx.serialization are the only new
  dependencies that are not optional. Do not add Room in phase 0 or 1.
- Use the existing English-string-as-key i18n convention. Do not convert to string resources.

## This machine: what you need to know

The file sandbox writes only inside this repo. Reads work anywhere. Google Maven is
unreachable, so Gradle must run offline against the local cache.

Gradle needs a writable user home because ~/.gradle is not writable here:

    cd native          # or frontend/android, same trick
    GRADLE_USER_HOME=$PWD/.gradle-home GRADLE_RO_DEP_CACHE=$HOME/.gradle/caches \
      ./gradlew :app:assembleDebug --offline

Delete .gradle-home when finished, and stop the daemon first or it recreates it:

    pkill -f 'Gradle[D]aemon'; rm -rf .gradle-home

Cached and usable offline: AGP 8.13.2 and 8.7.2, androidx.core 1.18.0, Gradle distributions
8.11.1 and 9.3.x, SDK platforms android-35, android-36 and android-36.1.

Other paths:

    Android SDK        ~/Android/Sdk          build-tools 36.0.0
    adb                ~/Android/Sdk/platform-tools/adb
    release key        android-keys/olygym-release.jks   alias: olygym
    key password       android-keys/password.txt
    build + sign       scripts/build-apk.sh  (add --install to push it over adb)

The React suite, which is the spec, runs in a couple of seconds:

    cd frontend && npx vitest run        # 1,144 tests, 107 files

Run graphify update . at the end of a task, per the repo instructions.

## Target architecture

| Concern | Choice |
|---|---|
| UI | Jetpack Compose + Material3 |
| State | One immutable state object shaped like the JS store S, exposed as StateFlow |
| Persistence | The same file, opengym-state.json. Room is not phase 0 or 1 |
| Navigation | navigation-compose, mirroring the current routes |
| DI | none, manual wiring |
| i18n | the existing locale JSON shipped as an asset; i18n-core ported |
| Catalogue | exercises-data shipped as an asset, parsed once at startup |
| Charts | Compose Canvas ports of LineChart, Heatmap, BodyMap, MuscleExplorer |
| xlsx | java.util.zip.ZipInputStream + XmlPullParser, no library |
| WebView | none |

## What there is to port

Measured from frontend/src at the time of writing.

| Layer | Size | Fate |
|---|---|---|
| Domain helpers | 54 files, 6,452 lines | Port almost verbatim |
| of which day one | 19 helpers, 3,114 lines | The whole gym loop |
| Views | 10 files, 2,901 lines | Rewrite in Compose |
| Components | 20 files, 1,984 lines | Rewrite in Compose |
| M3 CSS | 3,047 lines | Becomes the Compose theme |
| Tests | 107 files, 14,730 lines, 1,144 tests | Port to JVM JUnit |
| i18n | 1,325 keys x 2 locales | Ship as an asset |
| Platform bridge | about 1,400 lines | Replaced by native APIs, not ported |

### Day-one closure - the 19 helpers the gym loop needs

Derived by walking the session-engine roots transitively. Nothing outside this list is
required to run and log one session:

    active-exercise-swap(45)  active-workout-order(32)  backfill(40)      bar(62)
    effort(175)               exercises(217)            exercises-data(633)
    finish-workout(58)        format(103)               history(604)      i18n-core(102)
    muscles(349)              progression(246)          session-merge(41) session-start(35)
    supersetFlow(157)         units(76)                 weeks(57)         workout-model(82)

### Rewritten, not ported - the platform boundary

| JS helper | Lines | Becomes |
|---|---|---|
| mobile | 173 | file IO, notifications, share sheet, print |
| rest-notification | 130 | already native: RestNotification + RestTimerService |
| update | 171 | in-app APK updater |
| sound | 91 | AudioTrack / ToneGenerator |
| wakelock | 58 | FLAG_KEEP_SCREEN_ON |
| drive | 41 | Google Drive Picker |
| xlsx | 202 | ZipInputStream + XmlPullParser |
| plan-share | 257 | WebView print or PdfDocument |
| back | 63 | OnBackPressedDispatcher |
| hchips, viewport-guard, use-sheet-keyboard | 290 | replaced by Compose layout |

### Remaining domain helpers - phase 2 and 3

back, body-paths, coach-sheet, competition, contrast, csv, drive, equipment, exercise-history,
favourites, glyphs, hchips, i18n, import-plan, library-filter, media, migrate-weeks, mobile,
nav, plan-aliases, plan-share, progression-copy, recovery, recovery-view, rest-notification,
routines, sound, starter, update, use-sheet-keyboard, video, viewport-guard, wakelock,
workout-controls, xlsx.

Note: 11 helpers ship with no test today - format, i18n-core, exercises-data, nav, glyphs,
csv, body-paths, equipment, i18n, plan-share, workout-controls. Two of them are in the
day-one set, so they get tests written during the port rather than translated.

## Data compatibility - the reason cutover is safe

The React app already mirrors its whole state to a file on every persist:

    /data/data/olygym.app/files/opengym-state.json

App data survives an APK update when the package name and signing key match. So the release
build of the native app must keep package **olygym.app** and the existing release key, and it
will open reading the real training history. No export, no import, no migration script.

During development, use the **.dev** package suffix. That gives the native app its own
sandbox, so the two apps can never fight over the same file.

The schema lives in frontend/src/store/useStore.js (the S object) and
frontend/src/lib/migrate-weeks.js (old shapes). Port migrate-weeks early; the file on disk may
predate the current model.

## Phases

| Phase | Scope | Milestone | Estimate |
|---|---|---|---|
| 0 | Skeleton, format, i18n-core, catalogue asset, StateStore, migrate-weeks | Boots and lists the real workouts | ~1 week of evenings |
| 1 | The 19-helper closure, Home, Workout, rest timer, foreground service | A full gym session on the native app | ~2 more weeks |
| 2 | Library, Plan, WeekEdit, History, Stats, Muscles, Settings, all charts | Parity except imports | ~4-6 weeks |
| 3 | Coach xlsx import, Drive, competitions, print, reminders, in-app updater | Cutover candidate | ~3-4 weeks |

Phase 1 is the commitment point. After it, the new app is usable for real training even while
phases 2 and 3 continue. That milestone exists so the port has a win on the board in three
weeks rather than a four-phase march with nothing to show.

## Test strategy

- Pure helpers port with their vitest file, one to one, as JVM tests. No emulator, fast.
- The 11 untested helpers: port by reading, and write the tests as you go.
- Compose UI: a few smoke tests plus on-device use. Do not mechanically port the
  1,574-line Workout.test.jsx; its logic already lives in the helpers.
- Keep the React suite green for the whole hybrid period.
- Add one test that both locale files have the same 1,325 keys. It is easy to break and
  invisible when it happens.

## Risks

1. Behaviour drift while porting. Mitigated by translating tests first and refusing to redesign.
2. Two codebases drifting. Only real if both write the same state file; the .dev package
   removes that until cutover.
3. Iteration speed. A release build here is about 2m30s against Vite's instant reload. Use
   debug builds and Compose previews.
4. Scope creep. Competitions, M3 Expressive flourishes and the wavy bar are phase 4 at the
   earliest, if ever.
5. Motivation. Four phases with no new features is the shape of project that stalls. Phase 1
   is the antidote.

## Non-goals

- No iOS, no server, no sync.
- No Room migration in phases 0 and 1; the JSON blob is the contract with the old app.
- No redesign. Same features, same behaviour, different runtime.

## Cutover checklist

1. The native app covers phases 0 to 2 and has been used for real sessions.
2. Release package olygym.app, the existing release key, versionCode above the last Capacitor
   build (26 at the time of writing).
3. Confirm opengym-state.json is read and rewritten without loss.
4. Keep the last Capacitor APK and a state export until the new app has survived a week.
---

## Phase 0 decisions — what actually shipped

Recorded here so the next session does not re-derive them. The code is in `native/`; the build
recipe, the pinned versions and the generated files are in `native/README.md`.

1. **Navigation is Voyager 1.1.0-beta03**, not `navigation-compose` as the table above says. That
   artifact is not in this machine's offline Gradle cache and Google Maven is unreachable. Voyager
   is the library Komikku uses, at the version its own catalogue pins, and all four artifacts are
   cached; `androidx.navigation` still is not.
2. **M3 Expressive is deferred, not built in.** Phase 0 is plain `MaterialTheme`. The tokens — the
   Emphasized weights, the four curves, the state layers — are ported as data in
   `ui/theme/Type.kt` and `ui/theme/Motion.kt`. `material3 1.4.0` does contain
   `MaterialExpressiveTheme` and `MotionScheme`, for the phase that wires them.
3. **Pinned toolchain**: Gradle 9.3.1, AGP 8.13.2, Kotlin 2.4.0, Compose BOM 2026.06.01
   (material3 1.4.0), Voyager 1.1.0-beta03, kotlinx-serialization-json 1.11.0, JUnit 4.13.2, and
   `desugar_jdk_libs` for `java.time` at minSdk 24. The Gradle plugin markers are not cached, so
   the build uses a `buildscript` classpath; the buildscript repository order is `google()`,
   `mavenCentral()`, `gradlePluginPortal()`, and that order is load-bearing offline.
4. **The theme is generated too**, like the CSS: `scripts/design/m3-scheme-kotlin.mjs` imports
   `scheme()` and `SEEDS` from `m3-scheme.mjs` and writes `ui/theme/Scheme.kt` — eight accents,
   dark and light, nineteen roles each. The two asset files are generated from the frontend sources
   by `native/tools/assets.mjs`.
5. **`PlanState.Loaded` keeps the raw file text** next to the parsed model, so Phase 1 can write
   the file back without dropping whatever this model does not cover. Phase 0 never writes.
6. **The ported helpers take plain arguments** — `migrateToWeeks(routines, week, weekStart, now)`
   and the weeks readers take the list — instead of the whole state object. Same behaviour, and the
   whole read path is a JVM test: `StateStoreTest` drives a real file through parse, migrate and
   decide, including the two shapes of the old `week` field (array and bare string).
7. **The dev package is `olygym.app.dev`**, from `applicationIdSuffix ".dev"` on the debug build
   type, so cutover item 2 in the checklist is deleting one line for the release build.
8. **61 JVM tests** in `native/app/src/test`: the nine `migrate-weeks` cases one-to-one, the
   format and i18n behaviours measured against the browser, the store's four outcomes, and the two
   generated assets' shape and contrast. They run in about a second, offline.

### Still open after Phase 0

- Anything that writes the state file. Phase 1 owns that, and item 3 of the cutover checklist.
- The Emphasized scale and the expressive motion, deferred by decision 2.
- Custom exercises (`S.customEx`) are not merged into the catalogue index, so a day that
  references one shows its id. It arrives with the picker.
---

## Phase 1a — the day-one closure, and the first write

Phase 1 turned out to be two pieces of very different sizes: the nineteen helpers plus a store that
can write, and then Home and the workout screen with the sheets they open. 1a is the first piece.
1b is the second, and until it lands the app still shows Phase 0's read-only plan list — on the new
store.

**Every day-one helper is ported now** (Phase 0 had four of the nineteen), each with its vitest file
translated to JUnit 4: 19 helpers, 3,502 lines of Kotlin, 4,987 lines of tests, **425 JVM tests**
that run offline in about two seconds — including the nine `migrate-weeks` cases and 102 for
`history` alone.

The decisions this phase rests on:

1. **The session shapes are JSON, not data classes.** `data/Js.kt` carries the JS semantics the
   helpers were written against: `asNum` is `Number(x)`, `asBool` is `=== true` (the string
   "true" is not true), `asObj`/`asArr` are `objectOf`/`Array.isArray`. A data class would have to
   pick a type for every field, change behaviour wherever the real file disagrees with the choice, and
   re-serialize the keys it does not carry — and this file is hand-edited, imported and
   older-than-this-build all at once. `native/PORTING.md` is the contract for the next port.
2. **The mutating JS helpers return the new list.** `cleanupSg`, `pairAdjacent`, `cascadeWeight`,
   the warmup inserters and removers: JSON is immutable, so the caller stores the result and the test
   asserts on the return value where the vitest case asserted on the mutated input.
3. **The first write is atomic, coalesced and additive.** A `.writing` temp file, `fsync`, then a
   rename over the profile; the new state reaches the screens before the disk does; writes coalesce on
   `Dispatchers.IO`, because the workout screen will write on every set that gets ticked; and the
   write is a *merge over the object that was read*, so keys this app does not model survive the round
   trip. `_ts` is stamped the way the web app stamps it, and `MainActivity.onStop()` flushes.
4. **A corrupt profile is never overwritten.** The web app boots on its defaults and replaces the file
   on the next save. Here the store refuses to write and the screen says why — a training log is not
   something to reset to make an error message go away.
5. **The rest timer came over as Kotlin**: the foreground service, the action receiver, the
   notification including the Android 16 Live Update bar, with `POST_NOTIFICATIONS` and
   `FOREGROUND_SERVICE` declared and the shipping status-bar icon. Its copy goes through the same
   locale pack as the app, so "Skip" is "Salta" when the profile is Italian.
6. **Verified on an API 37 emulator**: the app boots and renders the Italian profile's week, and a
   device with no file shows the empty state and the path it looked in. No crashes in either.

### Not in 1a

- Home and the workout screen, the sheets they open, and the timer's screen wiring. That is 1b.
- Several ports carry their own private JS-truthiness helpers. `native/PORTING.md` records the debt
  and says to fold them into `Js.kt` the next time a port needs one.

---

## Phase 1b — Home and the session screen

The app now opens on Home, starts the day's session through the weigh-in, logs it set by set with the
rest timer running on the lock screen, and files it into the same training log. That is the Phase 1
milestone: the native app is usable for real training, while the Library, Plan, History, Stats and
Settings screens are still to come.

**479 JVM tests** run offline in about twenty seconds, and the APK builds as `olygym.app.dev`.

### The decisions this phase rests on

1. **A screen writes through edit combinators, not a mutable tree.** The web hands its update
   callback a mutable clone and the screen assigns straight into it; here the same write is composed
   from `editObject`, `editArray`, `editAt`, `append`, `insertAt`, `removeObjectAt`, `mapObjects`
   and the `with`/`without` leaves in `data/Js.kt`. An index that is gone changes nothing, which is
   what a tap racing a removal needs.
2. **The four private JS-truthiness copies are folded in** as `truthy(value)` — the debt
   `PORTING.md` recorded. `jsText` came with them, because a stored message's arguments are
   numbers: `JsonPrimitive(30.0).toString()` is "30.0" where the web's template says "30".
3. **The UI holder is the third module-level object**, beside the store and the catalogue: the sheet
   stack, the toast and the two countdowns, with the clock, the sound and the rest mirror injected so
   that all of it is a JVM test (`UiStateTest`, `RestMirrorTest`).
4. **The lock-screen mirror is the authority for the end time.** The in-app tick re-reads it every
   second, so the notification's own ±15s and Skip need no event bridge — and the same rule adopts a
   rest on resume, as the web's `adoptNativeState` does.
5. **Copy on the lock screen is the app's**, translated through the locale pack, so the card names
   the set that earned the rest and the workout it belongs to.
6. **Glyphs are transcribed** from `Icon.jsx` through `PathParser` rather than redrawn: one table
   of the web's own path data, at the same 1.7 stroke. Adding a glyph is a copy and paste.
7. **The tab bar lives in the shell, not in a nested navigator**, so the session screen can be pushed
   over the tabs and the bar stays — which is where the centre button's "Resume" lives.
8. **`POST_NOTIFICATIONS` is asked for at launch** rather than at the first rest: one call instead of
   a callback threaded through the timer, and the same answer one screen earlier than the web's.
9. **The timer's completion blinks the app's own theme**, as the web's TimerFlash does, by flipping
   the scheme for two and a half seconds in `MainActivity`.

### Deliberately not in 1b

Three things are stated on screen rather than silently missing, because each opens a screen that does
not exist yet:

- **The exercise picker** — `Add exercise`, `Swap exercise` and freestyle. A session built from a
  planned day is complete without it; a freestyle session cannot be filled in yet, so the buttons say
  so rather than opening an empty session. That is the next piece (1c), with the exercise config
  sheet behind them.
- **The Stats and Exercises tabs** show "Not ported yet."; the Plan tab keeps Phase 0's read-only
  list.
- **Settings** has no door: the gear is absent rather than opening nothing.

And the smaller omissions, each also recorded in `native/PORTING.md`:

- The merged complex draws its members' own set tables instead of one shared rounds table. The data
  written is identical; what is missing is three pieces of presentation — `RoundsTable`, the swipe
  between units and the scroll-to-the-actionable-row. The JS for each is in `Workout.jsx`.
- No exercise media (the demo video is its own phase) and no charts: the body-weight curve, the
  exercise history sheet and the calendar sheet are phase 2.
- The Home tiles are not tappable, the competition row is absent, and there is no starter-plan card:
  each opens a screen the port has not reached.
- Two strings Home uses are missing from `it.js` ("Open the plan", "Streak") and show English, as
  they already do in the shipping app. No key was added to `frontend/`.

---

## Phase 1c — the exercise picker

A session can now be built rather than only followed: add an exercise, configure it, swap one out,
and start a freestyle session with nothing behind it. The phase-0 promise that a custom exercise
shows its name instead of its id is kept here too — the profile's own exercises are merged into the
catalogue index on every read.

**490 JVM tests**, and the picker was checked on the emulator: the search field reads "Cerca tra 624
esercizi…", the Chosen chip counts the plan and the log, a row's "+" adds with the default config,
and the new exercise joins the session at the current unit.

### What it adds

- **The picker** (`ui/sheet/ExercisePickerSheet.kt`): search, the Favourites and Chosen shortcuts, the
  "Create your own exercise" row, the catalogue with a Chosen marker, the quick-add "+", and Show
  more. It applies an existing equipment profile when the profile has filtering on.
- **The exercise config** (`ui/sheet/ExConfigSheet.kt`): sets, reps or a hold, weight, planned
  warm-ups, per-exercise rest, bodyweight and the belt weight, the bar, the progression rule and its
  step, and the note. Wired to both flows: "Add exercise" and the ⋯ menu's progression settings.
- **Swap** (`swapActiveWorkoutExercise`): pick a replacement, configure it, and answer the
  logged-sets question before anything is relabelled — over the port of `swapActiveExercise` that was
  already here.
- **Freestyle**: the chooser's button starts an empty session, and its empty state now adds to it.
- **Your own exercises** (`ui/sheet/CustomExSheet.kt`): name, body part, equipment, the muscle groups
  and a description, plus deleting one without losing the sets already logged. The write goes into
  the raw `customEx` objects, so the muscle metadata this form does not own survives an edit.
- **The exercise detail sheet** (`ui/sheet/ExerciseDetailSheet.kt`): what it hits, your best and your
  last, the bar and how to do it — and the one place the favourite star is set, which is what makes
  the picker's Favourites shortcut usable.
- Five new readings with tests: `Favourites`, `Equipment`, `LibraryFilter`, `Usage`, and the two
  progression-step helpers that live in `sheets.jsx` rather than in `lib/`.

### Deliberately not in 1c

- **The demo media**: the picker's thumbnail, the config's poster and the detail sheet's gallery. A
  row shows the app's own glyph where a poster frame will go.
- **"By muscle"** (the muscle explorer) and the **body-part and equipment filter sheet** the Library
  shares. The picker therefore offers search and the two shortcuts, not the four filters.
- **The exercise history sheet**: the chart, and `lib/exercise-history.js` behind it. The detail
  sheet's row through to it is absent rather than dead.
- **Editing the equipment profiles** (Settings), and the plan editor's use of the config sheet with a
  routine behind it. `exConfigSheet` already takes that argument; nothing calls it with one yet.

### The one bug this phase found on device

The set row's number cells were too narrow between their step buttons for a two-digit value: "10"
rendered as a clipped mark. The buttons went from 32 to 26dp and the cell's number down to the
smaller type role — which is what the web's own media queries achieve by shrinking the cell at this
width.



## Phase 2a — the plan and the week editor

A week can be written in the app now, not only read: created (a starter plan, or New week), filled
with days and exercises, grouped into complexes, reordered, renamed and deleted — every write
landing in the existing `weeks` shape, with the keys this app does not model left exactly as they
were.

**517 JVM tests.** The device checks are in `native/README.md`; the screenshots are in
`oly-previews/native-phase2a/`.

### What it adds

- **The plan list** (`ui/plan/PlanScreen.kt`, replacing phase 0's read-only `PlanListScreen`): the
  week that covers today as a hero with its days on it — the short weekday, the day's name, its
  exercise count, a tick on the dates already trained — then Upcoming and Earlier as one line each,
  newest outward from today. `New week` mints the week after the last one (or this week's first day
  when there are none) and opens it.
- **The week editor** (`ui/plan/WeekEditScreen.kt`): an editable week title, one card per day with a
  seven-way weekday picker and an editable day name, the day's exercises drawn in their complexes
  with the link / move-up / move-down actions on the row, and the config sheet one tap away. The day
  and week menus carry the two deletes, each behind the app's own confirm.
- **The complex sheet** (`ui/sheet/ComplexSheet.kt`): the two numbers a complex shares — its sets
  and its load — written to every member at once. Each row keeps its own reps.
- **The starter plan** (`lib/Starter.kt` + `ui/sheet/StarterPlanSheet.kt`): the four ready-made
  plans the web offers, the same schedules and the same lifts, added as a fresh dated week. It is the
  way into an empty profile; the confirmation appears only when one of the plan's weekdays is already
  taken and has exercises, as on the web.
- **The plan's writes** (`lib/PlanEdit.kt`): one pure function per edit, over the raw state object —
  the web's `update(s => ...)` bodies, each with its own JVM test. They are why an unknown key on a
  week, a day or an exercise survives every one of these edits.
- **`lib/Weeks.kt`** grew the plan's own readings: next week's date, the first free weekday, the days
  in the profile's order with their live index, a week's three counts, and the date range.
  **`components/Ui.kt`** gained `LineField`, the editable line of text — with the dashed rule under
  it — that the week title and the day names are drawn with.

### The one behaviour phase 0 changed

A profile with **no file** used to show a notice and nothing else, because phase 0 had nothing that
could write one. It now shows the empty plan and its `Load starter plan` button: no weeks is exactly
what that state is for, and the first write creates the file. `Profile.fileExists` stays in the
model, but the plan list no longer gates on it.

### Deliberately not in 2a

- **"Share your plan"** — the print sheet and the coach's spreadsheet import are phase 3, and the
  upload glyph is not in this app's icon set. The button is absent rather than dead.
- **Competitions**: the web's Plan/Competitions switch. Phase 3 with them.
- **Swipe to delete** on a row: removal is the config sheet's own `Remove from routine`.
- **The demo thumbnail** on a row: media is its own phase, so a row gets the app's glyph.
- **Home's starter-plan door**: three lines once the sheet exists, and not part of this phase.

### The one bug this phase found on device

The bar-weight stepper wrapped its unit: the number field was a fixed 56dp inside a 150dp pill, which
left "kg" about four dp and printed it as "k" over "g", in the config sheet's BAR section. The field
now takes whatever the unit does not need. It shipped in 1c, so this fixes the config sheet it was
already visible in.
## Phase 2b — Stats, and the charts behind it

The analysis half of the app: four totals, a year of activity, the muscle balance and its fatigue
view, how hard the training has been, the body-weight curve, one exercise's own progress, and the
last few sessions opening into their own detail sheet.

**564 JVM tests.** The device checks are in `native/README.md`; the screenshots are in
`oly-previews/native-phase2b/`.

### What it adds

- **The Stats tab** (`ui/stats/StatsScreen.kt`): the Overview tiles (workouts, this month, week
  streak, the 30-day weight delta in its own colour), the 12-month activity heatmap, the muscle
  balance card with its Balance/Fatigue switch and its range, the effort card, then Progress — the
  body-weight curve with the goal line, and one exercise's curve with its five most recent sessions.
  It ends with the six latest workouts.
- **`ui/chart/LineChart.kt`**: the line chart, drawn on a Canvas. Gridlines, month ticks, the
  gradient under the curve, the marked dots that carry a second reading (effort on the weight curve)
  and the goal line. Its maths lives in `lib/ChartMath.kt`, where it is a test.
- **`ui/chart/Heatmap.kt`**: the GitHub-style activity grid, 53 whole weeks, shaded by the time
  trained, with the months labelled where a column starts one. Its readings are `lib/Activity.kt`.
- **`lib/Recovery.kt`**: the fatigue and retained-strength model — the causal downward-only EWMA
  reference, the 36-hour half-life, the intensity-weighted tonnage with its Epley cap, and the four
  state queries the UI asks for. A port of `recovery.js` and `recovery-view.js`, with its 545-line
  vitest suite translated.
- **`lib/Progress.kt`**: which exercises have a history, what each one's latest session reads as,
  and one exercise's curve — including the rule that an exercise which was never loaded reads as its
  rep count rather than as an empty card (issue #5).
- **The workout detail sheet** (`ui/sheet/WorkoutDetailSheet.kt`) and **WorkoutRow**
  (`ui/components/WorkoutRow.kt`): a logged session read back — its four numbers, every entry with
  the sets it actually did and their labels, its note, and the delete behind the ⋯ menu.

### Deliberately not in 2b

- **The body silhouette** on the balance and fatigue views: Phase 6b. Until it landed the same numbers
  were ranked bars with the same ramp and the same state words.
- **The hover tooltip** on a line chart: there is no pointer to hover with. The reading it gave is
  the dated value under the finger, which the card's own rows and captions already carry.
- **Competitions** (phase 3), the calendar sheet (Phase 6a) a heatmap cell with several sessions would
  open, and the exercise-history sheet (Phase 2f). The exercise picker's search is not in the ported
  `SelectRow` yet, so the progress card's picker lists every exercise with a history instead of
  filtering it.
- **The History screen itself** and **log a past workout**: they are phase 2c, and the door to them
  ("All N") is absent from Stats until they exist.

### What the device found

- The four Overview tiles and the detail sheet's four tiles were one row of four, and every value or
  label was cut ("1h 22m" as "1h ...", "Serie settimanale" as "Serie setti…"). The web's `.tiles`
  grid is two by two on a phone — four across is its desktop rule — so both are a 2×2 grid now.
- M3's segmented control gives each option an equal share of the width and then clips the label: the
  range picker's "Settimana" rendered as "Setti". The range picker is now the web's own control — the
  compact strip whose buttons shrink, with the type role one step down.
- The heatmap's weekday labels are three characters ("Mag", "Giu") in a cell 11dp tall, so they
  wrapped onto three lines. They are one unclipped line now, and the month labels overflow their
  column the way the web's do.
- The fatigue view was drawing its bars in the accent ramp; it now uses the red-orange-yellow one
  (`.hm-fatigue`) the web keeps for "this needs rest".
## Phase 2c — the log, and logging into the past

History is reachable now: Stats' Recent workouts ends with its own door, and the screen behind it is
the whole log — the four totals, a search over session and exercise names, a heading per month, and
every session opening into its detail sheet. A workout that happened before the app was opened can be
logged too, from the app bar or the empty state.

**571 JVM tests.** The device checks are in `native/README.md`; screenshots in
`oly-previews/native-phase2c/`.

### What it adds

- **The History screen** (`ui/history/HistoryScreen.kt`): two by two totals (workouts, sets, volume,
  PRs), the search field, one heading per consecutive month ("settembre 2026"), the rows, and the two
  empty states — never trained, and no match. Its readings are `lib/HistoryView.kt`.
- **Log a past workout** (`ui/sheet/LogPastWorkoutSheet.kt`): the date, the start time, the duration,
  what that date's plan holds, and what is already logged on it. Continue on a day that is spoken for
  asks what to do about it — add a second session, or replace one, by name — and a day that is free
  opens the session straight away.
- **The date and time pickers** (`ui/sheet/DateAndTimeSheets.kt`): the month grid the calendar uses
  (with a dot on the days already trained and on the days planned), and the hour/minute steppers with
  the handful of times a session actually starts at. On the web these were the last two platform
  widgets inside a sheet — `<input type="date">` and `<input type="time">` — drawn in the system's
  colours and the system's format.
- **`beginBackfill`** (`ui/workout/WorkoutActions.kt`): the session for a past date, built by the
  same walk a live start uses, with the date it is filed under and the `backfill` block the finish
  path reads. That path was already ported in 1a — a backfilled session is filed on its own day and
  claims no PRs, because a workout logged into the past cannot beat the history that came after it.

### Deliberately not in 2c

- **The calendar sheet** and **a day with several sessions**: Phase 6a — the grid itself was already
  here, as the backfill's date picker.
- **The exercise history sheet** behind the exercise detail's row: it needs its own chart work.
- **Settings** — the last of the phase-2 screens.
## Phase 2d — Settings

Settings is reachable: Home's app bar carries the gear, and behind it are the preferences the app
actually reads — the general ones, the ones the workout screen obeys, the appearance, and the data.

**574 JVM tests.** The device checks are in `native/README.md`; screenshots in
`oly-previews/native-phase2d/`.

### What it adds

- **The screen** (`ui/settings/SettingsScreen.kt`): language (English/Italiano, with the note that the
  instructions stay English), weight decimals (0.5/0.25), week start (Monday/Sunday), weigh-in before
  workouts, automatic progression, workout view (cards/list/compact), the rest timer, sounds, the
  flash at the end of a timer, effort per set, theme (dark/light/system), the eight accent swatches,
  and then Data: load starter plan, import backup, export backup, reset everything.
- **Backup, through the platform**: export writes the whole state object, pretty-printed, to a file
  the user picks (`ActivityResultContracts.CreateDocument`); import reads one back
  (`OpenDocument`), checks it looks like a backup at all, and asks before replacing everything. This
  is the app's first file I/O outside the store, and it is the platform's own picker rather than a
  path field.
- **Reset** (`lib/Settings.kt`): an empty state object. The web copies its `DEF` because that object
  is where its defaults live; here every default is in code — `Persisted`'s own and the settings
  derivation — so an empty object reads back as a fresh profile, and the React app merges its own
  DEF over whatever it finds, which is what its reset does too.

### Deliberately not in 2d

- **Weight classes**, **import a coach's plan**, **import from Google Drive**, **auto-backup** and the
  **update check**: phase 3 (competitions, the spreadsheet, the background jobs, the updater).
- **Keep the screen awake**, **exercise pictures**, **demo videos**, **the reminder card** and
  **play sounds when the phone is on silent**: the Capacitor build's job — a wake lock, the media
  packs, a local notification, an iOS audio session — each of which the native app would do with its
  own platform piece in its own phase.
- **Workout controls** and **the body diagram**: their sheets and their screen are not ported, and a
  switch that wrote a key nothing reads would be a lie. The automatic-progression help and the
  effort help's `(i)` are in the same position — the rows work, the help buttons are absent.
- A few rows carry **no glyph**: the app's own icon set has no globe, bell, sun, upload or download
  yet, and adding one means transcribing its path from `Icon.jsx`. The rows read fine without them.

---

## Phase 2e — the catalogue and the By-muscle explorer

The fourth tab is the catalogue now: search 624 lifts, narrow them with one filter sheet, see the best
weight each has already moved, and start from a muscle instead of a search box.

**575 JVM tests.** The device checks are in `native/README.md`; screenshots in
`oly-previews/native-phase2e/`.

### What it adds

- **The Library** (`ui/library/LibraryScreen.kt`, replacing the tab placeholder): the search field,
  the Filters chip that carries how many filters are on, the chips that drop one without reopening
  the sheet, the create-your-own row, the list with a best-weight pill, and the no-match state that
  offers the one tap which undoes all of it. The title button opens the By-muscle explorer.
- **The filter sheet** (`ui/sheet/LibraryFilterSheet.kt`): body part, equipment, "Only my equipment",
  and a commit button whose count is the count you get. The screen passes its own filter function in
  (`describeFor`), so the sheet cannot drift from the list it is filtering, and the sheet keeps its
  own state until "Show N exercises" — trying a body part and backing out changes nothing. A body
  part with no dumbbell work cannot leave "Dumbbell" chosen, because the effective value comes back
  from the same call that counts.
- **The By-muscle explorer** (`ui/library/MuscleExplorerScreen.kt`): the eighteen muscles and how many
  exercises train each, then — once one is picked — the same search, the same sheet and the same rows
  the Library uses, with "Primary target"/"Also trains" and the counts narrowed by the active
  equipment profile.
- **One row, one chip**: the picker's private row, thumb and chip are now `ExerciseRow`, `Thumb`,
  `BestWeightTag` and `Chip` in `ui/components/`, and all three screens draw them. The name is
  capitalised in that one row, which is what the web CSS did and the picker never did.
- **Three readings fold together**: `exerciseJson` was a private copy in both `Progression.kt` and
  `Recovery.kt` (plus another in `MusclesTest`); it is one function in `lib/Exercises.kt` now. The
  explorer's own reads — `muscleWeightOf` and `muscleCounts` — are in `lib/LibraryFilter.kt` with a
  test, and materialise each exercise once rather than once per muscle.

### Deliberately not in 2e

- **The demo media** — the row thumbnails, the config's poster, the detail sheet's gallery: the
  `Thumb` is still the app's own glyph. Media is its own phase, and it is the same omission the
  picker and the detail sheet have carried since Phase 1c.
- **The exercise history sheet** behind the detail sheet's row: it needs the chart work in its own
  phase.
- **The body silhouette** above the muscle chips. It is ~90 KB of SVG paths plus a path renderer, the
  same deliberate omission the Stats balance and fatigue views carry; the chips under it already say
  which muscle is which and how many exercises train it.
- **Show more.** The web paginates at 40 rows because six hundred DOM nodes are expensive; a
  LazyColumn builds only what is on screen, so the list is simply all there and the button is gone.
- **A sticky search field.** It heads the list and scrolls away with it; on the web it sticks under
  the bar. A sticky header is a later polish, not a missing read.
- **The By-muscle mode inside the picker.** The explorer is its own screen; the picker still offers
  search and its two shortcuts. Wiring a pick back out of a sheet is its own piece of work.

---

## Phase 2f — the exercise history sheet

One exercise's past is a sheet now: the curve, the two numbers it is opened for, and the last ten
sessions set by set — reachable from the exercise detail sheet and from the session's own menu, which
is where issue #43 wanted it.

**584 JVM tests.** The device checks are in `native/README.md`; screenshots in
`oly-previews/native-phase2f/`.

### What it adds

- **The reading** (`lib/ExerciseHistory.kt`): the port of `exercise-history.js`, its nine vitest cases
  as nine JUnit tests. One pass over the log yields the series and the last `HISTORY_SESSIONS`
  sessions, with the metric chosen the way Stats chooses it — the heaviest completed work set, the
  best rep count for an exercise that was never loaded, the longest hold for timed work — the PR on
  the session that first reached the best, and a session logged in another mode kept in the list with
  no point, so the curve never mixes seconds with kilos.
- **The sheet** (`ui/sheet/ExerciseHistorySheet.kt`): the name and how many sessions, the standing
  note, the Best and Last tiles, the line chart in the app's blue, and a row per session with its PR
  mark, its sets labelled by their own target, its volume, and a tap through to the workout detail
  sheet.
- **The standing note, editable with nothing running**: the note editor used to exist only inside a
  running session, keyed to an entry. `standingNoteSheet` is the id-only form — it reads and writes
  `exNotes[id]` and creates no session, which is what lets the note block on the history sheet be a
  control rather than a read-only line.
- **Two doors**: the exercise detail sheet gets the History row (only when there is a history, as on
  the web), and the session's exercise menu gets a History item with "Last time <date>" under it —
  which is the mid-workout read the issue was about.

### Deliberately not in 2f

- **The chart's hover tooltip**: the same omission the Stats curves carry. There is no pointer to
  hover with on a phone, and tapping a point for its reading is its own piece of work.
- **Media**, still: this sheet is text, a curve and tiles. (Phase 2g brings the posters to the list
  rows and the exercise detail sheet; this sheet is the same read without a picture.)
- The reading is recomputed on each pass rather than memoised the way the web's `useMemo` does. A
  sheet this short-lived makes one scan of the log cheaper than comparing two state objects to decide
  whether to scan.

---

## Phase 2g — the demo media

The catalogue looks like a catalogue now: every row carries the poster frame of its demo video, and
the exercise detail sheet opens with the same picture and a badge that plays it.

**595 JVM tests.** The device checks are in `native/README.md`; screenshots in
`oly-previews/native-phase2g/`.

### What it adds

- **The reading** (`lib/Media.kt`): the port of `media.js` — the video id out of every link shape the
  catalogue and the coaches use, the `img.youtube.com` URL for a size, and the chain an image walks
  when the pretty frame is missing. Its tests are `media.test.js` plus the `videoMode` half of
  `video.test.js`, and one of them scans the shipped catalogue: every built-in exercise has a usable
  link.
- **The picture** (`ui/components/Media.kt`): a `RemoteImage` that walks the chain, steps down when a
  URL fails, and draws the app's own tile when the chain runs out. The fetch is the platform's
  `HttpURLConnection` and `BitmapFactory` behind an `LruCache` — Compose has no network image and the
  app carries no image library on purpose, and this is the whole of what one would do here. It is the
  app's first network permission, and it makes the same hotlink the web app makes: nothing of the
  training log leaves the device.
- **The list rows** (`Thumb`): 44dp of `mqdefault`, the cheap end of the chain, with the glyph tile as
  the placeholder while it loads.
- **The detail sheet** (`ExerciseMedia`): the 16:9 poster with the play badge, honouring `S.video`,
  where `off` means no badge at all.

### The one deliberate difference from the web

The web embeds a YouTube `<iframe>` in the page. Here the badge hands the video to whichever app the
phone gives YouTube links to (`ACTION_VIEW`), which is the platform feature for it: no WebView, no
player to stop when a sheet closes, and the poster — the part that makes the Library browsable — is
the same picture. `embedUrl` is therefore not ported; `watchUrl` is what the badge opens.

### Deliberately not in 2g

- **The media in the workout cards**, **the Settings rows for it** and **the wake lock**: Phase 5a.
  Until then the profile's own keys are what is honoured.

---

## Phase 3a — the coach's spreadsheet

A week of the coach's Excel can be read, reviewed and added to the plan now: the file is opened through
the platform's picker, every row is shown with the exercise it was read as, and nothing lands in the
plan until "Add the week to my plan".

**JVM tests.** The device checks are in `native/README.md`; the pictures are in
`oly-previews/native-phase3a/`.

### What it adds

- **The workbook reader** (`lib/Xlsx.kt`): sheet names in workbook order, each a rectangle of the
  strings the file holds. The web walks the zip directory by hand, inflates with `DecompressionStream`
  and parses with `DOMParser`; here `ZipInputStream` does the first two and the JVM's own DOM parser
  the third — the same read with less of it written down, and the same handling of the parts that
  matter (the workbook, its rels, the shared strings, one part per sheet), of rich-text runs, of
  whitespace and non-breaking spaces, and of sparse rows padded to the widest one.
- **The sheet reader** (`lib/CoachSheet.kt`): column A is the day marker or a legend label, B the
  exercise, G the reps, H the sets, I the load, J the cue and P the coach's comment — with the two
  traps the real workbook sets (a marker row also carries the day's first exercise; the sets column
  can hold a stray date serial) handled here rather than by the caller.
- **The matcher** (`lib/PlanAliases.kt`): the coach's gym Italian against the catalogue, in the three
  tiers agreed for this work — the catalogue has it, the base lift exists and his words go in the
  note, or it becomes one of your own exercises under his name. A "fragment" (`"+ sosp bassa"`,
  `"touch n go"`) describes the exercise before it rather than inventing a second one.
- **The importer** (`lib/ImportPlan.kt`): `reviewWeek` proposes an exercise, a scheme, a load and a
  note per row and writes nothing; `bundleFromWeek` turns the reviewed week into one week of
  `S.weeks`, on Monday/Wednesday/Friday, with the coach's custom exercises created once and the
  kilos converted to the account's unit. A load only counts when the text opens with it: "poi togli
  10kg" is an instruction, not a 10 kg bar.
- **`lib/PlanShare.kt`** (the merging half): the week lands through `mergeWeek` — a fresh id, a
  custom exercise reused by name and body part rather than duplicated, every exercise remapped
  through it.
- **The review sheet** (`ui/sheet/CoachImportSheet.kt`): the week picker, the counts, one cell per day,
  and a row per exercise with what it was read as, the scheme, the coach's note and the marks worth
  checking. Tapping a row offers a different exercise, and the correction is remembered in
  `S.planAliases` under the coach's own words — so next week's sheet arrives already corrected.
- **The door**: Settings' Data section. The file is chosen with the platform's own picker
  (`OpenDocument`) and read off the main thread.

### The one deliberate difference

The web throws `unitError` when a plan is merged into an account that is not in kilos. A screen here
cannot catch a throw from inside the store's update, so `mergeWeek` returns null for the same case and
the screen says so. Everything else about the merge is the port.

### Deliberately not in 3a

- **CSV and Google Sheets.** The web's row for this says "An Excel, CSV or Google Sheets week"; the
  reader behind it is the .xlsx one, and the Drive door is its own piece of work. The native subtitle
  says Excel because that is what it reads.
- **The share/print half of `plan-share.js`** (`mergeWeek` and `convertedExercise` are ported; the
  printable page is not).
- **The `(check)` counts** are on screen as the "N to check" line and the per-row marks, but the
  row's own `ignored` fragments (a `+` component with nothing before it) are not listed anywhere.

---

## Phase 3b — competitions

The meets have a screen: what is coming and what is past, the best snatch, clean & jerk and total
across them, a meet written down attempt by attempt, and the federation's weight categories editable.

**674 JVM tests.** The device checks are in `native/README.md`; the pictures are in
`oly-previews/native-phase3b/`.

### What it adds

- **The reading** (`lib/Competition.kt`): the port of `competition.js` with its twenty vitest cases —
  what counts as an attempt (made *and* weighted, so a missed opener heavier than the made one never
  leaks into a best), a total that only exists once something was made in both lifts, the two
  sortings, "days until", and `blankMeet`/`upsertMeet`/`removeMeet`.
- **The screen** (`ui/competitions/CompetitionsScreen.kt`): the three best tiles, Upcoming and Past
  groups, and a row per meet — a medal once it has a total, the days to go while it has not, and the
  total where there is one.
- **The meet sheet** (`ui/sheet/MeetSheets.kt`): name, date, place, weight class, bodyweight at
  weigh-in, placing, three snatch and three clean & jerk attempts (a weight and Good/No lift each),
  the total read off the draft as it is typed, and a note.
- **The detail sheet**: the meta line with the days to go, the class/bodyweight/placing line, the
  three tiles, each lift's attempts as rows with their verdict, the note, and a ⋯ menu with Edit and
  Delete.
- **The categories editor** (`weightClassesSheet`): the male and female lists, edited in place and
  saved to `S.classes`, reached from Settings' new Competition section and from the meet sheet. A
  logged meet keeps the string it was saved with, so editing the list never relabels a result.
- **The medal glyph** is transcribed from `Icon.jsx` rather than approximated with the trophy.
- **Two doors**: Stats gets a Competitions card (the best total and "All competitions"), and Home
  gets the meet row it has been missing since Phase 1b — shown only when a meet is ahead, since an
  empty competition row would be another card of nothing.

### Deliberately not in 3b

- **The Plan/Competitions switcher** (`PlanTabs.jsx`). The web puts a segmented control at the top of
  both screens because they are one destination with two modes; here Competitions is pushed over the
  tabs the way History is, so the bottom bar's Plan tab keeps meaning the plan.
- **The class picker's inline pencil** (the web's `SelectRow action`): the meet sheet has an "Edit
  categories" row under the class picker instead, which does not need the shared component to grow an
  action slot.
- **A meet's attempts are not in the training log**, by design rather than omission: `competition.js`
  keeps them apart so no training curve answers a question it was never asked.

---

## Phase 3d — the coach's file, wherever it is

A week that arrives as a .csv now lands on the same review sheet as one that arrives as an .xlsx, and
the picker behind the row is the platform's own — so it reaches Downloads, a USB stick and Drive alike.

**689 JVM tests.** The device checks are in `native/README.md`; the pictures are in
`oly-previews/native-phase3d/`.

### What it adds

- **The CSV reader** (`lib/CoachFile.kt`): `csv.js`'s state machine — quoted fields, embedded commas
  and newlines, doubled quotes, a byte-order mark, CRLF — with the tests the web never shipped.
  Splitting on commas shifts a whole sheet by one column the first time an exercise is called
  "Bench Press, Close Grip", and nothing errors.
- **The dispatch**: a .csv is one sheet named after the file; anything else goes through the workbook
  reader, which throws `NotAWorkbookException` for a file that is neither. This is the half of the
  import `sheets.jsx` keeps inside the component; it is a pure function with a test here.
- **The row** in Settings: the picked file's own name is read back from the platform and decides the
  reader, the sheets with nothing in them are dropped, and the web's two sentences say why nothing
  arrived ("That file has no training in it", "Could not read that file…") where this screen used to
  invent its own. Its subtitle is the web's own copy now, so it is translated.

### Drive needed no code

The web carries a Drive plugin and a REST client (`drive.js`, `DrivePlugin.java`, Play Services auth,
a `drive.file` token) because a browser has no file system to browse. Android does: the picker behind
`ACTION_OPEN_DOCUMENT` lists Drive beside Downloads, and the bytes come back through the same
`ContentResolver` as any other file. So there is **no "Import from Google Drive" row of its own**, and
no OAuth, no Play Services and no Drive dependency in the app.

What that does not buy: a *native* Google Sheet has no bytes of its own, and only Drive's export
endpoint produces them. Whether the Drive provider hands an equivalent over is the provider's business;
if it does not, the read fails and says so. Exporting the Sheet to .xlsx or CSV and picking that is the
way through, and the row's "Excel, CSV or Google Sheets" is the web's own wording for it.

### Deliberately not in 3d

- **The Drive API and its sign-in**: it exists in the web build to reach a file system the platform
  here already hands over.
- **Writing a CSV** — an export of the plan. The row reads.

---

## Phase 3e — the workout-day reminder

A planned day announces itself now. One switch in Settings turns it on, the time comes from the same
picker every other time in the app uses, and the phone posts a notification on the days that have a
routine.

**699 JVM tests.** The device checks are in `native/README.md`; the pictures are in
`oly-previews/native-phase3e/`.

### What it adds

- **The date walk** (`lib/Reminder.kt`): the port of `buildReminderNotifications` — the next planned
  date inside a sixty-day window, skipping a day already trained and today once its time has passed,
  naming the day in the body and falling back to "Workout" when the day has no name of its own. The
  web queues one notification per date because Capacitor's plugin has no recurrence; here the walk
  stops at the first date that counts.
- **The alarm** (`platform/ReminderAlarm.kt`): one `setAndAllowWhileIdle` alarm, inexact on purpose —
  a reminder does not need the minute, and an exact alarm needs a permission of its own. The receiver
  posts the copy it was carrying and asks for the following date, reading the profile off disk
  because the system can run it in a process that never loaded a store.
- **The re-arm**: `BOOT_COMPLETED`, a clock or timezone change, every return to the foreground, and
  every write (`StateStore.onChange`) — so training a day drops that day's reminder instead of
  announcing a session already done. The web does the same on every persist, and this is its debounce
  with nothing added, because the writes are already coalesced.
- **The card** in Settings: the switch, the time row it reveals, the footer sentence, and the
  permission check — with notifications off, the switch refuses and says so rather than promising
  something the phone will throw away.

### Deliberately not in 3e

- **The web's sixty-notification queue and its `tz` stamp**: Android has a real alarm, and the phone's
  own clock is what that alarm is in. `tz` is still written by the web build and left alone here.
- **A sound or vibration of its own**: the channel takes the system default, so the phone's own
  notification settings decide what a reminder looks and sounds like.

---

## Phase 3f — the auto-backup

A dated copy of the whole profile now lands in the Documents folder whenever a workout is filed, so a
sync app or a file manager always has something recent to point at.

**701 JVM tests.** The device checks are in `native/README.md`; the pictures are in
`oly-previews/native-phase3f/`.

### What it adds

- **The name** (`lib/Backup.kt`): `opengym-backup-<today>.json`, the port of the path
  `writeAutoBackup` builds — one file per day, so a busy day leaves one snapshot rather than a pile.
  The manual export's suggested name comes from the same function, so the two cannot drift apart.
- **The writer** (`platform/AutoBackup.kt`): MediaStore on Android 10 and later, so the file is in the
  real Documents folder — no storage permission at all, and it outlives an uninstall. On Android 9
  and older the snapshot goes to the app's own external documents folder instead, because the public
  one would need `WRITE_EXTERNAL_STORAGE` and this app never asks for it.
- **The trigger** is the web's `autoBackupNow`: the moment a workout is filed, and only then. The
  row's subtitle also promises routine edits, which neither build does — inherited wording, kept
  because it is the same key the web ships.
- **The row** in Settings' Data section, above "Reset everything".

### Deliberately not in 3f

- **A backup on every write**: the web fires this once, when a workout is filed. Backing up as each
  set is ticked would write a fifty-kilobyte file every few seconds, and the private mirror in
  `files/` already covers every change.
- **A storage permission** for the public folder on Android 9 and older, as above.

---

## Phase 3g — the in-app updater

The app updates itself from this repository's own releases now: it reads the GitHub API, compares the
newest native release against the version it is running, downloads the APK, checks it against the
SHA-256 GitHub published for the upload, and hands it to the package installer.

**717 JVM tests.** There is no device check yet — see the end of this section.

### What it adds

- **The reading** (`lib/Update.kt`): the port of `update.js` — `compareSemver`, the walk over the
  releases, and `sha256Hex`. Two deliberate differences: the list is read with `per_page=30` and
  filtered on a `native-v` tag, because the web app's own releases (`v1.3.x`) share this repository
  and carry no APK; and drafts and pre-releases are never offered.
- **The platform half** (`platform/Updater.kt`): `HttpURLConnection` (no dependency), the download
  into the cache with the hash computed while it streams, the FileProvider hand-off to
  `ACTION_VIEW` + `application/vnd.android.package-archive`, and the one permission Android keeps to
  itself — `REQUEST_INSTALL_PACKAGES`, with its "install unknown apps" screen opened when it has not
  been granted.
- **The check runs itself**, once per process, when the app comes to the front: silent when the app
  is current and silent when github.com cannot be reached, because nobody asked. Settings' Updates
  section shows what it found, and its row is the on-demand check as well.
- **The `download` glyph** is transcribed from `Icon.jsx`; the update row needed one.

### Deliberately not in 3g

- **Silent installation.** Android shows the installer and the user confirms; no ordinary app can skip
  that. "Automatic" here means the check is automatic and the download starts when the offer is
  accepted.
- **Installing without a checksum**: GitHub publishes a SHA-256 for every uploaded asset, and that is
  what the download is checked against. A release that carried no digest would be installed
  unverified, as the web build does — leaving it off is not worth doing.
- **The device check.** The repository has no `native-v…` release yet, so the updater has nothing to
  find: the network path and the installer need a real release to walk through, and both are written
  and left for that day. The check, the parsing and the hash are unit-tested.

---

## Phase 4a — the wavy bar

The app's two real progress indicators now carry M3 Expressive's wave: the rest timer's countdown and
the workout header's sets-done bar. Those are the two the web app masks, and its CSS says why — they
are the only bars in the app that are decorative rather than informational.

**717 JVM tests** (unchanged: this phase is drawing). The device checks are in `native/README.md`;
the pictures are in `oly-previews/native-phase4a/`.

### What it adds

- **`ui/components/WaveProgress.kt`**: the web's sine, drawn rather than masked — one quadratic hump
  per half wavelength, a wavelength every 20dp, stroked 3.4dp on a 6dp bar, drifting one wavelength
  every 2.4 seconds. The fill still advances by width, so what the bar measures is exactly as
  accurate as it was as a plain rectangle.
- **Both call sites and only those**: `#timer .bar` (the rest bar) and `.wprog` (the workout header).
  The muscle-balance bars show how much each muscle got rather than how far along something is, and
  stay straight, which is the distinction the web's CSS draws in a comment.
- **The rest bar's 4dp became 6dp**, the height the web settled on: "at 4 there is no room for a wave
  to read".

### The expressive theme itself is not available

`material3` 1.4.0 — the version this build pins — does contain `MaterialExpressiveTheme`,
`MotionScheme` and `ExperimentalMaterial3ExpressiveApi`, but all three are Kotlin-`internal` in that
artifact: importing them is a compile error from another module. So the theme cannot be swapped in,
and the route taken here is the web's own — implement the expressive details with the ported tokens by
hand, which is what `m3.tokens.css` does on the other side. The Emphasized type scale (400 -> 500,
500 -> 700) is ported in `Type.kt` and the four curves in `Motion.kt`; what remains of the expressive
work is the per-call-site application of both, not a theme swap.

### Deliberately not in 4a

- **The Emphasized weights and the app's own curves**: Phase 4b, next.
- **The `MaterialExpressiveTheme` swap** — blocked, as above.
---

## Phase 4b — the Emphasized weights, and the two curves the app draws itself

Phase 4a drew the wave; this is the other half of the expressive pass — the per-call-site
application of the Emphasized type scale, and the two expressive curves whose motion the app owns
rather than Material.

**717 JVM tests** (unchanged: this phase moves nothing the tests can see). The device checks are in
`native/README.md`; the pictures are in `oly-previews/native-phase4b/`.

### The Emphasized scale, at the web's call sites and nowhere else

The web picks the Emphasized weight at five selectors — `.sheet h3`, `.card .big`, `.tile .v`,
`.ab-title` and `.ddow` — and the token table's rule is the whole table: 400 becomes 500, 500
becomes 700, weight only, never a size. `emphasizedWeight` has carried that rule since Phase 0; this
phase wires it at those sites, and each is handed the role's own baseline weight so the pair stays
the table's:

| call site | role | baseline | Emphasized |
|---|---|---|---|
| a sheet's title | title-large | 400 | 500 |
| a tile's value | headline-small | 400 | 500 |
| the app bar's headline, and WeekEdit's title field | headline-medium | 400 | 500 |
| Home's body weight | headline-medium | 400 | 500 |
| Workout's day name | headline-small | 400 | 500 |
| the weekday on a Plan day row | label-medium | 500 | 700 |

Nothing else moved. `emphasizedWeight` now takes the nullable weight a `TextStyle` reports — null is
the default weight, which is the 400 the rule starts from — so a call site can say "this role,
emphasized" without naming a number.

Two of the six selectors had no native counterpart to move. The web's `.big` is two places, and both
are here; `.ab-title` is two too — the plain heading every screen gets, and the `<input>` WeekEdit
puts in its place — and both took the weight.

### The two curves the app owns

The web's expressive block animates six things. Four of them are Material's own — the bottom sheet,
the dialog, the segmented thumb, the switch knob — and Material draws those: there is no easing
parameter to hand them, which is the same `MaterialExpressiveTheme` wall Phase 4a hit. The two the
app draws itself are now drawn:

- **The route fade.** The web keys `#app` on the path and replays `viewfade` on every route change:
  4dp up from transparent, 400ms on emphasized-decelerate, with the old view unmounted rather than
  faded out. `AppNavigator` now does the same around whatever screen is on top, keyed on the route
  and the selected tab.
- **The Start disc's press.** The web compresses `.start .cir` to .94 and springs it back — 400ms on
  `--m3-ease-spring`. The tab bar's centre button now does that, through `Motion.spring`.

`Motion.emphasized` still has no consumer, and has none in the web either, where
`--m3-ease-emphasized` is defined and referenced by nothing.

### Deliberately not in 4b

- **A blanket scale step.** The web's own token test asserts *which* selectors carry the Emphasized
  weight, and stepping the whole app up is a heavier app than the one being ported.
- **Tracking.** The call sites above keep the tracking the native port gave them — `-0.02em` on a
  sheet title where the web uses the role's `-0.008em`, and so on. Moving it is a pass of its own:
  decision 4 in DESIGN.md keeps the app's own weights, and it would change every screen at once.
- **Re-curving the four Material-owned animations** — that is the theme swap, blocked as in 4a.
---

## Phase 5a — the workout's picture, its Settings rows, and the screen that stays awake

The session screen and Settings now carry the three things Phases 2d and 2g left behind: the demo
poster in the exercise header, the two rows that decide what it shows, and the display that stays on
while a session is running.

**717 JVM tests** (one existing case grew an assertion; this phase is UI). The device checks are in
`native/README.md`; the pictures are in `oly-previews/native-phase5a/`.

### What it adds

- **The workout header's picture** (`WorkoutMedia`, `ui/components/Media.kt`): the demo poster at the
  web's 88dp chip in the exercise header's own row, with the play mark over it. `gifSize` 'off'
  renders nothing and the header closes up, which is what the Settings row means; `video` 'off' keeps
  the picture and drops the mark.
- **The three Settings rows** the web has: *Keep screen awake*, *Exercise pictures* (On tap / Hidden)
  and *Demo videos* (Hidden / On tap / Always). They write `keepAwake`, `gifSize` and `video` — the
  last being the key the media layer's badge already read.
- **The wake lock** (`MainActivity`): `FLAG_KEEP_SCREEN_ON` while `active` is set and `keepAwake` is
  not false, which is the web's `useWakeLock(!!S.active && S.keepAwake !== false)`. The flag is only
  honoured while the window is in front, so the browser's own backgrounding behaviour comes free.

### The expand is deliberately not ported

The web's chip expands to a full-width 16:9 block with a Minimize button. That exists to give the
YouTube `<iframe>` somewhere to live — the iframe is mounted on the tap and stopping when the block
collapses is what stops the playback. Here the mark hands the video to the platform player, the
difference Phase 2g already records, so there is nothing to expand *for*: the chip stays a chip.
That is also what the web's own comment on `.ex-head` wants — a full-width picture pushed the first
set row below the fold on the one screen where the phone is in your hand.

### Deliberately not in 5a

- **`inline`.** The web's third `video` value loads the player with the exercise. There is no player
  to load here; the row offers the value because the key is shared with the web app, and it behaves
  as 'On tap'.
- **Workout controls** and **the body diagram**: still absent from Settings, for the reasons in the
  screen's own header.

---

## Phase 5b — the in-app player (planned, not built)

Phase 5a hands the demo video to the platform player, which is the Phase 2g decision and what the
workout card is built on. There is one way to put a player back inside the app, and it was costed
before being deferred:

- **A `WebView` around the same `youtube-nocookie.com/embed` URL the web builds.** It needs no
  dependency — `android.webkit.WebView` is a platform class, and `androidx.webkit` 1.12.1 is in this
  machine's offline cache if the modern helpers are wanted. The shipping Capacitor app *is* a
  `WebView` running exactly that iframe
  (`frontend/android/app/src/main/res/layout/activity_main.xml`), so it is proven on the target
  phones rather than hoped for.
- **The other two routes are closed.** media3/ExoPlayer cannot play YouTube — YouTube serves no media
  URL an app may fetch, which is why the web uses an iframe at all — and it is not in the offline
  cache. The YouTube Android Player API is deprecated, needs the YouTube app installed, and is not
  cached either.
- **What it costs.** One renderer process per mounted player, so the expand has to unmount and
  `destroy()` the view or playback outlives the sheet — the web's own comment on `inline` is the
  precedent (it refuses six live players on the workout screen). It reverses two recorded decisions:
  `WebView | none` in the table above, and Phase 2g's hand-off. `embedUrl` comes back with them, and
  so do the five `embedUrl` cases in `frontend/src/lib/video.test.js` — including "can embed every
  exercise of the catalogue", which becomes a JVM test scanning the shipped 624.
- **The animation comes with it.** With a player in the app the web's expand-to-16:9 has a reason
  again: tap the chip, it grows to full width and plays, Minimize collapses it — the size and shape
  change on `Motion.LONG` + `Motion.emphasizedDecelerate`, which the theme already carries. `inline`
  becomes real in the detail sheet too, and *Always* in the Demo videos row finally means something.
- **It cannot be device-checked on this machine.** Phase 3c's emulator WebView died with `SIGILL`
  inside `libwebviewchromium.so` — the API 37 `ps16k` system image ships a broken Chromium — so that
  check would have to happen on a real phone.

---

## Phase 6a — the calendar, and a day with several sessions

Three taps that did nothing now do something: Home's streak and week tiles, and a heatmap cell holding
more than one session. Behind them are the two sheets Phase 2c left out.

**717 JVM tests** (unchanged: the month's own reading was already ported and tested, as the backfill
picker's). The device checks are in `native/README.md`; the pictures are in
`oly-previews/native-phase6a/`.

### What it adds

- **`ui/sheet/CalendarSheet.kt`** — `Calendar` and `DaySessions`, the ports of the two functions of the
  same name in `sheets.jsx`. Neither grid nor summary is new: `MonthGrid` is what the backfill's date
  picker already draws, so a day's dots mean the same thing in both, and `monthSummary` was ported with
  it. What the calendar adds is where they are put and what a tap on a day means.
- **What a tap means** depends on which side of today the day is, which is the web's rule: one session
  opens it, several open their list, a day already gone opens the backfill dated to it, and a day still
  to come goes to the plan. Only this screen knows which date was meant, so it hands the date over
  rather than sending the reader to the plan to find it.
- **The three taps**: Home's *Streak* and *This week* tiles, and the heatmap cell. The *Weight* tile
  opens the weigh-in sheet — the web's third tile does that and the native one was the one tile in the
  row with no tap at all.

### Deliberately not in 6a

- **The in-app player** — Phase 5b, above.
- **The day-sessions sheet is reached through the calendar from the heatmap**, not directly: the
  heatmap knows the month, the calendar knows the day. That is the web's own path.

---

## Phase 6b — the body silhouette

Phase 2b replaced the muscle map with ranked bars and said why: the map is ~90 KB of SVG paths plus a
path renderer. The renderer was already here — `Icons.kt` parses the app's own glyphs with Compose's
`PathParser` — so this phase is the geometry and the drawing, and nothing else. The bars stay: the map
answers "what did I neglect" at a glance, the rows say by how much.

**718 JVM tests** (one new: the geometry asset's four views). The device checks are in
`native/README.md`; the pictures are in `oly-previews/native-phase6b/`.

### What it adds

- **The geometry as an asset** (`body-paths.json`, ~93 KB): `native/tools/assets.mjs` now writes it
  from `frontend/src/lib/body-paths.js`, the way it already writes the catalogue and the Italian pack,
  and `AssetsTest` checks the four views and that every muscle the readings name has paths somewhere.
- **`ui/components/BodyMap.kt`**: the two views side by side, parsed once per process off the main
  thread — the web lazy-imports the same file for the same reason — with each canvas mapping its own
  viewBox, because the two views crop out of one shared coordinate space. Every path is stroked in the
  page colour so two neighbouring muscles read as two shapes.
- **The map's own ramp** (`muscleBase`, `muscleSilhouette`, `muscleLevelColor` in the theme): the web's
  `.bm-m` mixes the accent into `--bm-base`, a shade off the heatmap's base, and the silhouette is a
  third tone again. The fatigue view reuses `fatigueLevelColor`.
- **Draw order follows the web, not the asset**: the silhouette first, then the muscles head to toe.
  Where two flat shapes overlap the later one wins, so the order is part of the picture.

### Tapping a muscle

The map is tappable, as the web's is: a tap names the muscle under the finger back to the card, which
draws that muscle's outline in the label colour (the web's `.bm-m.sel`) and puts its one reading where
the ranked rows were — its sets in the balance view, its band in the fatigue view. Tapping the same
muscle again clears it, and so does changing the window, the hard-sets scale or the view: the row is
then about a different reading.

Finding *which* muscle is the interesting half. A Compose `Path` cannot be asked what is inside it, and
the obvious tool — the platform's `Region.setPath` — came back empty for these outlines. What works is
the rule the picture is already drawn by: each candidate path is rasterised into a single pixel at the
point's offset, and that pixel is the answer. Only paths whose bounds already hold the point are drawn,
and the candidates are tried in the web's own order and backwards, because the shape painted last is
the one you can see.

### What it deliberately does not do

The web gives every path `role="button"`, an `aria-label` and `aria-pressed`, so a screen reader
reads eighteen targets. A Compose `Canvas` is one node, so it reads the map and not the muscles in it.
That is the one thing this does not carry.

### Deliberately not in 6b

- **The map in the By-muscle explorer** (`MuscleExplorerScreen`): the explorer is a list of eighteen
  muscles you pick from, which does the same job without the picture.
- **The in-app player** — Phase 5b, above.

## Phase 7a — the complex, as one table of rounds

A complex is one barbell done as one sequence, and it was already one unit everywhere except the
screen you log it on: the pairing, the shared sets and load in the editor, and `complexRounds` in the
port of `supersetFlow.js` with its nine vitest cases. What was missing was the table itself, so this
phase is `RoundsTable` and the writes it needs, and no new state.

**720 JVM tests** (two new: the shared table's two gates). The device checks are in
`native/README.md`; the pictures are in `oly-previews/native-phase7a/`.

### What it adds

- **`RoundsTable`** (`ui/workout/ExerciseBlock.kt`): the weight column's header, one row per round with
  the phase headings the members share, a round number that opens the same "remove this set" menu a
  set row does, and "Add set".
- **`complexRoundsFor`** (`lib/SupersetFlow.kt`): the two gates the web puts in front of
  `complexRounds` — effort tracking rates RIR/RPE per movement, so a shared row would overwrite it,
  and a bodyweight movement with no load has no weight column to share. Both fall back to the
  per-movement tables.
- **The members go head-only** when the unit merges: everything but their set tables — the name with
  its `×reps`, the media, the tags, "last time", the bar maths, the progression line and the ⋯ menu —
  exactly as the web's `headOnly` does. It was already a parameter here; this gives it its first
  caller.
- **The writes** (`WorkoutScreen.kt`): a round ticked closes that round of every movement at once, a
  typed weight lands on every movement's row (and cascades through the phase), a round added or
  removed is added or removed from every movement, and a warm-up ramp added from a movement's menu
  belongs to the whole complex.
- **The round's own tick path** (`toggleRound`): there is no partner to step to, so the group moves
  together and the rest belongs after the round — the port of the `round` branch of the web's
  `toggle`, high-water mark and all.

### The trap worth writing down

`complexRounds` guarantees the members share a set structure, never that they share a *load*: a
bodyweight movement with an added weight is aligned and mergeable, and its `w` is the added load.
So the shared row reads and writes the first member's weight and the tick copies it across the group,
which is the web's "what is seen is what is logged", preserved rather than re-derived.

### Deliberately not in 7a

- **The scheme line** in the complex header (the web's `ss-load`, the coach's "3+3 @ 60kg"): the
  movements' own `×reps` ride on their name lines as before and the shared table shows the load
  itself.
- **The unloaded bodyweight complex and the effort-tracked one** keep their per-movement tables by
  design — that is the gate above, not a gap.

## Phase 7b — swipe-to-delete on a plan row

The week editor had one way to remove a row: open its config sheet and choose "Remove from routine".
The web's row also swipes left to reveal a Delete button, which is the same action without the sheet.
This phase is that gesture, and nothing else.

**720 JVM tests** (no new: a gesture has no pure decision to test). The device checks are in
`native/README.md`; the pictures are in `oly-previews/native-phase7b/`.

### What it adds

- **`SwipeToDeleteRow`** (`ui/plan/WeekEditScreen.kt`): the port of
  `frontend/src/components/SwipeToDelete.jsx`. A horizontal drag slides the row 76dp and a Delete
  button shows behind it; the row's own tap closes an open row instead of opening the sheet under it.
- **The axis lock** is Compose's own: `detectHorizontalDragGestures` claims the pointer only after it
  has clearly gone sideways, so the week's vertical scroll is never hijacked — the web reimplements
  the same rule by hand, with a non-passive `touchmove`, because React's synthetic handlers are
  passive.
- **Both row shapes**: a standalone row and a row inside a complex, each with its own opaque fill
  (`surfaceContainerLow` and `surfaceContainer`), and the complex's step number stays outside the
  sliding part, exactly as the web's `.cx-row` keeps it outside `SwipeToDelete`.

### The trap worth writing down

The button has to be absent until a swipe is going horizontal. Left mounted at rest, its red
background bleeds a hairline arc out of the row's antialiased rounded corner — the web hit the same
thing and fixed it the same way (its `armed` state), which is worth knowing before "simplifying" this
back into an always-drawn background.

### Deliberately not in 7b

- **Swipe between units** on the session screen: the port's Prev/Next buttons are the way through, and
  the web's `onSwipePointerDown` is the piece still missing there.
- **The demo thumbnail** on a row, which the session's own media chip carries and the editor's row
  does not.





