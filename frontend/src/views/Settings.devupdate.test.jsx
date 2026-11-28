// @vitest-environment happy-dom
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Settings from './Settings.jsx'

globalThis.IS_REACT_ACT_ENVIRONMENT = true
globalThis.__APP_VERSION__ ??= 'test'

// Settings has two update channels, chosen by the runtime package id: the prod row on the real
// package, the dev row on the test app. These mount Settings as `olygym.app.test` and check that
// only the dev channel is ever rendered or triggered there, and that it installs nothing it
// could not verify.
const RELEASE_PAGE = 'https://github.com/Rob-The-Coder/OlyGym/releases/tag/dev'
const COMMIT = 'abcdef0123456789abcdef0123456789abcdef01'
const SHORT = 'abcdef0'
const APK_URL = 'https://github.com/Rob-The-Coder/OlyGym/releases/download/dev/OlyGym-dev.apk'
const CHECKSUM = 'a'.repeat(64)

const mocks = vi.hoisted(() => {
  const state = {
    S: null, MOBILE: true, android: true, dev: true,
    toast: vi.fn(), confirmSheet: vi.fn(), downloadAndInstall: vi.fn(),
    checkForUpdate: vi.fn(), checkForDevUpdate: vi.fn(), devInfo: null,
  }
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
  const snap = () => ({ toast: (...a) => mocks.toast(...a), openSheet: vi.fn() })
  const useUI = selector => selector ? selector(snap()) : snap()
  useUI.getState = snap
  return { useUI }
})
vi.mock('react-router-dom', () => ({ useNavigate: () => () => {} }))
vi.mock('../lib/wakelock.js', () => ({ wakeLockSupported: () => false }))
// The two channel gates are read through getters so one module mock serves every case.
vi.mock('../lib/mobile.js', () => ({
  get MOBILE() { return mocks.MOBILE },
  isAndroid: () => Promise.resolve(mocks.android),
  isDevBuild: () => Promise.resolve(mocks.dev),
  shareExport: vi.fn(), syncReminder: vi.fn(),
}))
vi.mock('../lib/update.js', () => ({
  DEV_RELEASES_PAGE: 'https://github.com/Rob-The-Coder/OlyGym/releases/tag/dev',
  RELEASES_PAGE: 'https://github.com/Rob-The-Coder/OlyGym/releases/latest',
  checkForUpdate: (...a) => mocks.checkForUpdate(...a),
  checkForDevUpdate: (...a) => mocks.checkForDevUpdate(...a),
  downloadAndInstall: (...a) => mocks.downloadAndInstall(...a),
}))
vi.mock('../sheets.jsx', () => ({
  weightClassesSheet: vi.fn(),
  effortHelpSheet: vi.fn(),
  starterPlanSheet: vi.fn(), confirmSheet: (...a) => mocks.confirmSheet(...a),
  equipmentProfileSheet: vi.fn(), menuSheet: vi.fn(), importCoachPlanFromDrive: vi.fn(),
}))

const devResult = (over = {}) => ({
  exists: true, unreadable: false, hasUpdate: true,
  commit: COMMIT, apkUrl: APK_URL, apkSha256: CHECKSUM, pageUrl: RELEASE_PAGE,
  ...over,
})

let host, root, openSpy
beforeEach(() => {
  mocks.S = {
    unit: 'kg', restSec: 90, sound: false, effort: 'none',
    gifSize: 'full', workouts: [], routines: [], exWeights: {},
  }
  mocks.MOBILE = true
  mocks.android = true
  mocks.dev = true
  mocks.devInfo = devResult()
  mocks.checkForDevUpdate.mockReset()
  mocks.checkForDevUpdate.mockResolvedValue(mocks.devInfo)
  mocks.checkForUpdate.mockReset()
  mocks.checkForUpdate.mockResolvedValue({ hasUpdate: true, latestVersion: '9.9.9', apkUrl: 'https://x/prod.apk', hashUrl: null })
  mocks.downloadAndInstall.mockReset()
  mocks.downloadAndInstall.mockResolvedValue(undefined)
  mocks.confirmSheet.mockReset()
  mocks.toast.mockReset()
  openSpy = vi.spyOn(window, 'open').mockImplementation(() => null)
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
})
afterEach(() => {
  act(() => root.unmount())
  host.remove()
  openSpy.mockRestore()
})

// The effect resolves isAndroid, then isDevBuild, then the channel's check before the row can
// render.
const mount = async () => {
  await act(async () => { root.render(<Settings />) })
  await act(async () => { await Promise.resolve(); await Promise.resolve(); await Promise.resolve() })
}
const rows = () => [...host.querySelectorAll('.lrow')]
const devUpdateRow = () => rows().find(r => r.textContent.includes('Update to the latest dev build'))
const devCheckRow = () => rows().find(r => r.textContent.includes('Check for dev updates'))
const unreadableRow = () => rows().find(r => r.textContent.includes('Open the dev releases page'))
const prodRow = () => rows().find(r => r.textContent.includes('Update to OlyGym v9.9.9'))

