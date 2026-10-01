import { describe, it, expect } from 'vitest'
import { weeksInOrder, weekFor, dayFor, addDays } from './weeks.js'

const ex = [{ id: 'squat', sets: 3, reps: 5 }]
const mk = (id, startIso, days) => ({ id, startIso, name: id, days })
// 2026-03-09 and 2026-03-16 are Mondays, so each week covers seven days from its startIso.
const A = mk('a', '2026-03-09', [{ dow: 3, name: 'Legs', ex }])
const B = mk('b', '2026-03-16', [{ dow: 1, name: 'Push', ex }, { dow: 3, name: 'Pull', ex }])
const S = { weekStart: 1, weeks: [B, A] }

describe('addDays', () => {
  it('walks days across a month boundary', () => {
    expect(addDays('2026-03-30', 7)).toBe('2026-04-06')
    expect(addDays('2026-03-02', -7)).toBe('2026-02-23')
  })
})

describe('weeksInOrder', () => {
  it('sorts oldest first without touching the caller array', () => {
    expect(weeksInOrder(S).map(w => w.id)).toEqual(['a', 'b'])
    expect(S.weeks.map(w => w.id)).toEqual(['b', 'a'])
  })

  it('reads a state with no weeks at all as empty', () => {
    expect(weeksInOrder({})).toEqual([])
  })
})

describe('weekFor', () => {
  it('finds the week a date falls in, on both its first and last day', () => {
    expect(weekFor(S, '2026-03-09').id).toBe('a')
    expect(weekFor(S, '2026-03-11').id).toBe('a')
    expect(weekFor(S, '2026-03-15').id).toBe('a')
    expect(weekFor(S, '2026-03-16').id).toBe('b')
    expect(weekFor(S, '2026-03-22').id).toBe('b')
  })

  it('returns null for a date before the first week and after the last', () => {
    expect(weekFor(S, '2026-03-08')).toBe(null)
    expect(weekFor(S, '2026-03-23')).toBe(null)
    // A gap between two weeks is not covered by either.
    const gapped = { weeks: [A, mk('c', '2026-03-30', [])] }
    expect(weekFor(gapped, '2026-03-25')).toBe(null)
  })

  it('prefers the week that starts latest when two overlap', () => {
    const overlapping = { weeks: [A, mk('late', '2026-03-11', [])] }
    expect(weekFor(overlapping, '2026-03-12').id).toBe('late')
  })
})

describe('dayFor', () => {
  it('returns the day matching the date weekday', () => {
    // 2026-03-11 is a Wednesday.
    expect(dayFor(S, '2026-03-11').name).toBe('Legs')
    expect(dayFor(S, '2026-03-18').name).toBe('Pull')
  })

  it('returns null for a weekday the week leaves out, and outside every week', () => {
    expect(dayFor(S, '2026-03-10')).toBe(null)
    expect(dayFor(S, '2026-03-23')).toBe(null)
  })

  it('takes the first day when one weekday carries several', () => {
    const two = { weeks: [mk('w', '2026-03-09', [{ dow: 1, name: 'First', ex }, { dow: 1, name: 'Second', ex }])] }
    expect(dayFor(two, '2026-03-09').name).toBe('First')
  })
})
