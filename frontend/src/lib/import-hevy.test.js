import { describe, expect, it, vi, afterEach } from 'vitest'
import {
  matchHevyTemplate, buildHevyExerciseMap, parseHevyWorkouts, parseHevyBodyweight,
  parseHevyRoutines, mergeHevyRoutines, localWhen, importHevyData, HevyApiError, HEVY_ID_MAP,
} from './import-hevy.js'
import { EXIDX } from './exercises.js'
import { mergeImport } from './import-csv.js'

// Fixtures come out of the map itself rather than being typed in: the map is regenerated against
// the catalogue (scripts/build-hevy-id-map.mjs), and a template id written here by hand would go
// stale exactly the way the whole map did when the OlyGym catalogue replaced the old dataset.
const hidFor = name => {
  const hit = Object.entries(HEVY_ID_MAP).find(([, id]) => EXIDX[id]?.n === name)
  if (!hit) throw new Error(`HEVY_ID_MAP no longer maps "${name}" — pick another fixture`)
  return hit[0]
}
const SPLIT_HID = hidFor('bulgarian split squat')
const CURL_HID = hidFor('hammer curl')
const LUNGE_HID = hidFor('reverse lunge')
const SPLIT_ID = HEVY_ID_MAP[SPLIT_HID]
const CURL_ID = HEVY_ID_MAP[CURL_HID]
const LUNGE_ID = HEVY_ID_MAP[LUNGE_HID]

// Template ids Hevy has and the catalogue cannot reach: a distance template (no cardio entry in an
// Olympic catalogue) and a template the user invented in Hevy. Both become custom exercises.
const DIST_HID = 'DEAD0001'
const CUSTOM_HID = 'DEADBEEF'

const TEMPLATES = [
  {
    id: SPLIT_HID, title: 'Bulgarian Split Squat (Dumbbell)', type: 'weight_reps',
    primary_muscle_group: 'quadriceps', secondary_muscle_groups: [], equipment: 'dumbbell', is_custom: false,
  },
  {
    id: CURL_HID, title: 'Hammer Curl (Dumbbell)', type: 'weight_reps',
    primary_muscle_group: 'biceps', secondary_muscle_groups: [], equipment: 'dumbbell', is_custom: false,
  },
  {
    id: LUNGE_HID, title: 'Reverse Lunge (Dumbbell)', type: 'weight_reps',
    primary_muscle_group: 'quadriceps', secondary_muscle_groups: [], equipment: 'dumbbell', is_custom: false,
  },
  {
    id: DIST_HID, title: 'Treadmill Run', type: 'distance_duration',
    primary_muscle_group: 'cardio', secondary_muscle_groups: [], equipment: 'machine', is_custom: false,
  },
  {
    id: CUSTOM_HID, title: 'My Invented Landmine Row', type: 'weight_reps',
    primary_muscle_group: 'other', secondary_muscle_groups: [], equipment: 'other', is_custom: true,
  },
]

const WORKOUT = {
  id: 'w1',
  title: 'Oberkörper 2',
  start_time: '2026-08-25T10:10:24+00:00',
  end_time: '2026-08-25T11:10:08+00:00',
  exercises: [
    {
      index: 0,
      title: 'Bulgarische Ausfallschritte', // localized — must NOT drive the match
      exercise_template_id: SPLIT_HID,
      sets: [
        { index: 0, type: 'warmup', weight_kg: 25, reps: 15, distance_meters: null, duration_seconds: null, rpe: null },
        { index: 1, type: 'normal', weight_kg: 52, reps: 15, distance_meters: null, duration_seconds: null, rpe: 8 },
        { index: 2, type: 'normal', weight_kg: 52, reps: 10, distance_meters: null, duration_seconds: null, rpe: null },
      ],
    },
    {
      index: 1,
      title: 'Hammercurls',
      exercise_template_id: CURL_HID,
      sets: [
        { index: 0, type: 'normal', weight_kg: 20, reps: 12, distance_meters: null, duration_seconds: null, rpe: null },
      ],
    },
    {
      index: 2,
      title: 'Laufband',
      exercise_template_id: DIST_HID,
      sets: [{
        index: 0, type: 'normal', weight_kg: null, reps: null,
        distance_meters: 480, duration_seconds: 180, rpe: null,
      }],
    },
    {
      index: 3,
      title: 'Meine Eigenkreation',
      exercise_template_id: CUSTOM_HID,
      sets: [
        { index: 0, type: 'normal', weight_kg: 20, reps: 12, distance_meters: null, duration_seconds: null, rpe: null },
      ],
    },
  ],
}

