# AGENTS.md

Everything an agent needs to work in this repository. Assume nothing repo-specific is
known elsewhere: fresh-checkout setup, dev server, tests, build, "lint", and the project's
hard rules are all in this file. For the architecture deep-dive, see `CLAUDE.md` and `docs/`.

## What this is

OlyGym is a **local-only** Olympic weightlifting tracker — a personal fork of
[openGym](https://github.com/DuarteSantos8/openGym) pointed at the Catalyst Athletics
catalogue. One user, one device: no account, no login, no sync, no push, no telemetry and
**no backend of any kind**. It ships as an installable PWA and as a Capacitor-wrapped
Android app built from the same React source. License: AGPL-3.0-or-later.

## Layout

```
frontend/              React 19 + Vite app — all JavaScript lives here
  src/views/           one file per screen (Home, Plan, WeekEdit, Workout, Stats, History,
                       Library, Muscles, Competitions, Settings), routed from App.jsx
  src/components/      shared UI (charts, modals, timers, body map, sheets)
  src/store/           useStore.js — single Zustand store persisted to localStorage
                       (gym_state_v1); useUI.js — ephemeral UI state (sheets, modals)
  src/lib/             pure, framework-free domain logic; each file has a *.test.js beside it
  src/locales/it.js    Italian pack (English is the source language and has no file)
  scripts/             check-locales.mjs, check-source-strings.mjs, bundle-size.mjs,
                       fatigue-monotonic-probe.mjs
  android/             Capacitor shell for the Android app (Java plugins under
                       app/src/main/java/olygym/app/)
docs/                  MOBILE.md, DATA_IMPORTS.md, COMBINE_ROUTINES.md, LIST_VIEW.md
scripts/oly-catalogue/ generator for the Catalyst exercise catalogue (624 lifts)
factory/               agentic dev workflow: backlog.md, dashboard.html, jobs/ (gitignored
                       runtime state, one folder per live job), old_jobs/ (finished jobs)
.dsh/                  the factory's agent definitions and skill (committed)
```

There is no backend, no `api/`, no Docker, no iOS target and no TypeScript. Nothing here
is deployed as a server.

## Toolchain

- **npm only** — lockfile v3 at `frontend/package-lock.json`; no monorepo/workspace tooling
  and no root `package.json`. Every command below runs from `frontend/` unless stated.
- **Node** `^20.19.0 || >=22.12.0` (Vite 8's requirement; any current Node 22/24 works).
  Developed on Node 24.
- **No `.env` is needed** for web development — there are no API keys, no DB, no services.

## Fresh checkout setup

```bash
git clone <this repo>
cd OlyGym/frontend
npm ci                 # installs exactly what the lockfile pins (npm install also works)
npm test               # sanity check — the suite should pass in well under a minute
```

That is the whole setup. If `npm test` is green, you are ready to develop.

## Dev server

```bash
cd frontend
npm run dev                              # hot reload, http://localhost:5173

npm run dev -- --port 4321 --strictPort  # run on a GIVEN port (fails rather than drifting)
npm run dev -- --host --port 4321        # also expose on the LAN (testing the PWA on a phone)
npm run preview                          # serve a previously built dist/ instead
```

In a headless environment a quick health check is:
`curl -sf http://localhost:4321/ >/dev/null && echo up`. To actually **look at** the app,
use agent-browser (next section) — never assume the UI works without rendering it.

### Viewing the app: agent-browser

Agents have no GUI browser — view, click through and screenshot the app with the
`agent-browser` CLI (headless Chromium). If it is not installed:
`npm install -g agent-browser && agent-browser install`.

```bash
agent-browser open http://localhost:4321/         # navigate to the dev server
agent-browser snapshot -i                         # interactive elements with @refs (@e1, @e2, ...)
agent-browser click @e1                           # interact via refs from the last snapshot
agent-browser fill @e2 "60"                       # fill a field (clears existing text first)
agent-browser screenshot oly-previews/home.png    # visual check (--full = whole page);
                                                  # oly-previews/ is the gitignored folder
                                                  # this repo uses for UI mockups/screenshots
agent-browser errors                              # page errors after interacting
agent-browser console                             # browser console output
agent-browser close                               # when done with a session
```

Habits that matter in this repo:

- **Set a phone viewport first** — the app is mobile-first:
  `agent-browser set viewport 390 844` (or `agent-browser set device "iPhone 14"`).
  A desktop-width snapshot will mislead you about layout.
- **Refs go stale after navigation or big DOM changes** — re-run `snapshot -i` before the
  next click.
- **State persists in localStorage** (`gym_state_v1`). For a fresh-install run:
  `agent-browser storage local clear`, then `agent-browser reload` — that brings up the
  first-run/starter flow. Inspect saved state with
  `agent-browser storage local gym_state_v1`.
- This is how the CONTRIBUTING rule "click through the flow you touched (and the workout
  flow) in a browser" is satisfied in an agent session — do it before declaring a UI task
  done.
- The app is offline-first: `agent-browser set offline on` exercises the service-worker
  path when you need to test it.

## Tests

Vitest, run from `frontend/`:

```bash
npm test                                   # whole suite, single run
npm run test:watch                         # watch mode
npx vitest run src/lib/history.test.js     # a single file
npx vitest run -t "partial test name"      # tests matching a name
npx vitest run --coverage                  # v8 coverage (coverage/ is gitignored)
npm run test:fatigue-probe                 # standalone probe: recovery/fatigue model
                                           # monotonicity, imports src/lib/recovery.js
```

- Tests live beside the code: `src/lib/*.test.js`, `src/views/*.test.jsx`,
  `src/sheets.*.test.jsx`, `src/store/*.test.js`, `src/components/*.test.jsx`.
- Tests that need a DOM carry `// @vitest-environment happy-dom` as their first line;
  plain `lib/` tests run in the default node environment. Keep that split.
- Domain logic must stay testable without a DOM — that is what `src/lib` is for.

## Build

```bash
cd frontend
npm run build          # vite build → dist/ (relative base ./, service worker gets a
                       # content-stamped cache name)
npm run build:mobile   # VITE_MOBILE=1 vite build && cap sync android  — feeds the APK
```

Android APK (needs the Android SDK and **Java 21** for Gradle — see `docs/MOBILE.md`):

```bash
cd frontend && npm run build:mobile
cd android && ./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release-unsigned.apk  (unsigned; only CI signs)
```

After a mobile build, re-run a plain `npm run build` before publishing `dist/` as a
web/PWA build — the mobile flag changes the bundle.

## Lint

**There is no ESLint, Prettier or TypeScript here.** Do not add linter config, and do not
leave `/* eslint-disable */`-style comments — match the existing style by hand: small
components, clear names, comments only where the "why" isn't obvious.

What the repo does have are three guard scripts (run from `frontend/`):

```bash
node scripts/check-locales.mjs                  # all locale packs expose the same key set
node scripts/check-source-strings.mjs --strict  # t() literals missing from EVERY pack
node scripts/bundle-size.mjs --out bundle-size.json   # measure dist/ (report-only)
```

Run the first two whenever you touch user-facing strings. i18n model: English is the
source language and has no locale file — the key **is** the English string; Italian lives
in `src/locales/it.js`. A key added to only one pack fails `check-locales`; a literal
passed to `t()` that no pack defines is caught by `check-source-strings --strict`.

## CI

CI is GitLab (`.gitlab-ci.yml`); there are no GitHub Actions workflows. The single job,
`build:apk`, does `npm ci` → `npm run build:mobile` → `./gradlew assembleRelease` →
zipalign + sign, on every push to the default branch and on `v*` tags. It will **not** run
your tests — so run `npm test` yourself before declaring a task done.

## Hard rules of this codebase

- **Dependency-light is a hard constraint, not a preference.** Runtime dependencies are
  React, React Router, Zustand and the `@capacitor/*` plugins — nothing else. New runtime
  dependencies are a hard sell: the coach's `.xlsx` is read with the platform's own
  `DecompressionStream` and `DOMParser`, not a library.
- **No server calls.** Nothing in `frontend/` fetches application data from a backend;
  the app is offline-first and device-local.
- **Training logic gets a unit test.** Anything that reads a logged session back, or
  decides what the training numbers mean (progression, recovery/fatigue, PRs, plan
  import), belongs in a pure helper in `src/lib` with a test beside it. These rules are
  easy to get subtly wrong and nearly impossible to verify by clicking.
- **Never commit**: `frontend/dist/`, the Android build tree, user backup files, `*.xlsx`
  (the coach's plan is personal data), `android-keys/`, `*.jks`, `*.keystore`, `*.apk`.
- Native-only behavior (file persistence, local notifications, wake lock, in-app APK
  update) is gated behind the `MOBILE` flag in `src/lib/mobile.js` — keep web builds
  working without it.

## graphify

A local knowledge graph may exist at `graphify-out/` (god nodes, community structure,
cross-file relationships). It is gitignored, so a fresh checkout will not have it — the
rules below only apply when `graphify-out/graph.json` exists.

- For codebase questions, first run `graphify query "<question>"`. Use
  `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for
  focused concepts. These return a scoped subgraph, usually much smaller than
  `GRAPH_REPORT.md` or raw grep output.
- Dirty `graphify-out/` files are expected after hooks or incremental updates; dirty graph
  files are not a reason to skip graphify. Only skip graphify if the task is about stale
  or incorrect graph output, or the user explicitly says not to use it.
- If `graphify-out/wiki/index.md` exists, use it for broad navigation instead of raw
  source browsing.
- Read `graphify-out/GRAPH_REPORT.md` only for broad architecture review or when
  query/path/explain do not surface enough context.
- When ending a task, run `graphify update .` to keep the graph current (AST-only, no API
  cost). If it refuses to write after a code removal, re-run with `--force`.
