// Read .xlsx workbooks without a spreadsheet dependency.
//
// An .xlsx is a zip of XML parts, so there is no library to justify here: the platform owns
// every hard piece. The zip directory is walked by hand (Excel writes stored or deflate and
// nothing else), deflate is handed to DecompressionStream, and the XML goes to DOMParser.
// Only the parts that matter are read — workbook.xml for the sheet order, its rels for where
// each sheet lives, sharedStrings.xml for text, one part per sheet — and inside a sheet only
// the cell rectangles.
//
// Values come back as the strings the file holds, unrounded and unreformatted: the importer
// shows them back to whoever wrote the file, and "4.0" becoming 4 changes what they see.

const EOCD_SIG = 0x06054b50
const CENTRAL_SIG = 0x02014b50
const LOCAL_SIG = 0x04034b50
const MAX_COMMENT = 65535

const badFile = () => { throw new Error('this file is not an .xlsx workbook') }

const u16 = (view, at) => view.getUint16(at, true)
const u32 = (view, at) => view.getUint32(at, true)

// The End Of Central Directory is the last record in the archive, but a zip may carry a
// trailing comment, so it is found by scanning back for its signature rather than assuming
// a fixed offset.
function endOfCentral(view) {
  const stop = Math.max(0, view.byteLength - 22 - MAX_COMMENT)
  for (let i = view.byteLength - 22; i >= stop; i--) if (u32(view, i) === EOCD_SIG) return i
  return -1
}

/** name -> { method, data } for every entry. Sizes come from the central directory: a local
 *  header written with a data descriptor leaves them zeroed. */
function unzip(buffer) {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer)
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  const eocd = endOfCentral(view)
  if (eocd < 0) badFile()
  const count = u16(view, eocd + 10)
  const parts = new Map()
  let at = u32(view, eocd + 16)
  for (let n = 0; n < count; n++) {
    if (u32(view, at) !== CENTRAL_SIG) badFile()
    const nameLen = u16(view, at + 28)
    const extraLen = u16(view, at + 30)
    const commentLen = u16(view, at + 32)
    const start = u32(view, at + 42)
    if (u32(view, start) !== LOCAL_SIG) badFile()
    const name = new TextDecoder().decode(bytes.subarray(at + 46, at + 46 + nameLen))
    const from = start + 30 + u16(view, start + 26) + u16(view, start + 28)
    const size = u32(view, at + 20)
    parts.set(name, { method: u16(view, at + 10), data: bytes.subarray(from, from + size) })
    at += 46 + nameLen + extraLen + commentLen
  }
  return parts
}

async function inflateRaw(data) {
  if (typeof DecompressionStream !== 'function') throw new Error('this browser cannot inflate .xlsx files')
  const stream = new DecompressionStream('deflate-raw')
  // Feed and drain at once: a stream stops accepting input until the reader takes what is
  // already queued, so awaiting the write before reading would deadlock on a large part.
  const pump = (async () => {
    const writer = stream.writable.getWriter()
    await writer.write(data)
    await writer.close()
  })().catch(() => {})
  const reader = stream.readable.getReader()
  const chunks = []
  let size = 0
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    chunks.push(value)
    size += value.length
  }
  await pump
  const out = new Uint8Array(size)
  let at = 0
  for (const chunk of chunks) { out.set(chunk, at); at += chunk.length }
  return out
}

/** The text of one part, or null when the archive does not hold it. */
async function partText(parts, name) {
  const file = parts.get(name)
  if (!file) return null
  if (file.method === 0) return new TextDecoder().decode(file.data)
  if (file.method !== 8) throw new Error('this .xlsx file uses an unsupported compression')
  return new TextDecoder().decode(await inflateRaw(file.data))
}

const parseXml = xml => new DOMParser().parseFromString(xml, 'application/xml')

const first = (el, tag) => el.getElementsByTagName(tag)[0] || null

// A shared string or an inline string may be split into any number of <t> runs (inside <r>
// for rich text); the value is all of them joined.
function textsOf(el) {
  const runs = el.getElementsByTagName('t')
  if (!runs.length) return el.textContent || ''
  let s = ''
  for (let i = 0; i < runs.length; i++) s += runs[i].textContent
  return s
}