describe('HEVY_ID_MAP', () => {
  it('only points at real catalogue ids', () => {
    for (const id of Object.values(HEVY_ID_MAP)) {
      expect(EXIDX[id], `catalogue missing ${id}`).toBeTruthy()
    }
  })

  // Real import leftovers: same lifts under Hevy's naming. Map lookup is the only resolution path —
  // titles are never guessed at runtime.
  const PINNED = [[SPLIT_HID, SPLIT_ID], [CURL_HID, CURL_ID], [LUNGE_HID, LUNGE_ID]]
  for (const [hid, want] of PINNED) {
    it(`maps ${hid} → ${want} (${EXIDX[want].n})`, () => {
      expect(HEVY_ID_MAP[hid]).toBe(want)
      expect(matchHevyTemplate({ id: hid, title: 'ignored localized name' })).toBe(want)
    })
  }

  it('leaves catalogue gaps unmapped (import as custom)', () => {
    for (const id of [DIST_HID, CUSTOM_HID, '68CE0B9B']) {
      expect(HEVY_ID_MAP[id]).toBeUndefined()
      expect(matchHevyTemplate({ id, title: 'whatever' })).toBeNull()
    }
  })
})

describe('matchHevyTemplate', () => {
  it('is map-only — title never overrides the id', () => {
    expect(matchHevyTemplate({ id: SPLIT_HID, title: 'Totally Wrong Name' })).toBe(SPLIT_ID)
    expect(matchHevyTemplate({ id: CUSTOM_HID, title: 'Bench Press' })).toBeNull()
  })
})

describe('parseHevyWorkouts', () => {
  it('maps by template id, not the localized workout title', () => {
    const parsed = parseHevyWorkouts([WORKOUT], TEMPLATES, { unit: 'kg' })
    expect(parsed.error).toBeUndefined()
    expect(parsed.source).toBe('Hevy')
    expect(parsed.workouts).toHaveLength(1)

    const entries = parsed.workouts[0].entries
    const split = entries.find(e => e.id === SPLIT_ID)
    expect(split).toBeTruthy()
    expect(split.sets[0]).toMatchObject({ w: 25, r: 15, phase: 'warmup' })
    expect(split.sets[1]).toMatchObject({ w: 52, r: 15, rpe: 8 })
    expect(split.topW).toBe(52)

    const curl = entries.find(e => e.id === CURL_ID)
    expect(curl).toBeTruthy()
    expect(curl.sets[0]).toMatchObject({ w: 20, r: 12 })

    expect(parsed.warmups).toBe(1)
    expect(parsed.rpeSets).toBe(1)
    // The stored volume leaves the warm-up out, like a workout finished in the app (QA C14), and a
    // distance set carries no tonnage: 52×15 + 52×10 + 20×12 + 20×12.
    expect(parsed.workouts[0].vol).toBe(52 * 15 + 52 * 10 + 20 * 12 + 20 * 12)

    // Two customs: the distance template (no cardio in an Olympic catalogue) and the invented one.
    const customs = entries.filter(e => String(e.id).startsWith('im'))
    expect(customs).toHaveLength(2)
    const distance = customs.find(e => e.id === parsed.customEx.find(c => c.n === 'treadmill run').id)
    expect(distance.sets[0]).toMatchObject({ min: 3, done: true })
    expect(parsed.customEx.find(c => c.n === 'treadmill run').bp).toBe('cardio')
    // An invented row gets its family off the name, and the warm-up-less Hevy group does not win.
    expect(parsed.customEx.find(c => c.n === 'my invented landmine row').bp).toBe('Accessory - Upper Body')
    expect(parsed.unmatchedNames).toContain('My Invented Landmine Row')
    expect(parsed.unmatchedNames).toContain('Treadmill Run')
  })

  it('converts kg weights into a lb profile', () => {
    const parsed = parseHevyWorkouts([WORKOUT], TEMPLATES, { unit: 'lb' })
    const split = parsed.workouts[0].entries.find(e => e.id === SPLIT_ID)
    expect(split.sets.find(s => s.r === 15 && !s.phase).w).toBeCloseTo(114.6, 0)
    expect(parsed.converted).toBe(true)
  })

  it('merges two Hevy sessions on the same local day', () => {
    const a = { ...WORKOUT, id: 'a', title: 'AM', exercises: [WORKOUT.exercises[0]] }
    const b = {
      ...WORKOUT, id: 'b', title: 'PM',
      start_time: '2026-08-25T18:00:00+00:00',
      end_time: '2026-08-25T19:00:00+00:00',
      exercises: [WORKOUT.exercises[1]],
    }
    const parsed = parseHevyWorkouts([a, b], TEMPLATES, { unit: 'kg' })
    expect(parsed.workouts).toHaveLength(1)
    expect(parsed.workouts[0].entries.length).toBeGreaterThanOrEqual(2)
  })
})

