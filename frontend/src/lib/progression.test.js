import { describe, it, expect } from 'vitest'
import {
  readSession, sessionsFor, stallCount, nextPrescription, applyPrescription,
  policyFor, defaultIncrement, weightIncrement, POLICIES_FOR, DELOAD_AFTER
} from './progression.js'
import { entryExcluded } from './history.js'
import { EXDB } from './exercises.js'

// Named lookups rather than ids: the catalogue is auto-generated (scripts/oly-catalogue/), so a
// name is the stable key and an id is a detail. A name that disappears fails the file on its
// first line instead of quietly running the suite against the wrong exercise.
const byName = name => {
  const ex = EXDB.find(e => e.n === name)
  if (!ex) throw new Error(`the catalogue no longer has "${name}"`)
  return ex.id
}

// A plainly loaded lift that takes the small step: more weight is harder, 2.5 kg at a time.
const LIFT = byName('bench press')
// A lift the bigger step belongs to — see HEAVY_MUSCLES in progression.js.
const HEAVY = byName('back squat')
// Build a state whose history is a list of sessions given as [weight, ...repsPerSet].
// A rep count of null means "the set was never checked off".
const hist = (id, rows, target) => ({
  unit: 'kg',
  workouts: rows.map((row, i) => ({
    d: '2026-01-0' + (i + 1),
    entries: [{
      id,
      target: target || { sets: 3, reps: 5, weight: row[0] },
      sets: row.slice(1).map(r => (r === null ? { w: row[0], r: 0, done: false } : { w: row[0], r, done: true }))
    }]
  }))
})

describe('readSession', () => {
  const T = { sets: 3, reps: 5 }
  it('counts a session where every set made its reps as a hit', () => {
    const s = readSession({ id: LIFT, target: T, sets: [{ w: 60, r: 5, done: true }, { w: 60, r: 5, done: true }, { w: 60, r: 6, done: true }] })
    expect(s.ok).toBe(true)
    expect(s.weight).toBe(60)
  })

  it('counts short reps as a miss even when the set was checked off', () => {
    expect(readSession({ id: LIFT, target: T, sets: [{ w: 60, r: 5, done: true }, { w: 60, r: 5, done: true }, { w: 60, r: 3, done: true }] }).ok).toBe(false)
  })

  it('counts an unchecked set as a miss — it was not performed', () => {
    const s = readSession({ id: LIFT, target: T, sets: [{ w: 60, r: 5, done: true }, { w: 60, r: 5, done: true }, { w: 60, r: 0, done: false }] })
    expect(s.ok).toBe(false)
    expect(s.weight).toBe(60)       // the working weight is still known from the sets that counted
  })

  it('counts fewer sets than prescribed as a miss', () => {
    expect(readSession({ id: LIFT, target: T, sets: [{ w: 60, r: 5, done: true }, { w: 60, r: 5, done: true }] }).ok).toBe(false)
  })

  it('refuses to call a session a hit when nothing was prescribed', () => {
    expect(readSession({ id: LIFT, target: {}, sets: [{ w: 60, r: 5, done: true }] }).ok).toBe(false)
  })

  it('reads a timed session by the hold, not by reps', () => {
    const s = readSession({ id: LIFT, target: { sets: 2, sec: 45, mode: 'time' }, sets: [{ sec: 45, w: 0, done: true }, { sec: 50, w: 0, done: true }] })
    expect(s.mode).toBe('time')
    expect(s.ok).toBe(true)
    expect(s.best).toBe(50)
    expect(readSession({ id: LIFT, target: { sets: 2, sec: 45, mode: 'time' }, sets: [{ sec: 45, done: true }, { sec: 30, done: true }] }).ok).toBe(false)
  })
})

describe('stallCount', () => {
  it('counts consecutive misses back from the most recent session', () => {
    expect(stallCount([{ ok: true }, { ok: true }])).toBe(0)
    expect(stallCount([{ ok: true }, { ok: false }])).toBe(1)
    expect(stallCount([{ ok: false }, { ok: false }, { ok: false }])).toBe(3)
    expect(stallCount([{ ok: false }, { ok: true }, { ok: false }])).toBe(1)
    expect(stallCount([])).toBe(0)
  })

})

