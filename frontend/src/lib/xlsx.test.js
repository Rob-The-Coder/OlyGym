// @vitest-environment happy-dom
import { describe, expect, it } from 'vitest'
import { readXlsx } from './xlsx.js'

/* ------------------------------------------------------------ zip writer --- */
// The fixtures are real zips built here rather than bytes checked in, so the reader is
// tested against both compression methods Excel writes without shipping binaries around.

const enc = new TextEncoder()
const b = s => enc.encode(s)

// Table-less CRC32. The reader does not check it — a zip's CRC only matters to an unzipper
// that trusts the archive — but a fixture with a wrong one is not a valid zip, so compute it.
function crc32(data) {
  let crc = 0xffffffff
  for (const byte of data) {
    crc ^= byte
    for (let bit = 0; bit < 8; bit++) crc = crc & 1 ? 0xedb88320 ^ (crc >>> 1) : crc >>> 1
  }
  return (crc ^ 0xffffffff) >>> 0
}

const join = chunks => {
  const out = new Uint8Array(chunks.reduce((n, c) => n + c.length, 0))
  let at = 0
  for (const c of chunks) { out.set(c, at); at += c.length }
  return out
}

async function deflateRaw(bytes) {
  const stream = new CompressionStream('deflate-raw')
  const pump = (async () => {
    const writer = stream.writable.getWriter()
    await writer.write(bytes)
    await writer.close()
  })().catch(() => {})
  const chunks = []
  const reader = stream.readable.getReader()
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    chunks.push(value)
  }
  await pump
  return join(chunks)
}

/** files: [{ name, xml }] -> a complete archive. Stored when `deflated` is false. */
async function zip(files, deflated) {
  const parts = []
  const central = []
  let offset = 0
  for (const f of files) {
    const name = b(f.name)
    const raw = b(f.xml)
    const data = deflated ? await deflateRaw(raw) : raw
    const method = deflated ? 8 : 0
    const crc = crc32(raw)

    const local = new Uint8Array(30 + name.length)
    const lv = new DataView(local.buffer)
    lv.setUint32(0, 0x04034b50, true)
    lv.setUint16(4, 20, true)
    lv.setUint16(8, method, true)
    lv.setUint32(14, crc, true)
    lv.setUint32(18, data.length, true)
    lv.setUint32(22, raw.length, true)
    lv.setUint16(26, name.length, true)
    local.set(name, 30)
    parts.push(local, data)

    const entry = new Uint8Array(46 + name.length)
    const cv = new DataView(entry.buffer)
    cv.setUint32(0, 0x02014b50, true)
    cv.setUint16(4, 20, true)
    cv.setUint16(6, 20, true)
    cv.setUint16(10, method, true)
    cv.setUint32(16, crc, true)
    cv.setUint32(20, data.length, true)
    cv.setUint32(24, raw.length, true)
    cv.setUint16(28, name.length, true)
    cv.setUint32(42, offset, true)
    entry.set(name, 46)
    central.push(entry)
    offset += local.length + data.length
  }
  const cdSize = central.reduce((n, c) => n + c.length, 0)
  const eocd = new Uint8Array(22)
  const ev = new DataView(eocd.buffer)
  ev.setUint32(0, 0x06054b50, true)
  ev.setUint16(8, files.length, true)
  ev.setUint16(10, files.length, true)
  ev.setUint32(12, cdSize, true)
  ev.setUint32(16, offset, true)
  return join([...parts, ...central, eocd])
}

/* ------------------------------------------------------------- workbook ---- */

const WORKBOOK = `<?xml version="1.0"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Log" sheetId="1" r:id="rId1"/>
    <sheet name="Notes" sheetId="2" r:id="rId2"/>
    <sheet name="Empty" sheetId="3" r:id="rId3"/>
  </sheets>
</workbook>`

// rId2 spells its target out with the leading /xl/ some writers use; the others are relative.
const RELS = `<?xml version="1.0"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="t" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="t" Target="/xl/worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="t" Target="worksheets/sheet3.xml"/>
</Relationships>`

// 0: one <t> holding a non-breaking space and a newline. 1: two <r> runs. 2: plain.
const SHARED = `<?xml version="1.0"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="3" uniqueCount="3">
  <si><t xml:space="preserve">Barbell&#160;
 Bench</t></si>
  <si><r><rPr><b/></rPr><t>Leg</t></r><r><t xml:space="preserve">  Press </t></r></si>
  <si><t>Tail</t></si>
</sst>`

const SHEET_HEAD = '<?xml version="1.0"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'

