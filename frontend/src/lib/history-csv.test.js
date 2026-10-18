import { describe, it, expect } from 'vitest'
import { HISTORY_CSV_HEADER, historyRows, historyFilename } from './history-csv.js'
import { EXDB, EXIDX } from './exercises.js'
import { toCSV, parseCSV } from './csv.js'

const LIFT = EXDB.find(e => e.eq === 'barbell').id
const ALT = EXDB.find(e => e.eq === 'barbell' && e.id !== LIFT).id
const BW = EXDB.find(e => e.eq === 'body weight').id

const set = over => ({ w: 100, r: 5, done: true, ...over })
const entry = over => ({ id: LIFT, sets: [set()], ...over })
const workout = over => ({
  id: 'w1', d: '2026-02-02', start: Date.UTC(2026, 1, 2, 9), end: Date.UTC(2026, 1, 2, 10),
  name: 'Session', note: 'session note', prs: [], entries: [entry()], ...over,
})

const body = rows => rows.slice(1)

describe('HISTORY_CSV_HEADER', () => {
  it('is the frozen 20-column file format, in the documented order', () => {
    expect(Object.isFrozen(HISTORY_CSV_HEADER)).toBe(true)
    expect([...HISTORY_CSV_HEADER]).toEqual([
      'workout_id', 'date', 'start', 'end', 'workout', 'workout_note', 'entry',
      'exercise', 'exercise_id', 'set', 'phase', 'reps', 'seconds', 'weight',
      'unit', 'bodyweight', 'rir', 'rpe', 'pr', 'entry_note',
    ])
  })
})