describe('parseHevyBodyweight', () => {
  it('reads weigh-ins in the profile unit', () => {
    const parsed = parseHevyBodyweight([
      { id: 1, date: '2026-08-23', weight_kg: 83.2, created_at: '2026-08-23T18:22:48.070Z' },
    ], { unit: 'kg' })
    expect(parsed.bodyweight).toEqual([{ d: '2026-08-23', w: 83.2, t: expect.any(Number) }])
  })
})

describe('mergeImport with Hevy payloads', () => {
  it('adds workouts and skips days that already exist', () => {
    const parsed = parseHevyWorkouts([WORKOUT], TEMPLATES, { unit: 'kg' })
    const S = { workouts: [], customEx: [], exWeights: {}, bodyweight: [] }
    const first = mergeImport(S, parsed)
    expect(first.added).toBe(1)
    expect(S.customEx.length).toBe(2)   // the distance template and the invented exercise
    const second = mergeImport(S, parsed)
    expect(second.added).toBe(0)
    expect(S.workouts).toHaveLength(1)
  })
})

describe('localWhen', () => {
  it('parses an ISO timestamp into a local calendar day', () => {
    const w = localWhen('2026-08-25T10:10:24+00:00')
    expect(w.d).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(w.t).toBeGreaterThanOrEqual(0)
  })
})

describe('importHevyData', () => {
  afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })

  it('pages templates and workouts, never persists the key', async () => {
    const calls = []
    vi.stubGlobal('fetch', vi.fn(async (url, opts) => {
      calls.push({ url: String(url), key: opts.headers['api-key'] })
      const u = String(url)
      if (u.includes('/exercise_templates')) {
        return { ok: true, status: 200, json: async () => ({ page: 1, page_count: 1, exercise_templates: TEMPLATES }) }
      }
      if (u.includes('/workouts')) {
        return { ok: true, status: 200, json: async () => ({ page: 1, page_count: 1, workouts: [WORKOUT] }) }
      }
      if (u.includes('/routines')) {
        return { ok: true, status: 200, json: async () => ({ page: 1, page_count: 1, routines: [] }) }
      }
      if (u.includes('/body_measurements')) {
        return { ok: true, status: 200, json: async () => ({ page: 1, page_count: 1, body_measurements: [] }) }
      }
      return { ok: false, status: 404, json: async () => ({}) }
    }))

    const result = await importHevyData('test-key-not-stored', { unit: 'kg' })
    expect(result.workouts.workouts).toHaveLength(1)
    expect(result.routines.routineCount).toBe(0)
    expect(result.bodyweight.bodyweight).toHaveLength(0)
    expect(calls.every(c => c.key === 'test-key-not-stored')).toBe(true)
  })

  it('surfaces a refused key as HevyApiError auth', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 401, json: async () => ({}) })))
    await expect(importHevyData('bad')).rejects.toMatchObject({ name: 'HevyApiError', message: 'auth' })
  })
})

