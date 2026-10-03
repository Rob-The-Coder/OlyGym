// @vitest-environment happy-dom
// Stage WS17: the Plan tab is the plan, read outward from today. The week that covers today leads
// as a card with the days it plans; every other week is one line carrying what it planned and how
// much of it happened. A week's days are dates, so "done" is counted per date, not per weekday.
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

// This week's first day, the anchor both `weekFor` and the New week button work from.
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
const block = id => host.querySelector('[data-week="' + id + '"]')
const hero = () => host.querySelector('.hero')
const newWeekButton = () => host.querySelector('.ab-ico[aria-label="New week"]')
const click = el => act(() => { el.click() })

describe('Plan — the plan, read outward from today', () => {
  it('leads with the week that covers today, days and all', () => {
    mocks.S.weeks = [
      week('w2', addDays(monday(), 7), 'Next week', [day(3, 'Pull', [{ id: 'a' }])]),
      week('w1', monday(), 'This one', [day(1, 'Push', [{ id: 'a' }, { id: 'b' }])]),
    ]
    mount()
    expect(hero().dataset.week).toBe('w1')
    expect(hero().textContent).toContain('This one')
    expect(hero().textContent).toContain('Push')
    expect(hero().textContent).toContain('2 exercises')
    expect(hero().textContent).toContain('1 day · 2 exercises')
    // everything else is a single line with its dates, its size and what got done
    expect(block('w2').textContent).toContain('Next week')
    expect(block('w2').querySelector('.ss').textContent).toContain('1 day')
    expect(block('w2').querySelector('.ss').textContent).toContain('0 of 1 done')
  })

  it('orders the weeks around today rather than oldest first', () => {
    mocks.S.weeks = [
      week('w0', addDays(monday(), -7), 'Last'),
      week('w1', monday(), 'Now'),
      week('w2', addDays(monday(), 7), 'Next'),
      week('w3', addDays(monday(), 14), 'After'),
    ]
    mount()
    expect([...host.querySelectorAll('[data-week]')].map(el => el.dataset.week)).toEqual(['w1', 'w3', 'w2', 'w0'])
    expect(host.textContent).toContain('Upcoming')
    expect(host.textContent).toContain('Earlier')
  })

  // Two sessions on the same weekday in different weeks are two different days, so the count is
  // per date: the workout log holds dates, not weekdays.
  it('counts a day as done from the workout logged on that date', () => {
    mocks.S.workouts = [{ d: addDays(monday(), 1), entries: [] }]
    mocks.S.weeks = [week('w1', monday(), 'Now', [day(2, 'Push'), day(4, 'Pull')])]
    mount()
    expect(host.querySelectorAll('.dtick')).toHaveLength(1)
    const rows = [...hero().querySelectorAll('.day-row')]
    expect(rows.map(r => (r.querySelector('.dtick') ? 'done' : 'todo'))).toEqual(['done', 'todo'])
  })

  it('says so when the week plans no days', () => {
    mocks.S.weeks = [week('w1', monday(), 'Empty week')]
    mount()
    expect(hero().textContent).toContain('No days yet')
  })

  it('falls back to a list when no week covers today', () => {
    mocks.S.weeks = [week('w1', addDays(monday(), -14), 'Old')]
    mount()
    expect(hero()).toBeNull()
    expect(block('w1').textContent).toContain('Old')
    expect(host.textContent).toContain('Your weeks')
  })

  it('opens a week editor from a day on the card', () => {
    mocks.S.weeks = [week('w1', monday(), 'This one', [day(1, 'Push')])]
    mount()
    click(hero().querySelector('[role="button"]'))
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
