import { describe, expect, it } from 'vitest'
import { WIDGET_BODY_LINES, mix, widgetModel } from './widget-model.js'
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
  workouts: [], active: null, customEx: [], ...extra,
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
    expect(widgetModel(state([]), NOW).days[TODAY].overline)
      .toBe('Today · ' + new Date(TODAY + 'T12:00:00').toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' }))
  })

  it('names the session, its set count and its exercises, a complex on one line', () => {
    const days = [day(TODAY, 'Snatch day', [ex('wl58'), ex('wl77', 'g1'), ex('wl78', 'g1'), ex('wl98')])]
    const t = today(state(days))
    expect(t.icon).toBe('dumbbell')
    expect(t.title).toBe('Snatch day')
    expect(t.subtitle).toBe('12 sets')
    expect(t.body).toBe('Snatch\nBack Squat + Front Squat\nClean Pull')
    expect(t.footer).toBe('')
  })

  it('caps the body and says how many lines it dropped', () => {
    const days = [day(TODAY, 'A', ['wl58', 'wl77', 'wl78', 'wl98', 'wl163'].map(id => ex(id)))]
    const body = today(state(days)).body.split('\n')
    expect(body).toHaveLength(WIDGET_BODY_LINES + 1)
    expect(body[WIDGET_BODY_LINES]).toBe('+2 more')
  })

  it('nudges the next session on a rest day, under a moon', () => {
    const tomorrow = addDays(TODAY, 1)
    const days = [day(tomorrow, 'Clean day', [ex('wl98')])]
    const t = today(state(days))
    expect(t.icon).toBe('moon')
    expect(t.title).toBe('Rest day')
    expect(t.subtitle).toBe('')
    expect(t.body).toBe('')
    expect(t.footer).toBe('Next: ' + DAYN[dowOf(tomorrow)] + ', Clean day')
  })

  it('ticks a logged session off', () => {
    const days = [day(TODAY, 'Snatch day', [ex('wl58')])]
    const S = state(days, { workouts: [{ d: TODAY, name: 'Snatch day', entries: [worked([true, true])] }] })
    const t = today(S)
    expect(t.icon).toBe('check')
    expect(t.footer).toBe('Done · 2 sets')
  })

  it('runs the clock when the session is in progress, ahead of a logged one', () => {
    const days = [day(TODAY, 'Snatch day', [ex('wl58')])]
    const extra = {
      active: { d: TODAY, name: 'Snatch day', entries: [worked([true, false])] },
      workouts: [{ d: TODAY, name: 'Snatch day', entries: [worked([true, true])] }],
    }
    const t = today(state(days, extra))
    expect(t.icon).toBe('timer')
    expect(t.footer).toBe('In progress · 1/2 sets')
  })

  it('names a freestyle session trained on a rest day', () => {
    const S = state([], { workouts: [{ d: TODAY, entries: [worked([true])] }] })
    const t = today(S)
    expect(t.icon).toBe('check')
    expect(t.title).toBe('Workout done')
    expect(t.footer).toBe('1 sets')
  })

  it('offers the one action when the profile has no plan at all', () => {
    const t = today({ weeks: [], workouts: [], active: null })
    expect(t.title).toBe('No plan yet')
    expect(t.body).toBe('Open OlyGym to build one')
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
