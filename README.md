<div align="center">

<img src="assets/banner.png" alt="OlyGym" width="720">

<br>

**A self-hosted Olympic weightlifting tracker you actually own.**

Plan your week the way your coach writes it, run guided workouts, track every lift and your
body weight over time — on your phone, synced across devices, behind your own passkey login.
No account on someone else's server, no subscription, no ads. Just `docker compose up`.

<br>

[![License: AGPL v3](https://img.shields.io/badge/license-AGPL--3.0-a3e635?style=flat-square)](LICENSE)
![Self-hosted](https://img.shields.io/badge/self--hosted-%F0%9F%8F%A0-60a5fa?style=flat-square)
![PWA](https://img.shields.io/badge/PWA-installable-a78bfa?style=flat-square)
![React](https://img.shields.io/badge/React-19-38bdf8?style=flat-square&logo=react&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-compose-2496ED?style=flat-square&logo=docker&logoColor=white)
![No tracking](https://img.shields.io/badge/telemetry-none-f472b6?style=flat-square)

</div>

<br>

<div align="center">
<table>
<tr>
<td align="center"><img src="assets/screenshots/home.png" alt="Home" width="230"><br><sub><b>Home</b> — today's session & weight</sub></td>
<td align="center"><img src="assets/screenshots/workout.png" alt="Workout" width="230"><br><sub><b>Guided workout</b> — a complex, its sets and its video</sub></td>
<td align="center"><img src="assets/screenshots/stats.png" alt="Stats" width="230"><br><sub><b>Stats</b> — heatmap, muscle balance & PRs</sub></td>
</tr>
</table>
</div>

<br>

## What this is

**OlyGym is a personal fork of [openGym](https://github.com/DuarteSantos8/openGym)** — same
software, same self-hosting story, pointed hard at Olympic weightlifting. It exists because the
training did not fit the tool: the catalogue was generic gym work, the plan lived in a coach's
spreadsheet, and neither wanted to be a feature request.

What is different here:

- 🏋️ **A weightlifting catalogue** — [Catalyst Athletics](https://www.catalystathletics.com/exercises/)'
  **624 lifts and drills**, in ten movement families (snatch, clean, jerk, squats and pulls, trunk,
  plyometrics, accessories, carries), with their descriptions and muscle tags. Not a generic gym
  dataset with the barbell work bolted on
- 🎬 **The demo video is a layer of its own** — every exercise shows the poster frame of its video,
  hotlinked from YouTube by your browser. Tap it and the player opens; or have it load with the
  exercise, or keep it off entirely. This fork ships **no media at all**
- 📋 **Your coach's plan, imported** — point the app at his Excel workbook: one sheet per week, his
  exercises, sets, reps and loads. His Italian gym shorthand (*strappo*, *girata*, *spinta in
  piedi*, `1+2`) is matched against the catalogue, and anything the catalogue has no word for stays
  in a note, in his words. You review every row before it becomes a routine, and a correction you
  make once is remembered for the next week
- 🧩 **A complex is one thing** — the movements he writes with `+` are one card with one set count
  and one load, in the routine editor and in the workout, because that is how they are trained
- 🎨 **Material 3 Expressive** — tonal surfaces, springs and flowing progress bars, a tab bar with a
  real indicator, light and dark themes and eight accents over a hand-drawn icon set
- ✍️ **The name, everywhere the reader meets it** — the app, the passkey prompt, the push
  notifications and the docs all say OlyGym. Upstream's marketing site under `website/` is untouched
  and not deployed here

Everything else in this README describes the software both projects share.

## Why

Most workout apps lock your data behind a login on their servers, nag you to upgrade, or
disappear when the startup does. OlyGym is the opposite: **it runs on your box, your data
stays in a folder you control, and it's yours to fork.** It still feels modern — installable
as a home-screen app, passkey sign-in, offline support, sync across your phone and laptop.

## Features

- ⚖️ **Body-weight tracking** — interactive chart with a goal line you set, gains/losses colored by whether they move toward it
- 🏋️ **Weekly plan** — a routine per weekday, over the catalogue of **624 Catalyst lifts** (searchable, with the poster frame of each video), browsable **by muscle** on a body map
- ✨ **Four starter plans** — Push/Pull/Legs, Upper/Lower, Full Body, 5×5; loaded as ordinary routines you can edit, and a routine can be copied in one tap
- 🗓️ **Reschedule any day** — sick, missed a session, or fewer gym days this week? Move a workout to another day without touching your weekly plan
- 📅 **Your week starts where you say** — Monday or Sunday, in Settings. The weekly plan, the day strip on Home, the calendar and every "this week" total follow it, so the app reads the way the calendar on your wall does
- 🧭 **A workout screen that gets out of the way** — one ⋯ menu per exercise (note, details, progression, bar weight, warm-up, complex, swap, move, remove), the set number as the set's own menu (drop set, rest-pause burst, remove), and a scrollable **list view** of the whole session with the header pinned. Four switches under Settings → Workout controls bring any of the old button rows back
- 🌈 **Colour-coded RIR / RPE** — one tap logs how hard a set was, with a sentence per level ("one more rep in the tank"); the same colour whether you think in RIR or RPE, a free field for in-between values
- 📖 **History without leaving the workout** — the exercise's last sessions and a progress line, from the ⋯ menu or the exercise details
- ⭐ **Favourite exercises** — star what you use, it sorts first in the picker and the library
- ▶️ **Guided workouts** — it knows what day it is and starts today's session; asks your body weight first, pre-fills your weights from last time, rest timer, PR detection, per-exercise weight tracking. On a rest day it doesn't just say "rest day" — it names when your next session is and what it is
- 🙈 **Media is your call** — the frame can be full size, small, or hidden entirely during a workout; hidden collapses it rather than leaving a gap. The video itself can be a button, inline, or off — for anyone who finds a looping demo between sets more distracting than useful
- ☀️ **The screen stays awake while you train** — no unlocking the phone and finding your place again between every set. On for as long as a workout is running, released the moment you finish it, and switchable off in Settings
- 🔗 **Complexes and supersets** — plan them into a routine or pair two exercises *mid-session* with "make complex with previous/next", then work through the group back-to-back with a single rest at the end of each round. A complex shows as one card with its shared sets and load and its movements numbered inside; unpair at any time, and a group of one dissolves itself
- 🔥 **Warm-up sets** — mark the ramp-up rows as warm-ups and they stay out of the numbers that should not see them: no effect on your estimated 1RM, your progression, or the fatigue map, while still being there in the session where you need them. A weight change cascades down the rows that share their phase, not across the divide
- ➖ **Change your mind mid-session** — add an exercise you decided to do, or remove one you didn't, without ending the workout. Removing a member of a complex asks which one
- ⏱️ **Timed exercises** — planks, hangs, wall sits and loaded carries are logged by time, not reps, with a work timer that counts the set itself (separate from the rest timer) and logs the time you actually held. They can carry weight too
- ⏲️ **Rest per exercise** — heavy triples and curls don't want the same break: give any exercise its own rest time and it overrides the global timer for that exercise (a complex rests once, taking the longest). Travels with shared plans
- 🧘 **Planned deloads** — flag a routine as excluded from automatic progression: its sessions open with the routine's own target weights, stay in your history and statistics, and never become the baseline your next regular session progresses from
- 📈 **Progression that follows a rule** — pick one per routine, override it per exercise: linear, **Greyskull LP** (AMRAP top set, double jumps, 10 % resets), double progression through a **visible rep range** (both bounds editable, per-side exercises step in twos), or adding time. Your weights are already right when the session opens, and every target says *why* it's that number. Missed reps never advance the load, stalls trigger a deload, and bodyweight exercises progress in reps instead
- 💪 **Estimated 1RM** — per exercise, from your best eligible set (it names which one), with its own progress curve and a calculator for sets you haven't done. Won't guess above 12 reps
- 🎯 **Effort per set, in your scale** — an optional third column rating how hard a set was, as **RIR** (reps left in the tank) or **RPE** (the same judgement on a 10-point scale). Off by default; each set keeps the scale it was logged with, and nothing else reads the value — your progression and 1RM are unaffected
- 💪 **Bodyweight exercises, logged as bodyweight** — pull-ups, dips, push-ups and the rest arrive knowing they carry no load, so there's no weight column and no working-weight prompt: one stepper, log the reps. Add a dip belt and it reads as an addition, and progression goes back to following the weight. Without one, reps climb — and past a ceiling you set, a set is added instead of a rep, up to the point where the honest advice is load or a harder variation
- ↔️ **Reps per side** — for lunges, single-arm rows and the rest. You log the total, the app shows the split ("8 per side"), and the target steps in twos so it never lands on a number one side can't have
- 🏋️ **Plate math for barbell work** — barbell, EZ, trap bar and Smith machine carry a bar weight (20 kg / 45 lb and friends, or your own per exercise), and the workout screen tells you what goes on each side: *Bar 20 kg · 30 kg per side*. You still log the total, so your history, progression and 1RM keep meaning exactly what they always did
- 📝 **Log a past workout** — forgot your phone, trained on paper, or switched apps? Add a session after the fact from History: date, start time, duration, routine or freestyle, then the normal workout screen — weights, reps, RIR/RPE, timed sets and all. If that day already has a workout you choose: replace it, keep both, or cancel. Backfilled sessions never claim PRs against workouts that came later
- 🎲 **Freestyle sessions** — train without a plan and pick exercises as you go. Each one arrives prefilled from the last time you did it — same sets, same reps and weight by position — so an unplanned session doesn't start by asking you to retype last week
- 📤 **Share a plan** — send someone your routines and week schedule as a small file (no workouts, no weigh-ins), or print it as a clean PDF. Importing merges, so their plan is never overwritten
- 🔧 **Filter by equipment** — narrow the library to what you actually own; the options adapt to what you've picked, so every combination on screen has results behind it
- ✨ **Your own exercises** — a name and a body part is enough; they behave like built-in ones everywhere, with an optional description instead of a video
- 🟩 **Activity heatmap** — a GitHub-style year view, shaded by time spent training
- 💪 **Muscle map, three ways** — a front-and-back body diagram you can read as **Balance** (where the volume went, over a week, a month or all time — naming the muscles you *haven't* trained), **Fatigue** (what is still recovering, weighted by how close each set was to your maximum, decaying smoothly rather than expiring at a window edge) or **Strength** (how long since you trained each muscle, and behind every one the exercises that built it with their estimated 1RM). It previews what a routine hits while you build it, and shows what you just trained when you finish. Male or female figure, your pick
- 📳 **See the timer end, not just hear it** — an opt-in screen flash when a rest or work timer finishes, for loud gyms and headphones
- 🔔 **Push notifications** — rest-timer alerts even with the app closed, plus an optional reminder on days you have a workout planned but haven't logged one — on the Android app scheduled per calendar date, so a day you already trained or rescheduled stays quiet. Opt in per profile; keys are generated on first run, nothing to configure
- 🔑 **Passkeys, not passwords** — Face ID / Touch ID / fingerprint login; each profile keeps its own data, synced across devices. Sign-ins last 90 days by default (configurable), and “sign out everywhere” in Settings ends every session on every device at once
- 🛠️ **Admin dashboard** (optional) — for whoever runs the instance: who's training right now, per-user history, disable accounts, invite-only signup, and an **activity log** of sign-ins, failed attempts and admin actions. Off by default, so a fresh instance stays open with no admin
- 🎨 **Designed, not assembled** — light/dark themes and 8 accent colors saved to your profile, over a hand-drawn icon set instead of emoji, so it looks the same on every phone
- 🌍 **14 languages** — full UI translation (EN, DE, ES, FR, IT, PT (Portugal), PT (Brazil), PL, TR, RU, ZH, KO, HI, TH, HU); exercise instructions localized in 12 of them and built-in exercise names shown bilingually in PT-BR and HU, all loaded on demand so the app stays fast
- 📥 **Bring your history with you** — import from **FitNotes** (Android and iOS), **Strong** and **Hevy** (CSV or directly with a [Hevy Pro API key](https://hevy.com/settings?developer)), or body weight straight out of an **Apple Health** export. Exercise names are matched against the library and anything unrecognised becomes one of your own exercises, so nothing in the file is dropped
- 📦 **Yours to keep** — one-tap JSON export/import, guest mode, **no telemetry**; switching kg ↔ lb offers to convert every stored number
- 🤖 **Ask an AI about your training** (optional) — an [MCP server](mcp/README.md) lets a client like Claude Desktop or Cursor read your history in your own words: *"what did I snatch last week?"*. Read-only, spawned locally by the client, nothing leaves your box. Not in the Docker build — if you don't use an AI assistant, it isn't there
- 🧠 **An AI coach that writes your plan** (optional, off by default) — answer a handful of questions and it designs a week of routines; later it reads what you actually logged and proposes changes, each one with the evidence behind it. You approve every change and can undo it. It runs on **your** server under **your** provider account — Anthropic, OpenAI, Gemini or any OpenAI-compatible endpoint (Ollama on your LAN counts) with a pasted API key on the default image, or the Claude Agent SDK / Codex CLI on a separate build. The phone app can use your instance or its own key. See [docs/AI_COACH.md](docs/AI_COACH.md)
- 📱 **Standalone mobile app** — the whole tracker wrapped by Capacitor: no account, no server, data on the phone, native workout reminders. This fork publishes no APK; build your own with **[docs/MOBILE.md](docs/MOBILE.md)**

## Quick start (self-host)

You need [Docker](https://docs.docker.com/get-docker/) with Compose.

```bash
git clone https://github.com/Rob-The-Coder/OlyGym
cd OlyGym
cp .env.example .env
docker compose up -d --build
```

Open **http://localhost:8080**, tap **Create profile**, and you're in. Nothing is downloaded: each
exercise shows the poster frame of its demo video, hotlinked from YouTube by your browser.

> **About that picture:** the catalogue in this fork comes from
> [Catalyst Athletics](https://www.catalystathletics.com/exercises/) — names, descriptions and the
> link to each exercise's video — and the videos are theirs, hosted on YouTube. This fork ships no
> media at all: the frame is fetched at runtime and nothing is stored. Their text and videos are
> under neither this project's AGPL nor any license granted to you — see [NOTICE.md](NOTICE.md).

The images are not published to a registry by this fork, so the quick start builds them. Upstream
publishes its own under `registry.gitlab.com/duartesantos8/opengym/{api,web}` and
`ghcr.io/duartesantos8/opengym-{api,web}`; point the `image:` lines in `docker-compose.yml` at those
if you would rather pull — they are upstream's build, without the catalogue and the interface of
this fork.

> Want it reachable from your phone over the internet with passkeys? You'll need an HTTPS
> domain — a two-line change in `.env`. See **[docs/SELF_HOSTING.md](docs/SELF_HOSTING.md)**.

## Mobile app (no server at all)

The same codebase also builds a **standalone mobile app** (Capacitor): no account, no sync,
no backend — everything stays on the phone, with native workout-day reminders and share-sheet
backups. Self-hosting gets you multi-device sync and profiles for friends & family; the
mobile app is the install-and-done flavor.

- **Android:** build it and sideload it — openGym is deliberately not on the Play Store, and this
  fork publishes no APK of its own. **[docs/MOBILE.md](docs/MOBILE.md)**
- **iPhone:** Apple doesn't allow installing apps outside the App Store, so there is no iOS
  download. Self-host and add it to your home screen from Safari (it's a full PWA), or build
  the native app onto your own device from Xcode — see **[docs/MOBILE.md](docs/MOBILE.md)**.

## How it works

```
┌─────────────┐        ┌──────────────────────────────┐
│  Your phone │──HTTPS─▶│  web  (nginx)                │
│  / laptop   │        │   ├─ serves the built app    │
└─────────────┘        │   └─ proxies /api ──────────┐│
                       └──────────────────────────────┘│
                                                        ▼
                                        ┌──────────────────────────┐
                                        │  api  (Node + WebAuthn)  │
                                        │   └─ ./data (JSON files) │
                                        └──────────────────────────┘
```

- **frontend/** — React + Vite (React Router + Zustand), built to static files **inside Docker**.
  The Material 3 Expressive layer is one stylesheet (`src/m3.css`) loaded after the base one, so
  upstream's styles stay untouched and merges stay cheap
- **api/** — Node with no framework, two dependencies (`@simplewebauthn/server` for passkeys, `web-push` for notifications), storing everything as plain JSON files under `./data`
- **web/** — a multi-stage image that builds the frontend and serves it with nginx, proxying `/api` to the backend so it's all on **one origin** (passkeys require this)
- **scripts/oly-catalogue/** — the generator that turns Catalyst's public exercise pages into the catalogue this fork ships, with a `--check` mode so the data can never drift from the script that produced it

The training logic — progression rules, 1RM estimation, how a logged session is read back, how the
coach's spreadsheet becomes routines — lives in pure functions under `frontend/src/lib/` with tests
next to them: `npm test` in `frontend/`. Vitest is a dev dependency; the app itself ships no runtime
dependencies beyond React, the router and Zustand. Reading an `.xlsx` uses the platform's own
`DecompressionStream` and `DOMParser` rather than a spreadsheet library.

The full HTTP API is documented as an OpenAPI spec in [`api/openapi.yaml`](api/openapi.yaml).

## Your data

Lives in `./data` on your host: `db.json` (profiles + public passkeys), `state-<user>.json`
(each user's plan, workouts, body weight, settings), `audit.log` (the admin activity log — sign-ins
and admin actions, no IP addresses unless you ask for them) and `secret` (the session-cookie key).
**Back up `./data` and you've backed up everything.** Passkey private keys never touch the
server — they stay in your phone's secure hardware / your password manager.

## Configuration

All via `.env` (see `.env.example`):

| Variable      | What it is                                           | Default                 |
|---------------|------------------------------------------------------|-------------------------|
| `RP_ID`       | Hostname passkeys are bound to                       | `localhost`             |
| `ORIGIN`      | Full URL the app is served from                      | `http://localhost:8080` |
| `WEB_PORT`    | Host port for the web UI                             | `8080`                  |
| `NGINX_PORT`  | Port the web container listens on, inside the container | `80`                 |
| `BACKEND`     | Name of the API service that `/api` is proxied to — change it if yours isn't called `api` | `api` |
| `PORT`        | Port the API listens on; the web container proxies to the same value | `3000`  |
| `RP_NAME`     | Name shown in the passkey prompt                     | `OlyGym`                |
| `SESSION_DAYS`| How long a sign-in lasts, in days                    | `90`                    |
| `ADMIN_UIDS`  | User ids that get the admin dashboard (comma-separated) | *(none)*             |
| `INVITE_ONLY` | Require an invite code to create a profile           | *(off)*                 |
| `ALLOW_GUEST` | Offer "Continue without account" — set `0` to require a profile | *(on)*       |
| `AUDIT_LOG`   | Record sign-ins and admin actions — set `0` to record nothing | *(on)*        |
| `AUDIT_MAX`   | Events kept in the activity log; `0` for no limit    | `5000`                  |
| `AUDIT_DAYS`  | Days kept in the activity log; `0` to keep until `AUDIT_MAX` | `90`            |
| `AUDIT_IP`    | Record the caller's address: `off`, `net` (network only) or `full` | `off`     |
| `VAPID_SUBJECT` | Contact URL sent with push notifications           | your `ORIGIN`           |
| `API_TARGET`  | Which API image to build: `default` (no AI runtime — API-key providers still work) or `coach` (adds the Claude Agent SDK + Codex CLI) | `default`   |
| `COACH_DISABLED` | Set to `1` to force the AI Coach off instance-wide, whatever the admin toggled | *(unset)* |

Push notification keys are generated on first run and saved to `./data/vapid.json` — nothing to set.
`DATA_DIR` is pinned to `/data` by `docker-compose.yml` and mapped to `./data` on the host; change the
host side of that volume, not the variable.

## Roadmap

The plan for the software itself is upstream's, in [ROADMAP.md](ROADMAP.md): a release every two
weeks, then the storage and search rebuild, accounts, the health items, and what those unlock. This
fork follows it and merges what arrives — its own work is the weightlifting catalogue, the
interface, the video layer, the coach's spreadsheet and the branding, all listed under
[What this is](#what-this-is).

## Tech

React 19 + Vite (React Router, Zustand) · Node (no framework) · nginx · Docker Compose ·
WebAuthn · exercise data from [Catalyst Athletics](https://www.catalystathletics.com/exercises/);
demo videos on YouTube, frames hotlinked at runtime (see [License](#license)).
No database server, no cloud dependencies — the frontend builds inside Docker, so self-hosting
stays a one-command `docker compose up`.

The optional AI Coach (`api/coach/`) is built the same way round: a by-name allowlist decides
what may leave the server, and a closed-list validator decides what may come back — the model
can touch routines and the weekly schedule, nothing else, and every change is applied on the
client only after you approve it. The core of it — `api/coach/core/` — has no Node dependency,
so the phone app runs the same validator the server does. The in-container AI runtimes live in a
separate Docker build target; the API-key providers need none. See [docs/AI_COACH.md](docs/AI_COACH.md).

The same pure helpers power an optional MCP server (`mcp/`) that lets an LLM client like
Claude Desktop read your data over stdio — see [mcp/README.md](mcp/README.md). Opt-in, not
in the Docker build.

## Credits & upstream

OlyGym is **[openGym](https://github.com/DuarteSantos8/openGym)** by **Duarte Santos** — the app,
the API, the AI Coach, the MCP bridge and the deployment story are his work, under the AGPL. Nothing
here would exist without it, and this fork tracks it rather than forking away from it:

- **[github.com/DuarteSantos8/openGym](https://github.com/DuarteSantos8/openGym)** — source, releases and issues
- **[gitlab.com/DuarteSantos8/opengym](https://gitlab.com/DuarteSantos8/opengym)** — the mirror whose CI builds the release artefacts (signed APK, multi-arch images, SBOMs)
- **[opengym.duarte-santos.ch](https://opengym.duarte-santos.ch)** — upstream's site, its in-browser
  demo and the APK download; its [Discord](https://discord.gg/e62jY6fwVb) and its
  [coffee button](https://buymeacoffee.com/duartesantos) are upstream's too, and belong to upstream
- **[CONTRIBUTING.md](CONTRIBUTING.md)**, [CHANGELOG.md](CHANGELOG.md), [ROADMAP.md](ROADMAP.md) — upstream's, kept as they are

This fork is personal: no releases of its own, no APK, no Discord, and issues are upstream's to
answer. Bug reports and pull requests that belong to the software itself are upstream's.

## License

**The code** is [GNU AGPL v3.0](LICENSE) — free and open source. You can self-host, use, modify and
share it; if you run a modified version as a network service, you must offer that version's source
under the same license. Nobody can turn it into a closed, proprietary product.

**Third-party content is not, and this project cannot sublicense it.** The exercise catalogue in this
fork — names, movement categories, equipment, descriptions and the link to each demo video — comes
from [Catalyst Athletics](https://www.catalystathletics.com/exercises/), taken from their public
exercise pages; every entry cites the page it came from. Neither that text nor their videos are
covered by the AGPL or licensed to you by this project. No media is redistributed here: the app
hotlinks the video's poster frame from `img.youtube.com` at runtime and never stores it. To reuse
their text or video yourself, clear it with the rights holder first.

Full third-party notices, including the body-diagram geometry: **[NOTICE.md](NOTICE.md)**.
