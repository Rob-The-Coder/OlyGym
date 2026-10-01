import { describe, it, expect } from 'vitest'
import { bundleFromWeek, loadWeight, reviewWeek, schemesFor, sheetLabel } from './import-plan.js'
import { mergeWeek } from './plan-share.js'
import { EXDB } from './exercises-data.js'
import { COACH_COLUMNS } from './coach-sheet.js'
import { convertWeight } from './units.js'

const idOf = name => {
  const ex = EXDB.find(e => e.n === name)
  if (!ex) throw new Error(`"${name}" is not in the catalogue any more`)
  return ex.id
}

const grid = rows => rows.map(cells => {
  const row = Array(16).fill('')
  for (const [col, value] of Object.entries(cells)) row[COACH_COLUMNS[col]] = value
  return row
})

// One week, written the way the coach writes them: a primer line, a complex, a load with an
// instruction, a timed hold and a row of his own that the parser has to keep.
const week = {
  name: 'Settimana 23-29 marzo 2026',
  grid: grid([
    { day: 'Giorno 1', name: 'Snatch primer (sequenza che fai di solito)' },
    { name: 'Strappo + strappo sosp alta', reps: '1+2', sets: '4.0', load: '50kg , se leggeri le ultime due a 55kg' },
    { name: 'Piegamenti alle parallele', reps: '10.0', sets: '3.0' },
    { day: 'Giorno 2', name: 'Strappo no piedi', reps: '3.0', sets: '4.0', load: 'Due a 65kg, due a 70kg' },
    { name: 'Plank', reps: '1 minuto', sets: '3.0' },
    { name: 'Pogo jump' },
    { day: 'Giorno 3', name: 'Jerk primer*' }
  ])
}

describe('sheetLabel', () => {
  it('turns a sheet name into the dates a routine can be called', () => {
    expect(sheetLabel('Settimana 23-29 marzo 2026')).toBe('23-29 marzo')
    expect(sheetLabel('Settimana 8 - 15 giugno')).toBe('8 - 15 giugno')
    expect(sheetLabel('21-27 settembre')).toBe('21-27 settembre')
    expect(sheetLabel('Settimana 2')).toBe('2')
  })
})

describe('loadWeight', () => {
  it('takes the load the text opens with', () => {
    expect(loadWeight('50kg , se leggeri le ultime due a 55kg')).toBe(50)
    expect(loadWeight('70-75kg')).toBe(70)
    expect(loadWeight('Max 40kg')).toBe(40)
    expect(loadWeight('90,95 95')).toBe(90)
  })

  it('refuses a number that is an instruction', () => {
    // The whole point of the rule: "poi togli 10kg" is not a 10 kg bar.
    expect(loadWeight('Trova 6RM, poi togli 10kg e fai due serie a 7 rep')).toBe(null)
    expect(loadWeight('Due a 65kg, due a 70kg')).toBe(null)
    expect(loadWeight('Prima a 45, le altre tre a 50kg')).toBe(null)
    expect(loadWeight('1RM')).toBe(null)
    expect(loadWeight('5 minuti')).toBe(null)
    expect(loadWeight('bilanciere vuoto')).toBe(null)
    expect(loadWeight('Tesatura con elastico')).toBe(null)
    expect(loadWeight('')).toBe(null)
  })
})

describe('schemesFor', () => {
  it('reads one scheme per exercise', () => {
    expect(schemesFor('1+2', 2).schemes).toEqual([{ reps: 1 }, { reps: 2 }])
    expect(schemesFor('4.0', 1).schemes).toEqual([{ reps: 4 }])
    expect(schemesFor('3.0', 3).schemes).toEqual([{ reps: 3 }, { reps: 3 }, { reps: 3 }])
  })

  it('reads a hold and a per-side count', () => {
    expect(schemesFor('30 secondi + 10 rep per lato', 2).schemes).toEqual([
      { mode: 'time', sec: 30 },
      { reps: 10, side: true }
    ])
    expect(schemesFor('1 minuto', 1).schemes).toEqual([{ mode: 'time', sec: 60 }])
    expect(schemesFor('10 per lato', 1).schemes).toEqual([{ reps: 10, side: true }])
  })

  it('reads the coach asking for a rep max or an AMRAP', () => {
    expect(schemesFor('Trova 5RM', 1).schemes).toEqual([{ reps: 5, rmax: true }])
    expect(schemesFor('Amrap', 1).schemes[0]).toMatchObject({ reps: 10, amrap: true })
  })

  it('keeps a scheme that does not line up with the exercises', () => {
    // "Slancio 1+1" is one clean and jerk, not two exercises: the first number is the set and the
    // coach's own "1+1" goes in the note rather than disappearing.
    expect(schemesFor('1+1', 1).schemes).toEqual([{ reps: 1 }])
    expect(schemesFor('1+1', 1).note).toBe('1+1')
    expect(schemesFor('2+1', 3).schemes).toEqual([{ reps: 2 }, { reps: 2 }, { reps: 1 }])
    expect(schemesFor('2+1', 3).note).toBe('2+1')
  })
})

