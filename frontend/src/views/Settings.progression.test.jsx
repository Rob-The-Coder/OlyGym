// @vitest-environment happy-dom
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Settings from './Settings.jsx'

globalThis.IS_REACT_ACT_ENVIRONMENT = true

const mocks = vi.hoisted(() => {
  const state = { S: null, openSheet: vi.fn() }
  state.snapshot = () => ({
    S: state.S,
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
  const snap = () => ({ toast: vi.fn(), openSheet: mocks.openSheet })
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

let host, root
beforeEach(() => {
  mocks.S = {
    unit: 'kg', restSec: 90, sound: false, effort: 'none',
    gifSize: 'full', workouts: [], routines: [], exWeights: {},
  }
  mocks.openSheet.mockClear()
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
})
afterEach(() => {
  act(() => root.unmount())
  host.remove()
})

const mount = () => act(() => root.render(<Settings />))
const rowTitled = title => [...host.querySelectorAll('.lrow')].find(r => r.querySelector('.lrow-t')?.textContent === title)
// Scoped to this row: "Effort per set" further down also offers an Off.
const segIn = label => [...rowTitled('Automatic progression').querySelectorAll('.seg button')].find(b => b.textContent === label)
const helpButton = () => rowTitled('Automatic progression').querySelector('button.helpbtn')

describe('Settings — automatic progression', () => {
  it('offers Off / On, reads an absent value as off, and writes the setting', () => {
    mount()
    expect(segIn('Off').getAttribute('aria-pressed')).toBe('true')
    expect(segIn('On').getAttribute('aria-pressed')).toBe('false')
    act(() => { segIn('On').click() })
    expect(mocks.S.autoProg).toBe(true)
    mount()
    expect(segIn('On').getAttribute('aria-pressed')).toBe('true')
    act(() => { segIn('Off').click() })
    expect(mocks.S.autoProg).toBe(false)
  })

  it('explains both states and the arithmetic behind the (i)', () => {
    mount()
    expect(helpButton().getAttribute('aria-label')).toBe('How does automatic progression work?')
    act(() => { helpButton().click() })
    expect(mocks.openSheet).toHaveBeenCalledTimes(1)

    // Render what the sheet was handed, the way the sheet host would.
    const render = mocks.openSheet.mock.calls[0][0]
    const sheetHost = document.createElement('div')
    document.body.appendChild(sheetHost)
    const sheetRoot = createRoot(sheetHost)
    act(() => sheetRoot.render(render(() => {})))
    const text = sheetHost.textContent
    expect(text).toContain('With the switch off')
    expect(text).toContain('With the switch on')
    expect(text).toContain('2.5 kg')
    expect(text).toContain('10%')
    act(() => sheetRoot.unmount())
    sheetHost.remove()
  })
})
