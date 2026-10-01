// @vitest-environment happy-dom
// A day of the dated weeks is the whole session: beginning one copies its exercises, names the
// session after it and records which week/weekday it came from. Freestyle (null) is the other case.
import { act } from 'react'
import { beforeEach, describe, expect, it } from 'vitest'
import { beginWorkout } from './sheets.jsx'
import { EXDB } from './lib/exercises.js'
import { DEF, useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { isoOf, startOfWeek, todayISO } from './lib/format.js'

const clone = v => JSON.parse(JSON.stringify(v))
const ids = EXDB.filter(e => e.bp !== 'cardio').slice(0, 3).map(e => e.id)

const THIS_WEEK = isoOf(startOfWeek(todayISO(), 1))
const TODAY_DOW = new Date(todayISO() + 'T12:00:00').getDay()

const strengthDay = { dow: TODAY_DOW, name: 'Strength', ex: [{ id: ids[0], sets: 3, reps: 5, weight: 60 }] }
const coreDay = { dow: TODAY_DOW, name: 'Core', ex: [{ id: ids[1], sets: 3, reps: 12, weight: 0 }, { id: ids[2], sets: 3, reps: 10, weight: 0 }] }
const rehabDay = { dow: TODAY_DOW, name: 'Rehab', excludeFromProgression: true, ex: [{ id: ids[1], sets: 2, reps: 15, weight: 5 }] }

function install(days = [strengthDay]) {
  const S = clone(DEF)
  S.weeks = [{ id: 'w1', startIso: THIS_WEEK, name: 'This week', days }]
  S.active = null
  S.workouts = []
  useStore.setState({ S, user: null })
}

beforeEach(() => {
  localStorage.clear()
  useUI.setState({ sheets: [] })
  install()
})

describe('beginWorkout with a day', () => {
  it('builds the day’s entries, names it and records the week + weekday', () => {
    act(() => beginWorkout(strengthDay, null))
    const a = useStore.getState().S.active
    expect(a.name).toBe('Strength')
    expect(a.weekId).toBe('w1')
    expect(a.dow).toBe(TODAY_DOW)
    expect(a.entries.map(e => e.id)).toEqual([ids[0]])
    expect(a.entries[0]).not.toHaveProperty('rid')
    expect(a).not.toHaveProperty('routineIds')
    expect(a).not.toHaveProperty('routineId')
    expect(a).not.toHaveProperty('excludeFromProgression')
  })

  it('a null day is a freestyle session — no entries and no week behind it', () => {
    act(() => beginWorkout(null, null))
    const a = useStore.getState().S.active
    expect(a.name).toBe('Freestyle')
    expect(a.weekId).toBe(null)
    expect(a.dow).toBe(null)
    expect(a.entries).toEqual([])
  })

  it('every exercise of a multi-exercise day lands in the one session, in plan order', () => {
    act(() => beginWorkout(coreDay, null))
    const a = useStore.getState().S.active
    expect(a.name).toBe('Core')
    expect(a.entries.map(e => e.id)).toEqual([ids[1], ids[2]])
  })

  it('carries an excluded day’s per-entry noProg', () => {
    act(() => beginWorkout(rehabDay, null))
    const a = useStore.getState().S.active
    expect(a.entries[0].noProg).toBe(true)
    expect(a).not.toHaveProperty('excludeFromProgression')
  })

  it('a day planned with no exercises still opens as that named session', () => {
    act(() => beginWorkout({ dow: TODAY_DOW, name: 'Mobility', ex: [] }, null))
    const a = useStore.getState().S.active
    expect(a.name).toBe('Mobility')
    expect(a.entries).toEqual([])
    expect(a.weekId).toBe('w1')
  })
})
