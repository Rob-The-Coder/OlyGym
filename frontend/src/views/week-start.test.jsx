// @vitest-environment happy-dom
// The setting is only worth anything if the screens actually follow it: the toggle has to
// write the field, and the Plan list has to draw the week in that order.
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Settings from './Settings.jsx'
import Plan from './Plan.jsx'
import { isoOf, startOfWeek, todayISO } from '../lib/format.js'

globalThis.IS_REACT_ACT_ENVIRONMENT = true

const mocks = vi.hoisted(() => {
  const state = { S: null }
  state.snapshot = () => ({
    S: state.S,
    user: null,
    update: mut => {
      const next = structuredClone(state.S)
      mut(next)
      state.S = next
    },
    replaceState: vi.fn(), setUser: vi.fn(), pullState: vi.fn(), pushState: vi.fn(),
    signOut: vi.fn(), signOutAll: vi.fn(), resetDemo: vi.fn(), disconnectServer: vi.fn(),
  })
  return state
})
vi.mock('../store/useStore.js', () => {
  const useStore = selector => (selector ? selector(mocks.snapshot()) : mocks.snapshot())
  useStore.getState = mocks.snapshot
  return { useStore, DEF: { reminder: { time: '17:30' } }, hasData: () => false }
})
vi.mock('../store/useUI.js', () => {
  const snap = () => ({ toast: vi.fn(), openSheet: vi.fn() })
  const useUI = selector => (selector ? selector(snap()) : snap())
  useUI.getState = snap
  return { useUI }
})
vi.mock('react-router-dom', () => ({ useNavigate: () => () => {} }))
vi.mock('../lib/wakelock.js', () => ({ wakeLockSupported: () => false }))
vi.mock('../lib/mobile.js', () => ({ MOBILE: false, isAndroid: () => Promise.resolve(false), shareExport: vi.fn(), syncReminder: vi.fn() }))
vi.mock('../sheets.jsx', () => ({
  effortHelpSheet: vi.fn(),
  starterPlanSheet: vi.fn(), confirmSheet: vi.fn(),
  equipmentProfileSheet: vi.fn(), importCoachPlanFromDrive: vi.fn(),
  planToolsSheet: vi.fn(),
}))

globalThis.__APP_VERSION__ ??= 'test'

let host, root
beforeEach(() => {
  mocks.S = {
    unit: 'kg', restSec: 90, sound: false, effort: 'none',
    gifSize: 'full', workouts: [], routines: [], exWeights: {}, week: {}, dayPlan: {},
    weeks: [{ id: 'w1', startIso: isoOf(startOfWeek(todayISO(), 1)), name: 'This week', days: ALL_DAYS }],
  }
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
})
afterEach(() => {
  act(() => root.unmount())
  host.remove()
})

const segButton = label => [...host.querySelectorAll('.seg button')].find(b => b.textContent === label)

// One of each weekday, so a rendered order can be compared whatever the profile starts on.
const ALL_DAYS = [
  { dow: 0, name: 'Sunday run', ex: [] },
  { dow: 1, name: 'Monday push', ex: [{ id: 'a' }] },
  { dow: 2, name: 'Tuesday pull', ex: [] },
  { dow: 3, name: 'Wednesday legs', ex: [] },
  { dow: 4, name: 'Thursday core', ex: [] },
  { dow: 5, name: 'Friday arms', ex: [] },
  { dow: 6, name: 'Saturday cardio', ex: [] },
]

describe('Settings — week starts on', () => {
  const mount = () => act(() => root.render(<Settings />))

  it('offers Monday and Sunday and writes the getDay() index', () => {
    mount()
    expect(segButton('Monday').getAttribute('aria-pressed')).toBe('true')
    act(() => { segButton('Sunday').click() })
    expect(mocks.S.weekStart).toBe(0)
    mount()
    expect(segButton('Sunday').getAttribute('aria-pressed')).toBe('true')
    act(() => { segButton('Monday').click() })
    expect(mocks.S.weekStart).toBe(1)
  })

  it('shows a profile written before the setting existed as Monday', () => {
    delete mocks.S.weekStart
    mount()
    expect(segButton('Monday').getAttribute('aria-pressed')).toBe('true')
    expect(segButton('Sunday').getAttribute('aria-pressed')).toBe('false')
  })
})

describe('Plan — a week draws its days in the profile order', () => {
  const mount = () => act(() => root.render(<Plan />))
  const dayNames = () => [...host.querySelectorAll('[data-week] .day-row .tt')].map(e => e.textContent)
  const mondayFirst = ['Monday push', 'Tuesday pull', 'Wednesday legs', 'Thursday core', 'Friday arms', 'Saturday cardio', 'Sunday run']

  it('runs Monday to Sunday by default', () => {
    mount()
    expect(dayNames()).toEqual(mondayFirst)
  })

  it('runs Sunday to Saturday for a Sunday profile', () => {
    mocks.S.weekStart = 0
    mount()
    expect(dayNames()).toEqual([...mondayFirst.slice(-1), ...mondayFirst.slice(0, -1)])
  })

  it('keeps a day on its weekday, not on its position in the list', () => {
    // Stored out of order: the render still follows the weekday, and each day keeps its own name.
    mocks.S.weeks[0].days = [ALL_DAYS[0], ALL_DAYS[6], ALL_DAYS[3]]
    mount()
    expect(dayNames()).toEqual(['Wednesday legs', 'Saturday cardio', 'Sunday run'])
  })
})
