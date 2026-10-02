<div align="center">

<img src="assets/banner.png" alt="OlyGym" width="720">

<br>

**A local-only Olympic weightlifting tracker for Android and the browser.**

Plan your week the way your coach writes it, run guided workouts, and track every lift and your
body weight over time — entirely on your own device. No account, no server, no subscription, no
ads, no telemetry.

<br>

**AGPL-3.0 · Android app + installable PWA · React 19 · no account, no server**

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
software, pointed hard at Olympic weightlifting and pared back to a single-user app that never
talks to a server. It exists because the training did not fit the tool: the catalogue was generic
gym work, the plan lived in a coach's spreadsheet, and neither wanted to be a feature request.

What is different here:

- 🏋️ **A weightlifting catalogue** — [Catalyst Athletics](https://www.catalystathletics.com/exercises/)'
  **624 lifts and drills**, in ten movement families (snatch, clean, jerk, squats and pulls, trunk,
  plyometrics, accessories, carries), with their descriptions, muscle tags and a demo video. Not a
  generic gym dataset with the barbell work bolted on
- 🎬 **The demo video is a layer of its own** — every exercise shows the poster frame of its video,
  hotlinked from YouTube by your browser. Tap it and the player opens; or have it load with the
  exercise, or keep it off entirely. This fork ships **no media at all**
- 📋 **Your coach's plan, imported** — point the app at his Excel workbook, from the device or from
  Google Drive: one sheet per week, his exercises, sets, reps and loads. His Italian gym shorthand
  (*strappo*, *girata*, *spinta in piedi*, `1+2`) is matched against the catalogue, and anything the
  catalogue has no word for stays in a note, in his words. You review every row before it becomes a
  routine, and a correction you make once is remembered for the next week
- 🧩 **A complex is one thing** — the movements he writes with `+` are one card with one set count
  and one load, in the routine editor and in the workout, because that is how they are trained
- 🎨 **Material 3 Expressive** — tonal surfaces, springs and flowing progress bars, a tab bar with a
  real indicator, light and dark themes and eight accents over a hand-drawn icon set
- 📴 **Local-only by design** — everything stays on the device. There is no server to sign in to, no
  account, no sync, no push; the Android app adds a native reminder, a file mirror and an in-app
  updater, nothing that phones home

Everything else in this README describes the software both projects share, minus what this fork
removed to get there.

## Why

Most workout apps lock your data behind a login on their servers, nag you to upgrade, or
disappear when the startup does. OlyGym is the opposite: **it runs on your own phone, your data
stays on the device, and it's yours to fork.** It still feels modern — installable as a
home-screen app, offline support, the same UI on Android and in the browser.

## Features

- ⚖️ **Body-weight tracking** — interactive chart with a goal line you set, gains/losses colored by whether they move toward it
- 🏋️ **Weekly plan** — a routine per weekday (or several, run back-to-back as one session), over the catalogue of **624 Catalyst lifts** (searchable, with the poster frame of each video), browsable **by muscle** on a body map
- ✨ **Four starter plans** — Push/Pull/Legs, Upper/Lower, Full Body, 5×5; loaded as ordinary routines you can edit, and a routine can be copied in one tap
- 🗓️ **Reschedule any day** — sick, missed a session, or fewer gym days this week? Move a workout to another day without touching your weekly plan
- 📅 **Your week starts where you say** — Monday or Sunday, in Settings. The weekly plan, the day strip on Home, the calendar and every "this week" total follow it, so the app reads the way the calendar on your wall does
- 🧭 **A workout screen that gets out of the way** — one ⋯ menu per exercise (note, details, bar weight, warm-up, complex, swap, move, remove), the set number as the set's own menu (remove), and a scrollable **list view** of the whole session with the header pinned. Four switches under Settings → Workout controls bring any of the old button rows back
- 🌈 **Colour-coded RIR / RPE** — one tap logs how hard a set was, with a sentence per level ("one more rep in the tank"); the same colour whether you think in RIR or RPE, a free field for in-between values
- 📖 **History without leaving the workout** — the exercise's last sessions and its best-lift PR, from the ⋯ menu or the exercise details
- ⭐ **Favourite exercises** — star what you use, it sorts first in the picker and the library
- ▶️ **Guided workouts** — it knows what day it is and starts today's session; asks your body weight first, pre-fills your weights in absolute kilograms from last time, rest timer, best-lift PR detection, per-exercise weight tracking. On a rest day it doesn't just say "rest day" — it names when your next session is and what it is
- 🙈 **Media is your call** — the frame can be full size, small, or hidden entirely during a workout; hidden collapses it rather than leaving a gap. The video itself can be a button, inline, or off — for anyone who finds a looping demo between sets more distracting than useful
- ☀️ **The screen stays awake while you train** — no unlocking the phone and finding your place again between every set. On for as long as a workout is running, released the moment you finish it, and switchable off in Settings
- 🔗 **Complexes** — plan them into a routine or pair two exercises *mid-session* with "make complex with previous/next", then work through the group back-to-back with a single rest at the end of each round. A complex shows as one card with its shared sets and load and its movements numbered inside; unpair at any time, and a group of one dissolves itself
- 🔥 **Warm-up sets** — mark the ramp-up rows as warm-ups and they stay out of the numbers that should not see them, while still being there in the session where you need them. A weight change cascades down the rows that share their phase, not across the divide
- ➖ **Change your mind mid-session** — add an exercise you decided to do, or remove one you didn't, without ending the workout. Removing a member of a complex asks which one
- ⏱️ **Timed exercises** — planks, hangs, wall sits and loaded carries are logged by time, not reps, with a work timer that counts the set itself (separate from the rest timer) and logs the time you actually held. They can carry weight too
- ⏲️ **Rest per exercise** — heavy triples and curls don't want the same break: give any exercise its own rest time and it overrides the global timer for that exercise (a complex rests once, taking the longest)
- 💪 **Bodyweight exercises, logged as bodyweight** — pull-ups, dips, push-ups and the rest arrive knowing they carry no load, so there's no weight column and no working-weight prompt: one stepper, log the reps. Add a dip belt and it reads as an addition
- 🏋️ **Plate math for barbell work** — barbell, EZ, trap bar and Smith machine carry a bar weight (20 kg and friends, or your own per exercise), and the workout screen tells you what goes on each side: *Bar 20 kg · 30 kg per side*. You still log the total, so your history and PRs keep meaning exactly what they always did
- 📝 **Log a past workout** — forgot your phone, trained on paper, or switched apps? Add a session after the fact from History: date, start time, duration, routine or freestyle, then the normal workout screen — weights, reps, RIR/RPE, timed sets and all. If that day already has a workout you choose: replace it, keep both, or cancel
- 🎲 **Freestyle sessions** — train without a plan and pick exercises as you go. Each one arrives prefilled from the last time you did it — same sets, same reps and weight by position — so an unplanned session doesn't start by asking you to retype last week
- 🖨️ **Print a plan** — lay out your routines and week schedule as a clean PDF
- 🔧 **Filter by equipment** — narrow the library to what you actually own; the options adapt to what you've picked, so every combination on screen has results behind it
- ✨ **Your own exercises** — a name and a body part is enough; they behave like built-in ones everywhere, with an optional description instead of a video
- 🟩 **Activity heatmap** — a GitHub-style year view, shaded by time spent training, with a week streak
- 💪 **Muscle map, two ways** — a front-and-back body diagram you can read as **Balance** (where the volume went, over a week, a month or all time — naming the muscles you *haven't* trained) or **Fatigue** (what is still recovering, weighted by how close each set was to your best, decaying smoothly rather than expiring at a window edge). It previews what a routine hits while you build it, and shows what you just trained when you finish. Male or female figure, your pick
- 📳 **See the timer end, not just hear it** — an opt-in screen flash when a rest or work timer finishes, for loud gyms and headphones
- 🔔 **A local workout-day reminder** — on days you have a workout planned but haven't logged one, the Android app schedules a native local notification per calendar date, so a day you already trained or rescheduled stays quiet. No server, no push service
- 🎨 **Designed, not assembled** — light/dark themes and 8 accent colors saved to your profile, over a hand-drawn icon set instead of emoji, so it looks the same on every phone
- 🌍 **Italian and English** — the full UI in both languages, loaded on demand so the app stays fast
- 📦 **Yours to keep** — one-tap JSON export/import and automatic backups; **no telemetry** and every number in kilograms
- 📱 **Standalone Android app** — the whole tracker wrapped by Capacitor: no account, no server, data on the phone, native workout reminders, offline PWA behaviour and an **in-app APK updater**. See **[docs/MOBILE.md](docs/MOBILE.md)**

## Install

**Android.** Grab the signed APK the project's `build:apk` CI job publishes, or build and sign your
own — see **[docs/MOBILE.md](docs/MOBILE.md)**. Sideload it, allow the install once, and the app
checks for a newer build itself. No Play Store, no account.

**Browser / installed PWA.** The app is static files; there is no server component.

```sh
cd frontend
npm install
npm run build          # → frontend/dist
```

Serve `frontend/dist` over HTTP(S) with any static host, open it, and use *Add to Home Screen* to
install it. Without a host, `npm run preview` serves it locally.

Nothing is downloaded at runtime except the demo poster frames, which are hotlinked from YouTube.

> **About those pictures:** the catalogue in this fork comes from
> [Catalyst Athletics](https://www.catalystathletics.com/exercises/) — names, descriptions and the
> link to each exercise's video — and the videos are theirs, hosted on YouTube. This fork ships no
> media at all: the frame is fetched at runtime and nothing is stored. Their text and videos are
> under neither this project's AGPL nor any license granted to you — see [NOTICE.md](NOTICE.md).

## Your data

Everything lives on the device. In the browser the app keeps its state in `localStorage`; the
Android app mirrors the same state to `opengym-state.json` in the app's private storage on every
change, so an evicted WebView cache cannot lose it. Settings offers one-tap **JSON export/import**
and keeps **automatic backups**; backups go out through the OS share sheet on Android. There is no
server and no account — clearing the app's data removes the data.

## Roadmap

[ROADMAP.md](ROADMAP.md) is upstream openGym's public roadmap, kept here for provenance only.
This fork has no releases of its own and does not follow that plan.

## Tech

React 19 + Vite (React Router, Zustand) · Capacitor (Android) · Vitest · exercise data from
[Catalyst Athletics](https://www.catalystathletics.com/exercises/); demo videos on YouTube, frames
hotlinked at runtime (see [License](#license)). No backend, no database server, no cloud
dependencies. Reading the coach's `.xlsx` uses the platform's own `DecompressionStream` and
`DOMParser` rather than a spreadsheet library, and the app itself ships no runtime dependencies
beyond React, the router, Zustand and the Capacitor plugins.

The training logic — how a logged session is read back, the muscle-recovery model, how the coach's
spreadsheet becomes routines — lives in pure functions under `frontend/src/lib/` with tests next to
them: `npm test` in `frontend/`.

## Credits & upstream

OlyGym is **[openGym](https://github.com/DuarteSantos8/openGym)** by **Duarte Santos** — the app,
the training logic and the original feature set are his work, under the AGPL. Nothing here would
exist without it.

- **[github.com/DuarteSantos8/openGym](https://github.com/DuarteSantos8/openGym)** — source, releases and issues
- **[opengym.duarte-santos.ch](https://opengym.duarte-santos.ch)** — upstream's site; its
  [Discord](https://discord.gg/e62jY6fwVb) and its
  [coffee button](https://buymeacoffee.com/duartesantos) are upstream's too, and belong to upstream
- **[CONTRIBUTING.md](CONTRIBUTING.md)**, [CHANGELOG.md](CHANGELOG.md), [ROADMAP.md](ROADMAP.md) — upstream's, kept as they are

This fork is personal: no releases of its own, no Discord, and issues are upstream's to answer.

## License

**The code** is [GNU AGPL v3.0](LICENSE) — free and open source. You can use, modify and share it,
and any modified version you distribute must stay under the same license.

**Third-party content is not, and this project cannot sublicense it.** The exercise catalogue in this
fork — names, movement categories, equipment, descriptions and the link to each demo video — comes
from [Catalyst Athletics](https://www.catalystathletics.com/exercises/), taken from their public
exercise pages; every entry cites the page it came from. Neither that text nor their videos are
covered by the AGPL or licensed to you by this project. No media is redistributed here: the app
hotlinks the video's poster frame from `img.youtube.com` at runtime and never stores it. To reuse
their text or video yourself, clear it with the rights holder first.

Full third-party notices, including the body-diagram geometry: **[NOTICE.md](NOTICE.md)**.
