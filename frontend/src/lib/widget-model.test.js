import { describe, expect, it } from 'vitest'
import { mix, widgetModel } from './widget-model.js'
import { DAYN, isoOf, startOfWeek } from './format.js'
import { addDays } from './weeks.js'

// Every assertion pins the clock (NOW), so the week boundary, the "logged today" lookup and the
// next-training-day nudge read the same on any machine as they did when this was written.
const NOW = new Date('2026-10-14T10:00:00')     // a Wednesday
const TODAY = isoOf(NOW)
const WEEK_START = isoOf(startOfWeek(TODAY, 1))
const dowOf = iso => new Date(iso + 'T12:00:00').getDay()

const ex = (id, sg) => ({ id, sets: 3, reps: 3, weight: 60, ...(sg ? { sg } : {}) })
const day = (iso, name, list) => ({ dow: dowOf(iso), name, ex: list })
const state = (days, extra = {}) => ({
  weeks: [{ id: 'w1', startIso: WEEK_START, name: 'Week', days }],
  workouts: [], active: null, customEx: [], unit: 'kg', ...extra,
})
const worked = sets => ({ id: 'wl58', sets: sets.map(done => ({ w: 60, r: 3, done })) })
const today = (S, view) => widgetModel(S, NOW, view).days[TODAY]

describe('widget model', () => {
  it('keys every day of the current week by ISO date', () => {
    const m = widgetModel(state([]), NOW)
    const keys = Object.keys(m.days)
    expect(keys).toHaveLength(7)
    expect(keys).toContain(TODAY)
    expect(keys[0]).toBe(WEEK_START)
    expect(keys[6]).toBe(addDays(WEEK_START, 6))
    expect(m.today).toBe(TODAY)
  })

  it('leads with the same overline the Home hero carries', () => {
    expect(today(state([])).overline)
      .toBe('Today · ' + new Date(TODAY + 'T12:00:00').toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' }))
  })

  it('names the session, its size, and the one exercise it starts with', () => {
    const days = [day(TODAY, 'Snatch day', [ex('wl58'), ex('wl77', 'g1'), ex('wl78', 'g1'), ex('wl98')])]
    const t = today(state(days))
    expect(t.icon).toBe('dumbbell')
    expect(t.title).toBe('Snatch day')
    expect(t.subtitle).toBe('4 exercises · 12 sets')
    expect(t.now).toBe('Snatch')
    expect(t.scheme).toBe('3 × 3 · 60 kg')
    expect(t.session).toBeUndefined()
  })

  it('names the complex first when that is what starts the day', () => {
    const days = [day(TODAY, 'A', [ex('wl77', 'g1'), ex('wl78', 'g1')])]
    expect(today(state(days)).now).toBe('Back Squat')
  })

  it('nudges the next session on a rest day, under a moon', () => {
    const tomorrow = addDays(TODAY, 1)
    const days = [day(tomorrow, 'Clean day', [ex('wl98')])]
    const t = today(state(days))
    expect(t.icon).toBe('moon')
    expect(t.title).toBe('Rest day')
    expect(t.subtitle).toBe('')
    expect(t.now).toBe('')
    expect(t.scheme).toBe('Next: ' + DAYN[dowOf(tomorrow)] + ', Clean day')
  })

  it('says what a finished day totalled instead of naming an exercise', () => {
    const days = [day(TODAY, 'Snatch day', [ex('wl58')])]
    const S = state(days, { workouts: [{ d: TODAY, name: 'Snatch day', entries: [worked([true, true])] }] })
    const t = today(S)
    expect(t.icon).toBe('check')
    expect(t.scheme).toBe('Done · 2 sets')
    expect(t.session).toBeUndefined()
  })

  it('carries the running session, with a line per set and the position', () => {
    const days = [day(TODAY, 'Snatch day', [ex('wl58'), ex('wl77', 'g1'), ex('wl78', 'g1')])]
    const entries = [
      { id: 'wl58', target: { id: 'wl58' }, sets: [{ w: 70, r: 2, done: true }, { w: 70, r: 2, done: false }] },
      { id: 'wl77', sg: 'g1', target: { id: 'wl77' }, sets: [{ w: 100, r: 5, done: false }] },
      { id: 'wl78', sg: 'g1', target: { id: 'wl78' }, sets: [{ w: 80, r: 5, done: false }] },
    ]
    const S = state(days, { active: { d: TODAY, name: 'Snatch day', cur: 1, entries } })
    const t = today(S)
    expect(t.icon).toBe('timer')
    expect(t.subtitle).toBe('Exercise 2 of 2')
    expect(t.session.index).toBe(1)
    expect(t.session.count).toBe(2)
    expect(t.session.d).toBe(TODAY)
    expect(t.session.units[0]).toEqual({
      title: 'Snatch', schemes: ['Set 1 of 2 · 70×2', 'Set 2 of 2 · 70×2'], sets: 2, done: 1,
    })
    // a complex is one unit, and its members' names are joined the way the card shows them
    expect(t.session.units[1].title).toBe('Back Squat + Front Squat')
  })

  it('names a freestyle session trained on a rest day', () => {
    const S = state([], { workouts: [{ d: TODAY, entries: [worked([true])] }] })
    const t = today(S)
    expect(t.icon).toBe('check')
    expect(t.title).toBe('Workout done')
    expect(t.scheme).toBe('1 sets')
  })

  it('offers the one action when the profile has no plan at all', () => {
    const t = today({ weeks: [], workouts: [], active: null })
    expect(t.title).toBe('No plan yet')
    expect(t.scheme).toBe('Open OlyGym to build one')
    expect(t.overline).not.toBe('')
  })

  it('carries the app theme and accent, falling back only when the read came back empty', () => {
    const S = state([])
    expect(widgetModel(S, NOW).theme).toBe('dark')
    expect(widgetModel(S, NOW).colors.container).toBe('#4c3889')
    expect(widgetModel(S, NOW, { theme: 'nonsense' }).theme).toBe('dark')
    const live = widgetModel(S, NOW, { theme: 'light', container: '#e9ddff', onContainer: '#150d56' })
    expect(live.theme).toBe('light')
    expect(live.colors.onContainer).toBe('#150d56')
    // the mark is a 14% wash of the ink over the container, the same maths as the app's color-mix
    expect(live.colors.circle).toBe(mix('#150d56', '#e9ddff', 0.14))
  })

  it('computes the mark wash like color-mix(in srgb, …)', () => {
    expect(mix('#e9ddff', '#4c3889', 0.14)).toBe('#624f9a')
    expect(mix('#ffffff', '#000000', 0.5)).toBe('#808080')
  })
})
