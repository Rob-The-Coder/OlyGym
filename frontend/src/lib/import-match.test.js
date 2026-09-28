import { describe, it, expect } from 'vitest'
import { matchExercise, matchHevyTitle, parseWorkoutCSV, detectSource } from './import-csv.js'
import { EXIDX } from './exercises.js'

// The names other apps actually export, and the catalogue entry each has to land on. Everything
// here was reported as arriving in the app as a *custom* exercise (issue #74): the alias table is
// the intended place to fix that, so these pin it. Only names the OlyGym catalogue can actually
// reach are listed — the ones it has no counterpart for are pinned in the next describe.
const MAPS = {
  'Cable Core Pallof Press': 'wl526',
  'Face Pull': 'wl767',
  'Single Arm Lateral Raise (Cable)': 'wl811',
  'Bicep Curl (Dumbbell)': 'wl821',
  'Bicep Curl (Cable)': 'wl809',
  'Rear Delt Reverse Fly (Dumbbell)': 'wl870',
  'Chest Fly (Dumbbell)': 'wl866',
  'Lying Leg Curl': 'wl559',
  'Seated Leg Curl': 'wl559',
  'Flat Barbell Bench Press': 'wl806',
  'Bent-Over Row': 'wl171',
  'Romanian Deadlift': 'wl101',
  'Skull Crusher': 'wl903',
  'Triceps Pushdown': 'wl911',
  'Back Extension (Weighted Hyperextension)': 'wl425',
}

// The catalogue genuinely has no counterpart for these: an Olympic weightlifting library carries no
// machines, no cardio category and no calf work. They have to become custom exercises rather than
// being filed under the nearest gym lift — no equipment qualifier is worth dropping to force a
// match, because the qualifier is the only thing distinguishing them from the barbell lift.
const CUSTOM_BY_DESIGN = [
  'Treadmill', 'Cycling', 'Elliptical', 'Stationary Bike', 'Battle Ropes', 'Goblet Squat',
  'Hack Squat (Machine)', 'Chest Fly (Machine)', 'Butterfly (Pec Deck)',
  'Incline Chest Fly (Dumbbell)', 'Seated Cable Row - Bar Grip', 'Overhead Press', 'Military Press',
  'Shoulder Press', 'Leg Press', 'Calf Raise', 'Seated Calf Raise',
  'Cable Crossover', 'Decline Bench Press', 'Close Grip Bench Press', 'Sumo Deadlift',
]

describe('foreign exercise names resolve to the catalogue', () => {
  for (const [name, id] of Object.entries(MAPS)) {
    it(`maps "${name}" to ${id}`, () => {
      expect(matchExercise(name)).toBe(id)
      expect(EXIDX[id]).toBeTruthy()   // an alias pointing at a dropped id is worse than no alias
    })
  }

  // Equipment qualifiers must still beat the bare alias, or an import files years of dumbbell
  // work under the barbell lift.
  it('keeps the qualified variants distinct', () => {
    expect(matchExercise('Bicep Curl (Dumbbell)')).toBe('wl821')
    expect(matchExercise('Bicep Curl (Cable)')).toBe('wl809')
    expect(matchExercise('Bicep Curl (Barbell)')).toBe('wl838')
  })

  // The word-bag is order-insensitive, which is what lets one alias cover the two ways
  // exporters write the same movement.
  it('ignores word order and case', () => {
    expect(matchExercise('Squat (Barbell)')).toBe(matchExercise('Barbell Squat'))
  })

  // Regression guard for the aliases above: the common lifts must not have moved.
  it('leaves the established aliases alone', () => {
    expect(matchExercise('Bench Press')).toBe('wl806')
    expect(matchExercise('Squat')).toBe('wl77')
    expect(matchExercise('Barbell Row')).toBe('wl171')
    expect(matchExercise('Deadlift')).toBe('wl604')
  })

  // SYN rewrote 'machine' -> 'lever' before it ever reached 'smith machine' -> 'smith', and every
  // Smith name arrived as "smith lever …". The catalogue has no Smith or lever entry at all now, so
  // the guard is the same idea from the other side: the qualifier must survive, and the name must
  // stay a custom rather than quietly becoming the barbell lift.
  it('never drops an equipment qualifier to force a match', () => {
    expect(matchExercise('Bench Press (Smith Machine)')).toBeNull()
    expect(matchExercise('Overhead Press (Smith Machine)')).toBeNull()
    expect(matchExercise('Smith Machine Bench Press')).toBeNull()
    expect(matchExercise('Seated Fly (Machine)')).toBeNull()
  })

  it('leaves names the catalogue cannot reach as custom exercises', () => {
    for (const name of CUSTOM_BY_DESIGN) expect(matchExercise(name), name).toBeNull()
  })

  it('still refuses to guess', () => {
    expect(matchExercise('Some Movement I Invented')).toBe(null)
    expect(matchExercise('')).toBe(null)
  })
})

