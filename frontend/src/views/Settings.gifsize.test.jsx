// @vitest-environment happy-dom
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Settings from './Settings.jsx'
import { bindUI } from '../components/ui.jsx'
import { useUI } from '../store/useUI.js'

globalThis.IS_REACT_ACT_ENVIRONMENT = true

// App.jsx binds the UI store at boot so a shared control can open a sheet without importing the
// store at module scope; a test that renders a screen on its own has to do the same.
bindUI(useUI)

const mocks = vi.hoisted(() => {
  const state = { S: null, sheet: null }
  state.snapshot = () => ({
    S: state.S,
    user: null,
    update: mut => {
      const next = structuredClone(state.S)
      mut(next)
      state.S = next
    },
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
  // The row opens the shared picker through openSheet: keep the render callback so the test can
  // put the sheet on screen itself, which is the only way to click an option.
  const snap = () => ({ toast: vi.fn(), openSheet: render => { mocks.sheet = render } })
  const useUI = selector => selector ? selector(snap()) : snap()
  useUI.getState = snap
  return { useUI }
})
vi.mock('react-router-dom', () => ({ useNavigate: () => () => {} }))
vi.mock('../lib/wakelock.js', () => ({ wakeLockSupported: () => false }))
vi.mock('../lib/mobile.js', () => ({ MOBILE: false, isAndroid: () => Promise.resolve(false), shareExport: vi.fn(), syncReminder: vi.fn() }))
vi.mock('../sheets.jsx', () => ({
  loadStarterPlan: vi.fn(), starterPlanSheet: vi.fn(), confirmSheet: vi.fn(),
  equipmentProfileSheet: vi.fn(), importCoachPlanFromDrive: vi.fn(),
}))

// The Settings view reads the build-time version constant at render time.
globalThis.__APP_VERSION__ ??= 'test'

let host, root, sheetHost, sheetRoot
beforeEach(() => {
  mocks.S = {
    unit: 'kg', restSec: 90, sound: false, effort: 'none',
    gifSize: 'full', workouts: [], routines: [], exWeights: {},
  }
  mocks.sheet = null
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
const row = title => [...host.querySelectorAll('.lrow')].find(r => r.querySelector('.lrow-t')?.textContent === title)
const rowValue = title => row(title).querySelector('.lrow-v')?.textContent
// the row is a value row now: one tap opens the picker, and the options live in the sheet
const openPicker = title => {
  mount()
  act(() => row(title).click())
  sheetHost = document.createElement('div')
  document.body.appendChild(sheetHost)
  sheetRoot = createRoot(sheetHost)
  act(() => sheetRoot.render(mocks.sheet(() => {})))
}
const option = label => [...sheetHost.querySelectorAll('button.lrow')].find(b => b.querySelector('.lrow-t')?.textContent === label)

describe('Settings — exercise pictures', () => {
  it('offers Full / Small / Hidden and writes gifSize to the store', () => {
    openPicker('Exercise pictures')
    expect(option('Full')).toBeTruthy()
    expect(option('Small')).toBeTruthy()
    expect(option('Hidden')).toBeTruthy()
    act(() => { option('Small').click() })
    expect(mocks.S.gifSize).toBe("mini")
    act(() => { option('Hidden').click() })
    expect(mocks.S.gifSize).toBe("off")
    act(() => { option('Full').click() })
    expect(mocks.S.gifSize).toBe("full")
  })

  it('shows the value the store holds, and Full when it holds none', () => {
    mocks.S.gifSize = "mini"
    mount()
    expect(rowValue('Exercise pictures')).toBe('Small')
    act(() => root.unmount())
    root = createRoot(host)
    delete mocks.S.gifSize
    mount()
    expect(rowValue('Exercise pictures')).toBe('Full')
  })
})
