import { describe, expect, it } from 'vitest'
import { buildPlanBundle, mergePlan, parsePlan } from './plan-share.js'

// There was no test file for plan sharing at all, which is how a whole prescription field
// went missing without anyone noticing.
const stateWith = ex => ({
  weeks: [{
    id: 'w1', startIso: '2026-03-02', name: '',
    days: [{ dow: 1, name: 'Push', ex: [{ id: 'wl806', sets: 3, reps: 5, weight: 100, ...ex }] }],
  }],
  customEx: [],
})
const roundTrip = ex => parsePlan(JSON.stringify(buildPlanBundle(stateWith(ex), 'Plan'))).weeks[0].days[0].ex[0]

describe('plan-share units', () => {
  it('exports a unit and converts every load prescription at the import boundary', () => {
    const source = {
      unit: 'kg', customEx: [],
      weeks: [{
        id: 'w1', startIso: '2026-03-02', name: '',
        days: [{ dow: 1, name: 'Strength', ex: [
          { id: 'wl806', mode: 'reps', sets: 3, reps: 5, weight: 60, inc: 2.5 },
          { id: 'wl77', mode: 'time', sets: 1, sec: 30, weight: 20, inc: 5 },
        ] }],
      }],
    }
    const bundle = buildPlanBundle(source, 'Strength')
    expect(bundle.unit).toBe('kg')
    expect(bundle.opengym_plan).toBe(2)
    const imported = parsePlan(bundle, 'lb')
    expect(imported.unit).toBe('lb')
    expect(imported.weeks[0].days[0].ex[0]).toMatchObject({ weight: 132.5, inc: 5.5 })
    expect(imported.weeks[0].days[0].ex[1]).toMatchObject({ weight: 44, inc: 5 })
  })

  it('converts pounds back to kilograms and merges the converted prescription', () => {
    const source = {
      unit: 'lb', customEx: [],
      weeks: [{ id: 'w1', startIso: '2026-03-02', name: '', days: [{ dow: 1, name: 'Strength', ex: [{ id: 'wl806', sets: 3, reps: 5, weight: 135, inc: 10 }] }] }],
    }
    const bundle = buildPlanBundle(source, 'Strength')
    const target = { unit: 'kg', weeks: [], customEx: [] }
    mergePlan(target, bundle)
    expect(target.weeks[0].days[0].ex[0]).toMatchObject({ weight: 61.25, inc: 4.5 })
  })

  it('keeps valid legacy bundles without a unit in their destination-unit semantics', () => {
    const legacy = {
      opengym_plan: 1, name: 'Legacy', summary: 'old export',
      week: { 1: 'r' }, customEx: [],
      routines: [{ id: 'r', name: 'Strength', ex: [{ id: 'wl806', sets: 3, reps: 5, weight: 60, inc: 2.5 }] }],
    }
    const parsed = parsePlan(legacy, 'lb')
    expect(parsed.unit).toBe('lb')
    expect(parsed.weeks[0].days[0].ex[0]).toMatchObject({ weight: 60, inc: 2.5 })
  })

  it('accepts legacy root omissions and the older weightUnit marker', () => {
    const legacy = {
      opengym_plan: 1, weightUnit: 'lbs', legacyNote: 'kept as metadata',
      routines: [{ id: 'r', name: 'Strength', ex: [{ id: 'wl806', sets: 3, reps: 5, weight: 135, inc: 10 }] }],
    }
    const parsed = parsePlan(legacy, 'kg')
    expect(parsed.weeks[0].days[0].ex[0]).toMatchObject({ weight: 61.25, inc: 4.5 })
    expect(parsed.customEx).toEqual([])
  })
})

