// @vitest-environment happy-dom
// The note sheet is new JSX and nothing else mounts it, so a bad hook order, a missing import or
// a wrong store path would only surface on a real device. Render it through the real sheet stack
// and drive a save, so the wiring is checked and not just the shape of the module.
import { describe, expect, it, beforeEach, afterEach } from 'vitest'
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { useStore } from '../store/useStore.js'
import { useUI } from '../store/useUI.js'
import { exerciseNoteSheet } from '../sheets.jsx'

const activeWith = entry => ({ id: 'w1', d: '2026-08-25', start: 1, routineId: 'r1', name: 'Push', entries: [entry] })

// Roots have to be torn down between tests: the sheet subscribes to the store, so a root left
// mounted reacts to the next test's setState — outside act(), which React rightly complains about.
const mounted = []

function renderSheet() {
  exerciseNoteSheet({ entryIdx: 0 })
  const sheet = useUI.getState().sheets.at(-1)
  const host = document.createElement('div')
  document.body.appendChild(host)
  const root = createRoot(host)
  mounted.push(root)
  act(() => root.render(sheet.render(sheet_close(sheet))))
  return host
}
const sheet_close = sheet => () => useUI.getState().closeSheet(sheet.id)

// The other door: the standing note opened by exercise id, with no session behind it.
function renderForExercise(exId) {
  exerciseNoteSheet({ exId })
  const sheet = useUI.getState().sheets.at(-1)
  const host = document.createElement('div')
  document.body.appendChild(host)
  const root = createRoot(host)
  mounted.push(root)
  act(() => root.render(sheet.render(sheet_close(sheet))))
  return host
}

const type = (el, value) => {
  const setter = Object.getOwnPropertyDescriptor(el.constructor.prototype, 'value').set
  setter.call(el, value)
  el.dispatchEvent(new Event('input', { bubbles: true }))
}

describe('exercise note sheet', () => {
  beforeEach(() => {
    // React only treats act() as real when told it is in a test environment, and vitest shares
    // a worker across files — so set it per test rather than once at module scope.
    globalThis.IS_REACT_ACT_ENVIRONMENT = true
    useUI.setState({ sheets: [] })
    useStore.setState(s => ({
      S: { ...s.S, exNotes: {}, active: activeWith({ id: 'bench', sets: [] }) },
    }))
    document.body.innerHTML = ''
  })

  afterEach(() => {
    act(() => { mounted.splice(0).forEach(r => r.unmount()) })
  })

  it('renders the session note, the pin and the standing note', () => {
    const host = renderSheet()
    const areas = host.querySelectorAll('textarea')
    expect(areas).toHaveLength(2)
    // The pin cannot be set on an empty note — it would pin nothing.
    expect(host.querySelector('[role="switch"]').disabled).toBe(true)
  })

  it('saves the session note with its pin and the standing note separately', () => {
    const host = renderSheet()
    const [today, always] = host.querySelectorAll('textarea')
    act(() => { type(today, 'shoulder twinged'); type(always, 'seat at 4') })
    act(() => { host.querySelector('[role="switch"]').click() })
    const save = [...host.querySelectorAll('button')].find(b => /save/i.test(b.textContent))
    act(() => { save.click() })

    const S = useStore.getState().S
    expect(S.active.entries[0].note).toBe('shoulder twinged')
    expect(S.active.entries[0].notePin).toBe(true)
    // The standing note belongs to the exercise, not to today's entry.
    expect(S.exNotes.bench).toBe('seat at 4')
    expect(useUI.getState().sheets).toHaveLength(0)
  })

  // The standing note belongs to the exercise, not to a session, so it has to be writable when
  // nothing is running — otherwise a cue can be read in the history and never corrected.
  it('opens on the exercise alone when there is no session to attach it to', () => {
    useStore.setState(s => ({ S: { ...s.S, active: null, exNotes: { bench: 'seat at 4' } } }))
    const host = renderForExercise('bench')
    // One textarea: the session note has no entry to live on, so only the standing note is here.
    const areas = host.querySelectorAll('textarea')
    expect(areas).toHaveLength(1)
    expect(areas[0].value).toBe('seat at 4')
    act(() => { type(areas[0], 'seat at 5, pin 7') })
    const save = [...host.querySelectorAll('button')].find(b => /save/i.test(b.textContent))
    act(() => { save.click() })
    expect(useStore.getState().S.exNotes.bench).toBe('seat at 5, pin 7')
    expect(useUI.getState().sheets).toHaveLength(0)
  })

  it('emptying the standing note outside a session drops it', () => {
    useStore.setState(s => ({ S: { ...s.S, active: null, exNotes: { bench: 'seat at 4' } } }))
    const host = renderForExercise('bench')
    act(() => { type(host.querySelector('textarea'), '') })
    const save = [...host.querySelectorAll('button')].find(b => /save/i.test(b.textContent))
    act(() => { save.click() })
    expect(useStore.getState().S.exNotes.bench).toBeUndefined()
  })

  it('clearing the session note drops the pin with it', () => {
    useStore.setState(s => ({
      S: { ...s.S, active: activeWith({ id: 'bench', sets: [], note: 'old', notePin: true }) },
    }))
    const host = renderSheet()
    const [today] = host.querySelectorAll('textarea')
    act(() => { type(today, '') })
    const save = [...host.querySelectorAll('button')].find(b => /save/i.test(b.textContent))
    act(() => { save.click() })
    const e = useStore.getState().S.active.entries[0]
    expect(e.note).toBeUndefined()
    expect(e.notePin).toBeUndefined()
  })
})
