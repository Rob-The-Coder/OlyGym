# Brazilian Portuguese exercise names

> **State after the OlyGym catalogue swap (2026-09-28).** The catalogue replaced every exercise id,
> so the generated packs in `frontend/src/instr/` and `frontend/src/exercise-names/` were removed
> (they only ever resolved against the retired dataset) and `INSTR_LANGS` / `EXERCISE_NAME_LANGS`
> list only `en` now — the catalogue's own `st` steps. The scripts and the sources below are kept as
> the starting point for a new translation pass over the 624 Catalyst exercises; both the generator
> input (the upstream dataset) and the id key space have to be redone first.

`pt-BR.json` is the editable source for the Brazilian Portuguese exercise-name
pack. It maps every built-in EXDB exercise ID to a Portuguese title. The app
combines that title with the unchanged English source at runtime:

```text
Elevação assistida das pernas deitada (assisted lying leg raise)
```

Custom exercise names are never translated. IDs, plan data, workout history,
imports and exports continue to use the canonical catalogue entries.

Generate the runtime pack with:

```sh
node scripts/build-pt-br-exercise-names.mjs
```

The initial translations were produced from the English EXDB titles with LLM
assistance and must not be described as reviewed by a native speaker unless a
named human reviewer completes that review. They are original translations and
were not copied from another Portuguese exercise dataset.
