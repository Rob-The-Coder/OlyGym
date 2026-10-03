import { describe, it, expect } from 'vitest'
import { buildSessionEntries } from './session-start.js'
import { isWarmupRow } from './workout-model.js'

// The session builder used by the live start and by "log a past workout".
describe('buildSessionEntries', () => {
  const st = { unit: 'kg', workouts: [], exWeights: {}, routines: [] }

  it('ramps the warm-ups on the exercise’s own increment, not the unit default', () => {
    const r = { id: 'r', prog: 'off', ex: [{ id: '0025', sets: 3, reps: 5, weight: 60, inc: 1.25, warmupSets: 2 }] }
    const entries = buildSessionEntries(st, r)
    const warm = entries[0].sets.filter(isWarmupRow).map(s => s.w)
    expect(warm).toHaveLength(2)
    for (const w of warm) expect(Math.round(w / 1.25 * 1000) / 1000 % 1).toBe(0)   // a multiple of 1.25
    expect(entries[0].sets.filter(s => !isWarmupRow(s)).every(s => s.w === 60)).toBe(true)
  })

  it('keeps the unit default for timed exercises, whose inc is seconds', () => {
    const r = { id: 'r', prog: 'off', ex: [{ id: '0025', mode: 'time', sets: 2, sec: 30, inc: 10, weight: 0 }] }
    const entries = buildSessionEntries(st, r)
    expect(entries[0].sets.every(s => s.sec === 30)).toBe(true)
  })

  it('returns a bare array — no { entries, excluded } wrapper', () => {
    const r = { id: 'r', prog: 'off', ex: [{ id: '0025', sets: 3, reps: 5, weight: 60 }] }
    const out = buildSessionEntries(st, r)
    expect(Array.isArray(out)).toBe(true)
    expect(out).toHaveLength(1)
  })

  it('stamps noProg + plan.kind "off" on every entry of an excluded routine, and neither on a normal one', () => {
    const ex = [{ id: '0025', sets: 3, reps: 5, weight: 60 }, { id: '0031', sets: 3, reps: 8, weight: 40 }]
    const excluded = buildSessionEntries(st, { id: 'rehab', excludeFromProgression: true, ex })
    expect(excluded.every(e => e.noProg === true)).toBe(true)
    expect(excluded.every(e => e.plan.kind === 'off')).toBe(true)

    const normal = buildSessionEntries(st, { id: 'r', prog: 'off', ex })
    expect(normal.every(e => e.noProg === undefined)).toBe(true)
  })

  it('does not stamp a routine id — a day is atomic, so there is nothing to stamp', () => {
    const r = { id: 'r', prog: 'off', ex: [{ id: '0025', sets: 3, reps: 5, weight: 60 }] }
    expect(buildSessionEntries(st, r)[0].rid).toBeUndefined()
  })

  // Settings → During a workout → Automatic progression (lib/progression.js defaultPolicy). A
  // day's weight is the weight you lift; only the setting asks for it to climb.
  const stWithHistory = autoProg => ({
    unit: 'kg', exWeights: {}, routines: [], autoProg,
    workouts: [{
      d: '2026-01-01',
      entries: [{
        id: '0025',
        target: { sets: 3, reps: 5 },
        sets: [{ w: 60, r: 5, done: true }, { w: 60, r: 5, done: true }, { w: 60, r: 5, done: true }],
      }],
    }],
  })
  const plannedDay = prog => ({
    id: 'r', ex: [{ id: '0025', sets: 3, reps: 5, weight: 40, ...(prog ? { prog } : {}) }],
  })
  const workWeights = entries => entries[0].sets.filter(s => !isWarmupRow(s)).map(s => s.w)

  it('opens at the day’s own weight with automatic progression off (the default)', () => {
    const entries = buildSessionEntries(stWithHistory(false), plannedDay())
    expect(entries[0].plan).toEqual({ policy: 'off', kind: 'off' })
    expect(workWeights(entries)).toEqual([40, 40, 40])
  })

  it('adds a step once the profile turns automatic progression on', () => {
    const entries = buildSessionEntries(stWithHistory(true), plannedDay())
    expect(entries[0].plan.kind).toBe('up')
    expect(workWeights(entries)).toEqual([62.5, 62.5, 62.5])
  })

  it('lets the exercise’s own rule beat the setting, both ways', () => {
    expect(buildSessionEntries(stWithHistory(false), plannedDay('linear'))[0].plan.kind).toBe('up')
    expect(buildSessionEntries(stWithHistory(true), plannedDay('off'))[0].plan.kind).toBe('off')
  })
})
