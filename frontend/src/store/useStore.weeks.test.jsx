// @vitest-environment happy-dom
// Stage A: a profile saved before the dated-weeks model has routines but no `weeks`, so boot
// seeds the new field from them. This pins the wiring in loadState — the migration itself is
// covered by lib/migrate-weeks.test.js.
import { beforeEach, describe, expect, it, vi } from 'vitest'

const OLD = {
  routines: [{ id: 'r1', name: 'Push', ex: [{ id: 'bench', sets: 3, reps: 5 }] }],
  week: { 1: ['r1'] },
  dayPlan: {},
}

async function boot(stored) {
  localStorage.setItem('gym_state_v1', JSON.stringify(stored))
  vi.resetModules()
  const { useStore } = await import('./useStore.js')
  return useStore.getState().S
}

beforeEach(() => { localStorage.clear() })

describe('loadState weeks migration', () => {
  it('seeds weeks from an old profile and leaves the old plan alone', async () => {
    const S = await boot(OLD)
    expect(S.weeks).toHaveLength(1)
    expect(S.weeks[0].days.map(d => d.name)).toEqual(['Push'])
    expect(S.routines).toEqual(OLD.routines)
    expect(S.week).toEqual(OLD.week)
  })

  it('keeps weeks that are already there', async () => {
    const kept = [{ id: 'w1', startIso: '2026-03-23', name: '', days: [] }]
    const S = await boot({ ...OLD, weeks: kept })
    expect(S.weeks).toEqual(kept)
  })

  it('has nothing to migrate on a profile with no routines', async () => {
    expect((await boot({ bodyweight: [] })).weeks).toEqual([])
  })
})

// The app logs kilos only now. A profile written while it still offered pounds must convert
// exactly once on the way in and be pinned to kg, so an old number is never relabelled.
describe('loadState unit migration', () => {
  const lbState = {
    unit: 'lb',
    exWeights: { bench: { w: 135 } },
    weeks: [{ id: 'w1', startIso: '2026-03-23', name: '', days: [{ dow: 1, name: 'Push', ex: [{ id: 'bench', sets: 3, reps: 5, weight: 135 }] }] }],
    routines: [], week: {}, dayPlan: {},
  }

  it('converts a stored lb profile to kg and pins the unit', async () => {
    const S = await boot(lbState)
    expect(S.unit).toBe('kg')
    expect(S.exWeights.bench.w).toBe(61.25)
    expect(S.weeks[0].days[0].ex[0].weight).toBe(61.25)
  })

  it('is idempotent — a second boot does not convert the converted numbers again', async () => {
    const once = await boot(lbState)
    const twice = await boot(once)
    expect(twice.unit).toBe('kg')
    expect(twice.exWeights.bench.w).toBe(61.25)
  })
})