describe('policyFor', () => {
  it('keeps the app\'s long-standing behaviour as the default for reps work', () => {
    expect(policyFor({ id: LIFT }, null, 'reps')).toBe('linear')
  })
  it('leaves timed work alone unless asked', () => {
    expect(policyFor({ id: LIFT, mode: 'time' }, null, 'time')).toBe('off')
  })
  it('lets the exercise override the routine, and the routine override the default', () => {
    expect(policyFor({ id: LIFT, prog: 'off' }, { prog: 'linear' }, 'reps')).toBe('off')
    expect(policyFor({ id: LIFT }, { prog: 'off' }, 'reps')).toBe('off')
    expect(policyFor({ id: LIFT }, null, 'reps')).toBe('linear')
  })
  it('refuses a policy that makes no sense for the mode', () => {
    expect(policyFor({ id: LIFT, mode: 'time', prog: 'linear' }, null, 'time')).toBe('off')
    expect(POLICIES_FOR.time).toEqual(['off'])
  })
})

describe('defaultIncrement', () => {
  it('gives lower-body lifts the bigger jump', () => {
    expect(defaultIncrement(LIFT, 'kg')).toBe(2.5)
    expect(defaultIncrement(HEAVY, 'kg')).toBe(5)
  })
  // The rule reads the exercise's own muscles (HEAVY_MUSCLES), not the catalogue's movement
  // family. These are the lifts this fork cares about, so the table is pinned one by one.
  it('gives the classic heavy lifts the big step and the Olympic lifts the small one', () => {
    const expected = [
      ['back squat', 5], ['front squat', 5], ['split squat', 5],
      ['deadlift', 5], ['romanian deadlift (rdl)', 5], ['good morning', 5],
      ['bench press', 2.5], ['bent row', 2.5], ['pull-up', 2.5],
      ['snatch', 2.5], ['clean', 2.5], ['clean-jerk', 2.5],
      ['snatch balance', 2.5], ['snatch pull', 2.5]
    ]
    for (const [name, step] of expected) expect(defaultIncrement(byName(name), 'kg'), name).toBe(step)
  })
  it('falls back for an unknown exercise', () => {
    expect(defaultIncrement('nope', 'kg')).toBe(2.5)
  })
})

describe('weightIncrement', () => {
  it('uses a positive exercise override and otherwise the exercise/unit default', () => {
    expect(weightIncrement({ id: LIFT, inc: 1 }, 'kg')).toBe(1)
    expect(weightIncrement({ id: LIFT }, 'kg')).toBe(2.5)
    expect(weightIncrement({ id: HEAVY, inc: 0 }, 'kg')).toBe(5)
  })
})

