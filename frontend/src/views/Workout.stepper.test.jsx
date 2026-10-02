import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { parseHTML } from 'linkedom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Workout from './Workout.jsx'

// This suite pins down the stepper regression: one tap on a +/- button must move the value by
// exactly one step. The bug was a stale closure — any write re-renders the handler's owner and
// replaces the render-time snapshot it had closed over. A complex makes that sharper than ever:
// its one load cell writes to every movement of the group, so the handler must read the live
// value from the store at call time. The mocked `update` below clones the whole state on every
// write (structuredClone), which is exactly what invalidates those snapshots, so this environment
// reproduces the original two-taps-per-increment behaviour.
const mocks = vi.hoisted(() => {
  const state = {
    S: null,
    startRest: vi.fn(),
    stopRest: vi.fn(),
    stopWork: vi.fn(),
    toast: vi.fn(),
  }
  state.storeSnapshot = () => ({
    S: state.S,
    user: null,
    update: mut => {
      const next = structuredClone(state.S)
      mut(next)
      state.S = next
    },
  })
  state.uiSnapshot = () => ({
    timer: null,
    work: null,
    startRest: state.startRest,
    stopRest: state.stopRest,
    stopWork: state.stopWork,
    shiftRestOwner: vi.fn(),
    startWork: vi.fn(),
    toast: state.toast,
  })
  return state
})

vi.mock('../store/useStore.js', () => {
  const useStore = selector => selector(mocks.storeSnapshot())
  useStore.getState = mocks.storeSnapshot
  return { useStore }
})
vi.mock('../store/useUI.js', () => {
  const useUI = selector => selector ? selector(mocks.uiSnapshot()) : mocks.uiSnapshot()
  useUI.getState = mocks.uiSnapshot
  return { useUI }
})
vi.mock('react-router-dom', () => ({ useNavigate: () => () => {} }))
vi.mock('../sheets.jsx', () => ({
  startFlow: vi.fn(),
  exercisePicker: vi.fn(),
  exConfigSheet: vi.fn(),
  exerciseDetailSheet: vi.fn(),
  topWeightSheet: vi.fn(),
  finishWorkout: vi.fn(),
  workoutCompleteSheet: vi.fn(),
  confirmSheet: vi.fn(),
  swapActiveWorkoutExercise: vi.fn(),
  barWeightSheet: vi.fn(),
  exerciseNoteSheet: vi.fn(),
  sessionNoteSheet: vi.fn(),
  renameWorkoutSheet: vi.fn(),
}))
vi.mock('../components/Media.jsx', () => ({ default: () => null }))

let dom
let root
let container

function exercise(id, sets, extra = {}) {
  return {
    id,
    target: { mode: 'reps', reps: 5, weight: 60, bodyweight: false },
    sets: sets.map(done => ({ w: 60, r: 5, done })),
    ...extra,
  }
}

function workout(entries, cur = 0) {
  return {
    unit: 'kg', restSec: 90, sound: false, effort: 'none', gifSize: 'full',
    workouts: [], exWeights: {}, routines: [],
    active: { id: 'active', name: 'Test workout', start: Date.now(), cur, entries },
  }
}

function installDom() {
  const parsed = parseHTML('<!doctype html><html><body><div id="root"></div></body></html>')
  dom = parsed.window
  globalThis.window = dom
  globalThis.document = dom.document
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: dom.navigator })
  for (const key of ['HTMLElement', 'Node', 'Element', 'Event', 'Blob']) globalThis[key] = dom[key]
  dom.Element.prototype.scrollIntoView = vi.fn()
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  container = document.getElementById('root')
  root = createRoot(container)
}

async function mount(entries, cur = 0) {
  mocks.S = workout(entries, cur)
  installDom()
  await act(async () => { root.render(React.createElement(Workout)) })
}

async function unmount() {
  if (!root) return
  await act(async () => { root.unmount() })
  root = null
  container = null
  dom = null
}

// A complex is one table of rounds (`.cx-rounds`) with a single load cell per round: tapping it
// writes the same number to every movement, so this one handler is where a stale closure would
// bite hardest.
function roundStepper(setRow, direction) {
  const row = container.querySelectorAll('.cx-rounds .setrow')[setRow]
  expect(row).toBeTruthy()
  const cell = row.querySelector('.stp')
  expect(cell).toBeTruthy()
  return cell.querySelector(`button[aria-label="${direction}"]`)
}

// A group whose members cannot share one table keeps the per-movement tables, and the per-member
// stepper with them (the path this suite originally pinned down).
function memberStepper(exidx, setRow, col, direction) {
  const block = container.querySelector(`[data-exidx="${exidx}"]`)
  expect(block).toBeTruthy()
  const row = block.querySelectorAll('.setrow')[setRow]
  expect(row).toBeTruthy()
  const cell = row.querySelectorAll('.stp')[col]
  expect(cell).toBeTruthy()
  return cell.querySelector(`button[aria-label="${direction}"]`)
}

async function tap(button) {
  expect(button).toBeTruthy()
  await act(async () => { button.dispatchEvent(new dom.Event('click', { bubbles: true })) })
}

beforeEach(() => {
  vi.clearAllMocks()
})

afterEach(async () => {
  await unmount()
})

describe('complex load stepper — one tap moves one step, for the whole group', () => {
  // Weight steps by 2.5. A single tap on the round must land on 62.5 for every movement of the
  // complex, not stay at 60 waiting for a second tap.
  it('increments the round load by one step per tap, on every movement', async () => {
    await mount([
      exercise('press', [false], { sg: 'group' }),
      exercise('row', [false], { sg: 'group' }),
    ])

    await tap(roundStepper(0, 'Increase'))

    expect(mocks.S.active.entries[0].sets[0].w).toBe(62.5)
    expect(mocks.S.active.entries[1].sets[0].w).toBe(62.5)
  })

  // Three taps must reach 67.5 (60 -> 62.5 -> 65 -> 67.5). The stale closure made every tap after
  // the first re-apply against last render's value, so the load would lag behind the taps.
  it('advances the round load by exactly one step per tap across repeated taps', async () => {
    await mount([
      exercise('press', [false], { sg: 'group' }),
      exercise('row', [false], { sg: 'group' }),
    ])

    await tap(roundStepper(0, 'Increase'))
    await tap(roundStepper(0, 'Increase'))
    await tap(roundStepper(0, 'Increase'))

    expect(mocks.S.active.entries[0].sets[0].w).toBe(67.5)
    expect(mocks.S.active.entries[1].sets[0].w).toBe(67.5)
  })

  // Decrement walks the same path and must not undershoot or need a double tap either.
  it('decrements the round load by one step per tap, on every movement', async () => {
    await mount([
      exercise('press', [false], { sg: 'group' }),
      exercise('row', [false], { sg: 'group' }),
    ])

    await tap(roundStepper(0, 'Decrease'))

    expect(mocks.S.active.entries[0].sets[0].w).toBe(57.5)
    expect(mocks.S.active.entries[1].sets[0].w).toBe(57.5)
  })

  // Unequal set counts are not merged, so the group keeps its per-movement tables — and the
  // stepper there must still act on that member's own value, not on the partner's.
  it('keeps a per-movement stepper on a group that cannot share one table', async () => {
    await mount([
      exercise('press', [false], { sg: 'group' }),
      exercise('row', [false, false], { sg: 'group' }),
    ])

    await tap(memberStepper(1, 0, 1, 'Increase'))

    expect(mocks.S.active.entries[1].sets[0].r).toBe(6)
    expect(mocks.S.active.entries[0].sets[0].r).toBe(5)
  })
})