// Format 1 (`routines` + `week`) is still out there — in saved files and in the Coach's own
// proposal bundles — so it has to land as a week like everything else.
describe('format-1 files still import', () => {
  const v1 = {
    opengym_plan: 1, name: 'Legacy plan', customEx: [],
    week: { 1: ['a'], 3: ['b'] },
    routines: [
      { id: 'a', name: 'Push', ex: [{ id: 'wl806', sets: 3, reps: 5 }] },
      { id: 'b', name: 'Pull', ex: [{ id: 'wl97', sets: 3, reps: 8 }] },
    ],
  }

  it('turns the schedule into one week with a day per scheduled routine', () => {
    const parsed = parsePlan(v1, 'kg')
    expect(parsed.weekCount).toBe(1)
    expect(parsed.dayCount).toBe(2)
    expect(parsed.weeks[0].days.map(d => [d.dow, d.name])).toEqual([[1, 'Push'], [3, 'Pull']])
    expect(parsed.exerciseCount).toBe(2)
  })

  it('keeps a routine the schedule leaves out rather than dropping it', () => {
    const parsed = parsePlan({ ...v1, week: { 1: ['a'] } }, 'kg')
    expect(parsed.weeks[0].days.map(d => d.name)).toEqual(['Push', 'Pull'])
  })

  it('falls back to the Mon/Wed/Fri-first order when nothing is scheduled', () => {
    const parsed = parsePlan({ ...v1, week: {} }, 'kg')
    expect(parsed.weeks[0].days.map(d => d.dow)).toEqual([1, 3])
  })

  it('a v2 file round-trips: weeks, days and their dates come back unchanged', () => {
    const source = stateWith({})
    source.weeks[0].startIso = '2026-04-06'
    source.weeks[0].name = 'Week A'
    source.weeks[0].days.push({ dow: 3, name: 'Pull', ex: [{ id: 'wl97', sets: 3, reps: 8 }] })
    const parsed = parsePlan(buildPlanBundle(source, 'Plan'), 'kg')
    expect(parsed.weeks[0].startIso).toBe('2026-04-06')
    expect(parsed.weeks[0].name).toBe('Week A')
    expect(parsed.weeks[0].days.map(d => d.dow)).toEqual([1, 3])
    const target = { weeks: [], customEx: [] }
    mergePlan(target, parsed)
    expect(target.weeks[0].startIso).toBe('2026-04-06')
    expect(target.weeks[0].name).toBe('Week A')
    expect(target.weeks[0].days.map(d => [d.dow, d.ex.length])).toEqual([[1, 1], [3, 1]])
    expect(target.weeks[0].id).not.toBe('w1')    // fresh id, like a plan import
  })
})