describe('Settings — dev build update check', () => {
  it('test app: names the newer build, never asks the prod channel, and confirms before downloading', async () => {
    await mount()
    expect(mocks.checkForUpdate).not.toHaveBeenCalled()
    expect(mocks.checkForDevUpdate).toHaveBeenCalledTimes(1)
    expect(prodRow()).toBeUndefined()
    expect(host.textContent).not.toContain('Update to OlyGym v')

    const row = devUpdateRow()
    expect(row).toBeTruthy()
    expect(row.textContent).toContain('Build ' + SHORT)

    act(() => { row.click() })
    expect(mocks.confirmSheet).toHaveBeenCalledTimes(1)
    const opts = mocks.confirmSheet.mock.calls[0][0]
    expect(opts.title).toBe('Install the dev build from ' + SHORT + '?')
    expect(mocks.downloadAndInstall).not.toHaveBeenCalled()

    await act(async () => { await opts.onConfirm() })
    expect(mocks.downloadAndInstall).toHaveBeenCalledTimes(1)
    expect(mocks.downloadAndInstall.mock.calls[0][0]).toBe(APK_URL)
    expect(mocks.downloadAndInstall.mock.calls[0][1]).toBe(CHECKSUM)
    expect(mocks.downloadAndInstall.mock.calls[0][2]).toBeTypeOf('function')
  })

  it('test app with the same commit: tapping re-checks and says it is the latest', async () => {
    mocks.devInfo = devResult({ hasUpdate: false, apkUrl: null, apkSha256: null })
    mocks.checkForDevUpdate.mockResolvedValue(mocks.devInfo)
    await mount()
    expect(devUpdateRow()).toBeUndefined()
    const row = devCheckRow()
    expect(row).toBeTruthy()

    await act(async () => { row.click(); await Promise.resolve() })
    expect(mocks.checkForDevUpdate).toHaveBeenCalledTimes(2)
    expect(mocks.toast).toHaveBeenCalledWith('You have the latest dev build.')
    expect(mocks.confirmSheet).not.toHaveBeenCalled()
  })

  it('test app with no dev release yet: offers nothing, and the tap says it is the latest', async () => {
    mocks.devInfo = { exists: false, unreadable: false, hasUpdate: false, commit: null, apkUrl: null, apkSha256: null, pageUrl: null }
    mocks.checkForDevUpdate.mockResolvedValue(mocks.devInfo)
    await mount()
    expect(devUpdateRow()).toBeUndefined()
    expect(devCheckRow()).toBeTruthy()
    expect(mocks.confirmSheet).not.toHaveBeenCalled()

    await act(async () => { devCheckRow().click(); await Promise.resolve() })
    expect(mocks.toast).toHaveBeenCalledWith('You have the latest dev build.')
    expect(openSpy).not.toHaveBeenCalled()
  })

  it('test app with an unreadable release: the row opens the release page and never downloads', async () => {
    mocks.devInfo = { exists: true, unreadable: true, hasUpdate: false, commit: null, apkUrl: null, apkSha256: null, pageUrl: RELEASE_PAGE }
    mocks.checkForDevUpdate.mockResolvedValue(mocks.devInfo)
    await mount()
    const row = unreadableRow()
    expect(row).toBeTruthy()
    expect(row.textContent).toContain('The dev release could not be read.')

    act(() => { row.click() })
    expect(openSpy).toHaveBeenCalledWith(RELEASE_PAGE, '_blank', 'noopener')
    expect(mocks.confirmSheet).not.toHaveBeenCalled()
    expect(mocks.downloadAndInstall).not.toHaveBeenCalled()
  })

  it('test app without a checksum: opens the release page instead of installing', async () => {
    mocks.devInfo = devResult({ apkSha256: null })
    mocks.checkForDevUpdate.mockResolvedValue(mocks.devInfo)
    await mount()
    const row = devUpdateRow()
    expect(row).toBeTruthy()

    act(() => { row.click() })
    expect(openSpy).toHaveBeenCalledWith(RELEASE_PAGE, '_blank', 'noopener')
    expect(mocks.confirmSheet).not.toHaveBeenCalled()
    expect(mocks.downloadAndInstall).not.toHaveBeenCalled()
  })

  it('shows "Checking…" while the automatic dev check is in flight', async () => {
    let answer
    mocks.checkForDevUpdate.mockReturnValue(new Promise(resolve => { answer = resolve }))
    await mount()
    expect(devCheckRow()).toBeTruthy()
    expect(host.textContent).toContain('Checking…')

    await act(async () => { answer(devResult()); await Promise.resolve() })
    expect(devUpdateRow()).toBeTruthy()
  })

  it('real Android package: keeps the prod row and never asks the dev channel', async () => {
    mocks.dev = false
    await mount()
    expect(mocks.checkForDevUpdate).not.toHaveBeenCalled()
    expect(mocks.checkForUpdate).toHaveBeenCalledTimes(1)
    expect(prodRow()).toBeTruthy()
    expect(devUpdateRow()).toBeUndefined()
    expect(devCheckRow()).toBeUndefined()
  })
})
