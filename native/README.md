# native/ — the Kotlin app

The React app in `frontend/` is still the shipping app. This is the port described in
[../docs/PORT-TO-KOTLIN.md](../docs/PORT-TO-KOTLIN.md), and it is at **Phase 6a**: the app opens on
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
reads the coach's Excel or CSV week — whatever file the platform's picker can reach, Drive
included — and reviews every row of it before any of it lands in the plan, reminds you on the days
that have a routine, keeps a dated copy of it all in the Documents folder, keeps the meets apart
from the training log with their attempts, totals and weight categories, and is
configured (Settings — the preferences, the appearance and the data, with backup export/import
through the platform's own file picker), updates itself from its own releases, and moves the way the
design system says it should — the Emphasized weights at the web's own call sites, the wavy progress
bars, the route fade and the Start button's press — and shows the demo poster in the exercise header
itself, with the two Settings rows that decide what it shows and the display kept awake while a
session runs, and shows a month at a glance — the calendar behind Home's tiles, which is also the door
to a past day and to a day holding more than one session. **Phase 2 is complete, and phases 3 to 6 are
written**; the cutover, and the pieces each phase deliberately left out, are what remain.

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

# unit tests — 717 of them, no emulator, about twenty seconds
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

Phase 6a's check is the calendar. Home's *Streak* tile opened Ottobre 2026 with the month's own line
("4 allenamenti · 4h 0m · 4.000 kg"), a dot under 2, 5 and 7 (trained) and a muted one under 9
(planned), today ringed — and every tap on a day did what the web's rule says. Day 7, which holds two
sessions, opened "mer 7 ott · 2 allenamenti" with Clean & Jerk Day (4.800 kg) and Squat & Pull Day
(3.600 kg); day 8, past and empty, opened the backfill already dated gio 8 ott; day 2, one session,
opened Back Squat Day's own detail; day 9, still to come, closed the sheet and landed on the plan.
The month's arrows step a month either way, and September — which has nothing — says "Nessun
allenamento questo mese" and offers "Torna a questo mese". The pictures in
`oly-previews/native-phase6a/` are those steps.
Phase 5a's check is the session's picture and the screen that stays on. Starting today's session put
the demo poster in the exercise header as an 88dp chip beside "back squat", with the play mark over
it, the tags under the name and the set table exactly where it was; switching Settings' "Immagini
degli esercizi" to Nascosta — the file then read `"gifSize":"off"` — closed the header up on the
next visit, the picture gone and the name starting at the margin. Settings' workout section carries
all three new rows: Mantieni lo schermo acceso (on), Immagini degli esercizi (Al tocco / Nascosta)
and Video dimostrativi (Al tocco). The wake lock is the one to read in `dumpsys power`: with the
session running, `mWakeLockSummary=0x23` and a `SCREEN_BRIGHT_WAKE_LOCK ... ws=WorkSource{…
olygym.app.dev}`; with the same profile and no `active`, `mWakeLockSummary=0x1` and no such lock at
all — the app the front activity in both cases. The pictures in `oly-previews/native-phase5a/` are
those steps.
Phase 4a's check is the wave. Starting today's session and ticking one set put the two bars on
screen at once: the workout header read "0:44 · 1/8 serie" over a wavy fill covering its one eighth,
and the rest bar read "1:29 · Serie 1 di 5" over a wave covering nearly all of it. Two frames 0.7s
apart show the humps in different places — the drift is real, not a still sine — with the countdown
between them moving 1:29 to 1:28. The pictures in `oly-previews/native-phase4a/` are those frames.
Phase 4b's check is the weight and the fade, against the phase-4a build. The five call sites the web
gives the Emphasized weight settle on the token's value for their role — 500 where the app bar's title
was 700 and a sheet's title, a tile's value and Home's body weight were 600, and 700 where the weekday
on a Plan day row was 500. Measured as the share of lit pixels in the same rectangle, that is "OlyGym"
32.0% to 27.5%, "Piano" 30.5% to 26.6%, the body weight 23.2% to 21.7%, "Registra peso corporeo" 24.1%
to 22.8% — and the weekday the other way, 7.8% to 9.1%. The places whose weight deliberately does not
move (Home's hero title at 600, a card's heading at 600) are the control. The route fade is 400ms on
emphasized-decelerate: a frame grabbed as a tab is tapped has the arriving screen part-transparent —
its title's brightest pixel 191 against 226 settled, and 183 against 210 half-way through a pass
slowed to 3x with `animator_duration_scale`. The pictures in `oly-previews/native-phase4b/` are the
before/after pairs and the fade frames.
Phase 3g's check is the updater, and it has not been run: the repository has no `native-v…` release
for it to find, so there is nothing to download and nothing to install. It needs one release with the
APK attached — GitHub publishes the SHA-256 for every asset by itself — before the walkthrough can be
written. The check, the release parsing and the hash are unit-tested.
Phase 3f's check is the snapshot. Settings' Dati section has "Backup automatico alle modifiche";
turning it on wrote `"autoBackup": true` to the file, and finishing a workout (started from Home,
ticked off with nothing logged, "Termina comunque") left `Documents/opengym-backup-2026-10-07.json`
— 53 KB, and the app holds no storage permission at all, so MediaStore is what put it there. The file
parses as the profile: 12 workouts, the last dated 2026-10-07, no `active`, 2 weeks, kg, Italian.
Finishing a second workout the same day overwrote the same file (53,742 bytes, still one entry) rather
than leaving a second copy. The pictures in `oly-previews/native-phase3f/` are those steps.
Phase 3e's check is the reminder. Settings' Notifiche section has "Promemoria giorno di
allenamento"; turning it on writes `reminder: {on: "true", time: "08:00"}` in the file and arms one
alarm, and because today's 08:00 had gone the alarm named the next planned day — `origWhen=2026-10-09
08:00` (Friday; Thursday is a rest day). Setting the time to 16:10 re-armed it for the same day, and
at 16:11 the shade carried "Giorno di allenamento · Oggi è in programma Clean & Jerk Day — forza!",
with the alarm already re-armed for Friday. The channel is `workout_reminder`, "Promemoria giorno di
allenamento", on the system's own notification sound. The pictures in `oly-previews/native-phase3e/`
are those steps.
Phase 3d's check is the coach's file. Settings' Data row "Importa la scheda del coach" opens the
platform's picker; a CSV pushed into Downloads — a BOM, CRLF, a blank line and an exercise called
"Bench Press, Close Grip" in it — reads as 2 giorni · 4 esercizi · 1 da controllare: the comma survived
because the name and the cue arrived in the right cells ("bench press · 3 × 3 · 60 kg" with
"Bench Press, Close Grip · fermo al petto" under it) rather than every column shifted by one, and the
sheet is named after the file ("GIORNO 1 · COACH-WEEK"). Picking the phase-3a `coach-week.xlsx` from
the same folder still gives its own review — 3 giorni · 8 esercizi · 1 nuovi · 1 da controllare, the
complex intact. The picker's drawer offers Recent, Documents, Downloads and the SD card; Drive is not
in it on this emulator because the Drive app there has no account signed in. The pictures in
`oly-previews/native-phase3d/` are those steps.
Phase 3b's check is the meets. Home carries the coming one ("Coppa Italia · dom 22 nov 2026 · Milano ·
tra 46 giorni") and opens Competitions: the best tiles (100 / 120 / 220 kg), Prossime and Passate, and
a row per meet — a trophy while it is ahead, the medal once there is a result. A past meet's detail
shows its class, weigh-in and placing, the three tiles, and each lift's three attempts with their
verdict. "+" opens the form: three snatch and three clean & jerk attempts, each a weight and
Valida/Nulla, and the tiles at the foot read the draft as it is typed — a snatch-only draft shows the
snatch and no total, which is the rule. Settings' Gara section opens Categorie di peso: the
Uomo/Donna lists, edited in place, and the file then holds the added "73". The pictures in
`oly-previews/native-phase3b/` are each of those steps.
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
  platform/             Sound (tones and haptics), ReminderAlarm (the workout-day
                        reminder's alarm and receiver), AutoBackup (the dated snapshot
                        in Documents) and Updater (the updater's download and installer)
  ui/                   the shell (AppNavigator, tabs, rest bar, toast, sheet host), Home, Plan
                        and the week editor, the session screen, Stats, History, the Library and
                        its By-muscle explorer, Settings, the sheets, the shared controls and the
                        theme
app/src/main/assets/    the two generated assets
app/src/test/java/      717 JVM tests, one per ported behaviour
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
