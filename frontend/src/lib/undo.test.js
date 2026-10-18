import { describe, expect, it } from 'vitest'
import { pushUndo, takeUndo, restoreWorkout, UNDO_MAX } from './undo.js'

const w = (id, d, start = 0) => ({ id, d, start })

describe('pushUndo', () => {
  it('appends the most recent workout last', () => {
    const stack = [w('a', '2026-01-01'), w('b', '2026-01-02')]
    expect(pushUndo(stack, w('c', '2026-01-03')).map(x => x.id)).toEqual(['a', 'b', 'c'])
  })

  it('caps at UNDO_MAX, dropping the oldest', () => {
    const stack = [w('a', '2026-01-01'), w('b', '2026-01-02'), w('c', '2026-01-03'), w('d', '2026-01-04'), w('e', '2026-01-05')]
    const next = pushUndo(stack, w('f', '2026-01-06'))
    expect(next.map(x => x.id)).toEqual(['b', 'c', 'd', 'e', 'f'])
    expect(next).toHaveLength(UNDO_MAX)
  })

  it('respects a custom max', () => {
    expect(pushUndo([w('a', '2026-01-01')], w('b', '2026-01-02'), 1).map(x => x.id)).toEqual(['b'])
  })

  it('does not mutate the input', () => {
    const stack = [w('a', '2026-01-01'), w('b', '2026-01-02')]
    const copy = [...stack]
    pushUndo(stack, w('c', '2026-01-03'))
    expect(stack).toEqual(copy)
  })
})

describe('takeUndo', () => {
  it('pops the most recent entry', () => {
    const stack = [w('a', '2026-01-01'), w('b', '2026-01-02')]
    const { stack: next, workout } = takeUndo(stack)
    expect(workout.id).toBe('b')
    expect(next.map(x => x.id)).toEqual(['a'])
  })

  it('returns a null workout and empty stack when empty', () => {
    const { stack: next, workout } = takeUndo([])
    expect(workout).toBeNull()
    expect(next).toEqual([])
  })

  it('does not mutate the input', () => {
    const stack = [w('a', '2026-01-01'), w('b', '2026-01-02')]
    const copy = [...stack]
    takeUndo(stack)
    expect(stack).toEqual(copy)
  })
})

describe('restoreWorkout', () => {
  const list = [w('a', '2026-01-01', 10), w('b', '2026-01-05', 10), w('c', '2026-01-05', 20)]

  it('re-inserts in chronological order by date', () => {
    expect(restoreWorkout(list, w('x', '2026-01-03')).map(x => x.id)).toEqual(['a', 'x', 'b', 'c'])
  })

  it('re-inserts by start time on the same day', () => {
    expect(restoreWorkout(list, w('x', '2026-01-05', 15)).map(x => x.id)).toEqual(['a', 'b', 'x', 'c'])
  })

  it('does not mutate the input', () => {
    const copy = [...list]
    restoreWorkout(list, w('x', '2026-01-03'))
    expect(list).toEqual(copy)
  })
})