describe('linear progression', () => {
  const cfg = { id: LIFT, sets: 3, reps: 5, weight: 60, prog: 'linear' }

  it('says nothing useful before there is any history', () => {
    const p = nextPrescription({ unit: 'kg', workouts: [] }, cfg)
    expect(p.kind).toBe('first')
    expect(p.weight).toBeUndefined()
  })

  it('adds the increment after a clean session', () => {
    const p = nextPrescription(hist(LIFT, [[60, 5, 5, 5]]), cfg)
    expect(p.kind).toBe('up')
    expect(p.weight).toBe(62.5)
  })

  it('repeats the weight after a miss instead of advancing', () => {
    const p = nextPrescription(hist(LIFT, [[60, 5, 5, 3]]), cfg)
    expect(p.kind).toBe('hold')
    expect(p.weight).toBe(60)
  })

  it('does not advance when the last set was left unchecked', () => {
    const p = nextPrescription(hist(LIFT, [[60, 5, 5, null]]), cfg)
    expect(p.kind).toBe('hold')
    expect(p.weight).toBe(60)
  })

  it('deloads after three misses in a row, onto a loadable weight', () => {
    const p = nextPrescription(hist(LIFT, [[60, 5, 5, 3], [60, 5, 4, 4], [60, 5, 5, 4]]), cfg)
    expect(p.kind).toBe('deload')
    expect(p.weight).toBe(55)             // 60 × 0.9 = 54 → nearest loadable 2.5 step
    expect(DELOAD_AFTER.linear).toBe(3)
  })

  it('holds a below-step load instead of deloading upward', () => {
    // 1 kg with a 2.5 kg step: a cut would snap straight back up to 2.5, so the load holds.
    const p = nextPrescription(hist(LIFT, [[1, 1, 1, 1], [1, 1, 1, 1], [1, 1, 1, 1]]), { ...cfg, weight: 1, inc: 2.5 })
    expect(p.kind).toBe('deload')
    expect(p.weight).toBe(1)
  })

  it('a good session in between clears the stall', () => {
    const p = nextPrescription(hist(LIFT, [[60, 5, 5, 3], [60, 5, 5, 5], [60, 5, 5, 3]]), cfg)
    expect(p.kind).toBe('hold')
  })

  it('a deload starts a new streak, so one miss at the new weight does not deload again', () => {
    // Three misses at 60 kg earn a deload.
    const stalled = [[60, 4, 4, 4], [60, 4, 4, 4], [60, 4, 4, 4]]
    const deload = nextPrescription(hist(LIFT, stalled), cfg)
    expect(deload.kind).toBe('deload')

    // A bad day at the lighter weight is the first miss of a new run, not the fourth of the old
    // one. A deload is owed a fresh set of attempts, not an immediate second cut.
    const next = nextPrescription(hist(LIFT, [...stalled, [deload.weight, 4, 4, 4]]), cfg)
    expect(next.kind).toBe('hold')
    expect(next.weight).toBe(deload.weight)
  })

  it('never deloads below one increment, however light the lift already is', () => {
    const p = nextPrescription(hist(LIFT, [[2.5, 1, 1, 1], [2.5, 1, 1, 1], [2.5, 1, 1, 1]]), cfg)
    expect(p.kind).toBe('deload')
    expect(p.weight).toBe(2.5)
  })

  it('always makes a deload actually lighter, even when rounding would not', () => {
    // 20 × 0.9 = 18 → nearest 2.5 step is 17.5, fine. 5 × 0.9 = 4.5 → nearest step is 5,
    // which is no deload at all, so it has to step down instead.
    const p = nextPrescription(hist(LIFT, [[5, 1, 1, 1], [5, 1, 1, 1], [5, 1, 1, 1]]), cfg)
    expect(p.weight).toBeLessThan(5)
  })

  it('uses the heavier step for a lower-body lift', () => {
    const p = nextPrescription(hist(HEAVY, [[100, 5, 5, 5]]), { id: HEAVY, sets: 3, reps: 5, prog: 'linear' })
    expect(p.weight).toBe(105)
  })

  it('honours a per-exercise increment override', () => {
    const p = nextPrescription(hist(LIFT, [[60, 5, 5, 5]]), { ...cfg, inc: 1 })
    expect(p.weight).toBe(61)
  })

})