describe('parseHevyRoutines', () => {
  const ROUTINE = {
    id: 'r1',
    title: 'Oberkörper 2',
    exercises: [
      {
        title: 'Laufband',
        exercise_template_id: DIST_HID,
        superset_id: null,
        sets: [{ type: 'normal', weight_kg: null, reps: null, distance_meters: 480, duration_seconds: 180 }],
      },
      {
        title: 'Bulgarische Ausfallschritte',
        exercise_template_id: SPLIT_HID,
        superset_id: 1,
        sets: [
          { type: 'warmup', weight_kg: 25, reps: 15 },
          { type: 'normal', weight_kg: 52, reps: 15 },
          { type: 'normal', weight_kg: 52, reps: 12 },
          { type: 'normal', weight_kg: 52, reps: 10 },
        ],
      },
      {
        title: 'Reverse Lunge',
        exercise_template_id: LUNGE_HID,
        superset_id: 1,
        sets: [
          { type: 'normal', weight_kg: 20, reps: 12 },
          { type: 'normal', weight_kg: 20, reps: 12 },
        ],
      },
    ],
  }

  it('builds OlyGym routine configs from Hevy sets, as one week of days', () => {
    const parsed = parseHevyRoutines([ROUTINE], TEMPLATES, { unit: 'kg' })
    expect(parsed.routineCount).toBe(1)
    expect(parsed.week.days).toHaveLength(1)
    expect(parsed.week.days[0].name).toBe('Oberkörper 2')
    expect(parsed.week.days[0].dow).toBe(1)
    expect(parsed.exerciseCount).toBe(3)

    const [cardio, split, lunge] = parsed.week.days[0].ex
    // No cardio entry in the catalogue any more: the distance template arrives as a custom, still
    // logged in cardio mode.
    expect(parsed.customEx.find(c => c.id === cardio.id).bp).toBe('cardio')
    expect(cardio).toMatchObject({ sets: 1, min: 3 })
    expect(split.id).toBe(SPLIT_ID)
    expect(split).toMatchObject({ sets: 3, reps: 15, weight: 52, warmupSets: 1 })
    expect(lunge.id).toBe(LUNGE_ID)
    expect(split.sg).toBeTruthy()
    expect(split.sg).toBe(lunge.sg)
  })

  it('puts each Hevy routine on its own day, Monday/Wednesday/Friday first', () => {
    const parsed = parseHevyRoutines([ROUTINE, { ...ROUTINE, id: 'r2', title: 'Push' }], TEMPLATES, { unit: 'kg' })
    expect(parsed.week.days.map(d => [d.dow, d.name])).toEqual([[1, 'Oberkörper 2'], [3, 'Push']])
  })

  it('merges the imported week into state as a new dated week', () => {
    const parsed = parseHevyRoutines([ROUTINE], TEMPLATES, { unit: 'kg' })
    const S = { weeks: [], customEx: [] }
    expect(mergeHevyRoutines(S, parsed)).toEqual({ added: 1, updated: 0 })
    expect(S.weeks).toHaveLength(1)
    expect(S.weeks[0].days).toHaveLength(1)
    expect(S.weeks[0].days[0].name).toBe('Oberkörper 2')
    expect(S.weeks[0].startIso).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    // The customs the week references are stored once, with the ids the days point at.
    const custom = S.customEx.find(c => c.id === S.weeks[0].days[0].ex[0].id)
    expect(custom).toBeTruthy()
    expect(custom.bp).toBe('cardio')
    // A second import adds a second week rather than rewriting the first.
    const firstId = S.weeks[0].id
    mergeHevyRoutines(S, parsed)
    expect(S.weeks).toHaveLength(2)
    expect(S.weeks[0].id).toBe(firstId)
    expect(S.customEx.filter(c => c.id === custom.id)).toHaveLength(1)
  })

  it('changes nothing for a Hevy account with no routines', () => {
    const parsed = parseHevyRoutines([], TEMPLATES, { unit: 'kg' })
    const S = { weeks: [], customEx: [] }
    expect(mergeHevyRoutines(S, parsed)).toEqual({ added: 0, updated: 0 })
    expect(S.weeks).toEqual([])
  })
})

describe('buildHevyExerciseMap', () => {
  it('indexes every template id from the static map', () => {
    const map = buildHevyExerciseMap(TEMPLATES)
    expect(map.get(SPLIT_HID)).toBe(SPLIT_ID)
    expect(map.get(CURL_HID)).toBe(CURL_ID)
    expect(map.get(LUNGE_HID)).toBe(LUNGE_ID)
    expect(map.get(CUSTOM_HID)).toBeNull()
    expect(map.size).toBe(TEMPLATES.length)
  })
})
