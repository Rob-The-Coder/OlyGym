// @vitest-environment happy-dom
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Settings from './Settings.jsx'

globalThis.IS_REACT_ACT_ENVIRONMENT = true

// The reminder card only exists on the mobile build, and its time was the app's last native
// <input type="time"> — a WebView control whose popup follows the OS and ignores the theme. This
// pins MOBILE on and watches the row open the same sheet the backfill's Start time opens.
const mocks = vi.hoisted(() => {
  const state = { S: null, picker: null, presets: ['07:00', '08:00', '12:00', '18:00', '20:00'] }
  state.snapshot = () => ({
    S: state.S, user: null,
    update: mut => { const next = structuredClone(state.S); mut(next); state.S = next },
    replaceState: vi.fn(), setUser: vi.fn(), pullState: vi.fn(), pushState: vi.fn(),
    signOut: vi.fn(), signOutAll: vi.fn(), resetDemo: vi.fn(), disconnectServer: vi.fn(),
  })
  return state
})
vi.mock('../store/useStore.js', () => {
  const useStore = selector => selector ? selector(mocks.snapshot()) : mocks.snapshot()
  useStore.getState = mocks.snapshot
  return { useStore, DEF: { reminder: { time: '08:00' } }, hasData: () => false }
})
vi.mock('../store/useUI.js', () => {
  const snap = () => ({ toast: vi.fn(), openSheet: vi.fn() })
  const useUI = selector => selector ? selector(snap()) : snap()
  useUI.getState = snap
  return { useUI }
})
vi.mock('react-router-dom', () => ({ useNavigate: () => () => {} }))
vi.mock('../lib/wakelock.js', () => ({ wakeLockSupported: () => false }))
vi.mock('../lib/mobile.js', () => ({
  MOBILE: true, isAndroid: () => Promise.resolve(false), shareExport: vi.fn(), syncReminder: vi.fn(),
}))
vi.mock('../lib/update.js', () => ({ checkForUpdate: vi.fn(() => Promise.resolve(null)), downloadAndInstall: vi.fn() }))
vi.mock('../sheets.jsx', () => ({
  weightClassesSheet: vi.fn(),
  effortHelpSheet: vi.fn(), starterPlanSheet: vi.fn(), confirmSheet: vi.fn(),
  equipmentProfileSheet: vi.fn(), menuSheet: vi.fn(), importCoachPlanFromDrive: vi.fn(),
  REMINDER_TIMES: mocks.presets,
  timePickerSheet: opts => { mocks.picker = opts },
}))

globalThis.__APP_VERSION__ ??= 'test'

let host, root
beforeEach(() => {
  mocks.S = {
    unit: 'kg', restSec: 90, sound: false, effort: 'none', gifSize: 'full',
    workouts: [], routines: [], exWeights: {}, reminder: { on: true, time: '08:00', tz: null },
  }
  mocks.picker = null
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
})
afterEach(() => { act(() => root.unmount()); host.remove() })

describe('the reminder time', () => {
  it('opens the app’s own time sheet instead of a native input', async () => {
    await act(async () => { root.render(<Settings />) })
    await act(async () => { await Promise.resolve() })

    const row = [...host.querySelectorAll('.lrow')].find(x => /Reminder time/.test(x.textContent))
    expect(row, 'the reminder row is on screen').toBeTruthy()
    expect(host.querySelector('input[type="time"]')).toBe(null)

    act(() => row.click())
    // The stored time, the sheet's own title, and the reminder's quick times — not the gym ones.
    expect(mocks.picker).toMatchObject({ value: '08:00', title: 'Reminder time', presets: mocks.presets })
  })
})