describe('reviewWeek', () => {
  const review = reviewWeek(week)

  it('proposes one exercise per component and groups a complex', () => {
    const day1 = review.days[0]
    expect(day1.name).toBe('Giorno 1 · 23-29 marzo')
    expect(day1.entries.map(e => e.name)).toEqual(['snatch', 'hang snatch', 'dip'])
    expect(day1.entries[0].id).toBe(idOf('snatch'))
    expect(day1.entries[0].sg).toBe(day1.entries[1].sg)
    expect(day1.entries[2].sg).toBe(null)
  })

  it('carries the reps and the sets the coach wrote', () => {
    const day1 = review.days[0]
    expect(day1.entries[0]).toMatchObject({ sets: 4, reps: 1, weight: 50 })
    expect(day1.entries[1]).toMatchObject({ sets: 4, reps: 2, weight: 50 })
    const day2 = review.days[1]
    expect(day2.entries.find(e => e.name === 'plank')).toMatchObject({ mode: 'time', sec: 60, reps: null })
  })

  it('keeps what the catalogue has no word for in the note', () => {
    // "no piedi" IS a Catalyst exercise ("snatch with no jump"), so it arrives as one; the load
    // that is a sentence, which has no equivalent at all, is what stays in the note.
    const noFeet = review.days[1].entries[0]
    expect(noFeet.name).toBe('snatch with no jump')
    expect(noFeet.tier).toBe(1)
    // A load that is a sentence stays a note too, even though the weight came out of it.
    expect(noFeet.weight).toBe(null)
    expect(noFeet.note).toContain('Due a 65kg, due a 70kg')
    expect(review.days[0].entries[0].note).toContain('se leggeri le ultime due a 55kg')
  })

  it('defaults the sets and reps it was not given, and says so', () => {
    const pogo = review.days[1].entries.find(e => e.custom)
    expect(pogo).toMatchObject({ name: 'Pogo jump', sets: 3, reps: 10, custom: true })
    expect(pogo.warns).toContain('sets')
    expect(pogo.warns).toContain('reps')
  })

  it('leaves the primer lines out and reports them', () => {
    expect(review.days[1].entries.map(e => e.name)).not.toContain('Jerk primer*')
    expect(review.skipped.map(s => s.text)).toContain('Snatch primer (sequenza che fai di solito)')
    expect(review.skipped.map(s => s.text)).toContain('Jerk primer*')
  })

  it('drops a day with nothing in it', () => {
    // Day 3 of the real week is only a primer line: an empty routine is not worth importing.
    expect(review.days.map(d => d.n)).toEqual([1, 2])
  })

  it('counts what it understood', () => {
    expect(review.stats).toMatchObject({ rows: 5, exercises: 6, custom: 1 })
  })
})

describe('bundleFromWeek', () => {
  const review = reviewWeek(week)
  const wk = bundleFromWeek(review, { unit: 'kg' })

  it('is one week of the dated plan, named after the sheet', () => {
    expect(wk.startIso).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(wk.name).toBe('23-29 marzo')
    expect(wk.days.map(d => d.name)).toEqual(['Giorno 1 · 23-29 marzo', 'Giorno 2 · 23-29 marzo'])
    expect(wk.days.map(d => d.ex.length)).toEqual([3, 3])
  })

  it('schedules the days on the weekdays it was given, in order', () => {
    expect(wk.days.map(d => d.dow)).toEqual([1, 3, 5].slice(0, 2))
    expect(bundleFromWeek(review, { unit: 'kg', days: [2, 4] }).days.map(d => d.dow)).toEqual([2, 4])
  })

  it('imports as a new week, with the coach’s custom exercise created once', () => {
    const s = { weeks: [], customEx: [] }
    mergeWeek(s, bundleFromWeek(review, { unit: 'kg' }))
    expect(s.weeks).toHaveLength(1)
    expect(s.weeks[0].days.map(d => d.dow)).toEqual([1, 3])
    expect(s.weeks[0].days[0].ex[0].id).toBe(idOf('snatch'))
    const custom = s.customEx.filter(c => c.n === 'Pogo jump')
    expect(custom).toHaveLength(1)
    expect(custom[0]).toMatchObject({ bp: 'Jumping & Plyometrics', custom: true })
    expect(s.weeks[0].days[1].ex.some(e => e.id === custom[0].id)).toBe(true)

    // A second week with the same custom reuses it instead of making a twin.
    const other = reviewWeek({ name: 'Settimana 2', grid: week.grid })
    mergeWeek(s, bundleFromWeek(other, { unit: 'kg' }))
    expect(s.customEx.filter(c => c.n === 'Pogo jump')).toHaveLength(1)
    expect(s.weeks).toHaveLength(2)
  })

  it('hands its numbers over in the account’s own unit', () => {
    // The sheet is in kilos. An account in pounds gets pounds, the same way a shared plan does.
    const inLb = bundleFromWeek(review, { unit: 'lb' })
    const first = inLb.days[0].ex[0]
    expect(first.weight).toBe(convertWeight(50, 'kg', 'lb'))
    expect(first.sets).toBe(4)
  })
})
