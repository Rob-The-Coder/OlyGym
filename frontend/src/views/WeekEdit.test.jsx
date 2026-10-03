// @vitest-environment happy-dom
// The week editor behind /plan/w/:id: the name and the days of one dated week, written straight
// into S.weeks through update() like every other editor in the app.
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import WeekEdit from './WeekEdit.jsx'
import { exercisePicker, exConfigSheet, complexConfigSheet } from '../sheets.jsx'
import { DEF, useStore } from '../store/useStore.js'

const mocks = vi.hoisted(() => ({ confirmSheet: vi.fn(), complexConfigSheet: vi.fn(), menuSheet: vi.fn() }))
vi.mock('../sheets.jsx', () => ({
  effortHelpSheet: vi.fn(),
  exercisePicker: vi.fn(), exConfigSheet: vi.fn(), complexConfigSheet: mocks.complexConfigSheet,
  confirmSheet: mocks.confirmSheet, menuSheet: mocks.menuSheet,
}))
vi.mock('../components/Media.jsx', () => ({ Thumb: () => null }))

globalThis.IS_REACT_ACT_ENVIRONMENT = true
const clone = value => JSON.parse(JSON.stringify(value))

function type(el, value) {
  Object.getOwnPropertyDescriptor(el.constructor.prototype, 'value').set.call(el, value)
  el.dispatchEvent(new Event('input', { bubbles: true }))
}

let host, root
beforeEach(() => {
  localStorage.clear()
  mocks.confirmSheet.mockClear()
  mocks.complexConfigSheet.mockClear()
  mocks.menuSheet.mockClear()
  useStore.setState({
    S: {
      ...clone(DEF),
      weeks: [{ id: 'w1', startIso: '2026-02-09', name: 'Block 1', days: [{ dow: 1, name: 'Push', ex: [] }] }],
    },
    user: null,
  })
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
})
afterEach(() => {
  act(() => root.unmount())
  host.remove()
})

const mount = () => act(() => root.render(
  <MemoryRouter initialEntries={['/plan/w/w1']}>
    <Routes><Route path="/plan/w/:id" element={<WeekEdit />} /></Routes>
  </MemoryRouter>
))
const week = () => useStore.getState().S.weeks.find(w => w.id === 'w1')
const button = text => [...host.querySelectorAll('button')].find(b => b.textContent.includes(text))

describe('WeekEdit', () => {
  it('writes the week name back into S.weeks', () => {
    mount()
    act(() => type(host.querySelector('.ab-big input'), 'Deload'))
    expect(week().name).toBe('Deload')
  })

  it('adds a day on the first free weekday', () => {
    mount()
    act(() => { button('Add day').click() })
    expect(week().days).toHaveLength(2)
    expect(week().days[1]).toEqual({ dow: 2, name: 'New day', ex: [] })
  })

  // Removing a day is destructive, so it is behind the day's own menu rather than an unlabelled
  // X sitting on the card face next to the weekday control (WS17).
  it('deletes a day through its menu and the confirm sheet', () => {
    mount()
    act(() => { host.querySelector('[data-day="0"] button[aria-label="Day options"]').click() })
    expect(mocks.menuSheet).toHaveBeenCalledTimes(1)
    const menu = mocks.menuSheet.mock.calls[0][0]
    expect(menu.title).toBe('Push')
    expect(menu.items.map(i => i.label)).toEqual(['Remove this day'])
    expect(menu.items[0].danger).toBe(true)

    act(() => { menu.items[0].onClick() })
    expect(mocks.confirmSheet).toHaveBeenCalledTimes(1)
    const dialog = mocks.confirmSheet.mock.calls[0][0]
    expect(dialog.title).toBe('Delete day?')
    expect(dialog.message).toBe('“Push” and its exercises will be removed.')
    act(() => { dialog.onConfirm() })
    expect(week().days).toEqual([])
  })

  // The week's own destructive action lives in the app bar menu now: it used to be a full-width
  // red button at the end of the screen, the largest target on it.
  it('keeps deleting the week in the app bar menu, not on the page', () => {
    mount()
    expect([...host.querySelectorAll('button')].some(b => b.textContent === 'Delete week')).toBe(false)
    act(() => { host.querySelector('button[aria-label="Week options"]').click() })
    const menu = mocks.menuSheet.mock.calls[0][0]
    expect(menu.subtitle).toBe('Mon, 9 Feb 2026')
    expect(menu.items[0].label).toBe('Delete this week')
    act(() => { menu.items[0].onClick() })
    expect(mocks.confirmSheet.mock.calls[0][0].title).toBe('Delete week?')
  })

  it('opens a day into its exercise editor', () => {
    mount()
    const body = host.querySelector('[data-day="0"] button[aria-expanded]')
    act(() => { body.click() })
    expect(button('Add exercise')).toBeTruthy()
    expect(host.querySelector('[data-day="0"]').textContent).toContain('No exercises yet — add your first one.')
  })

  // The day editor is RoutineEdit's flow: the picker adds, and a row opens the exercise config.
  it('adds through the exercise picker and configures from the row', () => {
    const entry = { id: '0025', sets: 3, reps: 10, weight: 40, mode: 'reps' }
    const S = useStore.getState().S
    S.weeks[0].days[0].ex = [entry]
    useStore.setState({ S })
    mount()
    act(() => { host.querySelector('[data-day="0"] button[aria-expanded]').click() })
    act(() => { button('Add exercise').click() })
    expect(exercisePicker).toHaveBeenCalledTimes(1)
    act(() => { host.querySelector('[data-day="0"] .item').click() })
    expect(exConfigSheet).toHaveBeenCalledTimes(1)
    expect(exConfigSheet.mock.calls[0][1]).toEqual(entry)
  })

  // A complex is one card, and the sheet that writes its shared sets/load edits this day's own
  // ex — a day is what a session is built from, so that is the live plan.
  it('opens the complex sheet addressed at this week’s day', () => {
    const S = useStore.getState().S
    S.weeks[0].days[0].ex = [
      { id: '0025', sets: 3, reps: 3, weight: 30, sg: 'g1' },
      { id: '0031', sets: 3, reps: 1, weight: 30, sg: 'g1' },
    ]
    useStore.setState({ S })
    mount()
    act(() => { host.querySelector('[data-day="0"] button[aria-expanded]').click() })
    act(() => { host.querySelector('.cx-head').click() })
    expect(mocks.complexConfigSheet).toHaveBeenCalledWith({ weekId: 'w1', dayIndex: 0 }, 0)
  })
})
