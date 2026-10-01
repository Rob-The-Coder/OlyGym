// Google Drive access for the coach-plan import. The native side (DrivePlugin.java) does sign-in
// and the Picker; everything here is plain REST against drive/v3 so it can be tested with a
// stubbed fetch. drive.file is the only scope, and the same token will serve the backup/sync
// feature later.
export const XLSX_MIME = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
const SHEET_MIME = 'application/vnd.google-apps.spreadsheet'

// The native plugin is only ever imported on a Capacitor build, like every other plugin here.
export async function pickFromDrive() {
  const { registerPlugin } = await import('@capacitor/core')
  const { accessToken, fileIds } = await registerPlugin('Drive').pickSpreadsheet()
  const id = String(fileIds || '').split(',')[0].trim()
  if (!accessToken) throw new Error('no access token')
  if (!id) throw new Error('nothing picked')
  return { token: accessToken, fileId: id }
}

const authed = token => ({ Authorization: 'Bearer ' + token })

/** Metadata only: what decides export vs download. */
export async function fileMeta(fileId, token) {
  const r = await fetch(`https://www.googleapis.com/drive/v3/files/${fileId}?fields=id,name,mimeType`, { headers: authed(token) })
  if (!r.ok) throw new Error('Drive said ' + r.status)
  return r.json()
}

/** A native Google Sheet has no bytes of its own; only the export endpoint produces them. */
export async function exportSheetToXlsx(fileId, token) {
  const url = `https://www.googleapis.com/drive/v3/files/${fileId}/export?mimeType=${encodeURIComponent(XLSX_MIME)}`
  const r = await fetch(url, { headers: authed(token) })
  if (!r.ok) throw new Error('Drive export failed: ' + r.status)
  return r.arrayBuffer()
}

/** Already an .xlsx/.csv in Drive: the bytes come straight out of the file. */
export async function downloadFile(fileId, token) {
  const r = await fetch(`https://www.googleapis.com/drive/v3/files/${fileId}?alt=media`, { headers: authed(token) })
  if (!r.ok) throw new Error('Drive download failed: ' + r.status)
  return r
}
