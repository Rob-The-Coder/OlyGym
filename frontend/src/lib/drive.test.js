// Plain REST against the Drive v3 API, so the whole layer is testable with a stubbed fetch.
// pickFromDrive is the exception: it crosses into the native plugin, which is mocked here.
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { XLSX_MIME, pickFromDrive, fileMeta, exportSheetToXlsx, downloadFile } from './drive.js'

let pluginResult
vi.mock('@capacitor/core', () => ({
  registerPlugin: () => ({ pickSpreadsheet: () => Promise.resolve(pluginResult) })
}))

beforeEach(() => {
  pluginResult = { accessToken: 'tok', fileIds: 'id1,id2' }
})

afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })

const stubFetch = body => {
  const fetchMock = vi.fn(async () => ({ ok: true, status: 200, ...body }))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('Drive REST', () => {
  it('exports a native Sheet to xlsx', async () => {
    const fetchMock = stubFetch({ arrayBuffer: async () => new ArrayBuffer(0) })
    await exportSheetToXlsx('abc', 'tok')
    const [url, opts] = fetchMock.mock.calls[0]
    expect(url).toBe(`https://www.googleapis.com/drive/v3/files/abc/export?mimeType=${encodeURIComponent(XLSX_MIME)}`)
    expect(url).toContain('mimeType=application%2Fvnd.openxmlformats-officedocument.spreadsheetml.sheet')
    expect(opts.headers.Authorization).toBe('Bearer tok')
  })

  it('downloads the bytes of an uploaded file', async () => {
    const fetchMock = stubFetch({})
    await downloadFile('abc', 'tok')
    expect(fetchMock.mock.calls[0][0]).toBe('https://www.googleapis.com/drive/v3/files/abc?alt=media')
  })

  it('reads only the metadata fields it needs', async () => {
    const fetchMock = stubFetch({ json: async () => ({ id: 'abc' }) })
    await fileMeta('abc', 'tok')
    expect(fetchMock.mock.calls[0][0]).toBe('https://www.googleapis.com/drive/v3/files/abc?fields=id,name,mimeType')
  })
})

describe('pickFromDrive', () => {
  it('returns the first picked file and the token', async () => {
    expect(await pickFromDrive()).toEqual({ token: 'tok', fileId: 'id1' })
  })

  it('rejects an empty pick', async () => {
    pluginResult = { accessToken: 'tok', fileIds: '' }
    await expect(pickFromDrive()).rejects.toThrow('nothing picked')
  })

  it('rejects a missing token', async () => {
    pluginResult = { accessToken: '', fileIds: 'id1' }
    await expect(pickFromDrive()).rejects.toThrow('no access token')
  })
})