// Row 2 leaves B2 out; row 3 jumps to P3; the fourth row has no r at all (A4, continuing
// from row 3); row 16 sits alone, so everything between is padding.
const SHEET1 = `${SHEET_HEAD}<sheetData>
  <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
  <row r="2"><c r="A2" t="inlineStr"><is><t>  Inline
 name </t></is></c><c r="C2"><v>4.0</v></c></row>
  <row r="3"><c r="A3" t="b"><v>1</v></c><c r="P3"><v>1.5</v></c></row>
  <row><c><v>7</v></c></row>
  <row r="16"><c r="P16" t="s"><v>2</v></c></row>
</sheetData></worksheet>`

const SHEET2 = `${SHEET_HEAD}<sheetData><row r="1"><c r="B1" t="str"><v>cached</v></c></row></sheetData></worksheet>`
const SHEET3 = `${SHEET_HEAD}<sheetData/></worksheet>`

const FILES = [
  { name: 'xl/workbook.xml', xml: WORKBOOK },
  { name: 'xl/_rels/workbook.xml.rels', xml: RELS },
  { name: 'xl/sharedStrings.xml', xml: SHARED },
  { name: 'xl/worksheets/sheet1.xml', xml: SHEET1 },
  { name: 'xl/worksheets/sheet2.xml', xml: SHEET2 },
  { name: 'xl/worksheets/sheet3.xml', xml: SHEET3 },
]

const stored = await zip(FILES, false)
const deflated = await zip(FILES, true)

const WIDTH = 16   // column P is index 15, so every row is 16 cells wide
const row = cells => {
  const out = new Array(WIDTH).fill('')
  for (const i of Object.keys(cells)) out[i] = cells[i]
  return out
}

describe('readXlsx', () => {
  it('reads the sheet names in workbook order', async () => {
    const { sheets } = await readXlsx(stored)
    expect(sheets.map(s => s.name)).toEqual(['Log', 'Notes', 'Empty'])
  })

  it('reads a deflated archive the same as a stored one', async () => {
    expect(await readXlsx(deflated)).toEqual(await readXlsx(stored))
  })

  it('joins every <t> run of a shared string and collapses whitespace', async () => {
    const { sheets } = await readXlsx(stored)
    expect(sheets[0].grid[0][0]).toBe('Barbell Bench')   // nbsp + newline -> one space
    expect(sheets[0].grid[0][1]).toBe('Leg Press')       // two rich-text runs
  })

  it('reads an inline string from the cell itself', async () => {
    const { sheets } = await readXlsx(stored)
    expect(sheets[0].grid[1][0]).toBe('Inline name')
    expect(sheets[1].grid[0][1]).toBe('cached')          // t="str" cached formula result
  })

  it('returns numbers verbatim and booleans as TRUE/FALSE', async () => {
    const { sheets } = await readXlsx(stored)
    expect(sheets[0].grid[1][2]).toBe('4.0')
    expect(sheets[0].grid[2][15]).toBe('1.5')
    expect(sheets[0].grid[2][0]).toBe('TRUE')
    expect(sheets[0].grid[3][0]).toBe('7')
  })

  it('maps cell references and pads sparse rows to the widest one', async () => {
    const { sheets } = await readXlsx(stored)
    const grid = sheets[0].grid
    expect(grid).toHaveLength(16)
    expect(grid[0]).toEqual(row({ 0: 'Barbell Bench', 1: 'Leg Press' }))
    expect(grid[1]).toEqual(row({ 0: 'Inline name', 2: '4.0' }))
    expect(grid[2]).toEqual(row({ 0: 'TRUE', 15: '1.5' }))
    expect(grid[3]).toEqual(row({ 0: '7' }))             // no r: the row continues, column A
    expect(grid[7]).toEqual(row({}))                     // never written: a blank row
    expect(grid[15]).toEqual(row({ 15: 'Tail' }))        // P16: two-letter column
    expect(grid.every(r => r.length === WIDTH)).toBe(true)
  })

  it('gives an empty sheet an empty grid', async () => {
    const { sheets } = await readXlsx(stored)
    expect(sheets[2].grid).toEqual([])
  })

  it('accepts a Uint8Array as well as an ArrayBuffer', async () => {
    expect((await readXlsx(deflated)).sheets).toHaveLength(3)
    expect((await readXlsx(stored.buffer)).sheets).toHaveLength(3)
  })

  it('rejects a buffer that is not a zip', async () => {
    await expect(readXlsx(b('this is a csv, honestly'))).rejects.toThrow(Error)
    await expect(readXlsx(b('this is a csv, honestly'))).rejects.toThrow(/not an \.xlsx workbook/)
  })

  it('rejects a zip without xl/workbook.xml', async () => {
    const archive = await zip([{ name: 'readme.txt', xml: 'nothing to see' }], false)
    await expect(readXlsx(archive)).rejects.toThrow(/not an \.xlsx workbook/)
  })
})
