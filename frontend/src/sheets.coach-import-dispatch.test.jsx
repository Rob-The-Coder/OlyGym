// @vitest-environment happy-dom
// The coach import dispatches on the file name: .csv goes to parseCSV, anything else to readXlsx.
// This pins the awkward case — a picked file with no extension — to the workbook reader. (A native
// Google Sheets pick never reaches importCoachPlan: importCoachPlanFromDrive exports it to .xlsx
// first, see lib/drive.js.)
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { readXlsx } from './lib/xlsx.js'
import { parseCSV } from './lib/import-csv.js'
import { importCoachPlan } from './sheets.jsx'

vi.mock('./lib/xlsx.js', () => ({ readXlsx: vi.fn(async () => ({ sheets: [] })) }))
vi.mock('./lib/import-csv.js', () => ({ parseCSV: vi.fn(() => []), parseImport: vi.fn(), mergeImport: vi.fn() }))

beforeEach(() => {
  useUI.setState({ sheets: [], toastMsg: '' })
  useStore.setState({ S: {}, user: null })
  readXlsx.mockClear()
  parseCSV.mockClear()
})

afterEach(() => { useUI.setState({ sheets: [], toastMsg: '' }) })

describe('importCoachPlan dispatch', () => {
  it('reads a Google Sheets pick through the workbook reader', async () => {
    const file = new File(['x'], 'Programmazione', { type: 'application/vnd.google-apps.spreadsheet' })
    await importCoachPlan(file)
    expect(readXlsx).toHaveBeenCalledTimes(1)
    expect(parseCSV).not.toHaveBeenCalled()
  })

  it('reads a .csv through the CSV reader', async () => {
    const file = new File(['a,b\n1,2'], 'plan.csv', { type: 'text/csv' })
    await importCoachPlan(file)
    expect(parseCSV).toHaveBeenCalledTimes(1)
    expect(readXlsx).not.toHaveBeenCalled()
  })
})
