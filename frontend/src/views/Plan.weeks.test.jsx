// @vitest-environment happy-dom
// Stage B: the Plan tab is a week timeline. One block per dated week, oldest first, the week
// that covers today tagged, and a block's header opening that week's editor. The exercises
// themselves no longer live on this screen, so neither do the routine-list assertions.
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Plan from './Plan.jsx'
import { addDays } from '../lib/weeks.js'
import { isoOf, startOfWeek, todayISO } from '../lib/format.js'
import { starterPlanSheet } from '../sheets.jsx'

globalThis.IS_REACT_ACT_ENVIRONMENT = true

const mocks = vi.hoisted(() => {
  const state = { S: null, nav: vi.fn() }
  state.snapshot = () => ({
    S: state.S,
    user: null,
    config: null,
    coachLocal: null,
    update: mut => {
      const next = structuredClone(state.S)
      mut(next)
      state.S = next
    },
  })
  return state
})
vi.mock('../store/useStore.js', () => {
  const useStore = selector => (selector ? selector(mocks.snapshot()) : mocks.snapshot())
  useStore.getState = mocks.snapshot
  return { useStore, DEF: { reminder: { time: '17:30' } }, hasData: () => false }
})
vi.mock('react-router-dom', () => ({ useNavigate: () => mocks.nav }))
vi.mock('../lib/mobile.js', () => ({ MOBILE: false, isAndroid: () => Promise.resolve(false), shareExport: vi.fn(), syncReminder: vi.fn() }))
vi.mock('../sheets.jsx', () => ({
  effortHelpSheet: vi.fn(), planToolsSheet: vi.fn(), starterPlanSheet: vi.fn() }))

// This week's first day, the anchor `weekFor` and the New week button both work from.
const monday = () => isoOf(startOfWeek(todayISO(), 1))
const week = (id, startIso, name, days = []) => ({ id, startIso, name, days })
const day = (dow, name, ex = []) => ({ dow, name, ex })

let host, root
beforeEach(() => {
  mocks.S = { unit: 'kg', weeks: [], routines: [], week: {}, dayPlan: {}, workouts: [], exWeights: {} }
  mocks.nav.mockClear()
  starterPlanSheet.mockClear()
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
})
afterEach(() => {
  act(() => root.unmount())
  host.remove()
})

const mount = () => act(() => root.render(<Plan />))
const block = id => host.querySelector(`[data-week="${id}"]`)
const newWeekButton = () => [...host.querySelectorAll('button')].find(b => b.textContent.includes('New week'))
const click = el => act(() => { el.click() })

describe('Plan — the week timeline', () => {
  it('lists the weeks oldest first, each with the days it plans', () => {
    mocks.S.weeks = [
      week('w2', addDays(monday(), 7), 'Next week', [day(3, 'Pull', [{ id: 'a' }])]),
      week('w1', monday(), 'This one', [day(1, 'Push', [{ id: 'a' }, { id: 'b' }])]),
    ]
    mount()
    expect([...host.querySelectorAll('[data-week]')].map(el => el.dataset.week)).toEqual(['w1', 'w2'])
    expect(block('w1').textContent).toContain('This one')
    expect(block('w1').textContent).toContain('Push')
    expect(block('w1').textContent).toContain('2 exercises')
    expect(block('w2').textContent).toContain('Pull')
  })

  it('tags the week that covers today, and only that one', () => {
    mocks.S.weeks = [week('w1', monday(), 'Current'), week('w2', addDays(monday(), 7), 'Later')]
    mount()
    expect(block('w1').textContent).toContain('This week')
    expect(block('w2').textContent).not.toContain('This week')
  })

  it('says so when a week plans no days', () => {
    mocks.S.weeks = [week('w1', monday(), 'Empty week')]
    mount()
    expect(block('w1').textContent).toContain('No days yet')
  })

  // The plural form is not automatic when the English string is the key (the old Plan shipped
  // "1 routines" for exactly this reason), so both forms are pinned.
  it('uses the singular for a one-day week and the plural for more', () => {
    mocks.S.weeks = [
      week('w1', monday(), 'One', [day(1, 'Push')]),
      week('w2', addDays(monday(), 7), 'Three', [day(1, 'A'), day(2, 'B'), day(3, 'C')]),
    ]
    mount()
    expect(block('w1').querySelector('.ss').textContent).toBe('1 day')
    expect(block('w2').querySelector('.ss').textContent).toBe('3 days')
  })

  it('opens a week editor from the block header', () => {
    mocks.S.weeks = [week('w1', monday(), 'This one', [day(1, 'Push')])]
    mount()
    click(block('w1').querySelector('[role="button"]'))
    expect(mocks.nav).toHaveBeenCalledWith('/plan/w/w1')
  })

  it('offers New week on the empty state, starting with the week we are in', () => {
    mount()
    expect(host.textContent).toContain('No weeks yet.')
    click(newWeekButton())
    expect(mocks.S.weeks).toHaveLength(1)
    expect(mocks.S.weeks[0].startIso).toBe(monday())
    expect(mocks.S.weeks[0].days).toEqual([])
    expect(mocks.nav).toHaveBeenCalledWith('/plan/w/' + mocks.S.weeks[0].id)
  })

  it('offers the starter plan from the empty state', () => {
    mount()
    const button = [...host.querySelectorAll('button')].find(b => b.textContent === 'Load starter plan')
    expect(button).toBeTruthy()
    click(button)
    expect(starterPlanSheet).toHaveBeenCalledTimes(1)
  })

  it('starts a new week the week after the last one', () => {
    const last = addDays(monday(), 14)
    mocks.S.weeks = [week('w1', last, 'Last week')]
    mount()
    click(newWeekButton())
    expect(mocks.S.weeks).toHaveLength(2)
    expect(mocks.S.weeks.find(w => w.id !== 'w1').startIso).toBe(addDays(last, 7))
  })
})
