// Competition (meet) helpers.
//
// A competition is not a training session. It is a dated event with a bodyweight category and
// up to three snatch and three clean & jerk attempts, each judged good or no lift, and a total
// that only exists once something was made in both lifts. It is kept apart from the training log
// on purpose: a competition lift is not a workout set, and folding the two together would make
// every training curve answer a question it was never asked.
//
// Everything here is pure so the decisions (what a total is, which meet is next) are verifiable
// without mounting React, per CONTRIBUTING.md.
import { uid } from './format.js'

export const MAX_ATTEMPTS = 3

// The categories a federation runs are the federation's, and they change every few years —
// this is only the list the app starts from. They live in S.classes so they can be edited
// without a code change (Settings → Competition), and a logged meet keeps the string it was
// saved with, so a new set of categories never relabels an old result.
export const DEFAULT_CLASSES = {
  male: ['60', '65', '70', '75', '85', '95', '110', '110+'],
  female: [],
}

/** The profile's two lists, normalised. A profile from before the split carries one flat list,
 *  which reads as the male one; a missing shape falls back to the shipped default. */
export function classLists(classes) {
  if (Array.isArray(classes)) return { male: cleanClasses(classes), female: [] }
  return {
    male: Array.isArray(classes && classes.male) ? classes.male : [...DEFAULT_CLASSES.male],
    female: Array.isArray(classes && classes.female) ? classes.female : [],
  }
}

/** The categories the current body competes in. */
export const listFor = S => classLists(S && S.classes)[S && S.body === 'female' ? 'female' : 'male']

/** The category choices for a SelectRow, each already labelled. */
export const classOptions = list =>
  (Array.isArray(list) ? list : []).map(c => ({ value: c, label: c + ' kg' }))

/** The list as saved: trimmed, empty entries dropped, duplicates removed, order kept. */
export function cleanClasses(list) {
  const out = []
  for (const raw of list || []) {
    const c = String(raw == null ? '' : raw).trim()
    if (c && !out.includes(c)) out.push(c)
  }
  return out
}

// An attempt counts only when it was both made and carried a weight. A no-lift opener that was
// heavier than the made attempt must never leak into a best (the classic "opened at 120, missed,
// made 115" case).
export const attemptMade = a => !!(a && a.made && Number(a.w) > 0)

/** Heaviest made attempt, or null when the lift had none. */
export function bestAttempt(attempts) {
  let best = null
  for (const a of attempts || []) {
    if (!attemptMade(a)) continue
    best = best == null ? a.w : Math.max(best, a.w)
  }
  return best
}

/** Best snatch + best clean & jerk, or null unless something was made in both lifts. */
export function totalOf(meet) {
  const s = bestAttempt(meet && meet.snatch)
  const c = bestAttempt(meet && meet.cj)
  return s != null && c != null ? Math.round((s + c) * 100) / 100 : null
}

export const hasResult = meet => totalOf(meet) != null
export const hasAttempts = meet =>
  ((meet && meet.snatch) || []).length + ((meet && meet.cj) || []).length > 0

const byDateAsc = (a, b) => String((a && a.d) || '').localeCompare(String((b && b.d) || ''))

/** Every meet, oldest first. */
export const sortedMeets = list => [...(list || [])].sort(byDateAsc)

/** Meets dated today or later, soonest first. */
export const upcomingMeets = (list, today) =>
  sortedMeets(list).filter(m => String(m.d || '') >= String(today || ''))

/** Meets already gone, most recent first. */
export const pastMeets = (list, today) =>
  sortedMeets(list).filter(m => String(m.d || '') < String(today || '')).reverse()

/** The soonest meet still ahead, or null. */
export const nextMeet = (list, today) => upcomingMeets(list, today)[0] || null

/** Whole days from `today` to the meet; negative once it is behind you, null when unreadable. */
export function daysUntil(meet, today) {
  if (!meet || !meet.d) return null
  // Noon local, so a DST change cannot round the count off by a day.
  const a = new Date(String(meet.d) + 'T12:00:00')
  const b = new Date(String(today) + 'T12:00:00')
  if (Number.isNaN(a.getTime()) || Number.isNaN(b.getTime())) return null
  return Math.round((a - b) / 86400000)
}

/** The best made snatch, clean & jerk and total across every meet, each null when never done. */
export function competitionBests(meets) {
  let snatch = null, cj = null, total = null
  for (const m of meets || []) {
    const s = bestAttempt(m && m.snatch)
    if (s != null && (snatch == null || s > snatch)) snatch = s
    const c = bestAttempt(m && m.cj)
    if (c != null && (cj == null || c > cj)) cj = c
    const t = totalOf(m)
    if (t != null && (total == null || t > total)) total = t
  }
  return { snatch, cj, total }
}

/** Three rows for the editor, whatever the meet holds — the shape the form always shows. */
export const attemptRows = (meet, lift) =>
  Array.from({ length: MAX_ATTEMPTS }, (_, i) => {
    const a = ((meet && meet[lift]) || [])[i]
    // An attempted-but-unjudged row starts a good lift. Most attempts are made, so defaulting
    // to "no lift" made the whole form read as failure; and a row with no weight never counts
    // anyway (attemptMade), so the default is safe until a number lands on it.
    if (!a) return { w: 0, made: true }
    return { w: Number(a.w) || 0, made: a.made !== false }
  })

/** Drop empty rows and keep only the fields the app reads back. A weightless row is not an attempt. */
export const cleanAttempts = attempts =>
  (attempts || [])
    .map(a => ({ w: Math.round((Number(a && a.w) || 0) * 10) / 10, made: !!(a && a.made) }))
    .filter(a => a.w > 0)

/** A new, empty meet dated to today. */
export const blankMeet = today =>
  ({ id: uid(), d: today || '', name: '', place: '', class: null, bw: null, snatch: [], cj: [], placing: null, note: '' })

/** Put a meet back in the list, replacing the one with its id, kept in date order. */
export function upsertMeet(list, meet) {
  return sortedMeets([...(list || []).filter(m => m.id !== meet.id), meet])
}

export function removeMeet(list, id) {
  return (list || []).filter(m => m.id !== id)
}
