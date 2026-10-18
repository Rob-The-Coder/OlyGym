// The History screen's export: the training log flattened to one row per completed set, in the
// columns a spreadsheet or a script can read. Separate from lib/history.js because that file reads
// a session back for the app (labels, volume, PRs); this one is a file format, and its shape must
// stay stable even when the UI changes.
import { EXIDX } from './exercises.js'
import { isBw } from './history.js'
import { phaseForSet } from './workout-model.js'

// The header is part of the file format: stable English, snake_case, never localized, and frozen
// so a caller cannot reorder the columns of a file other tools already read.
export const HISTORY_CSV_HEADER = Object.freeze([
  'workout_id', 'date', 'start', 'end', 'workout', 'workout_note', 'entry',
  'exercise', 'exercise_id', 'set', 'phase', 'reps', 'seconds', 'weight',
  'unit', 'bodyweight', 'rir', 'rpe', 'pr', 'entry_note',
])

const isObject = value => value != null && typeof value === 'object' && !Array.isArray(value)
const finite = value => typeof value === 'number' && Number.isFinite(value) ? value : ''
const text = value => value == null ? '' : value

// A stored log may carry epoch milliseconds (sessions logged in the app) or a date string
// (imported data); both become the same ISO 8601 UTC column. Anything unparseable is left empty
// rather than shown as "Invalid Date".
function isoOrBlank(value) {
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) return ''
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? '' : date.toISOString()
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? '' : date.toISOString()
  }
  return ''
}

function startKey(value) {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null
  if (typeof value === 'string' && value.trim() !== '') {
    const ms = Date.parse(value)
    return Number.isNaN(ms) ? null : ms
  }
  return null
}

// Oldest first, on a copy: a flat log reads chronologically, and the screen's newest-first order
// is a presentation decision, not the file's. Stable sort, so two sessions at the same date and
// start keep the order they were stored in.
function oldestFirst(workouts) {
  return workouts
    .filter(isObject)
    .map((workout, index) => ({ workout, index }))
    .sort((a, b) => {
      const ad = a.workout.d == null ? '' : String(a.workout.d)
      const bd = b.workout.d == null ? '' : String(b.workout.d)
      if (ad < bd) return -1
      if (ad > bd) return 1
      const av = startKey(a.workout.start)
      const bv = startKey(b.workout.start)
      if (av == null && bv == null) return a.index - b.index
      // An unknown start cannot be ordered against a known one; keep those rows at the end of
      // their date instead of inventing a time for them.
      if (av == null) return 1
      if (bv == null) return -1
      return av - bv || a.index - b.index
    })
    .map(item => item.workout)
}

export function historyRows(workouts, unit = 'kg') {
  if (!Array.isArray(workouts)) return [HISTORY_CSV_HEADER]
  const rows = [HISTORY_CSV_HEADER]
  for (const workout of oldestFirst(workouts)) {
    const entries = Array.isArray(workout.entries) ? workout.entries : []
    const prs = Array.isArray(workout.prs) ? workout.prs : []
    entries.forEach((entry, entryIndex) => {
      if (!isObject(entry)) return
      const target = isObject(entry.target) ? entry.target : {}
      const exercise = EXIDX[entry.id]?.n ?? text(entry.id)
      const sets = Array.isArray(entry.sets) ? entry.sets : []
      sets.forEach((set, setIndex) => {
        if (!isObject(set) || set.done !== true) return
        rows.push([
          text(workout.id),
          text(workout.d),
          isoOrBlank(workout.start),
          isoOrBlank(workout.end),
          text(workout.name),
          text(workout.note),
          entryIndex + 1,
          exercise,
          text(entry.id),
          setIndex + 1,
          phaseForSet(set),
          finite(set.r),
          finite(set.sec),
          finite(set.w),
          text(unit),
          isBw({ ...target, id: entry.id }),
          finite(set.rir),
          finite(set.rpe),
          prs.includes(entry.id),
          text(entry.note),
        ])
      })
    })
  }
  return rows
}

// The date is the caller's (today, in the user's own timezone) so the filename matches the day
// the person thinks they exported, not the browser's UTC day.
export function historyFilename(dateIso) {
  return `opengym-history-${dateIso}.csv`
}
