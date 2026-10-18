// @vitest-environment happy-dom
// Deleting a logged workout is now undoable: the workout leaves S.workouts, lands on the
// ephemeral useUI.undo stack, and the snackbar offers an Undo action that restores it in
// chronological order. Pins the full flow through the ⋯ menu and the confirm dialog.
import React, { act } from 'react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createRoot } from 'react-dom/client'
import { useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { workoutDetailSheet } from './sheets.jsx'
import { EXIDX } from './lib/exercises.js'
import Toast from './components/Toast.jsx'

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

function renderToast() {
  const host = document.createElement('div')
  document.body.appendChild(host)
  const root = createRoot(host)
  mounted.push(root)
  act(() => root.render(<Toast />))
  return host
}

const type = (el, value) => {
  Object.getOwnPropertyDescriptor(el.constructor.prototype, 'value').set.call(el, value)
  el.dispatchEvent(new Event('input', { bubbles: true }))
}
const blur = el => el.dispatchEvent(new Event('focusout', { bubbles: true }))

// Drive the full ⋯ → Delete workout → confirm sequence; leaves the Undo action in useUI.
function deleteViaUi(w) {
  workoutDetailSheet(w)
  const detail = renderTop()
  act(() => { detail.querySelector('.ab-ico').click() })
  const menu = renderTop()
  act(() => { [...menu.querySelectorAll('.menu-item')].find(e => e.textContent.includes('Delete workout')).click() })
  const confirm = renderTop()
  act(() => { [...confirm.querySelectorAll('.dlg-btn')].find(b => b.textContent.trim() === 'Delete').click() })
}

const EX = Object.keys(EXIDX).find(id => (EXIDX[id].bp || '') !== 'cardio' && !EXIDX[id].custom)
const DAY = 86400000
const T0 = Date.UTC(2026, 2, 1, 9)
const mk = (i, extra = {}) => ({
  id: 'w' + i, d: new Date(T0 + (i - 1) * DAY).toISOString().slice(0, 10),
  start: T0 + (i - 1) * DAY, end: T0 + (i - 1) * DAY + 3600000,
  name: 'Push', vol: 600, bw: 80, prs: [EX], note: 'felt strong',
  entries: [{ id: EX, target: { mode: 'reps', bodyweight: false }, sets: [{ w: 60, r: 5, done: true }, { w: 70, r: 3, done: true }] }],
  ...extra,
})

beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  useUI.setState({ sheets: [], toastMsg: '', toastAction: null, undo: [] })
  useStore.setState(s => ({ S: { ...s.S, unit: 'kg', workouts: [], active: null } }))
  document.body.innerHTML = ''
})

afterEach(() => {
  act(() => { mounted.splice(0).forEach(root => root.unmount()) })
})

