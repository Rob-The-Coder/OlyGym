# M3 Expressive — the native app's redesign

This is the living record of the work that brings the Kotlin app in line with the React app and
finishes the expressive pass the port deliberately deferred (Phase 4a/4b in
[PORT-TO-KOTLIN.md](PORT-TO-KOTLIN.md)). It is updated **after every commit**, per workstream. The
sibling document `DESIGN.md` is the React app's design system; this one says what the Kotlin app
does about it and how each claim was proved.

Status: **in progress**. Nothing here is speculative — every divergence below was measured with the
two apps rendering the same exported profile.

---

## 1. The goal

The two apps are one product. The Kotlin app keeps its own runtime — offline, local file, no
server — but it must look and move like the React app: the same shape vocabulary, the same hero,
the same tab bar, the same segmented control, the same treatment of a completed set. On top of
that, it must finally be **accessible**: today a screen reader cannot name a single control.

## 2. Decisions taken with the user

| # | Decision |
|---|---|
| 1 | Bump to **material3 1.5.0-alpha14** as one revertible commit. It is the only way to reach MaterialExpressiveTheme, MotionScheme.expressive(), MaterialShapes and the expressive components; 1.4.0 keeps them Kotlin-internal. |
| 2 | Keep the shared **5-slot bottom bar with its centre Start**, re-tokenised — not a stock NavigationBar. |
| 3 | **Phone-first**: a reading-width cap and landscape sanity only; no two-pane layouts. |
| 4 | **Emphasized type stays weight-only**, as the React app has it. No sizes move. |
| 5 | **Adopt the React shape vocabulary wholesale** — pills, 20 px cards, 28 px sheets, 8 px outlined chips, a raised 16 dp-corner Start FAB. |
| 6 | **Home gets all three** parity items: the primary-container hero, the body-weight curve, the Welcome card. |
| 7 | **Completed sets**: the same intent, but with a token tone that holds at least 4.5:1 rather than a flat 0.45 opacity. The React app's own 45 % is proposed for the same correction. |
| 8 | Native before/after captures are produced on the emulator. |

## 3. The divergence table — this is the specification

Measured with both apps rendering one exported React profile (3 weeks, 9 workouts, 9 body-weight
entries, an active session, kg, dark, RIR).

| | React (target) | Kotlin (was) | Reference |
|---|---|---|---|
| Buttons, all sizes | full pill | 12 / 8 / 7 dp corners | `frontend/src/m3.components.css` 28-56 vs `Ui.kt` 375-379 |
| Card, tile, section | 20 px | 14 dp | `m3.components.css` 87, 405 vs `Shape.kt` 19 |
| Bottom sheet | 28 px top | 22 dp top | `frontend/src/index.css` 1059 vs `Shape.kt` 22 |
| Chip | 8 px, 1 px outline, tonal when on | filled pill, no outline | `m3.components.css` 64-72 vs `Ui.kt` 718-736 |
| Segmented | grey track + sliding accent pill, no checkmark | SegmentedButton **with a checkmark** | WeekEdit, Stats, Settings |
| Search field | full pill | 12 dp | `m3.components.css` 725 vs `Ui.kt` 769 |
| Home hero | primary-container, 28 px, on-container ink, uppercase overline, week strip inside | surfaceContainerLow, 14 dp, sentence case | `m3.components.css` 803-834 vs `HomeScreen.kt` 174-195 |
| Home body weight | LineChart with the goal line | number, delta, date | `frontend/src/views/Home.jsx` 167 |
| Home empty state | Welcome card + Load starter plan | absent | `Home.jsx` 119-130 |
| Start button | raised 20 dp, 16 dp corner, **orange + glow while a session runs**, "Workout" on the session screen | flat 46 dp circle in the bar, always primary, always "Resume" | `m3.components.css` 147-149, `index.css` 370-371 vs `AppNavigator.kt` 226-269 |
| Tab indicator | 24 %-accent pill behind the selected item, scales .72 to 1 on a spring | none; selected ink is primary | `m3.components.css` 110-114, 471-472 |
| Completed set | row recedes **and** the set number becomes an accent disc | alpha 0.55, number stays a grey digit | `index.css` 833-834 vs `ExerciseBlock.kt` 556-571 |

