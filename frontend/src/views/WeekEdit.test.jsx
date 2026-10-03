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

const mocks = vi.hoisted(() => ({ confirmSheet: vi.fn(), complexConfigSheet: vi.fn() }))
vi.mock('../sheets.jsx', () => ({
  effortHelpSheet: vi.fn(),
  exercisePicker: vi.fn(), exConfigSheet: vi.fn(), complexConfigSheet: mocks.complexConfigSheet,
  confirmSheet: mocks.confirmSheet,
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
    act(() => type(host.querySelector('.hdr input'), 'Deload'))
    expect(week().name).toBe('Deload')
  })

  it('adds a day on the first free weekday', () => {
    mount()
    act(() => { button('Add day').click() })
    expect(week().days).toHaveLength(2)
    expect(week().days[1]).toEqual({ dow: 2, name: 'New day', ex: [] })
  })

  it('deletes a day through the confirm sheet', () => {
    mount()
    act(() => { host.querySelector('[data-day="0"] button[aria-label="Remove"]').click() })
    expect(mocks.confirmSheet).toHaveBeenCalledTimes(1)
    const dialog = mocks.confirmSheet.mock.calls[0][0]
    expect(dialog.title).toBe('Delete day?')
    expect(dialog.message).toBe('“Push” and its exercises will be removed.')
    act(() => { dialog.onConfirm() })
    expect(week().days).toEqual([])
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
