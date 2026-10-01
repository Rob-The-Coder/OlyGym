// One-way, additive migration from the repeating plan (S.routines + S.week) to the dated weeks
// model. It reads the old fields and returns only the new `weeks` array: nothing on the input is
// mutated or deleted, no reader changes, and running it twice over the same state gives the same
// shape (bar the fresh ids). Later stages switch the readers over and drop the old fields.
import { isoOf, startOfWeek, weekStartOf, uid } from './format.js'
import { addDays } from './weeks.js'

/**
 * Which weekday the i-th day of an imported week lands on: Mon/Wed/Fri first — the days
 * CoachImport schedules a fresh week on (WEEK_PLAN = [1,3,5]) — then the remaining weekdays in
 * order. Wraps past seven, which only matters for a week with more than seven routines.
 */
const WEEK_ORDER = [1, 3, 5, 2, 4, 6, 0]
export const weekDayForPosition = i => WEEK_ORDER[i % 7]

// How reviewWeek names a coached day: "Giorno 3 · 23-29 marzo". The separator is the app's own
// middle dot, but a hand-edited name may carry a pipe instead.
const COACH_DAY = /^Giorno\s+(\d+)\s*[·|]\s*(.+)$/i

const dayOf = (r, dow) => ({
  dow,
  name: r.name,
  ex: r.ex,
  // Written only when true, so a day that is not excluded carries no key at all — the same
  // convention the routines themselves use.
  ...(r.excludeFromProgression === true ? { excludeFromProgression: true } : {}),
})

const byStart = (a, b) => (a.startIso < b.startIso ? -1 : a.startIso > b.startIso ? 1 : 0)

/**
 * Whether a profile still needs its repeating plan read into dated weeks: it has a plan of the
 * old shape and no weeks yet. The store asks this on load, so the old-field read lives here
 * rather than in the store.
 */
export const needsWeekMigration = (S = {}) => !(S.weeks || []).length && (S.routines || []).length > 0

/**
 * `S` (old plan) → `weeks` array. `now` is injected so a test can pin "today"; production calls
 * it with the clock. Three groups, in the order they are looked for:
 *
 *   1. the routines scheduled in S.week — one week starting on the current week's first day,
 *      each scheduled routine as a day on its own weekday. Several routines on one weekday give
 *      several days with the same dow; dayFor() takes the first.
 *   2. the coach's imported routines (named "Giorno N · label", not scheduled) — one week per
 *      label, on consecutive past weeks ending with the one before the current week. Ordering is
 *      best-effort: those routines carry a day number, not a date, so the label order is all
 *      there is, and the user reorders if the coach meant another week.
 *   3. everything left over — one week after the current one, so nothing is silently dropped.
 *      Leftover routines have no weekday information at all, so they get the same Mon/Wed/Fri
 *      order, and the week is left unnamed for the UI to label.
 *
 * The result is sorted by startIso. Week ids are fresh, so re-running this does not collide with
 * weeks already in state.
 */
export function migrateToWeeks(S = {}, now = new Date()) {
  const routines = S.routines || []
  const week = S.week || {}
  const byId = new Map(routines.map(r => [r.id, r]))
  // todayISO() reads the clock; this is the same three lines, fed from `now`.
  const currentMonday = isoOf(startOfWeek(isoOf(now), weekStartOf(S)))
  const out = []

  // 1. The schedule S.week holds: weekday → routine ids, in the order they were put on the day.
  const scheduled = new Set()
  const currentDays = []
  for (const key of Object.keys(week)) {
    const dow = Number(key)
    if (!(dow >= 0 && dow <= 6)) continue
    for (const id of [].concat(week[key] || [])) {
      const r = byId.get(id)
      if (!r) continue
      scheduled.add(id)
      currentDays.push(dayOf(r, dow))
    }
  }
  if (currentDays.length) out.push({ id: uid(), startIso: currentMonday, name: '', days: currentDays })

  // 2. The coach's weeks. Grouped by the label he wrote ("23-29 marzo"), each label one week.
  const groups = new Map()
  for (const r of routines) {
    if (scheduled.has(r.id)) continue
    const m = COACH_DAY.exec(String(r.name || '').trim())
    if (!m) continue
    const label = m[2].trim()
    if (!groups.has(label)) groups.set(label, [])
    groups.get(label).push({ n: Number(m[1]), r })
  }
  const labels = [...groups.keys()].sort()
  labels.forEach((label, gi) => {
    const days = groups.get(label)
      .sort((a, b) => a.n - b.n)
      .map((g, i) => dayOf(g.r, weekDayForPosition(i)))
    out.push({ id: uid(), startIso: addDays(currentMonday, -7 * (labels.length - gi)), name: label, days })
  })

  // 3. Anything not scheduled and not the coach's: one week, so it is still reachable.
  const leftovers = routines.filter(r => !scheduled.has(r.id) && !COACH_DAY.test(String(r.name || '').trim()))
  if (leftovers.length) {
    out.push({
      id: uid(),
      startIso: addDays(currentMonday, 7),
      name: '',
      days: leftovers.map((r, i) => dayOf(r, weekDayForPosition(i))),
    })
  }

  return out.sort(byStart)
}