**Withdrawn finding.** "Two section-label systems coexist" was reported as a defect and is not one:
the React app has the same two-tier system (uppercase overline for a group, sentence-case label for
a Settings section). Settings stays as it is.

## 4. The accessibility baseline and the result, measured on the device

The first baseline was measured wrongly — it read only the clickable node's *own* attributes, and
Compose puts a row's or a tab's label on a **child**. The corrected measurement asks whether a
clickable node has a name anywhere in its subtree, and every number below comes from that. The
wrong metric is recorded here because the first version of this table used it, and it over-counted
(Home read 21/21 when it was really 3/21).

| Screen | unnamed clickables, before | after WS2 |
|---|---|---|
| Home | 3 of 21 | **0 of 20** |
| Stats | 9 of 15 | **0 of 14** |
| Workout | 42 of 69 | **0 of 68** |
| Plan | — | **0 of 12** |
| Library | — | **0 of 15** |

Structural cause: `GlyphIcon` was a Canvas with no way to be named, `IconButton` had no label
parameter at all, the Stepper's `description` argument was declared and never used, and the heatmap
day cells, the body-map figures, the media chip, the search field and the sheet dismissal were
never named either. Trees: `oly-previews/native-m3e/ws2-before/` and `ws2/`.

## 5. Workstreams

| WS | What | State |
|---|---|---|
| WS0 | The expressive theme on material3 1.5.0-alpha14 | **landed** — see the progress log |
| WS2 | Accessibility floor, reduced motion, guard test | **landed** — see the progress log |
| WS6 | The 64 missing Italian keys, asset regen, guard test | not started |
| WS1 | The shape vocabulary | not started |
| WS3 | Checkbox, segmented, tab bar, snackbar, action stack, inert tabs | not started |
| WS4 | Home parity | not started |
| WS5 | Motion on one scheme | not started |
| WS7 | Adaptive-lite reading width | not started |
| WS8 | Documentation | in progress (this file) |

Out of scope, deliberately: two-pane and tablet layouts, Wear OS, iOS, new features, a palette
change, dependencies beyond the material3 version line, and any change to training logic or the
state-file format.

## 6. How this is verified

**Build and tests** (offline; `~/.gradle` is not writable in this environment):

    cd native
    # one-off: stub the cached distribution into the gitignored user home
    mkdir -p .gradle-home/wrapper/dists/gradle-9.3.1-bin
    cp -r "$HOME/.gradle/wrapper/dists/gradle-9.3.1-bin/"* .gradle-home/wrapper/dists/gradle-9.3.1-bin/

    GRADLE_USER_HOME=$PWD/.gradle-home GRADLE_RO_DEP_CACHE=$HOME/.gradle/caches \
      ./gradlew :app:testDebugUnitTest --offline -Pkotlin.compiler.execution.strategy=in-process

**Device captures.** The emulator can only be booted with a one-shot `danger-full-access`
escalation (it writes to `~/.android`); once it is up, everything else is unescalated:

    emulator -avd Pixel_10 -no-snapshot -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect
    adb install -r native/app/build/outputs/apk/debug/app-debug.apk
    adb shell run-as olygym.app.dev mkdir -p files
    adb push /tmp/oly-state.json /data/local/tmp/state.json
    adb shell run-as olygym.app.dev cp /data/local/tmp/state.json files/opengym-state.json
    adb shell pm grant olygym.app.dev android.permission.POST_NOTIFICATIONS
    adb shell am force-stop olygym.app.dev
    adb shell am start -n olygym.app.dev/olygym.app.MainActivity
    adb exec-out screencap -p > shot.png
    adb shell uiautomator dump /sdcard/u.xml && adb exec-out cat /sdcard/u.xml > tree.xml

Tab centres on this device (1080x2424): Home 101, Plan 305, Start 540, Stats 774, Exercises 978, y 2295.

**React captures**, at 390x844, through the DevTools protocol: the dev server plus headless Chrome
on 9222 and `node oly-previews/shot.mjs`. The profile is exported straight out of the running React
store so both apps render the identical state (`/tmp/oly-state.json`).

**Evidence rules.** A before/after capture, an identical file hash, or a uiautomator dump — never
"it looks right". Measurement is quoted, not asserted.

## 7. Progress log

