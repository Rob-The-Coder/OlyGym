// @vitest-environment happy-dom
// The History screen's one export action: on the native build the CSV leaves through the OS
// share sheet, so this asserts the payload (BOM, header, done sets, CRLF), the filename, the
// toast, and that a dismissed share sheet is silence rather than an error.
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import History from './History.jsx'
import { DEF, useStore } from '../store/useStore.js'
import { useUI } from '../store/useUI.js'
import { shareExport } from '../lib/mobile.js'
import { EXDB } from '../lib/exercises.js'
import { todayISO } from '../lib/format.js'
import { toCSV } from '../lib/csv.js'
import { HISTORY_CSV_HEADER, historyRows, historyFilename } from '../lib/history-csv.js'

globalThis.IS_REACT_ACT_ENVIRONMENT = true

vi.mock('react-router-dom', () => ({ useNavigate: () => () => {} }))
vi.mock('../lib/mobile.js', async importOriginal => {
  const real = await importOriginal()
  return { ...real, MOBILE: true, shareExport: vi.fn() }
})

const LIFT = EXDB.find(e => e.eq === 'barbell').id
const BW = EXDB.find(e => e.eq === 'body weight').id
const clone = value => JSON.parse(JSON.stringify(value))

const seededWorkouts = () => [{
  id: 'wk-1', d: '2026-02-02',
  start: Date.UTC(2026, 1, 2, 9), end: Date.UTC(2026, 1, 2, 10),
  name: 'Snatch day', note: 'felt good', vol: 600,
  prs: [LIFT],
  entries: [
    { id: LIFT, note: 'belt on', sets: [
      { w: 40, r: 5, done: true, phase: 'warmup' },
      { w: 100, r: 3, done: true },
      { w: 100, r: 3, done: false },
    ] },
    { id: BW, sets: [{ w: 0, r: 10, done: true }] },
  ],
}]

let host, root
beforeEach(() => {
  localStorage.clear()
  shareExport.mockReset()
  shareExport.mockResolvedValue(undefined)
  useUI.getState().dismissToast()
  useStore.setState({ S: { ...clone(DEF), unit: 'kg', workouts: seededWorkouts() } })
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
})
afterEach(() => {
  act(() => root.unmount())
  host.remove()
  useUI.getState().dismissToast()
})

const mount = () => act(() => root.render(<History />))
const exportButton = () => host.querySelector('button[aria-label="Export history as CSV"]')
const plusButton = () => host.querySelector('button[aria-label="Log a past workout"]')
const clickExport = () => act(async () => {
  exportButton().click()
  await new Promise(resolve => setTimeout(resolve, 0))
})

describe('History — export the log as CSV', () => {
  it('shares the whole log as CSV with a BOM, the stable header and only done sets, then toasts', async () => {
    mount()
    expect(exportButton()).not.toBeNull()
    expect(exportButton().getAttribute('title')).toBe('Export history as CSV')
    await clickExport()

    expect(shareExport).toHaveBeenCalledTimes(1)
    const [csv, name] = shareExport.mock.calls[0]
    expect(csv).toBe(toCSV(historyRows(seededWorkouts(), 'kg'), { bom: true }))
    expect(name).toBe(historyFilename(todayISO()))
    expect(name).toMatch(/^opengym-history-\d{4}-\d{2}-\d{2}\.csv$/)

    expect(csv.charCodeAt(0)).toBe(0xFEFF)
    expect(csv.startsWith('\uFEFF' + HISTORY_CSV_HEADER.join(','))).toBe(true)
    expect(csv.endsWith('\r\n')).toBe(true)
    // header + the warm-up, the work set and the bodyweight set; the unchecked row is absent
    expect(csv.trimEnd().split('\r\n')).toHaveLength(4)
    expect(csv).toContain('warmup')
    expect(csv).toContain('belt on')

    expect(useUI.getState().toastMsg).toBe('History exported')
  })

  it('stays silent when the share sheet is dismissed, with no error toast and no unhandled rejection', async () => {
    shareExport.mockRejectedValue(new Error('share cancelled'))
    mount()
    await clickExport()
    expect(shareExport).toHaveBeenCalledTimes(1)
    expect(useUI.getState().toastMsg).toBe('')
  })

  it('hides the export action on an empty log while keeping the plus action and the empty state', () => {
    useStore.setState({ S: { ...clone(DEF), unit: 'kg', workouts: [] } })
    mount()
    expect(exportButton()).toBeNull()
    expect(plusButton()).not.toBeNull()
    expect(host.textContent).toContain('No workouts yet.')
  })
})