// Hevy exports no category column, so every invented exercise took the same default and a third of
// an imported history was attributed to one family in the muscle map. With no category to read, the
// family comes off the name instead.
describe('invented exercises get a movement family from their name', () => {
  const HEAD = 'title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index,set_type,weight_kg,reps,distance_km,duration_seconds,rpe'
  const row = name => `W,"09 Oct 2025, 19:22","09 Oct 2025, 20:22",,"${name}",,,0,normal,40,10,,,`
  const bpOf = name => {
    const r = parseWorkoutCSV([HEAD, row(name)].join('\n'), { unit: 'kg' })
    return r.customEx[0] && r.customEx[0].bp
  }

  it('reads the family off names the catalogue does not have', () => {
    expect(bpOf('Kirk Shrug Machine Thing')).toBe('Accessory - Upper Body')
    expect(bpOf('Bicep Curl Contraption')).toBe('Accessory - Upper Body')
    expect(bpOf('Standing Calf Thing')).toBe('Accessory - Lower/Whole Body')
    expect(bpOf('Plank Thing')).toBe('Trunk (Ab & Back)')
  })

  it('reads the cardio names the fork no longer carries as cardio', () => {
    // The catalogue has no cardio entry, and 'cardio' is the only route to the cardio logging mode
    // for an exercise we invent — a burpee must still ask for time and speed, not weight and reps.
    expect(bpOf('Some Burpee Contraption')).toBe('cardio')
  })

  it('leaves a genuine leg movement on the legs', () => {
    expect(bpOf('Bulgarian Split Squat Machine v9')).toBe('General Exercises')
  })

  it('still prefers an explicit category column when the file has one', () => {
    const head = 'Date,Exercise,Category,Weight,Reps'
    const r = parseWorkoutCSV([head, '2025-10-09,Some Invented Lift,Shoulders,40,10'].join('\n'), { unit: 'kg' })
    expect(r.customEx[0].bp).toBe('Accessory - Upper Body')
  })
})

describe('Hevy CSV uses the generated title map', () => {
  const HEVY_CSV = [
    'title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index,set_type,weight_kg,reps,distance_km,duration_seconds,rpe',
    'Push,2026-08-01 10:00:00,2026-08-01 11:00:00,,Bulgarian Split Squat (Dumbbell),,,0,normal,40,10,0,,',
    'Push,2026-08-01 10:00:00,2026-08-01 11:00:00,,Hammer Curl (Dumbbell),,,0,warmup,12,15,0,,',
    'Push,2026-08-01 10:00:00,2026-08-01 11:00:00,,Hammer Curl (Dumbbell),,,1,normal,18,12,0,,8',
    'Push,2026-08-01 10:00:00,2026-08-01 11:00:00,,Reverse Lunge (Dumbbell),,,0,normal,20,10,0,,',
  ].join('\n')

  it('detects the Hevy export dialect', () => {
    expect(detectSource(HEVY_CSV.split('\n')[0].split(','))).toBe('Hevy')
  })

  it('resolves English Hevy titles through HEVY_TITLE_MAP before the word-bag', () => {
    expect(matchHevyTitle('Bulgarian Split Squat (Dumbbell)')).toBe('wl173')
    expect(matchHevyTitle('Hammer Curl (Dumbbell)')).toBe('wl841')
    expect(matchHevyTitle('Reverse Lunge (Dumbbell)')).toBe('wl735')
    // The word-bag alone misses all three — the title map is what makes a Hevy CSV work.
    expect(matchExercise('Bulgarian Split Squat (Dumbbell)')).toBeNull()
    expect(matchExercise('Hammer Curl (Dumbbell)')).toBeNull()
    expect(matchExercise('Reverse Lunge (Dumbbell)')).toBeNull()
  })

  it('imports a Hevy CSV onto catalogue ids, not customs', () => {
    const parsed = parseWorkoutCSV(HEVY_CSV, { unit: 'kg' })
    expect(parsed.source).toBe('Hevy')
    expect(parsed.created).toBe(0)
    const ids = parsed.workouts[0].entries.map(e => e.id).sort()
    expect(ids).toEqual(['wl173', 'wl735', 'wl841'].sort())
    const curl = parsed.workouts[0].entries.find(e => e.id === 'wl841')
    expect(curl.sets[0]).toMatchObject({ w: 12, r: 15, phase: 'warmup' })
    expect(curl.sets[1]).toMatchObject({ w: 18, r: 12, rpe: 8 })
  })
})
