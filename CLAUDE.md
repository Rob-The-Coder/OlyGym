# CLAUDE.md

@AGENTS.md

The `@AGENTS.md` line above imports the operational file into Claude Code sessions:
fresh-checkout setup, the dev server (including agent-browser for viewing the app), tests,
build, lint, CI, the project's hard rules, the factory workflow and graphify all live there —
do not duplicate them in this file. This file only carries what AGENTS.md points to: what the
app is, and how the code is structured.

## What this is

OlyGym is a **local-only** Olympic weightlifting tracker — a personal fork of openGym, pointed at
the Catalyst Athletics weightlifting catalogue and pared back to a single-user app that never talks
to a server. There is no account, no login, no multi-device sync, no push and no telemetry: all
state lives on the device. It ships as an Android app (Capacitor) and as an installable PWA built
from the same source. License: AGPL-3.0-or-later.

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
  Muscles, Competitions, Settings), routed by `react-router-dom` from `App.jsx`.
- **`components/`** — shared UI (charts, modals, timers); `locales/` is the i18n string
  catalogue (`lib/i18n.js` / `i18n-core.js`).
  Two locales ship: **Italian and English**.
- Mobile: `@capacitor/*` wraps the same web build into the native shell under `frontend/android`
  (Android only; there is no iOS target — see `docs/MOBILE.md`). `mobile.js` in `lib/` gates
  native-only behavior (file persistence, local notifications, wake lock, in-app APK update) behind
  a `MOBILE` flag.
