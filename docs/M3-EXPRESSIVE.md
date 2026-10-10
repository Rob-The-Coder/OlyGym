# M3 Expressive — the native app's redesign

This is the living record of the work that brings the Kotlin app in line with the React app and
finishes the expressive pass the port deliberately deferred (Phase 4a/4b in
[PORT-TO-KOTLIN.md](PORT-TO-KOTLIN.md)). It is updated **after every commit**, per workstream. The
sibling document `DESIGN.md` is the React app's design system; this one says what the Kotlin app
does about it and how each claim was proved.

Status: **six commits in, and usable**. Nothing here is speculative — every divergence below was
measured with the two apps rendering the same exported profile.

| | |
|---|---|
| Landed | WS0 the expressive theme · WS2 the accessibility floor · WS6 the Italian strings · WS1 the shape vocabulary · WS4 Home's hero · WS3's inert tab bar |
| Open | WS3's remaining two (segmented, snackbar) · WS4's body-weight curve and Welcome card |
| Verified | 730 JVM tests and 1144 web tests, 0 failures, at every commit; the debug APK builds offline; every visual claim has a capture in `oly-previews/native-m3e/`; every accessibility claim has a uiautomator dump |

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
| WS6 | The missing Italian keys, asset regen, guard test | **landed** — see the progress log |
| WS1 | The shape vocabulary | **landed** — see the progress log |
| WS3 | Checkbox, segmented, tab bar, snackbar, action stack | **inert tabs fixed**; the rest open |
| WS4 | Home parity | **hero landed**; curve and welcome card open |
| WS5 | Motion on one scheme | **landed** — see the progress log |
| WS7 | Adaptive-lite reading width | **landed** — see the progress log |
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

### WS3 — the session screen's action stack · landed

**What changed.** The session ended in six buttons at nearly the same weight — Add exercise, Swap
exercise, Move up, Move down, Add session note, Finish workout — so the screen's own hierarchy said
nothing about which one you press between sets. The four that act on the *session* moved into the
header's ⋯, which already holds Rename and Layout; Add exercise stays a full-width pill next to the
Prev/Next pair; Finish is the one primary action. Nothing was removed and the exercise's own actions
stay on the exercise's own ⋯.

**Evidence.** `oly-previews/native-m3e/ws3-action-stack.png` — the bottom of the screen is Prev,
Next, Add exercise, Finish. The device's tree reports **one** of those labels in the lower half where
it used to report five; 0 unnamed of 55 clickable nodes. 730 JVM tests, 0 failures.

### WS3 — a completed set says so · landed (three items of WS3 still open)

**What changed.** The web marks a done set twice: `.setrow.done .n` turns the row's number into an
accent disc, and `.setrow.done` drops the whole row to 45% opacity. The disc landed; the blanket
opacity did not, per decision 7.

**Measured, not asserted** — WCAG contrast of the set row's text on its own card:

| Row treatment | Contrast |
|---|---|
| full opacity (now) | **13.30:1** |
| this app's old `alpha(0.55)` | 4.87:1 |
| the web's `opacity: .45` | **3.74:1** (3.76:1 on the web's own surface) |

So the web's treatment is under the 4.5:1 floor for body text, and this app's older one only just
cleared it. The intent is kept — the row recedes and the number becomes an accent disc — and the
numbers you are actually reading between sets stay legible. `SetNumber` is shared by the per-movement
table and the complex's rounds table so the two cannot drift.

**Evidence.** `oly-previews/native-m3e/ws3-done-sets.png`; the workout screen is still 0 unnamed of
68 clickable nodes. 730 JVM tests, 0 failures.

**Still open in WS3**: the segmented control becoming the web's sliding pill, and the toast
becoming a real `Snackbar`.

### WS7 — the reading-width cap · landed

**What changed.** The shell caps its content at 600dp and centres it, which is the whole of the
adaptive work this app needs: a phone in one hand is the target, and on a tablet or in landscape the
same layout stretched edge to edge reads as a phone screen photographed onto a bigger one.

**Evidence — and the point is that nothing changed here.** The emulator is 411dp wide, so the cap
cannot bind. Home captured before and after is **0 changed pixels of 2,488,320** with the status bar
cropped out. The cap is a safety net for a window this device does not have, and it is proved not to
touch the one it does.

### WS5 — motion on one scheme · landed

**What changed.** `ui/theme/MotionSpecs.kt` exposes the scheme's own specs — `spatialSpec`,
`fastSpatialSpec`, `effectsSpec` — and the three animations the app draws itself now use them
instead of this codebase's private béziers: the route fade, the Start press, and the workout's media
expand. `Motion.kt`'s curves stay as the named token table and the fallback.

**The wave is Material's now.** `WaveProgress` was the app's own sine, drawn to match the web's
masked SVG. It is a call to `LinearWavyProgressIndicator` — one fewer thing the app owns, on the
scheme's motion — with the same 6px stroke the web settled on, and a plain `LinearProgressIndicator`
under a reduced-motion request so the bar still fills and still measures without flowing.

