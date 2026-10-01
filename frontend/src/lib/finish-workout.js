// The persisted boundary for a finished session. Keep this pure so compatibility tests can
// exercise the exact shape the UI writes without mounting React or mutating store state.
import { bestWeightForEntry } from './history.js'
import { hasCompletedWork } from './workout-model.js'

export function buildCompletedWorkout(active, { end = Date.now(), prs = [], snapshotFor } = {}) {
  const entries = (active?.entries || []).map(entry => {
    const completed = {
      id: entry.id,
      sets: entry.sets,
      topW: bestWeightForEntry(entry) || null,
      target: entry.target || null,
      // Whether this entry counts for progression. Written only when true, so a normal session
      // is byte-for-byte the shape it always was. Without this the whitelist drops it at finish.
      // There is no `rid`: a day is atomic, so there is nothing to group entries by.
      ...(entry.noProg === true ? { noProg: true } : {}),
    }
    const snapshot = typeof snapshotFor === 'function' ? snapshotFor(entry) : null
    if (snapshot && typeof snapshot === 'object' && !Array.isArray(snapshot) && Object.keys(snapshot).length) {
      completed.muscleSnapshot = { ...snapshot }
    }
    // What you typed about this exercise today, and whether you asked to see it again next
    // time. Written only when there is something to keep, so an untouched entry is byte-for-byte
    // the shape it always was.
    const note = (entry.note || '').trim()
    if (note) {
      completed.note = note
      if (entry.notePin) completed.notePin = true
    }
    return completed
  }).filter(entry => entry.sets.some(hasCompletedWork))

  const sessionNote = (active?.note || '').trim()
  // Legacy `w.excludeFromProgression` mirror: kept for older builds and external readers, but
  // it only makes sense when the *whole* session is excluded. Derived from the completed
  // entries, not read from `active` (which no longer carries the flag). A mixed session omits
  // it — that case is new territory only the per-entry `noProg` readers handle.
  const allNoProg = entries.length > 0 && entries.every(e => e.noProg === true)

  return {
    id: active.id,
    d: active.d,
    start: active.start,
    end,
    // Where the session came from in the dated weeks: the week it belongs to and the weekday it
    // was planned on, both null for freestyle. Older records carry `routineIds`/`routineId`
    // instead and are still read that way; nothing writes those any more.
    weekId: active.weekId ?? null,
    dow: active.dow ?? null,
    name: active.name,
    bw: active.bw,
    entries,
    prs,
    ...(allNoProg ? { excludeFromProgression: true } : {}),
    ...(sessionNote ? { note: sessionNote } : {}),
  }
}