### WS2 — the accessibility floor · landed

**What changed.** A glyph on a Canvas can be named (`GlyphIcon.contentDescription`, null by
default so decoration stays decoration). `IconButton` takes a **required** `label`, so the compiler
lists every unnamed control instead of a reviewer hoping to spot one; all 36 call sites carry a
translated string. `Button`, `Tile` and `ListRow` declare their role and disabled state; `Chip` is
toggleable and says whether it is on, except where it is a one-shot action; `Check` is a checkbox
with a state; the stepper arrows wire up the `description` argument that had been dead since the
port, and the two arrows of every stepper are named *and translated* now. The tab bar is a
`selectableGroup` of `Role.Tab` items, the toast is a polite live region, the centre Start button is
named, a sheet offers a dismissal action because scrim/drag/back are not available to everyone, and
a plan row offers Delete as an action because a swipe is not either. Five more were found only by
reading the device's own tree: the heatmap's day cells, the body-map figures, the workout media
chip, the search field's `BasicTextField`, and the search field's placeholder (which is now hidden
from a screen reader so the field is not read twice).

Three animations are the app's own rather than Material's, and all three now answer the system's
reduced-motion request through `ui/theme/ReduceMotion.kt`: the wave's drift, the route fade, and the
timer flash. The helper reads the platform's animator scales because `MotionDurationScale` is only
reachable from a coroutine, not from a composable — which is where the decision has to be made.

**Evidence.** The table in §4: Home 3 to 0, Stats 9 to 0, Workout 42 to 0, Plan 0 of 12, Library 0
of 15. 728 JVM tests (724 plus four guards), 0 failures. `AccessibilityTest` reads the sources and
asserts that every icon button's name is translated, that every set checkbox has one, that no
stepper arrow is named in bare English, and that the three app-owned animations still consult
`reduceMotion()`. It earned its keep immediately: it failed on its first run over a stepper.

**Deliberate departures, recorded rather than hidden.**

- **No `paneTitle` on a sheet.** The `Sheet` model carries no title, so naming a sheet would mean
  adding one to the model and to every sheet function. The dismissal action — the half that is
  actually blocking — is what landed.
- **The set table's tiny arrows stay tiny.** Two 26x40dp buttons live inside one set row, and
  DESIGN.md keeps a set row's density on purpose: a taller row costs a scroll mid-set. They are
  named now; the geometry is an exception with a reason, not an oversight.
- **The body map is one named figure.** Picking an individual muscle by touch is a sighted
  affordance; the ranked rows under the map carry the same information and are the accessible path.


### WS0 — the expressive theme · landed

**What changed.** `native/app/build.gradle` declares material3 **1.5.0-alpha14** explicitly beside
the BOM; `ui/theme/Theme.kt` swaps `MaterialTheme` for `MaterialExpressiveTheme` with
`MotionScheme.expressive()` and hands over the app's own ColorScheme, Shapes and Typography.

**Why the version, not the BOM.** `dependencyInsight` on debugRuntimeClasspath reports
`androidx.compose.material3:material3:1.5.0-alpha14`, "... by conflict resolution: between
versions 1.5.0-alpha14, 1.4.0 and 1.3.1". The BOM is untouched, so ui/foundation stay at 1.11.4.

**Evidence — the change is meant to move nothing, and it did not.**

| Screen | pixels changed (status bar cropped) |
|---|---|
| Home | **0** / 2,488,320 |
| Stats | **0** / 2,488,320 |
| Plan | **0** / 2,488,320 |
| Workout | 1.09 % after aligning a 14 px auto-scroll offset; the residual is confined to the rows that offset exposes (y 2089-2186) plus the live elapsed clock |

Control: two captures of the **same** build are pixel-identical in the card region (0 changed), so
the Workout variance is the clock and the app's own keep-the-acting-movement-in-view scroll, not the
theme. Captures: `oly-previews/native-m3e/ws0-before/` and `ws0-after/`.

**Tests.** 724 JVM tests, 0 failures, 0 errors — unchanged from the baseline, which is the point.
`assembleDebug` succeeds offline in ~44 s.

**Fallback**, if the alpha does not hold: revert this one commit and hand-port the expressive
primitives as the React app does in CSS. WS1, WS2 and WS6 are unaffected either way.
