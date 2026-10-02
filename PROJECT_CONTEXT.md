# PROJECT_CONTEXT.md

Notes for whoever picks this up next. Not a spec — a map of what the project is and why, kept next
to the code it describes. See `CLAUDE.md` for architecture and commands; this file covers the
project's shape after it became local-only.

## What this is now

OlyGym is a personal, **local-only** Olympic-weightlifting tracker — a fork of openGym, cut down to
a single user and no server. The app is the whole product: a React/Vite front end that builds to
static files, wrapped for Android with Capacitor and installable as a PWA. There is no account, no
login, no sync, no push and no telemetry. Every number lives on the device in kilograms, and the UI
ships in Italian and English.

## What changed in the local-only pass

The self-hosted, multi-user product was removed and the app was re-pointed at the device:

- **Deleted:** `api/`, `mcp/`, `web/` (Docker + nginx), `docker-compose.yml`, `.env.example`,
  the GitHub/Gitea workflow copies, `website/`, the self-host and AI-coach docs, the retired
  builder scripts, the iOS shell, and `docs/API.md`.
- **Removed from the app:** passkeys/login, multi-user + admin, invites, audit log, server sync and
  multi-device pairing, web push, AI Coach, the MCP bridge, the marketing/demo site, most CI (only
  the APK job remains), the gym check-in QR, cross-app CSV/API imports (FitNotes/Strong/Hevy/Apple
  Health), cardio mode, assisted machines, per-side logging, drop-sets/rest-pause, estimated 1RM and
  the strength/decay view, the Greyskull/double/time progression rules, the lb unit, every locale
  but Italian and English, and plan JSON sharing.
- **Kept:** the 624-lift Catalyst catalogue, the dated weekly plan, the coach's Excel/Google Drive
  plan import, absolute-kg guided logging, complexes (the app renamed the old "supersets"),
  history and best-lift PRs, YouTube poster frames and embeds, the muscle map and fatigue model,
  RIR/RPE, bodyweight tracking with a goal line, equipment profiles, timed holds, favourites, the
  activity heatmap and streak, starter plans, JSON backup/export/import and auto-backup, the local
  workout-day reminder, the in-app APK updater, the Android Capacitor shell and PWA offline support,
  Material 3, OlyGym branding, and the plan PDF print.

## Data and persistence

There is no database. The Zustand store (`frontend/src/store/useStore.js`) is the single source of
truth and persists to `localStorage` under `gym_state_v1`. On the Android build, `lib/mobile.js`
mirrors the same state to a JSON file in the app's private storage on every change, so an evicted
WebView cache cannot lose it. Settings offers one-tap JSON export/import and keeps automatic
backups. Because storage is local, a schema change can strand an old `localStorage` payload — when
the shape of `S` changes, seed or migrate it deliberately rather than assuming a fresh profile.

## Where the logic lives

Training logic is deliberately outside the components: pure helpers in `frontend/src/lib/` with a
`*.test.js` beside each one (see CONTRIBUTING.md). The coach-plan import (`xlsx.js`,
`coach-sheet.js`, `plan-aliases.js`, `import-plan.js`, `drive.js`) is the largest example — it
reads an `.xlsx` with the platform's own `DecompressionStream` and `DOMParser`, matches the coach's
Italian shorthand against the catalogue, and hands the UI rows to review before anything is saved.
Views under `frontend/src/views/` render it and nothing more.

## Pointers

- `README.md` — what the app is and how to get it.
- `CLAUDE.md` — layout, commands, architecture.
- `docs/MOBILE.md` — the Android build, sideloading and the in-app updater.
- `docs/DATA_IMPORTS.md` — the coach's plan and JSON backup/restore.
- `docs/LIST_VIEW.md`, `docs/COMBINE_ROUTINES.md` — workout-screen internals.
- `OLYGYM_PLAN.md` — the fork's own work log (Italian), including the catalogue and branding work.
