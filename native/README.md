# native/ — the Kotlin app

The React app in `frontend/` is still the shipping app. This is the port described in
[../docs/PORT-TO-KOTLIN.md](../docs/PORT-TO-KOTLIN.md), and it is at **Phase 1c**: the app opens on
Home, reads and rewrites the real `opengym-state.json`, starts the day's session through the
weigh-in, logs it set by set with the rest timer running on the lock screen, files it into the
training log — and builds a session as well as following one, since the exercise picker, the config
sheet, swap and freestyle are here too. The Library, Plan, History, Stats and Settings screens are
later phases.

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

# unit tests — 490 of them, no emulator, about twenty seconds
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

With no file the app shows the empty state and the path it looked in, which is the check that the
store's four outcomes are all reachable.

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
                        day-one closure, plus Format, I18nCore, Weeks and MigrateWeeks
  rest/                 RestTimer, its foreground service, its receiver and its notification
  rest/                 the rest mirror: the notification, its service, its receiver, its state
  platform/             Sound (tones and haptics)
  ui/                   the shell (AppNavigator, tabs, rest bar, toast, sheet host), Home, the
                        session screen, the sheets, the shared controls and the theme
app/src/main/assets/    the two generated assets
app/src/test/java/      490 JVM tests, one per ported behaviour
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
