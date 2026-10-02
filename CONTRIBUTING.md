# Contributing to OlyGym

Thanks for taking a look! OlyGym is a personal, local-only fork of openGym and is intentionally
small and dependency-light; the goal is to keep it that way — easy to read, easy to run on a phone.

## Project layout

```
frontend/  React + Vite app (src/views, src/components, src/store, src/lib). Builds to static files.
           android/ is the Capacitor shell for the standalone Android app (docs/MOBILE.md).
docs/      MOBILE.md, DATA_IMPORTS.md, COMBINE_ROUTINES.md, LIST_VIEW.md.
scripts/   oly-catalogue/ — the generator for the Catalyst exercise catalogue.
```

There is no backend and no server: the app is device-local and offline-first. There is no iOS
target.

## Running for development

```bash
cd frontend
npm install
npm run dev          # Vite dev server, hot reload

# domain logic (session read-back, recovery model, coach-plan import, workout models):
npm test             # vitest run
```

## Guidelines

- **Keep it dependency-light.** The frontend uses React + Router + Zustand and the `@capacitor/*`
  plugins and nothing else; new runtime deps are a hard sell. The coach's `.xlsx` is read with the
  platform's own `DecompressionStream` and `DOMParser` rather than a spreadsheet library.
- **Nothing talks to a server.** No API client, no sync, no telemetry. State stays on the device.
- **Match the style.** Small components, clear names, comments only where the "why" isn't obvious.
  State lives in the Zustand store (`src/store`); pure helpers in `src/lib`.
- **Don't commit** build output (`frontend/dist`), the native build tree, or user backup files.
- **Test the flow** you touched — click through the affected screens (and the workout flow) in a
  browser before opening a merge request.
- **Training logic gets a unit test.** Anything that reads a logged session back, or decides what the
  training numbers mean, belongs in a pure helper in `src/lib` with tests beside it (`npm test`).
  These rules are easy to get subtly wrong and nearly impossible to verify by clicking.

## What CI does with your pull request

The only CI job is the signed Android APK build (`build:apk` in `.gitlab-ci.yml`). There are no
backend, MCP, Pages or container jobs any more. If your change needs an APK to be judged, say so in
the MR. Merge requests opened from a fork keep working the same way they always did; the pipeline is
started on the project's runners after a maintainer has looked at the diff.

## Good first issues

- Additional starter plans
- Accessibility passes on the workout and chart screens
- More accurate coach-shorthand aliases in `src/lib/plan-aliases.js`
- Cleanup and extra unit tests in `src/lib`

## Where to ask what

OlyGym is a personal fork; the software itself is upstream's, and questions and bug reports that
belong to the software are upstream's to answer:

| You have | Goes to |
| --- | --- |
| A reproducible bug in the shared app | [Upstream issues](https://github.com/DuarteSantos8/openGym/issues) |
| A change you've already built | [An upstream pull request](https://github.com/DuarteSantos8/openGym/pulls) |
| A quick question | [Upstream's Discord](https://discord.gg/e62jY6fwVb) |

## Reporting bugs

Open an issue with: what you did, what you expected, what happened, and your browser/OS. If it
involves the Android app, include the app version and Android version.

By contributing you agree your work is licensed under the project's [GNU AGPL v3.0](LICENSE).
