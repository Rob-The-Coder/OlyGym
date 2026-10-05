// @vitest-environment happy-dom
// The history sheet (issue #43) is the one place the workout screen answers "what did I do
// on this last time, and the time before" — worth pinning that it reads the log newest
// first, labels sets the way the rest of the app does, and does not pretend an exercise
// with no sessions has a curve.
import React, { act } from 'react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createRoot } from 'react-dom/client'
import { useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { exerciseHistorySheet, exerciseDetailSheet } from './sheets.jsx'
import { EXIDX } from './lib/exercises.js'

const mounted = []

function renderTop() {
  const sheet = useUI.getState().sheets.at(-1)
  const host = document.createElement('div')
  document.body.appendChild(host)
  const root = createRoot(host)
  mounted.push(root)
  act(() => root.render(sheet.render(() => useUI.getState().closeSheet(sheet.id))))
  return host
}

const type = (el, value) => {
  Object.getOwnPropertyDescriptor(el.constructor.prototype, 'value').set.call(el, value)
  el.dispatchEvent(new Event('input', { bubbles: true }))
}

const EX = Object.keys(EXIDX).find(id => (EXIDX[id].bp || '') !== 'cardio' && !EXIDX[id].custom)
const DAY = 86400000
const T0 = Date.UTC(2026, 2, 2, 9)
const iso = i => new Date(T0 + i * DAY).toISOString().slice(0, 10)
const session = (i, rows) => ({
  id: 'w' + i, d: iso(i), start: T0 + i * DAY, end: T0 + i * DAY + 3600000, name: 'Push', vol: 0,
  entries: [{ id: EX, target: { mode: 'reps', bodyweight: false }, sets: rows }],
})

beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  useUI.setState({ sheets: [], toastMsg: '' })
  useStore.setState(s => ({ S: { ...s.S, unit: 'kg', workouts: [], active: null } }))
  document.body.innerHTML = ''
})

afterEach(() => {
  act(() => { mounted.splice(0).forEach(root => root.unmount()) })
})

describe('exercise history sheet', () => {
  it('shows an empty state instead of a chart when nothing was logged', () => {
    exerciseHistorySheet(EX)
    const host = renderTop()
    expect(host.querySelector('.empty').textContent).toContain('No sessions logged yet')
    expect(host.querySelector('.chart')).toBeNull()
    expect(host.querySelector('.note-read')).toBeNull()
  })

  // The exercise's own note — seat height, pin position — was readable only inside the note editor,
  // which lives on a session entry and cannot be opened outside a workout.
  it('shows the standing note the exercise carries into every session', () => {
    const cue = 'Seat height 3, pinky on the ring.'
    useStore.setState(s => ({ S: { ...s.S, workouts: [session(0, [{ w: 60, r: 5, done: true }])], exNotes: { [EX]: cue } } }))
    exerciseHistorySheet(EX)
    const host = renderTop()
    expect(host.querySelector('.note-read').textContent).toBe(cue)
    expect(host.textContent).toContain('Every session')
  })

  // It was read-only: the only editor was keyed to an entry in the running session, so a cue
  // could be seen in the history and never corrected there.
  it('opens the note editor from the cue and saves it back', () => {
    useStore.setState(s => ({ S: { ...s.S, workouts: [session(0, [{ w: 60, r: 5, done: true }])], exNotes: { [EX]: 'seat at 4' } } }))
    exerciseHistorySheet(EX)
    const host = renderTop()
    const block = host.querySelector('.note-edit')
    expect(block.getAttribute('role')).toBe('button')
    act(() => { block.click() })
    expect(useUI.getState().sheets).toHaveLength(2)
    const editor = renderTop()
    // No session is running, so the editor is the standing note alone.
    expect(editor.querySelectorAll('textarea')).toHaveLength(1)
    act(() => { type(editor.querySelector('textarea'), 'seat at 5') })
    act(() => { [...editor.querySelectorAll('button')].find(b => /save/i.test(b.textContent)).click() })
    expect(useStore.getState().S.exNotes[EX]).toBe('seat at 5')
  })

  it('shows the standing note even before there is anything logged', () => {
    useStore.setState(s => ({ S: { ...s.S, workouts: [], exNotes: { [EX]: 'Belt on for anything over 80%.' } } }))
    exerciseHistorySheet(EX)
    const host = renderTop()
    expect(host.querySelector('.note-read').textContent).toBe('Belt on for anything over 80%.')
    expect(host.querySelector('.empty')).toBeTruthy()
  })

  it('lists sessions newest first with labelled sets, volume and one PR marker', () => {
    useStore.setState(s => ({ S: { ...s.S, workouts: [
      session(0, [{ w: 40, r: 8, done: true, phase: 'warmup' }, { w: 60, r: 5, done: true }, { w: 60, r: 5, done: true }]),
      session(2, [{ w: 70, r: 5, done: true }]),
      session(4, [{ w: 70, r: 3, done: true }, { w: 90, r: 1, done: false }]),
    ] } }))
    exerciseHistorySheet(EX)
    const host = renderTop()
    const rows = [...host.querySelectorAll('.list .item')]
    expect(rows).toHaveLength(3)
    // newest first, oldest last; the warm-up and the unfinished set never show up
    expect(rows[0].querySelector('.ss').textContent).toBe('70×3')
    expect(rows[2].querySelector('.ss').textContent).toBe('60×5  ·  60×5')
    expect(rows[2].textContent).toContain('Volume 600 kg')
    // the record was set in the middle session and only that one carries the badge
    expect(rows.map(r => !!r.querySelector('.pr'))).toEqual([false, true, false])
    expect(host.querySelector('.chart svg')).toBeTruthy()
    // Best and Last are the pair of tiles the exercise sheet uses, rather than a "Best:" run-on
    // line above a list whose first row already carried the same figure.
    const tiles = [...host.querySelectorAll('.tile')]
    expect(tiles).toHaveLength(2)
    expect(tiles[0].textContent).toContain('70 kg')
    expect(tiles[1].textContent).toContain('70 kg')
    expect(host.querySelector('h4.sec')).toBeNull()
    expect([...host.querySelectorAll('.sech')].some(e => e.textContent === 'Sessions')).toBe(true)
  })

  // The sheet was the one list in the app whose rows could not be opened: you could read the
  // session but not get to the workout behind it.
  it('opens the workout behind a session', () => {
    useStore.setState(s => ({ S: { ...s.S, workouts: [session(0, [{ w: 60, r: 5, done: true }])] } }))
    exerciseHistorySheet(EX)
    const host = renderTop()
    const row = host.querySelector('.list .item')
    expect(row.getAttribute('role')).toBe('button')
    act(() => { row.click() })
    expect(useUI.getState().sheets).toHaveLength(2)
    expect(renderTop().querySelector('h3').textContent).toBe('Push')
  })

  it('is reachable from the exercise detail sheet once there is history', () => {
    exerciseDetailSheet(EXIDX[EX])
    const before = renderTop()
    expect([...before.querySelectorAll('button')].some(b => b.textContent === 'History')).toBe(false)

    useStore.setState(s => ({ S: { ...s.S, workouts: [session(0, [{ w: 60, r: 5, done: true }])] } }))
    useUI.setState({ sheets: [] })
    exerciseDetailSheet(EXIDX[EX])
    const host = renderTop()
    const btn = [...host.querySelectorAll('button')].find(b => b.textContent === 'History')
    expect(btn).toBeTruthy()
    act(() => { btn.click() })
    expect(useUI.getState().sheets).toHaveLength(2)
    const top = renderTop()
    expect(top.querySelectorAll('.list .item')).toHaveLength(1)
  })
})
