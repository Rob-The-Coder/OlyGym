// @vitest-environment happy-dom
// The coach's complexes (WS5): the routine list shows ONE card per complex, with the sets and the
// load the coach wrote once on the sheet, and the exercises numbered inside carrying only their own
// reps. The rows keep their `.item` class and their data attributes so the drag and the swipe go on
// working — this test is about what the card says, not about the gestures.
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import RoutineEdit from './RoutineEdit.jsx'
import { DEF, useStore } from '../store/useStore.js'
import { EXDB } from '../lib/exercises-data.js'

const mocks = vi.hoisted(() => ({ complexConfigSheet: vi.fn() }))
vi.mock('../lib/api.js', () => ({ api: vi.fn(() => Promise.resolve({})) }))
vi.mock('../sheets.jsx', () => ({
  glyphPicker: vi.fn(), exercisePicker: vi.fn(), exConfigSheet: vi.fn(), confirmSheet: vi.fn(),
  complexConfigSheet: mocks.complexConfigSheet
}))
vi.mock('../components/Media.jsx', () => ({ Thumb: () => null }))
vi.mock('../components/BodyMap.jsx', () => ({ default: () => null }))

globalThis.IS_REACT_ACT_ENVIRONMENT = true
const clone = value => JSON.parse(JSON.stringify(value))
const idOf = name => {
  const ex = EXDB.find(e => e.n === name)
  if (!ex) throw new Error(`"${name}" is not in the catalogue any more`)
  return ex.id
}

// "Strappo di forza + oh squat + sots press" as the importer writes it: one superset id, the same
// sets and load on all three, one rep count each.
const complex = (sets = 3, weight = 30) => [
  { id: idOf('muscle snatch'), sets, reps: 3, weight, sg: 'sg1', note: 'strappo di forza' },
  { id: idOf('overhead squat'), sets, reps: 1, weight, sg: 'sg1' },
  { id: idOf('press in snatch sots press'), sets, reps: 1, weight, sg: 'sg1' }
]

let root
let container

function setRoutine(ex) {
  const S = clone(DEF)
  S.routines = [{ id: 'r1', name: 'Giorno 1', emoji: 'dumbbell', ex }]
  useStore.setState({ S, user: null })
}

function renderRoutine() {
  container = document.createElement('div')
  document.body.appendChild(container)
  root = createRoot(container)
  act(() => root.render(
    <MemoryRouter initialEntries={['/routine/r1']}>
      <Routes><Route path="/routine/:id" element={<RoutineEdit />} /></Routes>
    </MemoryRouter>
  ))
}

const cards = () => [...container.querySelectorAll('.cx-card')]
const rowsOf = card => [...card.querySelectorAll('.cx-row')]

beforeEach(() => {
  mocks.complexConfigSheet.mockClear()
  setRoutine(complex())
})

afterEach(() => {
  act(() => root.unmount())
  container.remove()
})

describe('a complex in the routine', () => {
  it('is one card, with the sets and the load it shares in the heading', () => {
    renderRoutine()
    expect(cards()).toHaveLength(1)
    const head = cards()[0].querySelector('.cx-head')
    expect(head.textContent).toContain('Complex')
    expect(head.textContent).toContain('3 sets')
    expect(head.textContent).toContain('30 kg')
  })

  it('holds its exercises, numbered, each with its own reps', () => {
    renderRoutine()
    const rows = rowsOf(cards()[0])
    expect(rows).toHaveLength(3)
    expect(rows.map(r => r.querySelector('.cx-step').textContent)).toEqual(['1', '2', '3'])
    expect(rows[0].textContent.toLowerCase()).toContain('muscle snatch')
    expect(rows[0].textContent).toContain('3 reps')
    expect(rows[1].textContent).toContain('1 rep')
    // The scheme is on the heading: a row must not repeat "3 × 3 · 30 kg" a second and third time.
    expect(rows[0].textContent).not.toContain('3 × 3')
    expect(rows[1].textContent).toContain('1 rep')
    expect(rows[2].querySelector('.cx-reps').textContent).toContain('1')
  })

  it('reads a hold and a per-side count the way the rest of the app does', () => {
    setRoutine([
      { id: idOf('plank'), sets: 3, reps: null, mode: 'time', sec: 30, weight: 0, sg: 'sg1' },
      { id: idOf('side bend'), sets: 3, reps: 10, side: true, weight: 0, sg: 'sg1' }
    ])
    renderRoutine()
    const rows = rowsOf(cards()[0])
    expect(rows[0].querySelector('.cx-reps').textContent).toBe('0:30')
    expect(rows[1].querySelector('.cx-reps').textContent).toContain('/side')
  })

  it('keeps the note the coach wrote on its own exercise', () => {
    renderRoutine()
    expect(rowsOf(cards()[0])[0].textContent).toContain('strappo di forza')
    expect(rowsOf(cards()[0])[1].textContent).not.toContain('strappo di forza')
  })

  it('separates the exercises with a "+" in the numbers column', () => {
    renderRoutine()
    expect([...cards()[0].querySelectorAll('.cx-plus')].map(p => p.textContent)).toEqual(['+', '+'])
  })

  it('falls back to a full scheme per row when the members disagree', () => {
    // A routine edited by hand can end up with members that do not share sets or load; the heading
    // must not claim a number that is not true of every exercise under it.
    setRoutine(complex().map((e, i) => i === 2 ? { ...e, sets: 5, weight: 40 } : e))
    renderRoutine()
    const head = cards()[0].querySelector('.cx-head')
    expect(head.textContent).toContain('3 exercises')
    expect(head.textContent).not.toContain('30 kg')
    const rows = rowsOf(cards()[0])
    expect(rows[0].textContent).toContain('3 × 3')
    expect(rows[2].textContent).toContain('5 × 1')
  })

  it('opens the shared sets and load from the heading', () => {
    renderRoutine()
    act(() => cards()[0].querySelector('.cx-head').click())
    expect(mocks.complexConfigSheet).toHaveBeenCalledWith('r1', 0)
  })

  it('leaves a lone exercise as the plain row it has always been', () => {
    setRoutine([{ id: idOf('pause back squat'), sets: 4, reps: 1, weight: 70, note: 'stop in buca' }])
    renderRoutine()
    expect(cards()).toHaveLength(0)
    const row = container.querySelector('.item')
    expect(row.textContent.toLowerCase()).toContain('pause back squat')
    expect(row.textContent).toContain('4 × 1')
    expect(row.textContent).toContain('70 kg')
  })
})
