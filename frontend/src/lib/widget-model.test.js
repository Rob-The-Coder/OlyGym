import { describe, expect, it } from 'vitest'
import { WIDGET_BODY_LINES, widgetModel } from './widget-model.js'
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

  it('carries the app theme, not the phone one, and defaults to the app default', () => {
    const days = [day(TODAY, 'Snatch day', [ex('wl58')])]
    expect(widgetModel(state(days), NOW).theme).toBe('dark')
    expect(widgetModel(state(days), NOW, 'light').theme).toBe('light')
    expect(widgetModel(state(days), NOW, 'nonsense').theme).toBe('dark')
  })

  it('names the session and its exercises, a complex on one line', () => {
    const days = [day(TODAY, 'Snatch day', [ex('wl58'), ex('wl77', 'g1'), ex('wl78', 'g1'), ex('wl98')])]
    const today = widgetModel(state(days), NOW).days[TODAY]
    expect(today.title).toBe('Snatch day')
    expect(today.body).toBe('Snatch\nBack Squat + Front Squat\nClean Pull')
    expect(today.footer).toBe('')
  })

  it('caps the body and says how many lines it dropped', () => {
    const days = [day(TODAY, 'A', ['wl58', 'wl77', 'wl78', 'wl98', 'wl163'].map(id => ex(id)))]
    const body = widgetModel(state(days), NOW).days[TODAY].body.split('\n')
    expect(body).toHaveLength(WIDGET_BODY_LINES + 1)
    expect(body[WIDGET_BODY_LINES]).toBe('+2 more')
  })

  it('nudges the next session on a rest day', () => {
    const tomorrow = addDays(TODAY, 1)
    const days = [day(tomorrow, 'Clean day', [ex('wl98')])]
    const today = widgetModel(state(days), NOW).days[TODAY]
    expect(today.title).toBe('Rest day')
    expect(today.body).toBe('')
    expect(today.footer).toBe('Next: ' + DAYN[dowOf(tomorrow)] + ', Clean day')
  })

  it('reports a logged session without calling the day empty', () => {
    const days = [day(TODAY, 'Snatch day', [ex('wl58')])]
    const workouts = [{ d: TODAY, name: 'Snatch day', entries: [worked([true, true])] }]
    const today = widgetModel(state(days, { workouts }), NOW).days[TODAY]
    expect(today.title).toBe('Snatch day')
    expect(today.footer).toBe('Done · 2 sets')
  })

  it('reports the session in progress ahead of a logged one', () => {
    const days = [day(TODAY, 'Snatch day', [ex('wl58')])]
    const extra = {
      active: { d: TODAY, name: 'Snatch day', entries: [worked([true, false])] },
      workouts: [{ d: TODAY, name: 'Snatch day', entries: [worked([true, true])] }],
    }
    expect(widgetModel(state(days, extra), NOW).days[TODAY].footer).toBe('In progress · 1/2 sets')
  })

  it('names a freestyle session trained on a rest day', () => {
    const extra = { workouts: [{ d: TODAY, entries: [worked([true])] }] }
    const today = widgetModel(state([], extra), NOW).days[TODAY]
    expect(today.title).toBe('Workout done')
    expect(today.footer).toBe('1 sets')
  })

  it('offers the one action when the profile has no plan at all', () => {
    const today = widgetModel({ weeks: [], workouts: [], active: null }, NOW).days[TODAY]
    expect(today.title).toBe('No plan yet')
    expect(today.body).toBe('Open OlyGym to build one')
    expect(today.footer).toBe('')
    expect(today.overline).not.toBe('')
  })
})
