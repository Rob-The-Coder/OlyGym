import { describe, it, expect } from 'vitest'
import { migrateToWeeks, weekDayForPosition } from './migrate-weeks.js'

// Wednesday 25 March 2026 — the week that contains it starts Monday 2026-03-23, so the coach
// weeks land on 2026-03-16 and the leftover week on 2026-03-30. Pinned, never the clock.
const NOW = new Date('2026-03-25T12:00:00')
const r = (id, name, extra = {}) => ({ id, name, ex: [{ id: 'squat', sets: 3, reps: 5 }], ...extra })
const dows = week => week.days.map(d => d.dow)

describe('weekDayForPosition', () => {
  it('puts Monday, Wednesday and Friday first, like the import does', () => {
    expect([0, 1, 2, 3, 4, 5, 6].map(weekDayForPosition)).toEqual([1, 3, 5, 2, 4, 6, 0])
  })
})

describe('migrateToWeeks', () => {
  it('turns the scheduled week into one week starting on the current Monday', () => {
    const S = {
      weekStart: 1,
      routines: [r('r1', 'Push'), r('r2', 'Legs')],
      week: { 1: ['r1'], 5: ['r2'] },
    }
    const weeks = migrateToWeeks(S, NOW)
    expect(weeks).toHaveLength(1)
    expect(weeks[0].startIso).toBe('2026-03-23')
    expect(dows(weeks[0])).toEqual([1, 5])
    expect(weeks[0].days.map(d => d.name)).toEqual(['Push', 'Legs'])
    expect(weeks[0].days[0].ex).toBe(S.routines[0].ex)
  })

  it('keeps every routine of a multi-routine weekday as its own day', () => {
    const S = { routines: [r('a', 'A'), r('b', 'B')], week: { 2: ['a', 'b'] } }
    expect(migrateToWeeks(S, NOW)[0].days.map(d => [d.dow, d.name])).toEqual([[2, 'A'], [2, 'B']])
  })

  it('groups the coach-named routines into one week per label, Mon/Wed/Fri', () => {
    const S = {
      routines: [
        r('g2', 'Giorno 2 · 23-29 marzo'),
        r('g1', 'Giorno 1 · 23-29 marzo'),
        r('g3', 'Giorno 3 · 23-29 marzo'),
      ],
      week: {},
    }
    const weeks = migrateToWeeks(S, NOW)
    expect(weeks).toHaveLength(1)
    expect(weeks[0].name).toBe('23-29 marzo')
    expect(weeks[0].startIso).toBe('2026-03-16')
    expect(dows(weeks[0])).toEqual([1, 3, 5])
    // Ordered by the number the coach wrote, not by where the routine sits in the array.
    expect(weeks[0].days.map(d => d.name)).toEqual([
      'Giorno 1 · 23-29 marzo', 'Giorno 2 · 23-29 marzo', 'Giorno 3 · 23-29 marzo',
    ])
  })

  it('puts two labels on consecutive past Mondays', () => {
    const S = {
      routines: [r('a', 'Giorno 1 · 16-22 marzo'), r('b', 'Giorno 1 · 23-29 marzo')],
      week: {},
    }
    const weeks = migrateToWeeks(S, NOW)
    expect(weeks.map(w => [w.name, w.startIso])).toEqual([
      ['16-22 marzo', '2026-03-09'], ['23-29 marzo', '2026-03-16'],
    ])
  })

  it('collects the leftovers into one unnamed week after the current one', () => {
    const S = { routines: [r('x', 'Freestyle'), r('y', 'Mobility')], week: {} }
    const weeks = migrateToWeeks(S, NOW)
    expect(weeks).toHaveLength(1)
    expect(weeks[0].startIso).toBe('2026-03-30')
    expect(weeks[0].name).toBe('')
    expect(weeks[0].days.map(d => [d.dow, d.name])).toEqual([[1, 'Freestyle'], [3, 'Mobility']])
  })

  it('returns the three groups sorted by start date, and nothing when there is nothing to keep',
    () => {
      const S = {
        routines: [r('n', 'Giorno 1 · 23-29 marzo'), r('s', 'Scheduled'), r('l', 'Leftover')],
        week: { 4: ['s'] },
      }
      const weeks = migrateToWeeks(S, NOW)
      expect(weeks.map(w => w.startIso)).toEqual(['2026-03-16', '2026-03-23', '2026-03-30'])
      expect(migrateToWeeks({ routines: [], week: {} }, NOW)).toEqual([])
      // A schedule slot pointing at a routine that no longer exists is not a day.
      expect(migrateToWeeks({ routines: [], week: { 1: ['gone'] } }, NOW)).toEqual([])
    })

  it('carries exercises and excludeFromProgression over, and leaves the old plan untouched', () => {
    const bad = r('bad', 'Rehab', { excludeFromProgression: true })
    const good = r('good', 'Push')
    const S = { routines: [bad, good], week: { 1: ['bad', 'good'] }, dayPlan: { '2026-03-25': 'rest' } }
    const snapshot = JSON.stringify(S)
    const days = migrateToWeeks(S, NOW)[0].days
    expect(days[0].ex).toBe(bad.ex)
    expect(days[0].excludeFromProgression).toBe(true)
    expect('excludeFromProgression' in days[1]).toBe(false)
    expect(days.length).toBe(2)
    expect(JSON.stringify(S)).toBe(snapshot)
  })

  it('gives every week its own id', () => {
    const S = {
      routines: [r('a', 'Giorno 1 · 23-29 marzo'), r('b', 'Scheduled'), r('c', 'Leftover')],
      week: { 1: ['b'] },
    }
    const ids = migrateToWeeks(S, NOW).map(w => w.id)
    expect(new Set(ids).size).toBe(ids.length)
    expect(ids.every(id => typeof id === 'string' && id.length)).toBe(true)
  })
})
