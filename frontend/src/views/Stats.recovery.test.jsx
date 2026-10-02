// @vitest-environment happy-dom
// Regression: the Muscle-balance card at the bottom of the Stats default view used to crash.
// The purge that removed the Strength tab also deleted the three locals the render still used,
// so `top` fell through to window.top and `top.map` threw as soon as a completed session put
// any muscle in the default 7-day window. This mounts Stats with exactly that state.
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { EXDB } from '../lib/exercises.js'
import { useStore } from '../store/useStore.js'
import { useUI } from '../store/useUI.js'
import { bindUI } from '../components/ui.jsx'
import Stats from './Stats.jsx'

vi.mock('react-router-dom', () => ({ useNavigate: () => () => {} }))
vi.mock('../sheets.jsx', () => ({
  bwSheet: () => {}, goalSheet: () => {}, calendarSheet: () => {}, workoutDetailSheet: () => {},
  exerciseHistorySheet: () => {}, WorkoutRow: () => React.createElement('div'), bwDeltaColor: () => 'inherit',
}))
vi.mock('../components/LineChart.jsx', () => ({ default: () => React.createElement('div') }))
vi.mock('../components/Heatmap.jsx', () => ({ default: () => React.createElement('div') }))
vi.mock('../components/Icon.jsx', () => ({ default: props => React.createElement('span', props) }))
vi.mock('../components/BodyMap.jsx', () => ({
  default: () => React.createElement('div', { 'data-body-map': true }),
  BodyMapLegend: () => React.createElement('div'),
}))

bindUI(useUI)

const BENCH = EXDB.find(e => e.n === 'bench press')
const today = new Date()
const workout = {
  id: 'w1', d: today.toISOString().slice(0, 10), start: today.getTime(), unit: 'kg',
  entries: [{ id: BENCH.id, sets: [{ w: 60, r: 5, done: true }, { w: 60, r: 5, done: true }, { w: 60, r: 5, done: true }] }],
}

let host, root
beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  useStore.setState(s => ({
    S: { ...s.S, unit: 'kg', body: 'male', effort: 'rir', targetW: null, bodyweight: [], routines: [], workouts: [workout], weekStart: 1 },
    user: null,
  }))
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
})
afterEach(() => { act(() => root.unmount()); host.remove() })

describe('Stats muscle balance runtime', () => {
  it('renders the top-muscle rows for a session in the default week instead of crashing', () => {
    act(() => root.render(React.createElement(Stats)))
    const row = [...host.querySelectorAll('.mrow')].find(el => el.querySelector('.nm')?.textContent.includes('Chest'))
    expect(row, 'expected the chest muscle-balance row to render').toBeTruthy()
    expect(row.querySelector('.bar i').style.width).toMatch(/%$/)
    expect(row.querySelector('.v').textContent).toContain('sets')
  })
})
