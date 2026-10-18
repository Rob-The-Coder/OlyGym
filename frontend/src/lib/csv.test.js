import { describe, it, expect } from 'vitest'
import { parseCSV, toCSV } from './csv.js'

describe('toCSV', () => {
  it('joins records with CRLF, ends with one, and writes nothing for an empty table', () => {
    expect(toCSV([])).toBe('')
    expect(toCSV([], { bom: true })).toBe('')
    expect(toCSV([['a', 'b']])).toBe('a,b\r\n')
    expect(toCSV([['a'], ['b']])).toBe('a\r\nb\r\n')
  })

  it('prepends the UTF-8 BOM only when asked', () => {
    expect(toCSV([['a']], { bom: true })).toBe('\uFEFFa\r\n')
    expect(toCSV([['a']])).toBe('a\r\n')
  })

  it('quotes only when a cell has a comma, a quote or a line break, doubling embedded quotes', () => {
    expect(toCSV([['plain', 'a,b', 'say "hi"', 'a\nb', 'a\rb']])).toBe(
      'plain,"a,b","say ""hi""","a\nb","a\rb"\r\n'
    )
  })

  it('does not trim text and keeps Unicode accents as they are', () => {
    expect(toCSV([[' a ', 'Régimen', 'Città']])).toBe(' a ,Régimen,Città\r\n')
  })

  it('writes null, undefined, NaN and infinities as blanks, numbers and booleans as themselves', () => {
    expect(toCSV([[null, undefined, NaN, Infinity, -Infinity, 0, 12.5, true, false]])).toBe(
      ',,,,,0,12.5,true,false\r\n'
    )
  })

  it('prefixes a formula-looking non-numeric cell with a literal apostrophe', () => {
    expect(toCSV([['=SUM(A1)']])).toBe("'=SUM(A1)\r\n")
    expect(toCSV([['+1']])).toBe("'+1\r\n")
    expect(toCSV([['-1']])).toBe("'-1\r\n")
    expect(toCSV([['@cmd']])).toBe("'@cmd\r\n")
    expect(toCSV([['\t=x']])).toBe("'\t=x\r\n")
    // A leading CR is guarded first, then quoted because the final text contains a CR.
    expect(toCSV([['\rx']])).toBe('"\'\rx"\r\n')
  })

  it('quotes a guarded cell when the guard leaves it with a comma', () => {
    expect(toCSV([['=a,b']])).toBe('"\'=a,b"\r\n')
  })

  it('never prefixes a numeric or boolean cell, even when it is negative or false', () => {
    expect(toCSV([[-5, 0, 1.25, false, true]])).toBe('-5,0,1.25,false,true\r\n')
    expect(toCSV([['123', ' -5']])).toBe('123, -5\r\n')
  })

  it('never mutates the rows it is given', () => {
    const rows = [['a', 1, true], ['=x', null, 'a,b']]
    const snapshot = JSON.parse(JSON.stringify(rows))
    const csv = toCSV(rows, { bom: true })
    expect(rows).toEqual(snapshot)
    expect(csv).toBe('\uFEFFa,1,true\r\n\'=x,,"a,b"\r\n')
  })

  it('still reads back the cells it writes', () => {
    const rows = [['a,b', 'say "hi"', 'a\nb']]
    expect(parseCSV(toCSV(rows))).toEqual(rows)
  })
})
