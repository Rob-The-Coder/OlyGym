# Importing Data

OlyGym imports two things: the coach's plan (an Excel workbook, optionally picked from Google
Drive) and its own JSON backups. There is no server, so everything happens on the device.

## The coach's plan (Excel / Google Drive)

The app can read the coach's programme directly from `.xlsx`:

1. In the app, choose the plan import and pick the workbook — either a file on the device or one
   selected from **Google Drive**.
2. The app reads one sheet per week: the coach's exercises, sets, reps and loads, plus any notes.
3. His Italian gym shorthand (*strappo*, *girata*, *spinta in piedi*, `1+2`, and so on) is matched
   against the Catalyst catalogue. Movements he writes with `+` become a single **complex**.
4. Nothing is saved until you review it: every row is shown with the exercise the app matched it
   to, **his own row underneath it** — his words and his sets, reps and load as written, without
   Excel's trailing zero (`4.0` reads `4`) — and anything the catalogue has no word for is kept as a
   note, in his words. Tapping a row lets you swap the exercise it was read as, or correct its
   **sets, reps and weight**: the correction sheet takes the numbers in your display unit and his
   row stays printed underneath as the record of what he wrote. A movement he writes with `+` is one
   **complex** and is drawn as one card, with his numbers in its head and the movements numbered
   inside: correcting a complex's sets or load applies to every movement (he wrote them once for
   the whole row), while the reps are corrected for the movement you tapped. Ticking a row marks it
   read, and the summary counts what is left.
5. Routines are added to your plans when you confirm; each exercise keeps the coach's row it was
   read out of, so the week editor still shows it afterwards. An exercise-name correction — an alias
   for a name the matcher didn't know — is remembered for the next week's import, because the same
   phrases come back. A sets/reps/weight correction applies to the week you are importing and is not
   remembered: the same phrase comes back with different numbers (deloads, weeks of 5s).

The matcher only reads the workbook; it never modifies it. Reading `.xlsx` uses the platform's own
`DecompressionStream` and `DOMParser`, so no spreadsheet library is involved and no file leaves the
device.

## Backup & restore (JSON)

Settings → JSON export writes your whole local state to a single file: plans, the weekly schedule,
workouts, body weight and settings. Import it back on the same device, or on a new device, to
restore everything. The app also keeps **automatic backups**; on Android they go out through the OS
share sheet instead of a browser download.

- Weights are stored in **kilograms** only.
- The backup is the app's own format, not a plan-sharing format — there is no plan JSON share any
  more. To hand a plan to someone else, print it as a PDF from the plan screen.
- Cross-app history import (FitNotes, Strong, Hevy, Apple Health, CSV) was removed with the rest of
  the server-era features and is not supported.
