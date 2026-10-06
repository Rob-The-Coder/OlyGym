# native/ — the Kotlin app

The React app in `frontend/` is still the shipping app. This is the port described in
[../docs/PORT-TO-KOTLIN.md](../docs/PORT-TO-KOTLIN.md), and it is at **Phase 0**: the app boots,
reads the real `opengym-state.json`, and lists the plan it finds. It writes nothing.

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

# unit tests — 61 of them, no emulator, a couple of seconds
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
  data/                 Model, StateStore, Exercises/Catalogue/Assets
  lib/                  Format, I18nCore, Weeks, MigrateWeeks — the ported domain helpers
  ui/                   AppScreen, AppNavigator, plan/PlanListScreen, theme/
app/src/main/assets/    the two generated assets
app/src/test/java/      61 JVM tests, one per ported behaviour
```

The helpers keep the React filenames and are one-to-one ports, so a diff against `frontend/src/lib`
is a straight read.
