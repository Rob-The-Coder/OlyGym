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
- The tab bar's Stats and Exercises tabs are placeholders, and Settings has no door yet. Their
  absence is stated on screen rather than silently doing nothing.
- The exercise picker is ported without its demo thumbnails (media is its own phase), without the
  "By muscle" explorer and the Library's shared filter sheet, and the exercise history sheet is not
  ported at all — it needs the charts. See the Phase 1c list in docs/PORT-TO-KOTLIN.md.
- `Catalogue` is module-level, so a test that reads through it — `defaultIncrement`, a name lookup —
  must not assume whether another test class has installed a catalogue yet: assert against the same
  function rather than against a value that depends on it.

## What is already ported

`Format`, `I18nCore`, `Weeks`, `MigrateWeeks` (Phase 0), the rest of the day-one closure plus
`WorkoutControls`, `ProgressionCopy`, `NumInput`, `Favourites`, `Equipment`, `LibraryFilter` and
`Usage` (Phase 1a/1b/1c). Read `WorkoutModel.kt` and
`WorkoutModelTest.kt` first if you are porting another one; they are the pattern, including how the
JS's null checks and `Number()` coercions are written.
