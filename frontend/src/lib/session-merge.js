// Building one session's entries from a day of the dated-weeks plan.
//
// A day already holds the whole session (S.weeks[].days[].ex), so the old "combine several
// routines into one session" machinery collapses: one day = one session, and there is nothing
// to concatenate or to stamp per entry. `buildSessionEntries` does the work; this module only
// decides what a session with no day behind it is (freestyle) and what it is called.
//
// Imported by sheets.jsx; imports session-start.js (which pulls in history.js + progression.js).
// Nothing in that chain imports this file, so there is no cycle.
import { buildSessionEntries } from './session-start.js'
import { t } from './i18n-core.js'

/**
 * Build a session's entries and name from one day.
 *
 * `day` is a day object (`{ dow, name, ex, excludeFromProgression? }`) or null for freestyle —
 * which is an empty session the user fills as they go, not a named plan.
 *
 * Returns `{ entries, name }`.
 */
export function buildDayEntries(st, day) {
  return {
    entries: day ? buildSessionEntries(st, day) : [],
    name: day?.name || t('Freestyle'),
  }
}

/**
 * The name for a day that holds several planned sessions, from their names in order (ENG-12
 * rule):
 *   1–3 names → join with " + "          → "Rehab + Core"
 *   4+ names  → first two, then "+ N more" → "Rehab + Core + 2 more"
 * An empty list returns null. Nothing in the session path needs this any more (a day names
 * itself), but the printed plan still labels a legacy weekday that holds several routines.
 */
export function deriveSessionName(names) {
  if (!names.length) return null
  if (names.length <= 3) return names.join(' + ')
  return `${names[0]} + ${names[1]} + ${names.length - 2} more`
}
