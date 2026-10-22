import { describe, it, expect } from 'vitest'
import { applyEntryCorrection, bundleFromWeek, loadWeight, reviewWeek, schemesFor, sheetLabel } from './import-plan.js'
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
    // Neither has a field in the plan, so the coach's words ride in the note: without this
    // "Amrap" reaches the review as a plain ten reps with nothing saying otherwise.
    expect(schemesFor('Trova 5RM', 1).note).toBe('Trova 5RM')
    expect(schemesFor('Amrap', 1).note).toBe('Amrap')
    expect(schemesFor('Amrap', 2).note).toBe('Amrap')
    expect(schemesFor('3.0', 1).note).toBe('')
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

  it('puts an AMRAP on the note instead of leaving ten reps unexplained', () => {
    const r = reviewWeek({ name: 'Settimana', grid: grid([{ day: 'Giorno 1', name: 'Snatch', reps: 'Amrap', sets: '3.0' }]) })
    expect(r.days[0].entries[0].reps).toBe(10)
    expect(r.days[0].entries[0].note).toBe('Amrap')
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

  // What the review screen prints under the reading, and what the week keeps: his words, his sets and
  // his reps as written, so "was this ported right?" is a question the screen can answer.
  it('keeps the coach’s own row', () => {
    expect(review.days[0].entries.map(e => e.src)).toEqual([
      'Strappo · 4 × 1+2',
      'strappo sosp alta · 4 × 1+2',
      'Piegamenti alle parallele · 3 × 10'
    ])
    expect(review.days[1].entries.map(e => e.src)).toEqual([
      'Strappo no piedi · 4 × 3',
      'Plank · 3 × 1 minuto',
      // His own name is the row's title (the catalogue had nothing for it) and he wrote no numbers,
      // so there is nothing left to print.
      ''
    ])
    // A load written as a sentence stays the note and is not printed twice in the same row.
    expect(review.days[0].entries[0].src).not.toContain('se leggeri')
    expect(review.days[0].entries[0].note).toContain('se leggeri le ultime due a 55kg')
  })

  it('leaves out the words the note already shows', () => {
    // "Gambe avanti prima stop in buca" is a front squat with a pause: the catalogue has the lift, the
    // matcher keeps his phrase in the note, and the line under it is the rest of his row.
    const r = reviewWeek({ name: 'Settimana', grid: grid([
      { day: 'Giorno 1', name: 'Gambe avanti prima stop in buca', reps: '3.0', sets: '5.0', load: '100kg', cue: 'discesa controllata' }
    ]) })
    const e = r.days[0].entries[0]
    expect(e.note).toContain('Gambe avanti prima stop in buca')
    expect(e.src).toBe('5 × 3 · 100kg')
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

  it('carries his own row onto the exercise, in either unit', () => {
    expect(wk.days[0].ex[0].src).toBe('Strappo · 4 × 1+2')
    expect(bundleFromWeek(review, { unit: 'lb' }).days[0].ex[0].src).toBe('Strappo · 4 × 1+2')
    const s = { weeks: [], customEx: [] }
    mergeWeek(s, wk)
    expect(s.weeks[0].days[0].ex[0].src).toBe('Strappo · 4 × 1+2')
  })

  it('hands its numbers over in the account’s own unit', () => {
    // The sheet is in kilos. An account in pounds gets pounds, the same way a shared plan does.
    const inLb = bundleFromWeek(review, { unit: 'lb' })
    const first = inLb.days[0].ex[0]
    expect(first.weight).toBe(convertWeight(50, 'kg', 'lb'))
    expect(first.sets).toBe(4)
  })
})

// The review screen lets the user fix the numbers a row was read as. These pin the pure merge the
// screen and the import share: what a correction may touch, what it answers, and what it may not
// quietly rewrite.
describe('applyEntryCorrection', () => {
  const base = reviewWeek(week).days[1].entries.find(e => e.custom)

  it('merges only the prescription keys and returns a new entry', () => {
    const out = applyEntryCorrection(base, { sets: 4, reps: 6, weight: 40, note: 'x', src: 'y', tier: 9, id: 'nope', mode: 'time' })
    expect(out).not.toBe(base)
    expect(out).toMatchObject({ sets: 4, reps: 6, weight: 40 })
    // The rest of the row — what a number correction is not about — is untouched.
    expect(out.key).toBe(base.key)
    expect(out.name).toBe(base.name)
    expect(out.note).toBe(base.note)
    expect(out.src).toBe(base.src)
    expect(out.tier).toBe(base.tier)
    expect(out.custom).toBe(base.custom)
    expect(out.mode).toBe(base.mode)
    expect(out.sg).toBe(base.sg)
    // The entry it was given was not touched.
    expect(base.sets).toBe(3)
    expect(base.reps).toBe(10)
  })

  it('normalises what the fields hand it and ignores what is not a number', () => {
    expect(applyEntryCorrection(base, { sets: 0 }).sets).toBe(1)
    expect(applyEntryCorrection(base, { sets: -2 }).sets).toBe(1)
    expect(applyEntryCorrection(base, { reps: 4.6 }).reps).toBe(5)
    expect(applyEntryCorrection(base, { weight: -5 }).weight).toBe(0)
    const ignored = applyEntryCorrection(base, { sets: NaN, reps: Infinity, weight: NaN, bogus: 3 })
    expect(ignored.sets).toBe(base.sets)
    expect(ignored.reps).toBe(base.reps)
    expect(ignored.weight).toBe(base.weight)
    expect(ignored.bogus).toBeUndefined()
  })

  it('accepts a number as a count and nothing else', () => {
    // Number() would turn these into 1/7/60; a correction is numbers only, and Number(Symbol())
    // throws, so the helper must ignore it rather than crash.
    const out = applyEntryCorrection(base, { sets: true, reps: [7], sec: '45', weight: '60' })
    expect(out.sets).toBe(base.sets)
    expect(out.reps).toBe(base.reps)
    expect(out.sec).toBe(base.sec)
    expect(out.weight).toBe(base.weight)
    expect(() => applyEntryCorrection(base, { sets: Symbol('n') })).not.toThrow()
  })

  it('leaves a frozen entry and its warns array alone', () => {
    const frozen = Object.freeze({ ...base, warns: Object.freeze([...base.warns]) })
    const out = applyEntryCorrection(frozen, { sets: 5, reps: 5 })
    expect(out.sets).toBe(5)
    expect(out.reps).toBe(5)
    expect(frozen.sets).toBe(3)
    expect([...frozen.warns]).toEqual(['sets', 'reps'])
  })

  it('answers the warning about the number it corrected and leaves the others', () => {
    const warned = { ...base, warns: ['sets', 'reps', 'other'] }
    expect(applyEntryCorrection(warned, { sets: 3 }).warns).toEqual(['reps', 'other'])
    expect(applyEntryCorrection(warned, { reps: 5 }).warns).toEqual(['sets', 'other'])
    expect(applyEntryCorrection(warned, { sec: 45 }).warns).toEqual(['sets', 'other'])
    expect(applyEntryCorrection(warned, { weight: 40 }).warns).toEqual(['sets', 'reps', 'other'])
    // A non-finite value is not a correction, so it answers nothing.
    expect(applyEntryCorrection(warned, { sets: NaN }).warns).toEqual(['sets', 'reps', 'other'])
  })
})

describe('reviewWeek with corrections', () => {
  const plain = reviewWeek(week)

  it('corrects exactly the entry the key names', () => {
    const target = plain.days[0].entries[0]
    const r = reviewWeek(week, { corrections: { [target.key]: { sets: 5, reps: 2 } } })
    expect(r.days[0].entries[0]).toMatchObject({ sets: 5, reps: 2 })
    // The other movement of the same complex, the rest of the day and every other day are as
    // they were: one key is one correction.
    expect(r.days[0].entries[1]).toEqual(plain.days[0].entries[1])
    expect(r.days[0].entries[2]).toEqual(plain.days[0].entries[2])
    expect(r.days[1].entries).toEqual(plain.days[1].entries)
  })

  it('changes nothing for an unknown key and mutates nothing it is given', () => {
    const corrections = { nope: { sets: 9 } }
    expect(reviewWeek(week, { corrections }).days).toEqual(plain.days)
    expect(corrections).toEqual({ nope: { sets: 9 } })
    expect(plain.days[0].entries[0].sets).toBe(4)
  })

  it('reads a frozen corrections map without writing to it', () => {
    const target = plain.days[1].entries[0]
    const r = reviewWeek(week, { corrections: Object.freeze({ [target.key]: Object.freeze({ weight: 70 }) }) })
    expect(r.days[1].entries[0].weight).toBe(70)
  })
})

describe('bundleFromWeek with a corrected review', () => {
  const plain = reviewWeek(week)
  const firstKey = plain.days[0].entries[0].key
  const corrected = reviewWeek(week, { corrections: { [firstKey]: { sets: 2, reps: 5, weight: 55 } } })

  it('carries the corrected numbers into the week and keeps his own row', () => {
    expect(bundleFromWeek(corrected, { unit: 'kg' }).days[0].ex[0]).toMatchObject({ sets: 2, reps: 5, weight: 55, src: 'Strappo · 4 × 1+2' })
  })

  it('still converts the corrected load into the account’s unit', () => {
    expect(bundleFromWeek(corrected, { unit: 'lb' }).days[0].ex[0].weight).toBe(convertWeight(55, 'kg', 'lb'))
  })

  it('carries corrected seconds for a timed row', () => {
    const plank = plain.days[1].entries.find(e => e.name === 'plank')
    const r = reviewWeek(week, { corrections: { [plank.key]: { sets: 4, sec: 45 } } })
    const cfg = bundleFromWeek(r, { unit: 'kg' }).days[1].ex.find(e => e.id === idOf('plank'))
    expect(cfg).toMatchObject({ sets: 4, mode: 'time', sec: 45 })
  })
})
// Progressive sets in the review: a ladder correction is one prescription like the weight, so it
// sets the first load as the uniform weight and is resized whenever the set count changes.
describe('per-set loads in the review', () => {
  const entry = reviewWeek(week).days[0].entries[0]

  it('applies a ladder, with the first load as the single weight', () => {
    const out = applyEntryCorrection(entry, { setWeights: [60, 60, 65, 65] })
    expect(out.setWeights).toEqual([60, 60, 65, 65])
    expect(out.weight).toBe(60)
  })

  it('resizes an existing ladder when the set count is corrected', () => {
    const had = applyEntryCorrection(entry, { setWeights: [60, 60, 65, 65] })
    expect(applyEntryCorrection(had, { sets: 3 }).setWeights).toEqual([60, 60, 65])
    // and repeats the last load when the count grows
    expect(applyEntryCorrection(had, { sets: 6 }).setWeights).toEqual([60, 60, 65, 65, 65, 65])
  })

  it('collapses all-equal values back to the single weight', () => {
    const out = applyEntryCorrection(entry, { setWeights: [60, 60, 60, 60] })
    expect(out.setWeights).toBeUndefined()
    expect(out.weight).toBe(60)
    const had = applyEntryCorrection(entry, { setWeights: [60, 60, 65, 65] })
    const collapsed = applyEntryCorrection(had, { setWeights: [70, 70, 70, 70] })
    expect(collapsed.setWeights).toBeUndefined()
    expect(collapsed.weight).toBe(70)
  })

  it('clears the ladder when the single weight is corrected', () => {
    const had = applyEntryCorrection(entry, { setWeights: [60, 60, 65, 65] })
    const out = applyEntryCorrection(had, { weight: 70 })
    expect(out.setWeights).toBeUndefined()
    expect(out.weight).toBe(70)
  })

  it('never stores a ladder of junk, an empty one, or an unnormalised length', () => {
    expect(applyEntryCorrection(entry, { setWeights: ['60', null] }).setWeights).toBeUndefined()
    expect(applyEntryCorrection(entry, { setWeights: [] }).setWeights).toBeUndefined()
    expect(applyEntryCorrection(entry, { setWeights: [60, -5, 65] }).setWeights).toEqual([60, 0, 65, 65])
    // the entry it was given was not touched
    expect(entry.setWeights).toBeUndefined()
  })

  it('writes the ladder into the week, converted to the account unit', () => {
    const key = reviewWeek(week).days[0].entries[0].key
    const corrected = reviewWeek(week, { corrections: { [key]: { setWeights: [60, 60, 65, 65] } } })
    const kg = bundleFromWeek(corrected, { unit: 'kg' }).days[0].ex[0]
    expect(kg.setWeights).toEqual([60, 60, 65, 65])
    expect(kg.weight).toBe(kg.setWeights[0])

    const lb = bundleFromWeek(corrected, { unit: 'lb' }).days[0].ex[0]
    expect(lb.setWeights).toEqual([60, 60, 65, 65].map(w => convertWeight(w, 'kg', 'lb')))
    expect(lb.weight).toBe(lb.setWeights[0])
  })
})

