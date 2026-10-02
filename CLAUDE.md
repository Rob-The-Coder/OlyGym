# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

OlyGym is a **local-only** Olympic weightlifting tracker — a personal fork of openGym, pointed at
the Catalyst Athletics weightlifting catalogue and pared back to a single-user app that never talks
to a server. There is no account, no login, no multi-device sync, no push and no telemetry: all
state lives on the device. It ships as an Android app (Capacitor) and as an installable PWA built
from the same source. License: AGPL-3.0-or-later.

## Project layout

```
frontend/  React 19 + Vite app (src/views, src/components, src/store, src/lib). Builds to static files.
           android/ is the Capacitor shell for the standalone Android app (docs/MOBILE.md).
docs/      MOBILE.md, DATA_IMPORTS.md, COMBINE_ROUTINES.md, LIST_VIEW.md.
scripts/   oly-catalogue/ — generator for the Catalyst catalogue.
```

There is no backend, no Docker, no `web/`, no `mcp/`, no marketing site and no iOS target. Nothing
here is deployed as a server.

## Commands

```bash
# Frontend dev server (hot reload)
cd frontend && npm install && npm run dev

# Frontend tests (domain logic: session read-back, recovery, coach-plan import, models)
cd frontend && npm test            # vitest run
cd frontend && npm run test:watch
npx vitest run src/lib/history.test.js   # single file
npx vitest run -t "some test name"        # single test by name

# Production build
cd frontend && npm run build
cd frontend && npm run build:mobile   # VITE_MOBILE build + cap sync android
```

There is no linter/formatter configured (no ESLint/Prettier config in the repo) and no
TypeScript — match the existing style by hand.

The only CI job is `.gitlab-ci.yml`'s `build:apk`, which builds and signs the Android APK on
GitLab. There are no test, image, Pages or MCP jobs any more.

## Architecture

### Frontend (`frontend/src`)

- **`store/useStore.js`** — single Zustand store holding the entire client-side app state (`S`),
  persisted to `localStorage` (`gym_state_v1`). On the Capacitor Android build it is also mirrored
  to a file via `lib/mobile.js` (`nativeSave`), since WebView storage can be evicted.
  `store/useUI.js` holds ephemeral UI state (modals, active sheet, etc.) separately from persisted
  data.
- **`lib/`** — pure, framework-free helpers, each paired with a same-directory `*.test.js`. This is
  where the domain logic lives, most importantly:
  - `exercises.js` / `exercises-data.js` — the 624-lift Catalyst catalogue.
  - `workout-model.js`, `session-start.js`, `session-merge.js`, `supersetFlow.js` — the in-session
    workout state machine, including complexes and multi-routine sessions.
  - `finish-workout.js`, `history.js` — reducing a completed session back into state, best-lift
    PRs, and reading a logged session back.
  - `recovery.js` / `recovery-view.js`, `muscles.js` — the muscle map and fatigue/recovery model.
  - `effort.js` — RIR/RPE interpretation.
  - `xlsx.js`, `coach-sheet.js`, `plan-aliases.js`, `import-plan.js`, `drive.js` — reading the
    coach's Excel/Drive plan and matching it to the catalogue.
  - `equipment.js`, `favourites.js`, `starter.js`, `weeks.js`, `video.js`, `mobile.js` — the rest.
  - CONTRIBUTING.md is explicit: **anything that reads a logged session back, or decides what the
    training numbers mean, is a pure helper here with a unit test beside it** — not verifiable by
    clicking.
- **`views/`** — one file per screen (Home, Plan, WeekEdit, Workout, Stats, History, Library,
  Muscles, Settings), routed by `react-router-dom` from `App.jsx`.
- **`components/`** — shared UI (charts, modals, timers); `locales/` is the i18n string
  catalogue (`lib/i18n.js` / `i18n-core.js`).
  Two locales ship: **Italian and English**.
- Mobile: `@capacitor/*` wraps the same web build into the native shell under `frontend/android`
  (Android only; there is no iOS target — see `docs/MOBILE.md`). `mobile.js` in `lib/` gates
  native-only behavior (file persistence, local notifications, wake lock, in-app APK update) behind
  a `MOBILE` flag.

## Guidelines from CONTRIBUTING.md worth knowing before changing code

- **Dependency-light is a hard constraint, not a preference.** Runtime dependencies are React, React
  Router, Zustand and the `@capacitor/*` plugins. New dependencies are a hard sell; the coach's
  `.xlsx` is read with the platform's own `DecompressionStream` and `DOMParser`, not a library.
- **No server calls.** Nothing in `frontend/` should fetch application data from a backend; the app
  is offline-first and device-local.
- Don't commit build output (`frontend/dist`), the Android build tree, or user backup files.
- Session-read-back and training-logic changes need a unit test in `src/lib` beside the code, not
  just manual clicking-through.
