// @vitest-environment happy-dom
// The Effort row carries two actions: the row opens the picker, and the (i) beside it opens the
// chart. They were the same control once, and nesting the (i) inside the row button would fire
// both — this is the test that says which one a tap lands on.
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Settings from './Settings.jsx'
import { bindUI } from '../components/ui.jsx'
import { useUI } from '../store/useUI.js'

globalThis.IS_REACT_ACT_ENVIRONMENT = true
bindUI(useUI)

const mocks = vi.hoisted(() => {
  const state = { S: null, sheet: null, opens: 0 }
  state.snapshot = () => ({
    S: state.S,
    user: null,
    update: mut => { const next = structuredClone(state.S); mut(next); state.S = next },
    replaceState: vi.fn(), setUser: vi.fn(), pullState: vi.fn(), pushState: vi.fn(),
    signOut: vi.fn(), signOutAll: vi.fn(), resetDemo: vi.fn(), disconnectServer: vi.fn(),
  })
  return state
})
vi.mock('../store/useStore.js', () => {
  const useStore = selector => selector ? selector(mocks.snapshot()) : mocks.snapshot()
  useStore.getState = mocks.snapshot
  return { useStore, DEF: { reminder: { time: '17:30' } }, hasData: () => false }
})
vi.mock('../store/useUI.js', () => {
  const snap = () => ({ toast: vi.fn(), openSheet: render => { mocks.sheet = render; mocks.opens++ } })
  const useUI = selector => selector ? selector(snap()) : snap()
  useUI.getState = snap
  return { useUI }
})
vi.mock('react-router-dom', () => ({ useNavigate: () => () => {} }))
vi.mock('../lib/wakelock.js', () => ({ wakeLockSupported: () => false }))
vi.mock('../lib/mobile.js', () => ({ MOBILE: false, isAndroid: () => Promise.resolve(false), shareExport: vi.fn(), syncReminder: vi.fn() }))
vi.mock('../sheets.jsx', async importOriginal => {
  // The help sheet is not mocked away: the point is that the (i) opens the real chart.
  const actual = await importOriginal()
  return { ...actual, starterPlanSheet: vi.fn(), confirmSheet: vi.fn(), equipmentProfileSheet: vi.fn(), importCoachPlanFromDrive: vi.fn() }
})

globalThis.__APP_VERSION__ ??= 'test'

let host, root, sheetHost, sheetRoot
beforeEach(() => {
  mocks.S = { unit: 'kg', restSec: 90, sound: false, effort: 'none', gifSize: 'full', workouts: [], exWeights: {} }
  mocks.sheet = null
  mocks.opens = 0
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
})
afterEach(() => {
  act(() => root.unmount())
  host.remove()
  if (sheetRoot) { act(() => sheetRoot.unmount()); sheetHost.remove(); sheetRoot = null }
})

const mount = () => act(() => root.render(<Settings />))
const effortRow = () => [...host.querySelectorAll('.lrow')].find(r => r.querySelector('.lrow-t')?.textContent === 'Effort per set')
const showSheet = () => {
  sheetHost = document.createElement('div')
  document.body.appendChild(sheetHost)
  sheetRoot = createRoot(sheetHost)
  act(() => sheetRoot.render(mocks.sheet(() => {})))
}

describe('Settings — the Effort row', () => {
  it('puts the help button and the value beside each other, as two real buttons', () => {
    mount()
    const row = effortRow()
    expect(row.querySelector('.lrow-v').textContent).toBe('Off')
    expect(row.querySelector('button.helpbtn').getAttribute('aria-label')).toBe('What are RIR and RPE?')
    // the row itself is a container, not a button: a button cannot hold the help button
    expect(row.tagName).toBe('DIV')
    expect(row.querySelector('button.lrow-hit')).toBeTruthy()
    // and the (i) keeps the place it always had: between the title and the value, not after the
    // chevron, which is what the first cut of this did and a screenshot caught
    const order = [...row.children].map(el => el.className || el.tagName)
    expect(order.findIndex(c => String(c).includes('helpbtn'))).toBeLessThan(order.findIndex(c => String(c).includes('lrow-v')))
    expect(row.querySelector('.lrow-c')).toBeTruthy()
  })

  it('opens the RIR/RPE chart from the (i), and the picker from the row', () => {
    mount()
    act(() => { effortRow().querySelector('button.helpbtn').click() })
    expect(mocks.opens).toBe(1)
    showSheet()
    expect(sheetHost.textContent).toContain('RIR')
    expect(sheetHost.textContent).toContain('RPE')
    expect(sheetHost.textContent).toContain('How it felt')
    // and the row's own tap target still opens the picker — the three settings, not the chart
    act(() => sheetRoot.unmount())
    sheetHost.remove(); sheetRoot = null
    act(() => { effortRow().querySelector('button.lrow-hit').click() })
    expect(mocks.opens).toBe(2)
    showSheet()
    expect(sheetHost.textContent).toContain('Effort per set')
    for (const label of ['Off', 'RIR', 'RPE']) expect(sheetHost.textContent).toContain(label)
  })
})