describe('bodyweight exercises', () => {
  const cfg = { id: LIFT, sets: 3, reps: 10, weight: 0, prog: 'linear' }
  const bw = rows => hist(LIFT, rows, { sets: 3, reps: 10 })

  it('never invents a weight to deload to — there is nothing to take off a push-up', () => {
    const p = nextPrescription(bw([[0, 10, 10, 8], [0, 10, 10, 9], [0, 10, 10, 8]]), cfg)
    expect(p.kind).toBe('hold')
    expect(p.weight).toBe(0)
    expect(p.reps).toBe(10)
  })

  it('progresses in reps instead of load after a clean session', () => {
    const p = nextPrescription(bw([[0, 10, 10, 10]]), cfg)
    expect(p.kind).toBe('up')
    expect(p.weight).toBe(0)
    expect(p.reps).toBe(11)
  })

  /* A ceiling turns "+1 rep forever" into a plan — issue #33. */
  it('climbs to the ceiling one rep at a time', () => {
    const p = nextPrescription(bw([[0, 10, 10, 10]]), { ...cfg, repsMax: 15 })
    expect(p.kind).toBe('up')
    expect(p.reps).toBe(11)
    expect(p.sets).toBeUndefined()
  })

  it('leaves a belted set to the normal policies — there is a load to add now', () => {
    const belted = hist(LIFT, [[10, 10, 10, 10]], { sets: 3, reps: 10 })
    const p = nextPrescription(belted, { ...cfg, bodyweight: true, repsMax: 15 })
    expect(p.kind).toBe('up')
    expect(p.weight).toBeGreaterThan(10)
    expect(p.sets).toBeUndefined()
  })

  it('keeps climbing reps forever when no ceiling was set — the old behaviour', () => {
    const at30 = hist(LIFT, [[0, 30, 30, 30]], { sets: 3, reps: 30 })
    const p = nextPrescription(at30, cfg)
    expect(p.kind).toBe('up')
    expect(p.reps).toBe(31)
    expect(p.sets).toBeUndefined()
  })

  it('applies to linear progression', () => {
    const p = nextPrescription(bw([[0, 10, 10, 4], [0, 10, 10, 4], [0, 10, 10, 4]]), { ...cfg, prog: 'linear' })
    expect(p.weight).toBe(0)
    expect(p.kind).toBe('hold')
  })

  it('still adds load the moment the exercise is actually weighted', () => {
    const p = nextPrescription(hist(LIFT, [[10, 10, 10, 10]], { sets: 3, reps: 10 }), cfg)
    expect(p.kind).toBe('up')
    expect(p.weight).toBe(12.5)
  })
})

describe('policy "off"', () => {
  it('has no opinion at all', () => {
    const p = nextPrescription(hist(LIFT, [[60, 5, 5, 5]]), { id: LIFT, sets: 3, reps: 5, prog: 'off' })
    expect(p.kind).toBe('off')
    expect(p.weight).toBeUndefined()
  })
})

describe('sessionsFor', () => {
  it('skips workouts where the exercise was never actually logged', () => {
    const S = {
      unit: 'kg',
      workouts: [
        { d: '2026-01-01', entries: [{ id: LIFT, target: { sets: 1, reps: 5 }, sets: [{ w: 60, r: 5, done: true }] }] },
        { d: '2026-01-02', entries: [{ id: LIFT, target: { sets: 1, reps: 5 }, sets: [{ w: 60, r: 0, done: false }] }] },
        { d: '2026-01-03', entries: [{ id: 'other', target: {}, sets: [{ w: 20, r: 5, done: true }] }] }
      ]
    }
    expect(sessionsFor(S, LIFT).map(s => s.d)).toEqual(['2026-01-01'])
  })

  it('ignores marked deload workouts when it calculates the next regular target', () => {
    const target = { sets: 3, reps: 5, weight: 60 }
    const entry = (weight, reps) => ({
      id: LIFT,
      target: { ...target, weight },
      sets: reps.map(r => ({ w: weight, r, done: true }))
    })
    const S = {
      unit: 'kg',
      workouts: [
        { d: '2026-01-01', entries: [entry(60, [5, 5, 5])] },
        { d: '2026-01-08', excludeFromProgression: true, entries: [entry(30, [8, 8])] }
      ]
    }

    expect(sessionsFor(S, LIFT).map(s => s.d)).toEqual(['2026-01-01'])
    expect(nextPrescription(S, { id: LIFT, ...target, prog: 'linear' }).weight).toBe(62.5)
  })

  it('reads a legacy entry that has no target without crashing', () => {
    const S = { unit: 'kg', workouts: [{ d: '2026-01-01', entries: [{ id: LIFT, sets: [{ w: 60, r: 5, done: true }] }] }] }
    expect(sessionsFor(S, LIFT)).toHaveLength(1)
  })
})

