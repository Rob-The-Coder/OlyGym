/**
 * A real CSV reader: quoted fields, embedded commas and newlines, doubled quotes, BOM and CRLF.
 * Splitting on commas breaks on the first exercise named "Bench Press, Close Grip" — and a whole
 * coach's sheet would import shifted by one column without ever erroring.
 */
export function parseCSV(text) {
  const rows = []
  let row = [], field = '', quoted = false
  const s = String(text).replace(/^\uFEFF/, '')
  for (let i = 0; i < s.length; i++) {
    const c = s[i]
    if (quoted) {
      if (c === '"') { if (s[i + 1] === '"') { field += '"'; i++ } else quoted = false }
      else field += c
    } else if (c === '"') quoted = true
    else if (c === ',') { row.push(field); field = '' }
    else if (c === '\n' || c === '\r') {
      if (c === '\r' && s[i + 1] === '\n') i++
      row.push(field); field = ''
      if (row.some(x => x !== '')) rows.push(row)
      row = []
    } else field += c
  }
  row.push(field)
  if (row.some(x => x !== '')) rows.push(row)
  return rows
}

// A cell that starts with one of these is a formula to a spreadsheet, not text. A leading
// apostrophe is the spreadsheet's own "this is literal text" escape, so the guard travels with
// the file instead of relying on the app that opens it. Numbers and booleans are exempt: a
// negative load is data, and no spreadsheet would evaluate it as a formula.
const FORMULA_START = /^[=+\-@\t\r]/

function cellText(value) {
  if (value == null) return ''
  if (typeof value === 'number') return Number.isFinite(value) ? String(value) : ''
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  const text = String(value)
  return FORMULA_START.test(text) ? "'" + text : text
}

function quoteCell(text) {
  return /[",\r\n]/.test(text) ? '"' + text.replace(/"/g, '""') + '"' : text
}

/**
 * Write rows to RFC 4180-style CSV: CRLF between records and after the last one, a field quoted
 * only when it has to be, embedded quotes doubled. `bom` is for Excel, which needs the mark to
 * read UTF-8 without being told. An empty table is an empty string, BOM or not — a file with
 * nothing but a BOM is not a CSV with no rows.
 */
export function toCSV(rows, { bom = false } = {}) {
  if (!Array.isArray(rows) || rows.length === 0) return ''
  const records = rows.map(row => {
    const cells = Array.isArray(row) ? row : [row]
    return cells.map(cell => quoteCell(cellText(cell))).join(',')
  })
  return (bom ? '\uFEFF' : '') + records.join('\r\n') + '\r\n'
}
