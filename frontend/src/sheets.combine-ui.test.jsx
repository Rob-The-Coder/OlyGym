// @vitest-environment happy-dom
// Reading a saved workout back. A workout logged since the dated-weeks model is flat: one day is
// one session and its entries carry no routine id. Only a LEGACY record still groups, and it does
// so by the `rid` its entries kept.
import React, { act } from 'react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createRoot } from 'react-dom/client'
import { useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { workoutDetailSheet } from './sheets.jsx'
import { EXDB } from './lib/exercises.js'

const clone = v => JSON.parse(JSON.stringify(v))
const ids = EXDB.filter(e => e.bp !== 'cardio').slice(0, 4).map(e => e.id)
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

beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  useUI.setState({ sheets: [], toastMsg: '' })
  document.body.innerHTML = ''
})
afterEach(() => { act(() => { mounted.splice(0).forEach(r => r.unmount()) }) })

describe('WorkoutDetail — legacy per-routine grouping', () => {
  const combined = {
    id: 'w', d: '2026-09-06', start: 1, end: 2, name: 'Strength + Core', vol: 500,
    routineIds: ['strength', 'core'], prs: [],
    entries: [
      { id: ids[0], rid: 'strength', target: { reps: 5 }, sets: [{ w: 60, r: 5, done: true }, { w: 60, r: 5, done: true }] },
      { id: ids[1], rid: 'core', target: { reps: 12 }, sets: [{ w: 0, r: 12, done: true }] },
    ],
  }
  const legacy = {
    id: 'w2', d: '2026-09-06', start: 1, end: 2, name: 'Push', vol: 100, routineIds: ['strength'], prs: [],
    entries: [{ id: ids[0], target: { reps: 5 }, sets: [{ w: 100, r: 5, done: true }] }],
  }
  // What a session started from a day writes today: weekId/dow, no rids anywhere.
  const current = {
    id: 'w3', d: '2026-09-06', start: 1, end: 2, name: 'Push day', vol: 300, weekId: 'w1', dow: 3, prs: [],
    entries: [
      { id: ids[0], target: { reps: 5 }, sets: [{ w: 60, r: 5, done: true }] },
      { id: ids[1], target: { reps: 12 }, sets: [{ w: 0, r: 12, done: true }] },
    ],
  }

  beforeEach(() => {
    const S = clone(useStore.getState().S)
    S.routines = [
      { id: 'strength', name: 'Strength', emoji: '🏋️', ex: [] },
      { id: 'core', name: 'Core', emoji: '🧘', ex: [] },
    ]
    useStore.setState({ S, user: null })
  })

  it('groups a legacy combined workout into per-routine sections with a sets/volume subheader', () => {
    const host = (workoutDetailSheet(combined), renderTop())
    expect(host.textContent).toContain('Strength')
    expect(host.textContent).toContain('Core')
    expect(host.textContent).toMatch(/2 sets/)
    expect(host.textContent).toMatch(/1 sets|1 set/)
  })

  it('renders a legacy single-routine workout flat — no routine subheader', () => {
    const host = (workoutDetailSheet(legacy), renderTop())
    // the only routine name that could appear is the header <h3> (w.name = "Push"); there is
    // no "Strength" subheader row
    expect(host.querySelectorAll('.row.between').length).toBe(0)
  })

  it('renders a current (weekId/dow, no rid) workout flat — one day is one session', () => {
    const host = (workoutDetailSheet(current), renderTop())
    expect(host.querySelectorAll('.row.between').length).toBe(0)
    expect(host.textContent).toContain('Push day')
  })
})
