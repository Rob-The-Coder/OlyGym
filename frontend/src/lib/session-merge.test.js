import { describe, it, expect } from 'vitest'
import { buildDayEntries, deriveSessionName } from './session-merge.js'
import { buildSessionEntries } from './session-start.js'

// A day of the dated weeks is the whole session, so this builder has nothing to merge: it builds
// the day's entries and takes the day's name. Freestyle (no day) is the one other case.
const st = { unit: 'kg', workouts: [], exWeights: {}, routines: [] }
const day = (over = {}) => ({
  dow: 1,
  name: 'Strength',
  ex: [{ id: '0025', sets: 3, reps: 5, weight: 60 }, { id: '0031', sets: 3, reps: 8, weight: 40 }],
  ...over,
})

describe('buildDayEntries', () => {
  it('builds the day’s entries and names the session after the day', () => {
    const { entries, name } = buildDayEntries(st, day())
    expect(entries.map(e => e.id)).toEqual(['0025', '0031'])
    expect(name).toBe('Strength')
  })

  it('a null day is a freestyle session — no entries', () => {
    expect(buildDayEntries(st, null)).toEqual({ entries: [], name: 'Freestyle' })
    expect(buildDayEntries(st, undefined)).toEqual({ entries: [], name: 'Freestyle' })
  })

  it('an unnamed day still gets a session name', () => {
    expect(buildDayEntries(st, day({ name: '' })).name).toBe('Freestyle')
  })

  it('an empty day builds nothing but keeps its name', () => {
    expect(buildDayEntries(st, day({ name: 'Mobility', ex: [] }))).toEqual({ entries: [], name: 'Mobility' })
  })

  it('is exactly the plain buildSessionEntries path, with no per-entry routine stamp', () => {
    const d = day()
    const { entries } = buildDayEntries(st, d)
    expect(entries).toEqual(buildSessionEntries(st, d))
    expect(entries.every(e => !('rid' in e))).toBe(true)
  })

  it('carries the day’s per-entry noProg through', () => {
    const { entries } = buildDayEntries(st, day({ name: 'Rehab', excludeFromProgression: true }))
    expect(entries.every(e => e.noProg === true)).toBe(true)
    expect(buildDayEntries(st, day()).entries.every(e => e.noProg === undefined)).toBe(true)
  })
})

describe('deriveSessionName', () => {
  it('is null for an empty list', () => {
    expect(deriveSessionName([])).toBe(null)
  })
  it('joins 1–3 names with " + "', () => {
    expect(deriveSessionName(['Rehab'])).toBe('Rehab')
    expect(deriveSessionName(['Rehab', 'Core'])).toBe('Rehab + Core')
    expect(deriveSessionName(['A', 'B', 'C'])).toBe('A + B + C')
  })
  it('collapses 4+ to "A + B + N more"', () => {
    expect(deriveSessionName(['A', 'B', 'C', 'D'])).toBe('A + B + 2 more')
    expect(deriveSessionName(['A', 'B', 'C', 'D', 'E'])).toBe('A + B + 3 more')
  })
})
