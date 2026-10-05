// @vitest-environment happy-dom
// The competitions screen (views/Competitions.jsx). The point of it is the split that a meet
// list has and a workout list does not: what is still ahead, what is already done, and the
// totals that only exist for the done ones. The sheets are mocked — this asserts the list, the
// door into a meet, and the door to add one.
import React, { act } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRoot } from 'react-dom/client'
import { useStore } from '../store/useStore.js'
import { isoOf, todayISO } from '../lib/format.js'
import { meetSheet, meetDetailSheet } from '../sheets.jsx'
import Competitions from './Competitions.jsx'

const nav = vi.fn()
vi.mock('react-router-dom', () => ({ useNavigate: () => nav }))
vi.mock('../sheets.jsx', () => ({ meetSheet: vi.fn(), meetDetailSheet: vi.fn() }))

const shift = n => { const d = new Date(todayISO() + 'T12:00:00'); d.setDate(d.getDate() + n); return isoOf(d) }
const meet = (id, d, name, s, c) => ({
  id, d, name,
  snatch: [{ w: s, made: true }], cj: [{ w: c, made: true }],
})

let host, root
beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  nav.mockClear(); meetSheet.mockClear(); meetDetailSheet.mockClear()
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
})
afterEach(() => { act(() => root.unmount()); host.remove() })

const setS = (competitions = []) => useStore.setState(s => ({ S: { ...s.S, competitions } }))
const mount = () => act(() => root.render(<Competitions />))

describe('Competitions', () => {
  it('points at the one action when there are no meets yet', () => {
    setS([]); mount()
    expect(host.textContent).toContain('No competitions yet.')
    act(() => { host.querySelector('.ab-acts .iconbtn').dispatchEvent(new MouseEvent('click', { bubbles: true })) })
    expect(meetSheet).toHaveBeenCalledTimes(1)
  })

  it('separates what is ahead from what is done, with the done totals', () => {
    setS([meet('m1', shift(20), 'Regionale', 100, 130), meet('m2', shift(-30), 'Nazionale', 110, 140)])
    mount()
    expect(host.textContent).toContain('Upcoming')
    expect(host.textContent).toContain('Past')
    expect(host.textContent).toContain('Regionale')
    expect(host.textContent).toContain('Nazionale')
    // An upcoming row counts down; a past row reports its total. The tile row carries the
    // all-time bests across every meet.
    expect(host.textContent).toContain('in 20 days')
    expect(host.textContent).toContain('110')   // best snatch
    expect(host.textContent).toContain('140')   // best clean & jerk
    expect(host.textContent).toContain('250')   // best total, and the past meet's own
    expect(host.textContent).toContain('Best total')
  })

  it('is a mode of the Plan tab, switchable from the segmented control', () => {
    setS([]); mount()
    const seg = host.querySelectorAll('.plan-tabs .seg button')
    expect(seg).toHaveLength(2)
    act(() => seg[0].dispatchEvent(new MouseEvent('click', { bubbles: true })))
    expect(nav).toHaveBeenCalledWith('/plan')
  })

  it('opens a meet from its row', () => {
    const m = meet('m1', shift(10), 'Regionale', 100, 130)
    setS([m]); mount()
    act(() => { host.querySelector('.item').dispatchEvent(new MouseEvent('click', { bubbles: true })) })
    expect(meetDetailSheet).toHaveBeenCalledTimes(1)
    expect(meetDetailSheet.mock.calls[0][0]).toMatchObject({ id: 'm1', name: 'Regionale' })
  })
})
