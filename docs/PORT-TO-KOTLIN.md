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