**The timer flash is fixed.** It held the opposite theme for 2.4s in one step, which is a
full-screen change in luminance long enough to read as a fault. It is the web's four steps at 600ms
now — opposite, back, opposite, back — and nothing at all under reduced motion.

**Evidence.** Built and tested on the device; `oly-previews/native-m3e/ws5-workout-wave.png` shows
the indicator's wave in the session header. 730 JVM tests, 0 failures.

### WS3 — the tab bar does something · landed (the rest of WS3 is open)

**The bug.** The bar is drawn over a pushed screen, and `onTab` only set the selected index —
state nothing reads until the stack returns to the host. So with a session on screen, or any screen
opened from a sheet, the four destinations were **inert**. It is why the first attempt at a Plan
capture came back as a second Workout capture. On the web the same tap navigates and the session
keeps running.

**The fix.** A tab tap pops the stack back to the host before it selects, so the bar means what it
looks like it means.

**Evidence, from the device's own tree.** With the session open, the tree lists the session title
*and* the bar's labels. After tapping Stats it lists Stats and no longer the session title. Before
the fix the session title stayed.

**Still open in WS3**: the completed-set treatment (accent number disc plus a token tone that keeps
4.5:1, rather than the web's flat 45% opacity), the segmented control becoming the web's sliding
pill, the toast becoming a real `Snackbar`, the session screen's six stacked action buttons
collapsing into the ⋯ sheet the exercise rows already use, and the Complex card's near-empty header.

### WS4 — Home parity, the hero · landed (curve and welcome card still open)

**What changed.** The hero is the web's `.hero`: a **primary-container** surface at the **xl**
radius, with every child taking `on-primary-container` — the overline uppercased at 78% of it, the
day icon in the web's 44dp disc at 14% of it, the title and subtitle at full and 82%, and the week
strip inheriting the same ink behind the same hairlines. The chip icons (the two week arrows, the
reset) carry that ink too. Nothing is hardcoded: the whole block follows `primaryContainer` and
`onPrimaryContainer`, so it moves with the accent.

**Evidence.** `oly-previews/native-m3e/ws1/01-home-hero.png` against the React Home of the same
profile — the two are the same design now. Home's accessibility tree is still 0 unnamed of 20.

**Still open in WS4**, and deliberately not bundled here:

- The **body-weight curve** the web draws on Home. That is a real port (the LineChart exists in
  Compose already, in Stats) rather than a treatment, and it is worth its own change.
- The **Welcome card** for the empty state ("Set up your weekly routine — or load a ready-made
  starter plan"), which only appears with no weeks at all.

### WS1 — the shape vocabulary · landed

**What changed.** `CardShape` 14 to 20 (what `.card`, `.sect-b` and `.tile` are drawn in);
`SheetShape` 22 to 28 (what `.sheet` is); every `Button` size is a pill, not 12/8/7dp corners;
`SearchField` is a pill; `Chip` is the web's filter chip — transparent with a 1px outline, a tonal
fill and no outline when on, and the ink brightening from `onSurfaceVariant` to `onSurface` rather
than the accent moving; and the bottom bar's Start control is a 16dp-corner FAB (`FabShape`)
instead of a circle.

**Evidence.** `oly-previews/native-m3e/ws1/` against the React captures of the same profile. The
accessibility tree was re-read after the change because shape work moves hit targets: Home is still
0 unnamed of 20.

**Two things found and handled honestly.**

- **The lift is not there.** The web takes the Start disc out of the flow and lifts it 20px above
  the bar. The bar here is a Material `Surface`, which clips its content, so offsetting the disc
  cut it off at the bar's own edge — visible in the first capture of this pass. The shape and size
  landed; the lift needs the Start control drawn *outside* the Surface rather than inside its row,
  which is a layout change of its own.
- **The pills cost horizontal room.** `Button` keeps its padding, so a pill of the same content is
  wider than the 12dp rectangle it replaced; the three Home tiles and the two-row button groups were
  checked on the device for overflow and none clipped.

### WS6 — the missing Italian strings · landed

**What changed.** 80 entries added to `frontend/src/locales/it.js`: the 78 the comparison found, plus
`weeks` and `sessions`, which the comparison's own collation had hidden and the new guard test
caught on its first run. `native/tools/assets.mjs` regenerated the native asset from it.

The React app reads the same file, so **the web's Italian UI is fixed by the same change** — and the
web suite was run to prove it: 107 files, 1144 tests, green.

**Evidence.** `I18nCoverageTest` reads every `t("…")` literal in the Kotlin sources and asserts each
is a key in the shipped asset, then spot-checks ten of the ones that were leaking for a value that is
not just the key pasted back. `AssetsTest`'s pinned entry count moved 1420 to 1496 — it failed first,
which is the guard working. 730 JVM tests, 0 failures.

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
