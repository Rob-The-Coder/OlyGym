// @vitest-environment happy-dom
// The picture is a thumbnail in the exercise header that opens to full size on tap, so the setting
// is a yes/no now: show it, or hide it. Legacy 'mini' meant "always small", which the collapsed
// header does anyway, so it reads as shown.
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Settings from './Settings.jsx'
import { bindUI } from '../components/ui.jsx'
import { useUI } from '../store/useUI.js'

globalThis.IS_REACT_ACT_ENVIRONMENT = true
bindUI(useUI)

const mocks = vi.hoisted(() => {
  const state = { S: null, sheet: null }
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
  const snap = () => ({ toast: vi.fn(), openSheet: render => { mocks.sheet = render } })
  const useUI = selector => selector ? selector(snap()) : snap()
  useUI.getState = snap
  return { useUI }
})
vi.mock('react-router-dom', () => ({ useNavigate: () => () => {} }))
vi.mock('../lib/wakelock.js', () => ({ wakeLockSupported: () => false }))
vi.mock('../lib/mobile.js', () => ({ MOBILE: false, isAndroid: () => Promise.resolve(false), shareExport: vi.fn(), syncReminder: vi.fn() }))
vi.mock('../sheets.jsx', () => ({
  weightClassesSheet: vi.fn(),
  effortHelpSheet: vi.fn(), starterPlanSheet: vi.fn(), confirmSheet: vi.fn(),
  equipmentProfileSheet: vi.fn(), importCoachPlanFromDrive: vi.fn(),
}))

globalThis.__APP_VERSION__ ??= 'test'

let host, root
beforeEach(() => {
  mocks.S = { unit: 'kg', restSec: 90, sound: false, effort: 'none', gifSize: 'full', workouts: [], exWeights: {} }
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
})
afterEach(() => { act(() => root.unmount()); host.remove() })

const mount = () => act(() => root.render(<Settings />))
const segButton = label => [...host.querySelectorAll('.seg button')].find(b => b.textContent === label)

describe('Settings — exercise pictures', () => {
  it('offers On tap / Hidden, and writes gifSize to the store', () => {
    mount()
    expect(segButton('On tap')).toBeTruthy()
    expect(segButton('Hidden')).toBeTruthy()
    expect(segButton('Small')).toBeFalsy()
    expect(segButton('On tap').getAttribute('aria-pressed')).toBe('true')
    act(() => { segButton('Hidden').click() })
    expect(mocks.S.gifSize).toBe('off')
    mount()
    expect(segButton('Hidden').getAttribute('aria-pressed')).toBe('true')
    act(() => { segButton('On tap').click() })
    expect(mocks.S.gifSize).toBe('full')
  })

  it("shows a legacy/absent value — and 'mini' — as On tap", () => {
    for (const gifSize of [undefined, 'mini', 'huge']) {
      if (gifSize === undefined) delete mocks.S.gifSize; else mocks.S.gifSize = gifSize
      mount()
      expect(segButton('On tap').getAttribute('aria-pressed')).toBe('true')
      expect(segButton('Hidden').getAttribute('aria-pressed')).toBe('false')
      act(() => root.unmount())
      root = createRoot(host)
    }
  })
})
