import { describe, it, expect } from 'vitest'
import { readCoachSheet, setsOf, splitComplex, COACH_COLUMNS } from './coach-sheet.js'

// A grid the shape of the coach's sheets: day markers in A, the exercise in B, reps in G, sets in
// H, the load in I, a cue in J, his own comment in P. Built dense and padded, the way readXlsx
// hands a sheet over.
const grid = rows => rows.map(cells => {
  const row = Array(16).fill('')
  for (const [col, value] of Object.entries(cells)) row[COACH_COLUMNS[col]] = value
  return row
})

describe('splitComplex', () => {
  it('splits the coaches complexes on +', () => {
    expect(splitComplex('Strappo + strappo sosp alta')).toEqual(['Strappo', 'strappo sosp alta'])
    expect(splitComplex('Strappo')).toEqual(['Strappo'])
    expect(splitComplex('')).toEqual([])
  })
})

describe('setsOf', () => {
  it('reads the numbers Excel writes', () => {
    expect(setsOf('4.0')).toBe(4)
    expect(setsOf(' 3 ')).toBe(3)
    expect(setsOf('1')).toBe(1)
  })

  it('refuses anything that is not a set count', () => {
    // One week of the real workbook has a date serial in the sets column; 46115 sets is not a
    // number to hand a routine.
    expect(setsOf('46115.0')).toBe(null)
    expect(setsOf('')).toBe(null)
    expect(setsOf('0')).toBe(null)
    expect(setsOf('-2')).toBe(null)
    expect(setsOf('due')).toBe(null)
  })
})

describe('readCoachSheet', () => {
  it('reads the days and their exercises', () => {
    const { days, skipped } = readCoachSheet(grid([
      { day: 'Giorno 1', name: 'Strappo + strappo sosp alta', reps: '1+2', sets: '4.0', load: '50kg' },
      { name: 'Gambe avanti', reps: '3.0', sets: '5.0' },
      { day: 'Giorno 2', name: 'Girata', reps: '1+1', sets: '3.0' }
    ]))
    expect(days.map(d => d.n)).toEqual([1, 2])
    expect(days[0].entries.map(e => e.name)).toEqual(['Strappo + strappo sosp alta', 'Gambe avanti'])
    expect(days[0].entries[0]).toMatchObject({ row: 1, reps: '1+2', sets: 4, load: '50kg' })
    expect(days[1].entries.map(e => e.name)).toEqual(['Girata'])
    expect(skipped).toEqual([])
  })

  it('keeps the exercise written on the day marker row', () => {
    // Row 3 of the real sheets is "Giorno 1" in A and the day's first exercise in B.
    const { days } = readCoachSheet(grid([
      { day: 'Giorno 1', name: 'Pogo jump' },
      { name: 'Gambe avanti', reps: '3.0', sets: '4.0' }
    ]))
    expect(days[0].entries.map(e => e.name)).toEqual(['Pogo jump', 'Gambe avanti'])
  })

  it('drops the primers and the legend block', () => {
    const { days, skipped } = readCoachSheet(grid([
      { day: 'Giorno 1', name: 'Snatch primer (sequenza che fai di solito)' },
      { name: 'Strappo', reps: '2.0', sets: '4.0' },
      { day: 'Giorno 2', name: 'Jerk primer*' },
      { name: 'Spinte di forza', reps: '5.0', sets: '3.0' },
      { day: 'Jerk Primer:', name: 'Due serie per esercizio, 5 rep per serie' },
      { day: 'Spinte dalla spaccata' },
      { day: 'Spinte dalla mezza spaccata' }
    ]))
    expect(days.map(d => d.entries.map(e => e.name))).toEqual([['Strappo'], ['Spinte di forza']])
    // The legend labels are reported rather than silently eaten: the review screen lists them.
    expect(skipped.map(s => s.text)).toEqual([
      'Snatch primer (sequenza che fai di solito)',
      'Jerk primer*',
      'Jerk Primer:',
      'Due serie per esercizio, 5 rep per serie',
      'Spinte dalla spaccata',
      'Spinte dalla mezza spaccata'
    ])
  })

  it('stops reading exercises after the legend block', () => {
    const { days } = readCoachSheet(grid([
      { day: 'Giorno 1', name: 'Strappo', reps: '2.0', sets: '4.0' },
      { day: 'Jerk Primer:' },
      { name: 'Qualcosa che il parser non deve leggere', reps: '5.0', sets: '3.0' }
    ]))
    expect(days[0].entries.map(e => e.name)).toEqual(['Strappo'])
  })

  it('survives a sheet with no markers, blank rows and short rows', () => {
    const { days } = readCoachSheet([[], ['', 'Strappo', '', '', '', '', '2.0', '4.0']])
    expect(days).toEqual([{ n: 1, entries: [{ row: 2, name: 'Strappo', reps: '2.0', sets: 4, load: '', cue: '', comment: '' }] }])
  })

  it('continues a day instead of shadowing it when a marker repeats', () => {
    const { days } = readCoachSheet(grid([
      { day: 'Giorno 1', name: 'Strappo' },
      { day: 'Giorno 1', name: 'Girata' }
    ]))
    expect(days).toHaveLength(1)
    expect(days[0].entries.map(e => e.name)).toEqual(['Strappo', 'Girata'])
  })

  it('takes the cue and the comment with the row', () => {
    const { days } = readCoachSheet(grid([
      { day: 'Giorno 1', name: 'Gambe dietro', reps: '6.0', sets: '3.0', cue: 'Esci dalla buca forte', comment: 'Sono delle sosp' }
    ]))
    expect(days[0].entries[0].cue).toBe('Esci dalla buca forte')
    expect(days[0].entries[0].comment).toBe('Sono delle sosp')
  })
})
