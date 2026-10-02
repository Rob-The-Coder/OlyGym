# Building the Android app

OlyGym is **local-only**. There is no server, so there is no self-hosted flavor and no "pair the
app to your instance" mode: the phone is the account. The same React/Vite source ships two ways:

| | **Browser / installed PWA** | **Android app** (`VITE_MOBILE=1`) |
|---|---|---|
| Runs | any static host, installed to the home screen | natively, Capacitor shell |
| Accounts | none — single-user, device-local | none — the phone *is* the account |
| Data | `localStorage` | `localStorage` mirrored to a JSON file in the app's private storage |
| Reminders | none (no push) | native local notification, scheduled on-device |
| Exercise media | poster frames of the demo videos, fetched from YouTube | same |
| Updates | re-deploy the static files | in-app APK updater, or sideload a newer build |

There is no iOS target any more: `frontend/ios` and the iOS CI job were removed with the rest of
the self-hosted/multi-platform plumbing.

## Local persistence, backup and reminders

The app keeps its whole state in `localStorage`. WebView storage can be evicted under memory
pressure, so on every change the Android build also mirrors the state to `opengym-state.json` in
the app's private data directory and restores it on launch — that file is the durable copy.

- **Backup / restore:** Settings → JSON export/import, plus automatic backups. On Android the file
  goes out through the OS share sheet instead of a browser download.
- **Workout-day reminder:** opt in and the app schedules a native local notification per calendar
  date on days a workout is planned; a day you already trained or rescheduled stays quiet. It needs
  the notification permission (requested only when the reminder is switched on) and, on Android,
  declares `SCHEDULE_EXACT_ALARM` so it fires to the minute where the user allows it. Nothing is
  sent to a server — there is no push service.
- **In-app updater:** the app can check for and offer a newer signed APK without going through a
  store. Updates must be signed with the same key as the installed build or Android refuses them.

## Prerequisites

- Node 20+
- **Android:** Android Studio (bundles the SDK). Java 21 for Gradle.

## Build & run

```sh
cd frontend
npm install
npm run build:mobile        # VITE_MOBILE build + `cap sync android`

npx cap open android        # opens Android Studio → run on emulator or device
```

`npm run build:mobile` copies the web build into the native project (`cap sync android`; there is no
media base to bake in — the pictures come from YouTube) — re-run it after every web-code change
before building natively.

> **Heads-up:** after `build:mobile`, `frontend/dist` contains the *mobile* bundle.
> Run a plain `npm run build` again before publishing `dist` as a web/PWA build.

## App icons & splash screens

`frontend/resources/icon.svg` is the 1024×1024 source (the app's dumbbell glyph on the app
background). Generate all platform assets from it on a machine with the tooling:

```sh
cd frontend
npx @capacitor/assets generate --iconBackgroundColor '#0c0e12' --splashBackgroundColor '#0c0e12'
```

(If the generator won't take the SVG directly, export it to `resources/icon.png` at 1024×1024 first
— any image tool can do it.)

## Distribution — sideload the APK

OlyGym is not on the Play Store, and that is a choice: no store accounts, no store rules, no yearly
fees between you and an open-source app.

The project's CI builds a signed APK with the `build:apk` job in [`.gitlab-ci.yml`](../.gitlab-ci.yml):
it runs `npm run build:mobile` and `./gradlew assembleRelease`, then `zipalign`s and signs the
result with the release key. The job runs on every push to `main`, and on a `v*` tag it also
uploads the APK and its `.sha256` to the GitLab generic package registry. Signing keys live in
*protected* CI variables (`ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`), so a merge request from a fork builds an unsigned APK and never sees the key.

Android asks you to allow installs from the browser the first time — standard for any app outside
the Play Store.

To build and sign your own:

```sh
cd frontend && npm run build:mobile
cd android && ./gradlew assembleRelease            # → app/build/outputs/apk/release/app-release-unsigned.apk

# one-time: create a keystore. KEEP IT — updates must be signed with the same key,
# or Android refuses to install the new version over the old one.
keytool -genkeypair -keystore my.keystore -alias olygym -keyalg RSA -validity 10950

# align + sign (zipalign/apksigner ship with the Android SDK build-tools)
zipalign -f -p 4 app-release-unsigned.apk aligned.apk
apksigner sign --ks my.keystore --ks-key-alias olygym --out OlyGym.apk aligned.apk
```

### Release notes for maintainers

- Bump `versionName`/`versionCode` in `android/app/build.gradle` per release; keep them in step
  with `frontend/package.json`. `versionCode` must strictly increase or the in-app updater (and
  Android itself) won't install the new build over the old one. The APK is *named* from
  `frontend/package.json`, so the two drifting apart shows up as a misnamed file.
- **License:** OlyGym is AGPL-3.0, which by itself sits badly with app-store terms of service.
  `NOTICE.md` carries an app-store exception (an additional permission under AGPL §7) granted by the
  copyright holder — relevant only if store distribution ever happens.