// "Excluded from progression" is per-entry now (ENG-11): a rehab routine combined with real
// work must exclude only its own exercises, not the whole session.
describe('per-entry noProg (combine routines)', () => {
  const PRESS = EXDB.find(e => e.bp !== 'cardio' && e.id !== LIFT && !['upper legs', 'lower legs', 'back', 'hips', 'glutes'].includes(e.bp)).id
  const tgt = w => ({ sets: 3, reps: 5, weight: w })
  const entry = (id, w, reps, extra) => ({ id, target: tgt(w), sets: reps.map(r => ({ w, r, done: true })), ...extra })

  it('skips a noProg entry for that exercise only, in a mixed combined session', () => {
    const S = {
      unit: 'kg',
      workouts: [{
        d: '2026-02-01',
        routineIds: ['strength', 'rehab'],
        entries: [entry(LIFT, 60, [5, 5, 5]), entry(PRESS, 40, [5, 5, 5], { noProg: true })],
      }],
    }
    expect(sessionsFor(S, LIFT)).toHaveLength(1)
    expect(sessionsFor(S, PRESS)).toHaveLength(0)
  })

  it('still advances the non-excluded exercise of a mixed combined session', () => {
    const S = {
      unit: 'kg',
      workouts: [{
        d: '2026-02-01',
        entries: [entry(LIFT, 60, [5, 5, 5]), entry(PRESS, 40, [5, 5, 5], { noProg: true })],
      }],
    }
    expect(nextPrescription(S, { id: LIFT, ...tgt(60), prog: 'linear' }).weight).toBe(62.5)
  })

  it('a noProg gap never becomes the deload / stall baseline', () => {
    const S = {
      unit: 'kg',
      workouts: [
        { d: '2026-02-01', entries: [entry(LIFT, 60, [5, 5, 5])] },
        { d: '2026-02-03', entries: [entry(LIFT, 60, [5, 5, 5])] },
        { d: '2026-02-05', entries: [entry(LIFT, 30, [8, 8, 8], { noProg: true })] },
      ],
    }
    expect(sessionsFor(S, LIFT).map(s => s.d)).toEqual(['2026-02-01', '2026-02-03'])
    expect(nextPrescription(S, { id: LIFT, ...tgt(60), prog: 'linear' }).weight).toBe(62.5)
  })

  it('honours a legacy whole-workout excludeFromProgression flag (all entries skipped)', () => {
    const S = {
      unit: 'kg',
      workouts: [
        { d: '2026-02-01', entries: [entry(LIFT, 60, [5, 5, 5])] },
        { d: '2026-02-08', excludeFromProgression: true, entries: [entry(LIFT, 30, [8, 8])] },
      ],
    }
    expect(sessionsFor(S, LIFT).map(s => s.d)).toEqual(['2026-02-01'])
  })

  it('an exercise only ever logged noProg → sessionsFor [] → nextPrescription kind "first"', () => {
    const S = {
      unit: 'kg',
      workouts: [{ d: '2026-02-01', entries: [entry(LIFT, 30, [8, 8, 8], { noProg: true })] }],
    }
    expect(sessionsFor(S, LIFT)).toHaveLength(0)
    expect(nextPrescription(S, { id: LIFT, ...tgt(50), prog: 'linear' }).kind).toBe('first')
  })

  it('entryExcluded truth table', () => {
    expect(entryExcluded({}, {})).toBe(false)
    expect(entryExcluded({ excludeFromProgression: true }, {})).toBe(true)
    expect(entryExcluded({}, { noProg: true })).toBe(true)
    expect(entryExcluded({ excludeFromProgression: true }, { noProg: true })).toBe(true)
  })

  it('stallCount is not reset by a noProg gap at the same weight', () => {
    // three real misses at 60, with a noProg 60 session interleaved — still streaks to a deload
    const miss = d => ({ d, entries: [entry(LIFT, 60, [4, 4, 4])] })
    const S = {
      unit: 'kg',
      workouts: [
        { d: '2026-02-01', entries: [entry(LIFT, 60, [5, 5, 5])] },
        miss('2026-02-03'),
        { d: '2026-02-04', entries: [entry(LIFT, 60, [3, 3, 3], { noProg: true })] },
        miss('2026-02-05'),
        miss('2026-02-07'),
      ],
    }
    const sessions = sessionsFor(S, LIFT)
    expect(sessions.map(s => s.d)).toEqual(['2026-02-01', '2026-02-03', '2026-02-05', '2026-02-07'])
    expect(stallCount(sessions)).toBe(3)
    expect(nextPrescription(S, { id: LIFT, ...tgt(60), prog: 'linear' }).kind).toBe('deload')
  })
})

