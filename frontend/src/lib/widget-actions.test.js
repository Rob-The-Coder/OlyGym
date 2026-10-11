import { describe, expect, it } from 'vitest'
import { applyWidgetActions } from './widget-actions.js'

const entry = (id, dones) => ({ id, sets: dones.map(done => ({ w: 60, r: 3, done })) })
const session = (cur, entries) => ({ d: '2026-10-14', name: 'A', cur, entries })

describe('the widget controls, applied to a session', () => {
  it('ticks the round of the unit under the cursor', () => {
    const a = session(0, [entry('wl58', [false, false])])
    const out = applyWidgetActions(a, [{ k: 'check' }])
    expect(out.entries[0].sets.map(s => s.done)).toEqual([true, false])
    expect(a.entries[0].sets[0].done).toBe(false)   // pure: the input is untouched
  })

  it('ticks every member of a complex at once', () => {
    const a = session(0, [
      { id: 'wl77', sg: 'g1', sets: [{ done: false }] },
      { id: 'wl78', sg: 'g1', sets: [{ done: false }] },
    ])
    const out = applyWidgetActions(a, [{ k: 'check' }])
    expect(out.entries.map(e => e.sets[0].done)).toEqual([true, true])
  })

  it('does nothing when the unit has no set left to tick', () => {
    const a = session(0, [entry('wl58', [true])])
    expect(applyWidgetActions(a, [{ k: 'check' }])).toBe(a)
  })

  it('moves the cursor by the unit, and stops at both ends', () => {
    const a = session(0, [entry('wl58', [false]), entry('wl77', [false])])
    expect(applyWidgetActions(a, [{ k: 'next' }]).cur).toBe(1)
    expect(applyWidgetActions(a, [{ k: 'next', }, { k: 'next' }]).cur).toBe(1)
    expect(applyWidgetActions(a, [{ k: 'prev' }]).cur).toBe(0)
    const last = applyWidgetActions(session(1, a.entries), [{ k: 'prev' }])
    expect(last.cur).toBe(0)
  })

  it('applies a queue in order, and ignores what it cannot use', () => {
    const a = session(0, [entry('wl58', [false, false]), entry('wl77', [false])])
    const out = applyWidgetActions(a, [{ k: 'check' }, { k: 'nonsense' }, { k: 'next' }, { k: 'check' }])
    expect(out.cur).toBe(1)
    expect(out.entries[0].sets[0].done).toBe(true)
    expect(out.entries[1].sets[0].done).toBe(true)
    expect(applyWidgetActions(null, [{ k: 'check' }])).toBe(null)
    expect(applyWidgetActions(a, [])).toBe(a)
  })

  it('drops a tap that was queued for the previous day', () => {
    const a = session(0, [entry('wl58', [false])])
    expect(applyWidgetActions(a, [{ k: 'check', d: '2026-10-13' }])).toBe(a)
    expect(applyWidgetActions(a, [{ k: 'check', d: '2026-10-14' }]).entries[0].sets[0].done).toBe(true)
  })
})
