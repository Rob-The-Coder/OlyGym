// @vitest-environment happy-dom
import React, { act } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRoot } from 'react-dom/client'
import { LANGS, DERIVED_LOCALES } from './lib/i18n-core.js'
import { useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { calendarSheet, logPastWorkoutSheet } from './sheets.jsx'
import { fmtDate, isoOf, todayISO } from './lib/format.js'
import { nav } from './lib/nav.js'

vi.mock('./lib/nav.js', () => ({ nav: vi.fn() }))

// The calendar and the backfill sheet are one flow: the calendar knows the date you meant, and the
// backfill sheet is where that date becomes a session. It used to be two unconnected screens —
// tapping a past day sent you to the plan, and the only way into the backfill was a + in History
// that then made you find the same day again in a platform date picker.

const mounted = []
function mountTop() {
  const sheet = useUI.getState().sheets.at(-1)
  const host = document.createElement('div')
  document.body.appendChild(host)
  const root = createRoot(host)
  mounted.push(root)
  act(() => root.render(sheet.render(() => useUI.getState().closeSheet(sheet.id))))
  return host
}
const button = (host, text) => [...host.querySelectorAll('button')].find(b => b.textContent.trim() === text)
const dayCell = (host, n) => [...host.querySelectorAll('.cal-d')].find(c => c.textContent.trim() === String(n))
const rowTitled = (host, title) => [...host.querySelectorAll('.lrow')].find(r => r.textContent.includes(title))
const monthAgo = n => { const d = new Date(); d.setDate(1); d.setMonth(d.getMonth() - n); return d }

describe('log a past workout', () => {
  beforeEach(() => {
    globalThis.IS_REACT_ACT_ENVIRONMENT = true
    useUI.setState({ sheets: [], toastMsg: '' })
    useStore.setState(s => ({ S: { ...s.S, active: null, unit: 'kg', weeks: [], workouts: [{ id: 'old', d: todayISO(), start: 1, end: 2, name: 'Old', entries: [], prs: [] }] } }))
    document.body.innerHTML = ''
  })
  afterEach(() => { act(() => { mounted.splice(0).forEach(root => root.unmount()) }) })

  it('refuses while a workout is running', () => {
    const toast = vi.fn()
    useUI.setState({ toast })
    useStore.setState(s => ({ S: { ...s.S, active: { id: 'a', entries: [] } } }))
    logPastWorkoutSheet()
    expect(useUI.getState().sheets).toHaveLength(0)
    expect(toast).toHaveBeenCalledWith('Finish the current workout first.')
  })

  it('opens on the date it was handed, so the calendar does not make you find it again', () => {
    const d = isoOf(monthAgo(1))
    logPastWorkoutSheet(d)
    const host = mountTop()
    expect(host.querySelector('.lrow-v').textContent).toBe(fmtDate(d, true))
  })

  // The two fields were <input type="date"> and <input type="time"> — platform widgets in a sheet,
  // drawn in the platform's colours and the platform's format, against the rule the rest of the
  // control set is built on. There is no <input> left in either sheet.
  it('has no platform date or time field left in it', () => {
    logPastWorkoutSheet()
    const host = mountTop()
    expect(host.querySelectorAll('input[type=date], input[type=time]')).toHaveLength(0)
    expect(host.textContent).not.toMatch(/\d\d\/\d\d\/\d{4}/)
  })

  it('opens the month grid on the date row, with the days that already have sessions marked', () => {
    logPastWorkoutSheet(todayISO())
    const host = mountTop()
    act(() => rowTitled(host, 'Date').click())
    const pick = mountTop()
    expect(pick.querySelectorAll('.cal-d').length).toBeGreaterThanOrEqual(28)
    expect(pick.querySelector('.cal-d.has')).toBeTruthy()
    expect(pick.querySelector('.cal-d.sel')).toBeTruthy()
  })

  it('sets the date from the grid and closes it', () => {
    const d = isoOf(monthAgo(1))
    logPastWorkoutSheet(d)
    const host = mountTop()
    act(() => rowTitled(host, 'Date').click())
    const pick = mountTop()
    act(() => dayCell(pick, 12).click())
    expect(useUI.getState().sheets).toHaveLength(1)
    expect(host.querySelector('.lrow-v').textContent).toBe(fmtDate(d.slice(0, 8) + '12', true))
  })

  it('sets the start time from the app own stepper and presets', () => {
    logPastWorkoutSheet()
    const host = mountTop()
    act(() => rowTitled(host, 'Start time').click())
    const pick = mountTop()
    act(() => button(pick, '19:30').click())
    act(() => button(pick, 'Done').click())
    expect(host.textContent).toContain('19:30')
  })

  it('shows what is already on the day before Continue, not after it', () => {
    logPastWorkoutSheet(todayISO())
    const host = mountTop()
    expect(host.textContent).toContain('Already on this day')
    expect(host.textContent).toContain('Old')
  })

  // It was a centred alert with two full-width buttons and a third for Cancel, the destructive one
  // above the safe one. It is the app's menu, with the trash on Replace and dismiss for Cancel.
  it('offers the choice as a menu, with Replace carrying the danger role', () => {
    logPastWorkoutSheet(todayISO())
    const host = mountTop()
    act(() => button(host, 'Continue').click())
    const menu = mountTop()
    expect(menu.textContent).toContain('There is already a workout on that day.')
    expect(menu.textContent).toContain('Add as second workout')
    expect(menu.querySelector('.menu-item.danger')).toBeTruthy()
    expect(useStore.getState().S.active).toBeNull()
  })

  it('starts a backfilled session straight away on a free day', () => {
    logPastWorkoutSheet('2020-01-02')
    const host = mountTop()
    act(() => button(host, 'Continue').click())
    const A = useStore.getState().S.active
    expect(A.d).toBe('2020-01-02')
    expect(new Date(A.start).getHours()).toBe(18)
    expect(A.backfill).toEqual({ durationMin: 60, replaceId: null })
  })
})

describe('the calendar knows which side of today a day is', () => {
  beforeEach(() => {
    globalThis.IS_REACT_ACT_ENVIRONMENT = true
    useUI.setState({ sheets: [], toastMsg: '' })
    useStore.setState(s => ({ S: { ...s.S, active: null, unit: 'kg', weeks: [], workouts: [] } }))
    document.body.innerHTML = ''
    nav.mockClear()
  })
  afterEach(() => { act(() => { mounted.splice(0).forEach(root => root.unmount()) }) })

  it('opens the backfill on the date of a past day that has nothing on it', () => {
    const month = monthAgo(1)
    calendarSheet(month)
    const host = mountTop()
    act(() => dayCell(host, 15).click())
    const next = mountTop()
    expect(next.querySelector('h3').textContent).toBe('Log a past workout')
    const iso = isoOf(new Date(month.getFullYear(), month.getMonth(), 15))
    expect(next.querySelector('.lrow-v').textContent).toBe(fmtDate(iso, true))
    expect(nav).not.toHaveBeenCalled()
  })

  it('lists the sessions on a day that holds more than one', () => {
    const month = monthAgo(1)
    const d = isoOf(month).slice(0, 8) + '15'
    useStore.setState(s => ({ S: { ...s.S, workouts: [
      { id: 'a', d, start: 1, end: 2, name: 'Morning', entries: [], prs: [] },
      { id: 'b', d, start: 3, end: 4, name: 'Evening', entries: [], prs: [] },
    ] } }))
    calendarSheet(month)
    const host = mountTop()
    act(() => dayCell(host, 15).click())
    const list = mountTop()
    expect(list.textContent).toContain('Morning')
    expect(list.textContent).toContain('Evening')
  })

  it('still sends a day that has not happened yet to the plan', () => {
    calendarSheet(monthAgo(-2))
    const host = mountTop()
    act(() => dayCell(host, 15).click())
    expect(nav).toHaveBeenCalledWith('/plan')
  })
})

describe('calendar and backfill locale coverage', () => {
  const required = [
    'Previous month', 'Next month', 'Back to this month', 'Hour', 'Minute',
    'Already on this day', 'Replace {0}',
    'Tap a day you trained for it · a past day to log it · a day to come to plan it',
  ]
  const packs = import.meta.glob('./locales/*.js', { eager: true, import: 'default' })

  it('defines every new prompt in every locale pack', () => {
    const packed = Object.keys(LANGS).filter(code => code !== 'en' && !DERIVED_LOCALES[code])
    expect(Object.keys(packs)).toHaveLength(packed.length)
    Object.entries(packs).forEach(([path, pack]) => {
      required.forEach(key => expect(pack, `${path} is missing ${key}`).toHaveProperty(key))
    })
  })
})