describe('what survives a shared plan', () => {
  it('carries a drop-set prescription', () => {
    expect(roundTrip({ intensifier: { type: 'dropset', count: 2, pct: 20 } }).intensifier)
      .toEqual({ type: 'dropset', count: 2, pct: 20 })
  })

  it('carries a rest-pause prescription', () => {
    expect(roundTrip({ intensifier: { type: 'restpause', totalReps: 12, restSec: 15 } }).intensifier)
      .toEqual({ type: 'restpause', totalReps: 12, restSec: 15 })
  })

  it('carries planned warm-ups', () => {
    expect(roundTrip({ warmupSets: 3 }).warmupSets).toBe(3)
  })

  it('carries a non-default Epley deload factor and omits the default', () => {
    expect(roundTrip({ deloadFactor: 0.8 }).deloadFactor).toBe(0.8)
    expect('deloadFactor' in roundTrip({ deloadFactor: 0.9 })).toBe(false)
  })

  it('carries progression exclusion on a day through export and merge', () => {
    const source = stateWith({})
    source.weeks[0].days[0].excludeFromProgression = true
    const bundle = parsePlan(buildPlanBundle(source, 'Plan'))
    const target = { weeks: [], customEx: [] }

    expect(bundle.weeks[0].days[0].excludeFromProgression).toBe(true)
    mergePlan(target, bundle)
    expect(target.weeks[0].days[0].excludeFromProgression).toBe(true)
  })

  // Issue #10: the rest an exercise prescribes is part of the prescription. A shared 5x5 whose
  // rests arrive as the recipient's 60 s default is a different session than the one written.
  it('carries a per-exercise rest', () => {
    expect(roundTrip({ restSec: 180 }).restSec).toBe(180)
  })

  // The ramp's own rest is the same kind of prescription: a shared plan whose warm-up sets
  // arrive resting the full working rest is not the plan that was written.
  it('carries a per-exercise warm-up rest, and leaves it out when unset', () => {
    expect(roundTrip({ restSec: 150, warmupRestSec: 45 }).warmupRestSec).toBe(45)
    expect('warmupRestSec' in roundTrip({})).toBe(false)
    expect('warmupRestSec' in roundTrip({ warmupRestSec: 0 })).toBe(false)
  })

  // The absence has to survive too: writing a 0 would pin the recipient's timer to "off"
  // instead of letting the exercise keep inheriting whatever their own default is.
  it('leaves an exercise that set no rest free of the field', () => {
    expect('restSec' in roundTrip({})).toBe(false)
    expect('restSec' in roundTrip({ restSec: 0 })).toBe(false)
  })

  it('carries the rest onto the day mergePlan adds', () => {
    const bundle = parsePlan(JSON.stringify(buildPlanBundle(stateWith({ restSec: 180 }), 'Plan')))
    const s = { weeks: [], customEx: [] }
    mergePlan(s, bundle)
    expect(s.weeks[0].days[0].ex[0].restSec).toBe(180)
  })

  it('drops an intensifier it does not recognise rather than passing it on', () => {
    expect(roundTrip({ intensifier: { type: 'nonsense', count: 3 } }).intensifier).toBeUndefined()
  })

  it('clamps a hand-edited warm-up count instead of showing it verbatim', () => {
    const bundle = { opengym_plan: 1, name: 'x', week: { 1: 'r' }, customEx: [], routines: [{ id: 'r', name: 'R', ex: [{ id: 'wl806', sets: 3, reps: 5, warmupSets: 999 }] }] }
    expect(parsePlan(bundle).weeks[0].days[0].ex[0].warmupSets).toBe(5)
  })

  // A plan file is someone else's data: a rest that arrives as a string would reach the timer's
  // arithmetic as one, and a negative or garbage one has no meaning to keep.
  it('normalises a hand-edited rest to a positive whole number or drops it', () => {
    const withRest = restSec => ({ opengym_plan: 1, name: 'x', week: { 1: 'r' }, customEx: [], routines: [{ id: 'r', name: 'R', ex: [{ id: 'wl806', sets: 3, reps: 5, restSec }] }] })
    expect(parsePlan(withRest('120')).weeks[0].days[0].ex[0].restSec).toBe(120)
    expect(parsePlan(withRest(90.6)).weeks[0].days[0].ex[0].restSec).toBe(91)
    expect('restSec' in parsePlan(withRest(-30)).weeks[0].days[0].ex[0]).toBe(false)
    expect('restSec' in parsePlan(withRest('abc')).weeks[0].days[0].ex[0]).toBe(false)
  })

  // The floors are the config sheet's own (count >= 1, pct >= 5); a value that is present but
  // out of range is pulled up to the floor, while a missing one falls back to the default.
  it('clamps out-of-range intensifier numbers to the floors the app enforces', () => {
    const bundle = { opengym_plan: 1, name: 'x', week: { 1: 'r' }, customEx: [], routines: [{ id: 'r', name: 'R', ex: [{ id: 'wl806', sets: 3, reps: 5, intensifier: { type: 'dropset', count: 0, pct: -5 } }] }] }
    expect(parsePlan(bundle).weeks[0].days[0].ex[0].intensifier).toEqual({ type: 'dropset', count: 1, pct: 5 })
  })

  it('falls back to the default drop percentage when the file omits it', () => {
    const bundle = { opengym_plan: 1, name: 'x', week: { 1: 'r' }, customEx: [], routines: [{ id: 'r', name: 'R', ex: [{ id: 'wl806', sets: 3, reps: 5, intensifier: { type: 'dropset' } }] }] }
    expect(parsePlan(bundle).weeks[0].days[0].ex[0].intensifier).toEqual({ type: 'dropset', count: 1, pct: 20 })
  })
})

// ---- a weekday holds a routine-id list in format 1 (ENG-9 §5) ----
describe('format-1 week schedule as a routine-id list', () => {
  const twoRoutines = {
    opengym_plan: 1, customEx: [],
    routines: [
      { id: 'a', name: 'A', ex: [{ id: 'wl806', sets: 3, reps: 5 }] },
      { id: 'b', name: 'B', ex: [{ id: 'wl97', sets: 3, reps: 8 }] },
    ],
  }

  it('build → parse → merge round-trips an array week as days on their weekdays', () => {
    const parsed = parsePlan({ ...twoRoutines, week: { 1: ['a', 'b'], 3: ['a'] } })
    expect(parsed.dayCount).toBe(3)

    const target = { weeks: [], customEx: [] }
    mergePlan(target, parsed)
    expect(target.weeks[0].days.map(d => d.dow)).toEqual([1, 1, 3])
    expect(target.weeks[0].days.map(d => d.name)).toEqual(['A', 'B', 'A'])
  })

  it('tolerates a legacy scalar bundle value', () => {
    const parsed = parsePlan({ ...twoRoutines, week: { 1: 'a' } })
    expect(parsed.dayCount).toBe(2)
    const target = { weeks: [], customEx: [] }
    mergePlan(target, parsed)
    expect(target.weeks[0].days[0].dow).toBe(1)
    expect(target.weeks[0].days[0].name).toBe('A')
  })

  it('never schedules a routine id that is not in the file', () => {
    const parsed = parsePlan({ ...twoRoutines, week: { 1: ['a', 'gone'], 2: ['gone'] } })
    expect(parsed.weeks[0].days.map(d => d.name)).not.toContain('gone')
    expect(parsed.weeks[0].days.find(d => d.dow === 1).name).toBe('A')
  })

  it('dayCount counts the days the schedule actually names', () => {
    expect(parsePlan({ ...twoRoutines, week: { 1: ['a'], 2: [], 4: ['b'] } }).dayCount).toBe(2)
  })
})

