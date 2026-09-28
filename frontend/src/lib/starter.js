// The starter-plan catalog. Training data only: the chooser's plan names and descriptions are
// written as string literals inside t() calls in sheets.jsx, because check-source-strings.mjs
// only finds them there — copy parked in here would silently ship English in every language.
//
// A routine is [key, name, emoji, [[exerciseId, sets, reps], …]]. The key is what a plan's
// schedule points at, so a weekday never depends on the position of a routine in the array.
// Names stay canonical English — they become ordinary user routines, which are not translated.
import { uid } from './format.js'

// Exercise ids below are from the OlyGym catalogue (see exercises-data.js): "wl" + the
// Catalyst Athletics exercise-page id. Snatch = wl58, Power snatch = wl61, Snatch pull = wl97,
// Clean = wl59, Power clean = wl67, Clean pull = wl98, Clean-jerk = wl76, Push jerk = wl405,
// Split jerk = wl60, Push press = wl87, Back squat = wl77, Front squat = wl78,
// Overhead squat = wl79, Deadlift = wl604, Romanian deadlift = wl101, Good morning = wl183,
// Bench press = wl806, Bent row = wl171, Pull-up = wl39.
const PPL = [
  ['snatch', 'Snatch Day', 'barbell', [['wl58', 5, 3], ['wl97', 3, 3], ['wl79', 3, 5], ['wl78', 3, 5]]],
  ['cj', 'Clean & Jerk Day', 'barbell', [['wl59', 5, 3], ['wl405', 4, 3], ['wl98', 3, 3], ['wl77', 3, 5]]],
  ['squat-pull', 'Squat & Pull Day', 'legs', [['wl77', 5, 5], ['wl604', 3, 5], ['wl183', 3, 8], ['wl171', 3, 10]]]
]

const UPPER_LOWER = [
  ['tech-a', 'Technique A', 'barbell', [['wl61', 5, 3], ['wl97', 3, 3], ['wl79', 3, 5]]],
  ['strength-a', 'Strength A', 'legs', [['wl77', 4, 5], ['wl101', 3, 8], ['wl39', 3, 8]]],
  ['tech-b', 'Technique B', 'barbell', [['wl67', 5, 3], ['wl405', 4, 3], ['wl98', 3, 3]]],
  ['strength-b', 'Strength B', 'legs', [['wl78', 4, 5], ['wl604', 3, 5], ['wl806', 3, 8]]]
]

const FULL_BODY = [
  ['fb-a', 'Full Body A', 'figureStrength', [['wl58', 4, 3], ['wl77', 3, 5], ['wl171', 3, 10]]],
  ['fb-b', 'Full Body B', 'figureStrength', [['wl76', 4, 2], ['wl78', 3, 5], ['wl39', 3, 8]]],
  ['fb-c', 'Full Body C', 'figureStrength', [['wl78', 3, 5], ['wl604', 3, 5], ['wl806', 3, 8]]]
]

const FIVE_BY_FIVE = [
  ['5x5-a', '5×5 A', 'barbell', [['wl77', 5, 5], ['wl806', 5, 5], ['wl171', 5, 5]]],
  ['5x5-b', '5×5 B', 'barbell', [['wl78', 5, 5], ['wl87', 5, 5], ['wl604', 5, 5]]],
  ['5x5-c', '5×5 C', 'barbell', [['wl77', 5, 5], ['wl183', 5, 5], ['wl39', 5, 5]]]
]

// [weekday, routineKey] — weekday is a DAYN index, so 1 is Monday. Fixed weeks only: every
// plan repeats the same seven days, which is all the weekly plan model can represent.
const PLANS = {
  ppl: { routines: PPL, schedule: [[1, 'snatch'], [3, 'cj'], [5, 'squat-pull']] },
  'upper-lower': { routines: UPPER_LOWER, schedule: [[1, 'tech-a'], [2, 'strength-a'], [4, 'tech-b'], [5, 'strength-b']] },
  'full-body': { routines: FULL_BODY, schedule: [[1, 'fb-a'], [3, 'fb-b'], [5, 'fb-c']] },
  '5x5': { routines: FIVE_BY_FIVE, schedule: [[1, '5x5-a'], [3, '5x5-b'], [5, '5x5-c']] }
}

const build = routines =>
  routines.map(([, name, emoji, list]) => ({ id: uid(), name, emoji, ex: list.map(([id, sets, reps]) => ({ id, sets, reps, weight: 0 })) }))

// Fresh routine objects (new ids) — [snatch, clean & jerk, squat & pull]. The demo build seeds a
// history on top of exactly these three, so this entry point keeps its shape.
export const starterRoutines = () => build(PPL)

// [{ id, days }] for the chooser. The day count is read off the schedule rather than stored
// beside it, so the two can never disagree.
export const starterPlanOptions = () =>
  Object.entries(PLANS).map(([id, { schedule }]) => ({ id, days: schedule.length }))

// The weekdays a plan would claim, or null for an unknown id.
export const starterPlanDays = id => PLANS[id]?.schedule.map(([day]) => day) ?? null

// Fresh routines plus the weekdays to put them on, or null for an unknown id — a caller that
// treats null as "change nothing" can never half-apply a plan.
export const buildStarterPlan = id => {
  const plan = PLANS[id]
  if (!plan) return null
  const routines = build(plan.routines)
  // key → the id just minted for it, so the schedule below names its routine
  const byKey = Object.fromEntries(plan.routines.map(([key], i) => [key, routines[i].id]))
  return { routines, schedule: plan.schedule.map(([day, key]) => ({ day, routineId: byKey[key] })) }
}