// Anything that reads as blank to the eye is blank here: runs of whitespace — newlines and
// non-breaking spaces from a formatted sheet included — become one space, then the trim.
const clean = s => String(s == null ? '' : s).replace(/[\s\u00a0]+/g, ' ').trim()

// Excel letters are base-26 with A=1: A -> 0, Z -> 25, AA -> 26, P -> 15.
function colIndex(letters) {
  let n = 0
  for (const ch of letters.toUpperCase()) n = n * 26 + ch.charCodeAt(0) - 64
  return n - 1
}

function relId(el) {
  const attrs = el.attributes
  for (let i = 0; i < attrs.length; i++) if (attrs[i].name.endsWith(':id')) return attrs[i].value
  return null
}

function cellValue(cell, shared) {
  const type = cell.getAttribute('t')
  if (type === 'inlineStr') return clean(textsOf(first(cell, 'is') || cell))
  const v = first(cell, 'v')
  const raw = v ? v.textContent : ''
  if (!raw) return ''
  if (type === 's') return clean(shared[Number(raw)] || '')
  if (type === 'b') return raw === '1' ? 'TRUE' : 'FALSE'
  return clean(raw)   // t="str" (a formula's cached string) and plain numbers, verbatim
}

function gridOf(doc, shared) {
  const rowEls = doc.getElementsByTagName('row')
  const grid = []
  let width = 0
  let prevRow = -1
  for (let i = 0; i < rowEls.length; i++) {
    const ref = rowEls[i].getAttribute('r')
    const ri = ref ? Number(ref) - 1 : prevRow + 1
    prevRow = ri
    const row = grid[ri] || (grid[ri] = [])
    const cellEls = rowEls[i].getElementsByTagName('c')
    let prevCol = -1
    for (let j = 0; j < cellEls.length; j++) {
      const cref = cellEls[j].getAttribute('r')
      const letters = cref && /^([A-Za-z]+)/.exec(cref)
      const ci = letters ? colIndex(letters[1]) : prevCol + 1
      prevCol = ci
      row[ci] = cellValue(cellEls[j], shared)
      if (ci + 1 > width) width = ci + 1
    }
  }
  // Rows are stored sparse, keyed by their own reference. Hand back a rectangle: every row
  // the same width, holes and a missing tail filled with blanks.
  if (!width) return []
  const out = []
  for (let i = 0; i < grid.length; i++) {
    const row = grid[i] || []
    const line = []
    for (let k = 0; k < width; k++) line.push(row[k] === undefined ? '' : row[k])
    out.push(line)
  }
  return out
}

/** Read an .xlsx workbook: sheet names in workbook order, each with a rectangular string grid. */
export async function readXlsx(buffer) {
  const parts = unzip(buffer)
  if (!parts.has('xl/workbook.xml')) badFile()
  const workbook = parseXml(await partText(parts, 'xl/workbook.xml'))

  const rels = new Map()
  const relXml = await partText(parts, 'xl/_rels/workbook.xml.rels')
  if (relXml) {
    const relEls = parseXml(relXml).getElementsByTagName('Relationship')
    for (let i = 0; i < relEls.length; i++) {
      // Targets are relative to xl/, but some writers spell that out with a leading "/xl/".
      const target = String(relEls[i].getAttribute('Target') || '').replace(/^\/+/, '')
      if (target) rels.set(relEls[i].getAttribute('Id'), target.startsWith('xl/') ? target : 'xl/' + target)
    }
  }

  const shared = []
  const sharedXml = await partText(parts, 'xl/sharedStrings.xml')
  if (sharedXml) {
    const siEls = parseXml(sharedXml).getElementsByTagName('si')
    for (let i = 0; i < siEls.length; i++) shared.push(textsOf(siEls[i]))
  }

  const sheets = []
  const sheetEls = workbook.getElementsByTagName('sheet')
  for (let i = 0; i < sheetEls.length; i++) {
    const path = rels.get(relId(sheetEls[i]))
    const xml = path ? await partText(parts, path) : null
    if (xml != null) sheets.push({ name: sheetEls[i].getAttribute('name') || '', grid: gridOf(parseXml(xml), shared) })
  }
  return { sheets }
}
