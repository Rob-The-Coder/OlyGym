# native/ — the Kotlin app

The React app in `frontend/` is still the shipping app. This is the port described in
[../docs/PORT-TO-KOTLIN.md](../docs/PORT-TO-KOTLIN.md), and it is at **Phase 3a**: the app opens on
Home, reads and rewrites the real `opengym-state.json`, starts the day's session through the
weigh-in, logs it set by set with the rest timer running on the lock screen, files it into the
training log — builds a session as well as following one, since the exercise picker, the config
sheet, swap and freestyle are here too — writes the plan itself (Plan and the week editor, with the
starter plans, the complexes and a pure write for every edit), reads it back (Stats, with the
activity heatmap, the muscle balance and its fatigue view, the effort card, the body-weight and
per-exercise curves, and the workout detail sheet behind the recent sessions), keeps the log
readable and writable (History, with its search, its month headings and log-a-past-workout), browses
the catalogue (the Library, its shared filter sheet and the By-muscle explorer), reads one exercise
back (the Exercise history sheet — its curve, its PR, and the cue that outlives a session), shows
the catalogue's demo posters (hotlinked from YouTube, with the platform's player behind the badge),
reads the coach's Excel week and reviews every row of it before any of it lands in the plan, and is
configured (Settings — the preferences, the appearance and the data, with backup export/import
through the platform's own file picker). **Phase 2 is complete**; phase 3 is what is left.

## Pinned toolchain

Everything below is in this machine's offline Gradle cache, and this is the combination that
resolves against it.

| Piece | Version |
|---|---|
| Gradle | 9.3.1 (`gradle-9.3.1-bin` distribution) |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.4.0 (`kotlin-gradle-plugin`, `compose-compiler-gradle-plugin`, `kotlin-serialization`) |
| Compose BOM | 2026.06.01 → ui/foundation 1.11.4, **material3 1.4.0** |
| Navigation | Voyager 1.1.0-beta03 (`navigator`, `tab-navigator`, `transitions`, `screenmodel`) |
| JSON | kotlinx-serialization-json 1.11.0 |
| Tests | JUnit 4.13.2 |
| SDK | compileSdk 36, targetSdk 35, minSdk 24, Java 17 target |

Two things are *not* available in the cache and shaped the build:

- **The Gradle plugin markers** are missing, so the build uses the `buildscript` classpath and
  `apply plugin:` rather than the `plugins {}` DSL.
- **`androidx.navigation:navigation-compose` is missing**, which is why navigation is Voyager — the
  library the Komikku app uses, at the version its own catalogue pins.

The buildscript repositories are ordered `google()`, `mavenCentral()`, `gradlePluginPortal()`.
That order is load-bearing with `--offline`: Gradle takes the *metadata* from the first repository
that has it and then needs the *artifact* from that same repository, and the Kotlin plugins were
cached under the plugin portal while their transitive dependencies were cached under Google.

## Build and test

```bash
cd native

# unit tests — 654 of them, no emulator, about twenty seconds
GRADLE_USER_HOME=$PWD/.gradle-home GRADLE_RO_DEP_CACHE=$HOME/.gradle/caches \
  ./gradlew :app:testDebugUnitTest --offline

# the debug APK
GRADLE_USER_HOME=$PWD/.gradle-home GRADLE_RO_DEP_CACHE=$HOME/.gradle/caches \
  ./gradlew :app:assembleDebug --offline
# -> app/build/outputs/apk/debug/app-debug.apk
```

On a machine with a normal `~/.gradle`, `./gradlew :app:assembleDebug` just works, online or off.
The extra environment is for this one, where `~/.gradle` is not writable:

- `GRADLE_USER_HOME` must point inside the repo, and the wrapper needs the Gradle distribution
  stubbed into it (`docs/PORT-TO-KOTLIN.md` has the four commands).
- `-Pkotlin.compiler.execution.strategy=in-process` avoids the Kotlin daemon, which wants to write
  a marker in `~/.local/share`. Without it the build still succeeds but logs a permission error.
- `local.properties` must hold `sdk.dir`; it is ignored by git.
- Delete `.gradle-home` when finished, after `pkill -f 'Gradle[D]aemon'`.

## On a device

The debug build is `olygym.app.dev` — its own sandbox, so it can never touch the Capacitor app's
`/data/data/olygym.app/files/opengym-state.json`. The release build is `olygym.app`, which is the
cutover (one `applicationIdSuffix` line).

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk

# hand it a profile: any export works, or a fixture
adb push some-state.json /data/local/tmp/state.json
adb shell run-as olygym.app.dev cp /data/local/tmp/state.json files/opengym-state.json
adb shell am start -n olygym.app.dev/olygym.app.MainActivity
```

With no file the app shows an empty plan and its "Load starter plan" button, and the first write
creates the file — the store's three outcomes (loading, ready, one that will not parse) are still all
reachable.

Phase 1b's check is a whole session: Home renders the profile's week, the tiles and the weigh-in
card; the centre button starts the day, the weigh-in sheet is locked until it is answered; ticking a
set writes it, starts the rest, and posts the lock-screen notification; force-stopping the app and
reopening it resumes the session with the countdown adopted from the notification; finishing writes
the workout record and the summary. `adb shell dumpsys notification | grep olygym.app.dev` shows the
rest notification, and `run-as olygym.app.dev cat files/opengym-state.json` shows the record.

Phase 1c's check is the picker: its search field reads the catalogue's size, the Chosen chip counts
the plan and the log, a row's "+" adds with the default config (a toast names the day), and the new
exercise joins at the current unit. The set rows are where to look when a number goes missing — the
cells are the tightest control in the app, and a two-digit value is what finds their width.

Phase 3a's check is the coach's week. Settings' Data row "Importa la scheda del coach" opens the
platform's picker; choosing a real two-sheet .xlsx shows the review — "3 giorni · 8 esercizi · 1
nuovi · 1 da controllare", the day cells 1/2/3, and a row per exercise with what it was read as: the
complex's two parts sharing one superset, "Piegamenti alle parallele" as dip, the load sentence kept
in the note, plank as 3 × 1:00, "Amrap" kept as a note over ten reps. A row opens its own menu, and
picking Back Squat for "Strappo" rewrites the row and stores `planAliases: {"strappo":"wl77"}`.
"Add the week to my plan" lands it on Mo/We/Fr with Pogo jump created once as your own exercise
(`customEx: ["Pogo jump"]`, 4 × 3 at 40 kg, note "esplosivo"), and the file shows the week's three
days with the shared `sg`. The pictures in `oly-previews/native-phase3a/` are each of those steps.
Phase 2g's check is the pictures. The Library draws a real poster frame on every built-in row — Snatch,
Back Squat, Pull-Up, the position snatches — while "Create your own exercise" keeps the sparkle tile,
which is what a custom exercise gets. Tapping Back Squat opens the detail sheet with the 16:9 poster
and its "video" badge, and the badge hands the video to the YouTube app (the emulator's own is what
this asked for), which is the platform player rather than an iframe. Seeding `video: "off"` leaves the
poster and drops the badge. The pictures in `oly-previews/native-phase2g/` are each of those steps.
Phase 2f's check is one exercise's past. The Library opens Back Squat's detail sheet, whose History
row opens the history sheet: 7 sessions, Best 130 kg with the date it was set, Last 125 kg, the blue
curve, and the rows newest first with their sets, RIR, volume and chevrons — the PR mark landing on
"ven 11 set", the first session that reached 130. The standing-note block opens the note editor with
no session running, keeps "Gomiti alti" and writes it to `exNotes.wl77` without creating an `active`
one. Mid-workout the same sheet is in the session's exercise menu, reading "Cronologia · L'ultima
volta 2 ott". The pictures in `oly-previews/native-phase2f/` are each of those steps.
Phase 2e's check is the catalogue. The Exercises tab opens on the whole 624 with the filters that
are on: the list reads 505 once the "Casa" profile is applied, the three favourites are starred at
the top with their best weights, and the Filters chip carries the one filter. The sheet re-counts its
commit button as a body part is picked ("Show 52 exercises"), the equipment strip disappears when that
filter leaves it one option, and committing closes the sheet on two applied chips and a 52-row list;
dropping one chip puts the 505 back. "Per muscolo" pushes the explorer: eighteen muscles with their
counts, a picked one highlighted with its "Esercizi per trapezio" list at the same 140, and the search
field narrowing that to 69. The pictures in `oly-previews/native-phase2e/` are each of those steps.
Phase 2d's check is Settings: Home's gear opens it, the language row swaps the whole UI to Italian
and back, changing the accent re-themes the app as you tap the swatch (the file shows the new key),
and Esposta backup (JSON) opens the system's "save to" picker with a dated file name. "Azzera tutto"
asks first and then leaves an empty state — the file holds only its timestamp, Home reads 0 workouts
and no weight, and the app is back on Home. **That is the check to be careful with: it deletes the
fixture you seeded.**

Phase 2c's check is the log: Stats' Recent workouts ends with "All N", which opens History on the
totals, the search field and the month headings; typing an exercise name filters the list, and a row
opens its detail sheet. The app bar's "+" opens Log a past workout: the date opens the month grid
(a dot on the days trained, tomorrow disabled), the start time opens the steppers and the presets,
and Continue on a free day starts the session — which is the check that matters, because it is where
a past session is built rather than followed. Logging a set and finishing files it on its own date:
`run-as olygym.app.dev cat files/opengym-state.json` shows it in date order with no PRs claimed, and
a day that already has a session asks whether to add a second one or replace the one that is there.

Phase 2b's check is Stats: the Overview tiles are two by two and read in full, the activity heatmap
shades the days trained and rings today, the balance card's range strip shows "Settimana" whole, its
Fatigue view colours the muscles red-orange-yellow, the effort card's curve and histogram agree with
"28 of 158 sets rated", and the body-weight curve draws the goal line. A row in Recent workouts opens
its detail sheet — four tiles, every set's label, the note. The four pictures in
`oly-previews/native-phase2b/` are what each of those looked like when they were checked.

Phase 2a's check is the plan: the week that covers today leads the Plan tab with its days and a tick
on the dates already trained, and opening it gives one card per day. A day opens into its exercises
with the link / move-up / move-down actions on the row; linking two rows makes a complex card, and the
complex sheet's sets and load reach every member at once. The picker's "+" adds a lift (a toast names
the day), the row's own sheet edits it and removes it, and New week starts the week after the last
one. Then `adb shell pm clear olygym.app.dev`: an empty plan offers "Load starter plan", and the plan
lands as a fresh week with its own days. `run-as olygym.app.dev cat files/opengym-state.json` is what
confirms the writes, including that a deleted week leaves `"weeks": []`.

## Generated files — do not edit by hand

```bash
node native/tools/assets.mjs                 # -> app/src/main/assets/{i18n/it.json, exercises-data.json}
node scripts/design/m3-scheme-kotlin.mjs     # -> app/src/main/java/olygym/app/ui/theme/Scheme.kt
```

The first reads `frontend/src/locales/it.js` and `frontend/src/lib/exercises-data.js`; the second
imports `scheme()` and `SEEDS` from `scripts/design/m3-scheme.mjs`, so the Kotlin palette cannot
drift from `m3.tokens.css`. Both outputs are committed, and both are checked by tests
(`AssetsTest`, `SchemeTest`) for exactly the drift a committed copy invites.

## Layout

```
app/src/main/java/olygym/app/
  OlyGymApp.kt          the only wiring: catalogue, then profile
  MainActivity.kt       edge-to-edge, theme from the profile, one Navigator
  data/                 Model, StateStore, Js (the JS-shaped JSON reads), Exercises/Catalogue/Assets
  lib/                  the ported domain helpers, one file per React helper: all nineteen of the
                        day-one closure, plus Format, I18nCore, Weeks, MigrateWeeks, PlanEdit,
                        Starter, Recovery, ChartMath, Activity, Progress, HistoryView and Settings
  rest/                 RestTimer, its foreground service, its receiver, its notification and
                        the rest mirror the app ticks from
  platform/             Sound (tones and haptics)
  ui/                   the shell (AppNavigator, tabs, rest bar, toast, sheet host), Home, Plan
                        and the week editor, the session screen, Stats, History, the Library and
                        its By-muscle explorer, Settings, the sheets, the shared controls and the
                        theme
app/src/main/assets/    the two generated assets
app/src/test/java/      654 JVM tests, one per ported behaviour
```

The helpers keep the React filenames and are one-to-one ports, so a diff against `frontend/src/lib`
is a straight read.

## Writing the profile

`StateStore.update { it }` takes the state object, returns the next one, and saves it:

- a `.writing` temp file, `fsync`, then a rename over the profile — a phone that dies mid-write
  leaves the old file or the new one, never half of either;
- the new state reaches the screens *before* the file is written, and the writes coalesce on
  `Dispatchers.IO`, because the workout screen writes on every set that gets ticked;
- the write is a merge over the state object that was read, so keys this app does not model survive
  the round trip. That is the whole reason two apps can take turns owning one file;
- `_ts` is stamped on every write, which is the field the React app compares when it decides whether
  its browser storage or this file is newer;
- a profile that failed to parse is **never** overwritten. The React app boots on its defaults and
  replaces it on the next save; here the file is left alone and the screen says why, because a
  training log is not something to reset to make an error message go away;
- `MainActivity.onStop()` flushes, the way the web app flushes on `visibilitychange`.

A screen does not mutate: it composes an edit from the combinators at the bottom of `data/Js.kt`
(`editObject`, `editArray`, `editAt`, `append`, `removeObjectAt`, `with`), so an index that has
gone since the screen was drawn changes nothing. `ui/UiState.kt` is the ephemeral half — the sheet
stack, the toast and the two countdowns — and it is what the session screen talks to.

## Porting rules

[native/PORTING.md](PORTING.md) is the contract for a helper port: one-to-one, the spec's cases
translated to JUnit 4, the session shapes left as `JsonObject` with the JS-shaped reads in
`data/Js.kt`, and the mutating JS helpers returning the new list. Read it before porting anything
else out of `frontend/src/lib`.
