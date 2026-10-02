import { describe, it, expect } from 'vitest'
import { phaseForSet, isWarmupRow, modeForSet, modeForEntry } from './workout-model.js'

describe('phaseForSet / isWarmupRow', () => {
  it('reads both the explicit phase and the legacy boolean', () => {
    expect(isWarmupRow({ phase: 'warmup' })).toBe(true)
    expect(isWarmupRow({ warmup: true })).toBe(true)
    expect(isWarmupRow({ phase: 'work' })).toBe(false)
    expect(isWarmupRow({})).toBe(false)
  })
})

// A stored row may still carry the retired drop-set/rest-pause type; mode resolution has to keep
// reading it as ordinary reps work rather than failing on it.
describe('modeForSet / modeForEntry read legacy drop-set/rest-pause rows as reps', () => {
  it('infers reps mode from the row\'s own r field regardless of type', () => {
    expect(modeForSet({ type: 'dropset', w: 100, r: 5 })).toBe('reps')
    expect(modeForSet({ type: 'restpause', w: 60, r: 8 })).toBe('reps')
  })

  it('an entry mixing straight and legacy rows still reads as one reps-mode entry', () => {
    const entry = { sets: [{ w: 100, r: 5 }, { type: 'dropset', w: 100, r: 5, drops: [{ w: 80, r: 5 }] }] }
    expect(modeForEntry(entry)).toBe('reps')
  })
})