// Workouts only began storing their prescription in v1.2.2. Everything logged before that is
// targetless, and reading it as "missed" would tell every long-standing user to deload on
// their first session after updating — which is exactly what the demo history did.
describe('history logged before targets were recorded', () => {
  const legacy = rows => ({
    unit: 'kg',
    workouts: rows.map((row, i) => ({
      d: '2026-03-' + String(i + 1).padStart(2, '0'),
      entries: [{ id: LIFT, sets: row.slice(1).map(r => ({ w: row[0], r, done: true })) }]   // no target
    }))
  })
  const cfg = { id: LIFT, sets: 3, reps: 5, weight: 60, prog: 'linear' }

  it('judges a targetless session against the current plan instead of calling it a miss', () => {
    const p = nextPrescription(legacy([[60, 5, 5, 5]]), cfg)
    expect(p.kind).toBe('up')
    expect(p.weight).toBe(62.5)
  })

  it('does not manufacture a stall out of a long clean history', () => {
    const p = nextPrescription(legacy(Array.from({ length: 11 }, () => [60, 5, 5, 5])), cfg)
    expect(p.kind).toBe('up')
  })

  it('still spots a genuine miss in old data', () => {
    expect(nextPrescription(legacy([[60, 5, 5, 2]]), cfg).kind).toBe('hold')
  })

  it('matches the weight hint the app showed before this engine existed', () => {
    // Old rule: every set at or above the plan's reps, with a real weight → suggest a step up.
    expect(nextPrescription(legacy([[60, 5, 6, 5]]), cfg).weight).toBe(62.5)
    expect(nextPrescription(legacy([[60, 5, 4, 5]]), cfg).kind).toBe('hold')
  })
})

describe('applyPrescription', () => {
  const sets = [{ w: 60, r: 5, done: true }, { w: 60, r: 5, done: false }]

  it('rewrites only what the policy decided, and only unlogged sets', () => {
    const out = applyPrescription(sets, { kind: 'up', weight: 62.5 })
    expect(out[0]).toEqual({ w: 60, r: 5, done: true })
    expect(out[1]).toEqual({ w: 62.5, r: 5, done: false })
  })

  it('sets reps too when the policy has an opinion about them', () => {
    expect(applyPrescription(sets, { kind: 'up', weight: 42.5, reps: 8 })[1]).toEqual({ w: 42.5, r: 8, done: false })
  })

  it('touches nothing for "off" or a first session', () => {
    expect(applyPrescription(sets, { kind: 'off' })).toBe(sets)
    expect(applyPrescription(sets, { kind: 'first' })).toBe(sets)
    expect(applyPrescription(sets, null)).toBe(sets)
  })

  it('adjusts a timed set without inventing a weight', () => {
    const timed = [{ sec: 45, w: 0, done: false }]
    expect(applyPrescription(timed, { kind: 'up', sec: 50 })).toEqual([{ sec: 50, w: 0, done: false }])
  })

  it('never shrinks a session that has already logged sets', () => {
    expect(applyPrescription(sets, { kind: 'up', weight: 60, sets: 1 })).toHaveLength(sets.length)
  })
})


describe('warm-up rows in session reads (round 3)', () => {
  it('readSession ignores warm-up rows for reps and ok', () => {
    // An undone warm-up (r 0) must not poison `ok` forever; its lighter reps must not
    // drag the work-row read - the warm-up is prep, the session is the work rows.
    const s = readSession({ id: LIFT, target: { sets: 2, reps: 5, mode: 'reps' }, sets: [
      { w: 20, r: 8, done: true, warmup: true },
      { w: 60, r: 5, done: true },
      { w: 60, r: 6, done: true },
    ] })
    expect(s.reps).toEqual([5, 6])
    expect(s.ok).toBe(true)
  })

  it('readSession keeps an undone warm-up out of held/ok in time mode', () => {
    const s = readSession({ id: LIFT, target: { sets: 2, sec: 45, mode: 'time' }, sets: [
      { sec: 45, done: true, warmup: true },
      { sec: 45, done: true },
      { sec: 30, done: true },
    ] })
    expect(s.held).toEqual([45, 30])
    expect(s.ok).toBe(false) // the 30s work row is the miss, not the warm-up
  })

  it('uses phase as authoritative and falls back to the legacy warm-up flag', () => {
    const s = readSession({ id: LIFT, target: { sets: 1, reps: 5 }, sets: [
      { phase: 'warmup', w: 120, r: 20, done: true },
      { phase: 'work', warmup: true, w: 60, r: 5, done: true },
    ] })
    expect(s.weight).toBe(60)
    expect(s.reps).toEqual([5])
    expect(s.ok).toBe(true)
  })
})

