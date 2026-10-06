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
- Keep the comments that explain a *decision*, including the issue numbers. Drop the ones that
  narrate the obvious.
- Do not add an abstraction the JS does not have. No interfaces with one implementation, no
  options object where a parameter will do.

## JSON, not data classes

The session shapes (a set row, a workout entry, a session) are `JsonObject`, read through the
JS-shaped accessors in `data/Js.kt`: `asObj`, `asArr`, `asStr`, `asBool`, `asNum`,
`present`, and the object-level `obj/arr/str/num/int/bool`. Build them with `js("w" to 100,
"r" to 5)`, which drops null values the way the JS's conditional spreads do.

The reason is fidelity, not convenience. The JS is loose on purpose — `Number(x) || 0`,
`typeof x === 'string'`, `x != null` (which is false for undefined *and* null), `delete e.sg` —
and the state file is hand-edited, imported, and written by builds older and newer than this one. A
data class would have to pick a type for every field, change behaviour wherever the file disagrees,
and re-serialize every key it did not model. So the shapes stay JSON and `Js.kt` carries the JS
semantics: `asNum` is `Number(x)` and returns null where that gives NaN (so `x.asNum() ?: 0.0` is
`Number(x) || 0`), `asBool` is `x === true` (the string "true" is not true), and `asObj` /
`asArr` are `objectOf` / `Array.isArray`.

The one thing they do not reproduce is absent-vs-null: both read as null. Every site that cared
used a loose check, so the two behave the same.

## Mutation

Several helpers mutate their argument in place (`cleanupSg`, `pairAdjacent`, `unpairSuperset`,
`cascadeWeight`, `insertWarmupRow`, `removeRowAt`, `rerampWarmups`, `moveSupersetUnit`).
`JsonObject` is immutable, so the port returns the new list and the caller uses the return value.
The test then asserts on the return value where the vitest case asserts on the mutated input — that
is the one place the translation is not literal, and it is noted at the function.

## Tests

JUnit 4 only (`org.junit.Test`, `org.junit.Assert.*`); there is no kotlin-test or kotest in the
offline cache. Names in backticks. One JUnit test per `it()`, in the same order.

```bash
cd native
GRADLE_USER_HOME=$PWD/.gradle-home ANDROID_USER_HOME=$PWD/.gradle-home/android \
  ./gradlew :app:testDebugUnitTest --offline -Pkotlin.compiler.execution.strategy=in-process
```

Add `--tests '*NameTest*'` to run one file. A build may wait on a lock if another one is running;
that is expected.

## Known debt

Several ports carry their own private JS-truthiness helpers (`truthy`, `jsString`,
`arrayOfValue`) because `Js.kt` deliberately exposes the *stricter* `asBool` (`=== true`) and
`asNum` (`Number`). The copies are small and private, but they are copies: fold them into `Js.kt`
as `JsonElement?.truthy()` and `JsonElement?.jsString()` the next time a port needs one, and note
that `SupersetFlow.kt` currently flattens a JS truthiness check to `=== true` where
`History.kt` keeps the distinction. Nothing in the shipped data tells those two apart — a stored
`done` is a real boolean — but imported data could.

## What is already ported

`Format`, `I18nCore`, `Weeks`, `MigrateWeeks` (Phase 0) and `WorkoutModel` (Phase 1a) — read
`WorkoutModel.kt` and `WorkoutModelTest.kt` first if you are porting another one; they are the
pattern, including how the JS's null checks and `Number()` coercions are written.