describe('undo delete workout', () => {
  it('deletes, then restores the same workout through the Undo snackbar action', () => {
    const w1 = mk(1), w2 = mk(2), w3 = mk(3)
    useStore.setState(s => ({ S: { ...s.S, workouts: [w1, w2, w3] } }))

    workoutDetailSheet(w2)
    const detail = renderTop()

    // ⋯ → Delete workout
    act(() => { detail.querySelector('.ab-ico').click() })
    const menu = renderTop()
    const deleteItem = [...menu.querySelectorAll('.menu-item')].find(e => e.textContent.includes('Delete workout'))
    expect(deleteItem).toBeTruthy()
    act(() => { deleteItem.click() })

    // The confirm dialog, still behind the .center + .mback shape (confirmSheet opens kind:'center').
    const confirm = renderTop()
    expect(useUI.getState().sheets.at(-1).kind).toBe('center')
    const confirmBtn = [...confirm.querySelectorAll('.dlg-btn')].find(b => b.textContent.trim() === 'Delete')
    expect(confirmBtn).toBeTruthy()
    act(() => { confirmBtn.click() })

    // Gone from S, remembered on the ephemeral stack, snackbar offers Undo.
    expect(useStore.getState().S.workouts.map(x => x.id)).toEqual(['w1', 'w3'])
    expect(useUI.getState().undo.map(x => x.id)).toEqual(['w2'])
    expect(useUI.getState().toastMsg).toBe('Workout deleted')
    expect(useUI.getState().toastAction).toBeTruthy()
    expect(useUI.getState().toastAction.label).toBe('Undo')

    // Tap Undo: it restores in chronological order, intact, and clears the stack.
    const toastHost = renderToast()
    const undoBtn = toastHost.querySelector('.toast-act')
    expect(undoBtn).toBeTruthy()
    expect(undoBtn.textContent).toBe('Undo')
    act(() => { undoBtn.click() })

    expect(useUI.getState().undo).toEqual([])
    expect(useStore.getState().S.workouts.map(x => x.id)).toEqual(['w1', 'w2', 'w3'])
    const restored = useStore.getState().S.workouts.find(x => x.id === 'w2')
    expect(restored.entries).toEqual(w2.entries)
    expect(restored.prs).toEqual(w2.prs)
    expect(restored.note).toBe('felt strong')
    expect(useUI.getState().toastMsg).toBe('Workout restored')
    expect(useUI.getState().toastAction).toBeNull()
  })

  it('restores the note as edited before deleting, not the stale snapshot', () => {
    const w2 = mk(2)
    useStore.setState(s => ({ S: { ...s.S, workouts: [w2] } }))

    workoutDetailSheet(w2)
    const detail = renderTop()
    const ta = detail.querySelector('textarea')
    act(() => { type(ta, 'NEW note typed before delete') })
    // saveNote writes into a deep-cloned S, so the `w` prop is now a stale snapshot.
    act(() => { blur(ta) })
    expect(useStore.getState().S.workouts[0].note).toBe('NEW note typed before delete')
    expect(w2.note).toBe('felt strong')

    // Delete through the real flow, then undo.
    act(() => { detail.querySelector('.ab-ico').click() })
    const menu = renderTop()
    act(() => { [...menu.querySelectorAll('.menu-item')].find(e => e.textContent.includes('Delete workout')).click() })
    const confirm = renderTop()
    act(() => { [...confirm.querySelectorAll('.dlg-btn')].find(b => b.textContent.trim() === 'Delete').click() })
    expect(useStore.getState().S.workouts).toEqual([])

    const toastHost = renderToast()
    act(() => { toastHost.querySelector('.toast-act').click() })

    const restored = useStore.getState().S.workouts.find(x => x.id === 'w2')
    expect(restored).toBeTruthy()
    expect(restored.note).toBe('NEW note typed before delete')
  })

  it('a double-fired undo restores its own workout, not an older deletion', () => {
    const w1 = mk(1), w2 = mk(2), w3 = mk(3)
    useStore.setState(s => ({ S: { ...s.S, workouts: [w1, w2, w3] } }))

    deleteViaUi(w2)                 // stack [w2]
    deleteViaUi(w3)                 // stack [w2, w3]; the Undo action now belongs to w3
    const action = useUI.getState().toastAction.onClick

    act(() => { action() })         // restores w3
    expect(useStore.getState().S.workouts.map(x => x.id)).toEqual(['w1', 'w3'])
    expect(useUI.getState().undo.map(x => x.id)).toEqual(['w2'])

    act(() => { action() })         // double-fire: top is w2, not w3 — must not restore w2
    expect(useStore.getState().S.workouts.map(x => x.id)).toEqual(['w1', 'w3'])
    expect(useUI.getState().undo.map(x => x.id)).toEqual(['w2'])
  })

  it('caps the stack at UNDO_MAX without mutating the stored workouts', () => {
    const seeds = [1, 2, 3, 4, 5, 6].map(mk)
    useStore.setState(s => ({ S: { ...s.S, workouts: seeds } }))
    useUI.setState({ undo: seeds.slice(0, 5) })
    // Delete the sixth and confirm: the oldest drops off the top of the stack.
    workoutDetailSheet(seeds[5])
    const detail = renderTop()
    act(() => { detail.querySelector('.ab-ico').click() })
    const menu = renderTop()
    act(() => { [...menu.querySelectorAll('.menu-item')].find(e => e.textContent.includes('Delete workout')).click() })
    const confirm = renderTop()
    act(() => { [...confirm.querySelectorAll('.dlg-btn')].find(b => b.textContent.trim() === 'Delete').click() })
    expect(useUI.getState().undo.map(x => x.id)).toEqual(['w2', 'w3', 'w4', 'w5', 'w6'])
  })
})
