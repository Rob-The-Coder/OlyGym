import { describe, it, expect } from 'vitest'
import { convertedExercise, planPrintHTML } from './plan-share.js'
import { convertWeight } from './units.js'

// A plan's per-set ladder travels with its single weight: converted in, and printed as one list
// with the unit after it, while a uniform plan keeps the one number it has always printed.
describe('convertedExercise', () => {
  it('converts a ladder with the weight, first load on top', () => {
    const inLb = convertedExercise({ id: '0025', sets: 4, reps: 10, weight: 60, setWeights: [60, 60, 65, 65] }, 'kg', 'lb')
    expect(inLb.weight).toBe(convertWeight(60, 'kg', 'lb'))
    expect(inLb.setWeights).toEqual([60, 60, 65, 65].map(w => convertWeight(w, 'kg', 'lb')))
    expect(inLb.setWeights[0]).toBe(inLb.weight)
  })

  it('adds no ladder to a config that has none, and leaves the source alone', () => {
    const cfg = { id: '0025', sets: 3, reps: 10, weight: 60 }
    expect(convertedExercise(cfg, 'kg', 'lb')).not.toHaveProperty('setWeights')
    expect(cfg.weight).toBe(60)
  })
})

describe('planPrintHTML', () => {
  const html = ex => planPrintHTML({
    unit: 'kg',
    weeks: [{ id: 'w', startIso: '2026-01-05', name: 'Block 1', days: [{ dow: 1, name: 'Day 1', ex }] }],
  })

  it('prints a progressive load as its ladder, with the unit once', () => {
    expect(html([{ id: '0025', sets: 4, reps: 10, weight: 60, setWeights: [60, 60, 65, 65] }]))
      .toContain('4 × 10 · 60/60/65/65\u00a0kg')
  })

  it('still prints one number for a uniform week', () => {
    expect(html([{ id: '0025', sets: 3, reps: 10, weight: 60 }])).toContain('3 × 10 · 60 kg')
  })
})
