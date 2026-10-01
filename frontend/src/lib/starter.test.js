import { describe, expect, it } from 'vitest'
import { EXIDX } from './exercises.js'
import { buildStarterPlan, starterPlanDays, starterPlanOptions, starterRoutines } from './starter.js'
import { isoOf, startOfWeek, todayISO } from './format.js'

// The approved prescription, written out again rather than imported: a test that reads the
// same table as the code would pass no matter what that table said. [weekday, name, sets].
const APPROVED = {
  ppl: [
    [1, 'Snatch Day', [['wl58', 5, 3], ['wl97', 3, 3], ['wl79', 3, 5], ['wl78', 3, 5]]],
    [3, 'Clean & Jerk Day', [['wl59', 5, 3], ['wl405', 4, 3], ['wl98', 3, 3], ['wl77', 3, 5]]],
    [5, 'Squat & Pull Day', [['wl77', 5, 5], ['wl604', 3, 5], ['wl183', 3, 8], ['wl171', 3, 10]]],
  ],
  'upper-lower': [
    [1, 'Technique A', [['wl61', 5, 3], ['wl97', 3, 3], ['wl79', 3, 5]]],
    [2, 'Strength A', [['wl77', 4, 5], ['wl101', 3, 8], ['wl39', 3, 8]]],
    [4, 'Technique B', [['wl67', 5, 3], ['wl405', 4, 3], ['wl98', 3, 3]]],
    [5, 'Strength B', [['wl78', 4, 5], ['wl604', 3, 5], ['wl806', 3, 8]]],
  ],
  'full-body': [
    [1, 'Full Body A', [['wl58', 4, 3], ['wl77', 3, 5], ['wl171', 3, 10]]],
    [3, 'Full Body B', [['wl76', 4, 2], ['wl78', 3, 5], ['wl39', 3, 8]]],
    [5, 'Full Body C', [['wl78', 3, 5], ['wl604', 3, 5], ['wl806', 3, 8]]],
  ],
  '5x5': [
    [1, '5×5 A', [['wl77', 5, 5], ['wl806', 5, 5], ['wl171', 5, 5]]],
    [3, '5×5 B', [['wl78', 5, 5], ['wl87', 5, 5], ['wl604', 5, 5]]],
    [5, '5×5 C', [['wl77', 5, 5], ['wl183', 5, 5], ['wl39', 5, 5]]],
  ],
}

const shape = r => r.ex.map(e => [e.id, e.sets, e.reps])
// The day's exercises, whichever list they came from.
const dayShape = d => d.ex.map(e => [e.id, e.sets, e.reps])

describe('starter plan catalog', () => {
  it('offers exactly the four plans, with the day count read off the schedule', () => {
    expect(starterPlanOptions()).toEqual([
      { id: 'ppl', days: 3 }, { id: 'upper-lower', days: 4 },
      { id: 'full-body', days: 3 }, { id: '5x5', days: 3 },
    ])
    for (const { id, days } of starterPlanOptions()) expect(starterPlanDays(id)).toHaveLength(days)
  })

  it('changes nothing for an unknown plan id', () => {
    expect(buildStarterPlan('nope')).toBeNull()
    expect(buildStarterPlan(undefined)).toBeNull()
    // A click handler wired straight to a loader would hand the event in as the plan.
    expect(buildStarterPlan({ type: 'click' })).toBeNull()
    expect(starterPlanDays('nope')).toBeNull()
  })
})

describe.each(Object.keys(APPROVED))('%s', planId => {
  const approved = APPROVED[planId]

  it('builds the approved exercises, sets and reps in order', () => {
    const week = buildStarterPlan(planId)
    expect(week.days.map(d => d.name)).toEqual(approved.map(([, name]) => name))
    week.days.forEach((d, i) => expect(dayShape(d)).toEqual(approved[i][2]))
  })

  it('puts each day on its approved weekday', () => {
    const week = buildStarterPlan(planId)
    expect(week.days.map(d => [d.dow, d.name])).toEqual(approved.map(([day, name]) => [day, name]))
  })

  it('is a dated week of the plan, carrying no customs', () => {
    const week = buildStarterPlan(planId)
    expect(week.id).toBeTruthy()
    expect(week.startIso).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(week.startIso).toBe(isoOf(startOfWeek(todayISO(), 1)))
    expect(week.customEx).toEqual([])
  })

  it('references only real exercises and starts every one at weight 0', () => {
    for (const d of buildStarterPlan(planId).days) {
      for (const e of d.ex) {
        expect(EXIDX[e.id], e.id).toBeTruthy()
        expect(e.sets).toBeGreaterThan(0)
        expect(e.reps).toBeGreaterThan(0)
        expect(e.weight).toBe(0)
      }
    }
  })

  it('mints fresh ids and independent objects on every build', () => {
    const first = buildStarterPlan(planId)
    const second = buildStarterPlan(planId)
    expect(first.id).not.toBe(second.id)
    expect(first.days[0].ex[0]).not.toBe(second.days[0].ex[0])
    // and the static definition survives a caller mutating what it got back
    first.days[0].ex[0].sets = 99
    expect(buildStarterPlan(planId).days[0].ex[0].sets).toBe(approved[0][2][0][1])
  })

  it('places the week in the profile’s current one', () => {
    // A Sunday-start profile gets the week anchored on Sunday, or the day it is now would fall
    // outside the week it just loaded.
    const sunday = buildStarterPlan(planId, { now: new Date('2026-03-04T12:00:00'), weekStart: 0 })
    expect(sunday.startIso).toBe('2026-03-01')
    const monday = buildStarterPlan(planId, { now: new Date('2026-03-04T12:00:00'), weekStart: 1 })
    expect(monday.startIso).toBe('2026-03-02')
  })
})

describe('starterRoutines (the demo build entry point)', () => {
  it('still returns snatch, clean & jerk and squat/pull unchanged', () => {
    const routines = starterRoutines()
    expect(routines.map(r => r.name)).toEqual(['Snatch Day', 'Clean & Jerk Day', 'Squat & Pull Day'])
    routines.forEach((r, i) => expect(shape(r)).toEqual(APPROVED.ppl[i][2]))
    expect(routines.map(r => r.emoji)).toEqual(['barbell', 'barbell', 'legs'])
  })

  it('mints fresh ids on every invocation', () => {
    const first = starterRoutines().map(r => r.id)
    const second = starterRoutines().map(r => r.id)
    expect(new Set([...first, ...second]).size).toBe(6)
  })
})
