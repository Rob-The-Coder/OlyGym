// @vitest-environment happy-dom
// Home offers the starter plan to someone who has no routines yet. It used to wire the button
// straight to the loader, which quietly handed the click event in as the plan id and loaded
// nothing at all — so the entry point is pinned here. (Plan's own offer went away in Stage B:
// the weeks timeline no longer loads a repeating plan.)
import React, { act } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRoot } from 'react-dom/client'
import { useStore } from '../store/useStore.js'
import { todayISO } from '../lib/format.js'
import { starterPlanSheet } from '../sheets.jsx'
import Home from './Home.jsx'

vi.mock('react-router-dom', () => ({ useNavigate: () => () => {} }))
vi.mock('../sheets.jsx', () => ({
  starterPlanSheet: vi.fn(), bwSheet: vi.fn(), goalSheet: vi.fn(),
  calendarSheet: vi.fn(), startFlow: vi.fn(), bwDeltaColor: () => '',
  planToolsSheet: vi.fn(),
}))

let host, root
beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  starterPlanSheet.mockClear()
  useStore.setState(s => ({ S: { ...s.S, routines: [], week: {}, weeks: [], active: null }, user: null }))
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
})
afterEach(() => {
  act(() => root.unmount())
  host.remove()
})

const starterButton = () => [...host.querySelectorAll('button')].find(b => b.textContent === 'Load starter plan')

describe('Home empty state', () => {
  it('opens the starter plan chooser instead of loading one plan blind', () => {
    act(() => root.render(<Home />))
    const button = starterButton()
    expect(button).toBeTruthy()

    act(() => { button.click() })
    expect(starterPlanSheet).toHaveBeenCalledTimes(1)
  })

  it('drops the offer for a plan built in the dated weeks, with no routines behind it', () => {
    useStore.setState(s => ({ S: {
      ...s.S,
      weeks: [{ id: 'w1', startIso: todayISO(), name: '', days: [{ dow: 1, name: 'Push', ex: [{ id: '0025' }] }] }],
    } }))
    act(() => root.render(<Home />))
    expect(starterButton()).toBeFalsy()
  })
})
