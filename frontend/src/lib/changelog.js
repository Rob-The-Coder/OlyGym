// The bundled "What's new" list: one entry per release, newest first, a few short lines each.
// A release bumps the newest entry's version and frontend/package.json together; entries stay
// newest first with one short line per bullet, so the dialog reads in the order things shipped.
//
// Bullets are English source strings rendered through t(bullet). check-source-strings.mjs only
// sees literals written inside a t() call, so copy held in this module is invisible to it —
// changelog.test.js is the guard that every bullet is a key in frontend/src/locales/it.js.

import { compareSemver } from './update.js'

export const CHANGELOG = [
  {
    version: '1.3.12',
    bullets: [
      'Per-set weights that follow your progression',
      'Edit sets, reps and weight on an imported plan',
      'Export your history as a CSV file',
      'PR badges on the lifts you beat',
      'Undo a workout you deleted by mistake',
      'A rest timer between sets',
    ],
  },
]

// The newest release, or null when there is nothing to show.
export function latestEntry() {
  return CHANGELOG[0] || null
}

// Every release newer than the acknowledged version, newest first. Nothing acknowledged yet
// (null, undefined or an empty string) reads as "show everything".
export function changesSince(ackVersion) {
  return CHANGELOG.filter(entry => !ackVersion || compareSemver(entry.version, ackVersion) > 0)
}

// Whether the running version has not been acknowledged yet.
export function shouldAnnounce(ackVersion, currentVersion) {
  return !ackVersion || compareSemver(currentVersion, ackVersion) > 0
}