describe('applyPrescription never touches warm-up rows (round 3)', () => {
  it('leaves a done warm-up exactly as logged', () => {
    const sets = [
      { w: 20, r: 8, done: true, warmup: true },
      { w: 60, r: 5, done: true },
      { w: 60, r: 5, done: false },
    ]
    const out = applyPrescription(sets, { kind: 'up', weight: 62.5, reps: 5 })
    expect(out[0]).toEqual({ w: 20, r: 8, done: true, warmup: true })
    expect(out[1]).toEqual({ w: 60, r: 5, done: true })
    expect(out[2]).toEqual({ w: 62.5, r: 5, done: false })
  })

  it('an all-warm-up entry terminates and stays untouched', () => {
    const sets = [
      { w: 20, r: 8, done: true, warmup: true },
      { w: 25, r: 6, done: true, warmup: true },
    ]
    const out = applyPrescription(sets, { kind: 'up', weight: 62.5, reps: 5, sets: 4 })
    expect(out).toEqual(sets) // no work row to seed growth from - nothing grows, no loop
  })
})

describe('drop-sets and rest-pause sets in progression', () => {
  it('readSession judges a drop-set row on its own main weight/reps, ignoring the drops', () => {
    const withDrops = readSession({ id: LIFT, target: { sets: 1, reps: 5 }, sets: [
      { type: 'dropset', w: 60, r: 5, done: true, drops: [{ w: 40, r: 8 }, { w: 20, r: 10 }] },
    ] })
    const plain = readSession({ id: LIFT, target: { sets: 1, reps: 5 }, sets: [{ w: 60, r: 5, done: true }] })
    expect(withDrops).toEqual(plain)
  })

  it('readSession judges a rest-pause row on its activation weight/reps, ignoring the bursts', () => {
    const withBursts = readSession({ id: LIFT, target: { sets: 1, reps: 8 }, sets: [
      { type: 'restpause', w: 60, r: 8, done: true, clusters: [{ r: 4, restSec: 15 }, { r: 3, restSec: 15 }] },
    ] })
    const plain = readSession({ id: LIFT, target: { sets: 1, reps: 8 }, sets: [{ w: 60, r: 8, done: true }] })
    expect(withBursts).toEqual(plain)
  })

  it('applyPrescription still rewrites a drop-set/rest-pause row\'s own weight, leaving its drops/clusters untouched', () => {
    const sets = [{ type: 'dropset', w: 60, r: 5, done: false, drops: [{ w: 40, r: 8 }] }]
    const out = applyPrescription(sets, { kind: 'up', weight: 62.5 })
    expect(out[0]).toEqual({ type: 'dropset', w: 62.5, r: 5, done: false, drops: [{ w: 40, r: 8 }] })
  })

})

describe('a weight off the increment grid keeps its offset when it goes up (issue #175)', () => {
  it('adds the step instead of snapping the sum to the grid', () => {
    // A sled logged as its own 167 lb plus plates: 397 with a 10 lb step goes to 407, not 410.
    const lin = { id: LIFT, sets: 3, reps: 5, weight: 397, prog: 'linear', inc: 10 }
    const p = nextPrescription(hist(LIFT, [[397, 5, 5, 5]], { sets: 3, reps: 5, weight: 397 }), lin)
    expect(p.kind).toBe('up')
    expect(p.weight).toBe(407)
  })
  it('still snaps from a weight that sits on the grid', () => {
    const p = nextPrescription(hist(LIFT, [[60, 5, 5, 5]]), { id: LIFT, sets: 3, reps: 5, weight: 60, prog: 'linear', inc: 2.5 })
    expect(p.weight).toBe(62.5)
  })
})