// ---- custom exercises travel whole (QA C7) ----
// The bundle used to carry only {id, n, bp} and mergePlan stored exactly that: the recipient's copy
// showed an empty equipment tag, no muscle credit, and — without `custom: true` — no Edit or Delete,
// so a wrong import could only be fixed by hand-editing localStorage.
describe('custom exercises in a shared plan', () => {
  // The shape CustomExForm writes, in the map's order.
  const landmine = {
    id: 'c1', n: 'QA Landmine Row', bp: 'back', desc: 'Bar in the corner', tg: 'upper-back', sm: ['biceps'],
    muscleGroups: ['upper-back', 'biceps'], primaries: ['upper-back'], secondaries: ['biceps'], eq: 'barbell', custom: true,
  }
  const source = {
    customEx: [landmine],
    weeks: [{ id: 'w1', startIso: '2026-03-02', name: '', days: [{ dow: 1, name: 'Back day', ex: [{ id: 'c1', sets: 3, reps: 8, weight: 40 }] }] }],
  }

  it('exports equipment, muscles and description with the custom exercise', () => {
    expect(buildPlanBundle(source, 'Plan').customEx[0]).toEqual({
      id: 'c1', n: 'QA Landmine Row', bp: 'back', desc: 'Bar in the corner', eq: 'barbell', tg: 'upper-back',
      primaries: ['upper-back'], secondaries: ['biceps'], muscleGroups: ['upper-back', 'biceps'],
    })
  })

  it('stores the imported custom exercise the way the form would have created it', () => {
    const parsed = parsePlan(JSON.stringify(buildPlanBundle(source, 'Plan')))
    const target = { weeks: [], customEx: [] }
    mergePlan(target, parsed)
    const stored = target.customEx[0]
    expect(stored).toMatchObject({ ...landmine, id: stored.id })
    expect(stored.id).not.toBe('c1')
    expect(target.weeks[0].days[0].ex[0].id).toBe(stored.id)
  })

  // A file written before the metadata travelled has only a name and a body part; it still imports,
  // and the recipient can now add the equipment and muscles themselves.
  it('keeps importing an old name-plus-body-part file, editable at the other end', () => {
    const legacy = { opengym_plan: 1, week: { 1: 'r' }, routines: [{ id: 'r', name: 'R', ex: [{ id: 'x', sets: 3, reps: 5 }] }], customEx: [{ id: 'x', n: 'Old one', bp: 'legs' }] }
    const target = { weeks: [], customEx: [] }
    mergePlan(target, parsePlan(legacy))
    expect(target.customEx[0]).toEqual({ id: target.customEx[0].id, n: 'Old one', bp: 'legs', custom: true })
    expect(target.weeks[0].days[0].ex[0].id).toBe(target.customEx[0].id)
  })

  // A plan file is someone else's data: only muscles the map can draw are kept, and a muscle listed
  // as both primary and secondary counts once, as the form itself enforces.
  it('drops muscles it cannot draw and a secondary that repeats a primary', () => {
    const bundle = { opengym_plan: 1, routines: [], customEx: [{ id: 'x', n: 'Odd', bp: 'back', eq: 'barbell', primaries: ['upper-back', 'wings'], secondaries: ['upper-back', 'biceps', 7] }] }
    const target = { weeks: [], customEx: [] }
    mergePlan(target, parsePlan(bundle))
    expect(target.customEx[0]).toMatchObject({ primaries: ['upper-back'], secondaries: ['biceps'], muscleGroups: ['upper-back', 'biceps'], tg: 'upper-back', sm: ['biceps'] })
  })

  it('still reuses a custom the recipient already has under the same name and body part', () => {
    const parsed = parsePlan(JSON.stringify(buildPlanBundle(source, 'Plan')))
    const mine = { id: 'mine', n: 'qa landmine row', bp: 'back', eq: 'landmine', custom: true }
    const target = { weeks: [], customEx: [mine] }
    mergePlan(target, parsed)
    expect(target.customEx).toEqual([mine])
    expect(target.weeks[0].days[0].ex[0].id).toBe('mine')
  })
})