describe('historyRows', () => {
  it('returns the header first and gives every row the same width', () => {
    const rows = historyRows([workout()])
    expect(rows[0]).toBe(HISTORY_CSV_HEADER)
    expect(rows).toHaveLength(2)
    expect(rows[1]).toHaveLength(HISTORY_CSV_HEADER.length)
  })

  it('returns the header alone for an absent or empty log', () => {
    expect(historyRows([])).toEqual([HISTORY_CSV_HEADER])
    expect(historyRows(null)).toEqual([HISTORY_CSV_HEADER])
    expect(historyRows(undefined)).toEqual([HISTORY_CSV_HEADER])
    expect(historyRows({})).toEqual([HISTORY_CSV_HEADER])
    expect(historyRows('workouts')).toEqual([HISTORY_CSV_HEADER])
  })

  it('orders sessions oldest first by date then start, keeping stored order on a tie', () => {
    const newest = workout({ id: 'newest', d: '2026-02-02', start: 5000 })
    const later = workout({ id: 'later', d: '2026-02-01', start: 9000 })
    const earlier = workout({ id: 'earlier', d: '2026-02-01', start: 1000 })
    const tieA = workout({ id: 'tie-a', d: '2026-02-03', start: 100 })
    const tieB = workout({ id: 'tie-b', d: '2026-02-03', start: 100 })
    expect(body(historyRows([newest, later, earlier, tieA, tieB])).map(r => r[0]))
      .toEqual(['earlier', 'later', 'newest', 'tie-a', 'tie-b'])
  })

  it('numbers entries and sets from 1 and exports only done sets, keeping skipped entries counted', () => {
    const w = workout({
      entries: [
        null,
        entry({ id: LIFT, sets: [set({ r: 1, done: false }), set({ r: 2 }), set({ r: 3, done: false })] }),
        entry({ id: ALT, sets: [set({ r: 4 }), set({ r: 5, done: false }), set({ r: 6 })] }),
        { id: LIFT, sets: [] },
        { id: LIFT },
        7,
      ],
    })
    expect(body(historyRows([w])).map(r => [r[6], r[9], r[11]]))
      .toEqual([[2, 2, 2], [3, 1, 4], [3, 3, 6]])
  })

  it('exports a done warm-up with phase=warmup, timed sets with seconds and rep sets with reps', () => {
    const w = workout({
      entries: [entry({ sets: [
        set({ phase: 'warmup', r: 10, w: 40 }),
        set({ warmup: true, r: 8, w: 60 }),
        set({ sec: 75, r: undefined, w: 10, rir: 2, rpe: 7 }),
        set({ r: 5, w: 100, rpe: 8, rir: NaN }),
        set({ r: 3, w: 0 }),
        set({ r: 3, w: NaN, rir: Infinity }),
      ] })],
    })
    const rows = body(historyRows([w]))
    expect(rows.map(r => r[10])).toEqual(['warmup', 'warmup', 'work', 'work', 'work', 'work'])
    expect(rows[2].slice(11, 18)).toEqual(['', 75, 10, 'kg', false, 2, 7])
    expect(rows[3].slice(11, 18)).toEqual([5, '', 100, 'kg', false, '', 8])
    expect(rows[4].slice(11, 14)).toEqual([3, '', 0])
    expect(rows[5].slice(11, 14)).toEqual([3, '', ''])
    expect(rows[5][16]).toBe('')
  })

  it('uses the canonical catalogue name with the raw id as fallback, and reads bodyweight and PR flags', () => {
    const w = workout({
      prs: [LIFT],
      entries: [
        entry({ id: LIFT, target: { bodyweight: true } }),
        entry({ id: BW, sets: [set({ w: 0, r: 10 })] }),
        entry({ id: 'no-such-lift' }),
        entry({ id: ALT }),
      ],
    })
    const rows = body(historyRows([w]))
    expect(rows[0][7]).toBe(EXIDX[LIFT].n)
    expect(rows[0][8]).toBe(LIFT)
    expect(rows[0][15]).toBe(true)
    expect(rows[0][18]).toBe(true)
    expect(rows[1][7]).toBe(EXIDX[BW].n)
    expect(rows[1][15]).toBe(true)
    expect(rows[1][18]).toBe(false)
    expect(rows[2][7]).toBe('no-such-lift')
    expect(rows[2][8]).toBe('no-such-lift')
    expect(rows[2][15]).toBe(false)
    expect(rows[3][15]).toBe(false)
    expect(rows[3][18]).toBe(false)
  })

  it('treats a missing or non-array prs as no PRs', () => {
    const rows = body(historyRows([
      workout({ id: 'missing', prs: undefined }),
      workout({ id: 'string', prs: LIFT }),
    ]))
    expect(rows.map(r => r[18])).toEqual([false, false])
  })

  it('converts epoch and parseable start/end to ISO UTC, leaving anything else blank', () => {
    const start = Date.UTC(2026, 0, 2, 3, 4, 5)
    const w = workout({
      d: '2026-01-02', start, end: '2026-01-02T05:00:00Z',
      name: 'Snatch day', note: 'felt, "good"\nready',
      entries: [entry({ note: 'belt, "tight"\nline' })],
    })
    const row = body(historyRows([w]))[0]
    expect(row[1]).toBe('2026-01-02')
    expect(row[2]).toBe(new Date(start).toISOString())
    expect(row[3]).toBe('2026-01-02T05:00:00.000Z')
    expect(row[4]).toBe('Snatch day')
    expect(row[5]).toBe('felt, "good"\nready')
    expect(row[19]).toBe('belt, "tight"\nline')

    const broken = workout({ start: 'not a date', end: {}, entries: [entry()] })
    const blank = body(historyRows([broken]))[0]
    expect(blank[2]).toBe('')
    expect(blank[3]).toBe('')
  })

  it('leaves missing fields blank, invents nothing and still writes the row', () => {
    const w = { entries: [{ sets: [{ done: true }] }] }
    const row = body(historyRows([w]))[0]
    expect(row).toEqual([
      '', '', '', '', '', '', 1, '', '', 1, 'work', '', '', '', 'kg', false, '', '', false, '',
    ])
  })

  it('skips non-object workouts and malformed entries/sets instead of failing', () => {
    expect(historyRows([
      null, 1, 'x', [],
      { entries: [null, 4, [], { sets: [null, 'x', { done: false }] }, { sets: 'nope' }] },
    ])).toEqual([HISTORY_CSV_HEADER])
  })

  it('writes the requested unit', () => {
    const w = workout({ entries: [entry()] })
    expect(body(historyRows([w]))[0][14]).toBe('kg')
    expect(body(historyRows([w], 'lb'))[0][14]).toBe('lb')
  })

  it('does not mutate the workouts, their order or their nested data', () => {
    const workouts = [
      workout({ id: 'newest', d: '2026-02-02', entries: [entry({ note: 'n' })] }),
      workout({ id: 'older', d: '2026-02-01', entries: [entry({ sets: [set({ phase: 'warmup' })] })] }),
    ]
    const snapshot = JSON.parse(JSON.stringify(workouts))
    historyRows(workouts, 'lb')
    expect(workouts).toEqual(snapshot)
    expect(workouts.map(w => w.id)).toEqual(['newest', 'older'])
  })

  it('round-trips a note with a comma, a quote and a newline as one cell on one logical row', () => {
    const note = 'a, "b"\nc'
    const rows = historyRows([workout({ entries: [entry({ note })] })])
    const back = parseCSV(toCSV(rows))
    expect(back).toHaveLength(2)
    expect(back[0]).toEqual([...HISTORY_CSV_HEADER])
    expect(back[1]).toHaveLength(HISTORY_CSV_HEADER.length)
    expect(back[1][19]).toBe(note)
  })
})

describe('historyFilename', () => {
  it('names the export after the export date', () => {
    expect(historyFilename('2026-02-02')).toBe('opengym-history-2026-02-02.csv')
  })
})
