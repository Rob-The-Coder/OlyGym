# Security

OlyGym is a local-only, single-user app. It has no server, no accounts, no sign-in, no sync
and no telemetry: everything lives on the device, and none of your training data is sent
anywhere.

## What still applies

- **Your data is stored unencrypted.** The web build keeps it in `localStorage` under
  `gym_state_v1`; the Android build mirrors it to a private JSON file so WebView storage
  eviction can't lose it. Anyone who can read the device or the browser profile can read
  your whole log, body-weight history and notes.
- **Exported backups are plain JSON.** "Export backup (JSON)" — and the optional daily
  `opengym-backup-<date>.json` dropped in the Android Documents folder — is not encrypted
  and not password-protected. Treat a backup like the data itself: anyone you share it with,
  or anyone who can read that folder or a synced copy of it, can read everything in it.
- **A few network requests are deliberate, and none of them carry your data.** Exercise demo
  poster frames are hotlinked from `img.youtube.com` and videos embed from YouTube;
  importing a coach plan from your own Google Drive is user-initiated with your OAuth token;
  the Android updater checks GitHub Releases and may download an APK. There is no account and
  no endpoint that receives your training log.
- **The Android updater trusts its release source.** It verifies a downloaded APK against its
  published SHA-256 checksum before opening the installer. Only install builds you trust.

## Reporting

This is a personal fork of openGym. If you find a real issue, open a confidential issue at
<https://gitlab.com/DuarteSantos8/opengym/-/issues/new> (tick "This issue is confidential")
and include the version, steps to reproduce and impact.
