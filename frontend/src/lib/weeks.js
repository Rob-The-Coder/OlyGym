// The dated weeks model (S.weeks) — the replacement for the repeating S.week/S.dayPlan pair.
//
//   S.weeks = [{ id, startIso: 'YYYY-MM-DD' /* first day of that week */, name, days: [
//     { dow /* getDay() 1=Mon..0=Sun */, name, ex: [...], excludeFromProgression?: true }
//   ]}]
//
// Unlike S.week, a week here is one concrete calendar week that never repeats: no date arithmetic
// and no per-date override — the week you are in is the week whose startIso covers today.
//
// The session path reads this: a day is the whole session, and `dayFor` is what Start, Home's
// week strip and the day reminder resolve. S.routines/S.week/S.dayPlan survive only as the model
// the plan producers (starter plan, shared plan files, the coach import, the demo) still write,
// with migrateToWeeks deriving `weeks` from them on load.
import { isoOf } from './format.js'

/** A date `n` days away, through the same noon-local idiom as format.js's date math. */
export function addDays(iso, n) {
  const d = new Date(iso + 'T12:00:00')
  d.setDate(d.getDate() + n)
  return isoOf(d)
}

/** A copy of the weeks, oldest first. `YYYY-MM-DD` string compare is chronological. */
export const weeksInOrder = S => (S.weeks || []).slice()
  .sort((a, b) => (a.startIso < b.startIso ? -1 : a.startIso > b.startIso ? 1 : 0))

/**
 * The week `iso` falls in, or null — covering from its startIso up to, but not including, the day
 * seven later. Only the date on the week object matters, so a gap between weeks is a real gap.
 *
 * Should two weeks ever overlap, the one starting latest wins: it is the more recently written
 * plan, and "which week am I in" has to have one answer either way.
 */
export function weekFor(S, iso) {
  let found = null
  for (const w of weeksInOrder(S)) {
    if (w.startIso <= iso && iso < addDays(w.startIso, 7)) found = w
  }
  return found
}

/**
 * The day planned for a date — the day object itself, or null for a rest day, a gap between
 * weeks, or a weekday this week leaves out.
 *
 * The weekday comes from `iso + 'T12:00:00'` rather than `new Date(iso)`: a bare date string is
 * parsed as UTC midnight, which reads as the day before anywhere west of Greenwich (the same
 * reason startOfWeek and isoOf pin noon). When a weekday carries several days
 * (migration emits one per scheduled routine) the first is the day; see migrate-weeks.js.
 */
export function dayFor(S, iso) {
  const w = weekFor(S, iso)
  if (!w) return null
  const dow = new Date(iso + 'T12:00:00').getDay()
  return (w.days || []).find(d => d.dow === dow) || null
}
