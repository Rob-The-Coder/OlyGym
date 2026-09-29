// Import a training history exported from another app.
//
// Every one of these apps exports the same thing in a different dialect: one row per
// *set*, carrying a date, an exercise name and some mix of weight/reps/distance/time.
// So this reads a column MAP built from the header rather than fixed positions, which
// means a new app is usually a few header aliases rather than another importer.
//
// Verified against real exports:
//   FitNotes (Android) Date,Exercise,Category,Weight,Weight Unit,Reps,Distance,Distance Unit,Time,Comment
//   FitNotes 2 (iOS)   Date,Exercise,Category,Weight (kg),Weight (lbs),Reps,Distance,Distance Unit,Time,Notes,Kind
//   Strong             Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Reps,Distance,Seconds,Notes,Workout Notes,RPE
//   Hevy               title,start_time,end_time,description,exercise_title,superset_id,exercise_notes,set_index,set_type,weight_kg,reps,distance_km,duration_seconds,rpe
// Anything else falls through to loose header matching, which covers Lyfta and the
// spreadsheet round-trips people actually have on disk, as long as the file has a
// date, an exercise name and something measured.
//
// Apple Health is a different animal — an XML dump, often hundreds of MB — and only its
// body-weight records are interesting here. parseBodyweight() scans for those without
// building a DOM.

import { EXDB, EXIDX } from './exercises.js'
import { uid } from './format.js'
import { isWarmupRow } from './workout-model.js'
import { HEVY_TITLE_MAP } from './hevy-id-map.js'

/* ----------------------------------------------------------------- CSV ---- */

/**
 * A real CSV reader: quoted fields, embedded commas and newlines, doubled quotes, BOM
 * and CRLF. Splitting on commas breaks on the first exercise named "Bench Press, Close
 * Grip" — and a whole history would import shifted by one column without ever erroring.
 */
export function parseCSV(text) {
  const rows = []
  let row = [], field = '', quoted = false
  const s = String(text).replace(/^﻿/, '')
  for (let i = 0; i < s.length; i++) {
    const c = s[i]
    if (quoted) {
      if (c === '"') { if (s[i + 1] === '"') { field += '"'; i++ } else quoted = false }
      else field += c
    } else if (c === '"') quoted = true
    else if (c === ',') { row.push(field); field = '' }
    else if (c === '\n' || c === '\r') {
      if (c === '\r' && s[i + 1] === '\n') i++
      row.push(field); field = ''
      if (row.some(x => x !== '')) rows.push(row)
      row = []
    } else field += c
  }
  row.push(field)
  if (row.some(x => x !== '')) rows.push(row)
  return rows
}

const norm = h => h.toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim()

// header text -> the field we care about. Specific names first; first match wins.
const COLUMNS = [
  ['exercise', ['exercise', 'exercise name', 'exercise title']],
  ['date', ['date', 'workout date']],
  ['startTime', ['start time', 'start date']],
  ['endTime', ['end time']],
  ['workoutName', ['workout name', 'title', 'workout']],
  ['category', ['category', 'body part', 'muscle group']],
  ['weightKg', ['weight kg']],
  ['weightLb', ['weight lbs', 'weight lb']],
  ['weight', ['weight']],
  ['weightUnit', ['weight unit', 'unit']],
  ['reps', ['reps', 'repetitions']],
  // Hevy and Strong both write an RPE per set. Nothing mainstream exports RIR, but read it
  // when it is there rather than dropping the column on the floor.
  ['rpe', ['rpe', 'rpe rating']],
  ['rir', ['rir', 'reps in reserve']],
  ['distanceKm', ['distance km']],
  ['distance', ['distance']],
  ['distanceUnit', ['distance unit']],
  ['seconds', ['seconds', 'duration seconds', 'set duration sec']],
  ['time', ['time', 'duration']],
  ['setType', ['set type']],
  ['note', ['comment', 'comments', 'notes', 'note', 'workout notes']],
]

function mapHeader(header) {
  const map = {}
  header.forEach((h, i) => {
    const n = norm(h)
    for (const [field, names] of COLUMNS) {
      if (map[field] === undefined && names.includes(n)) { map[field] = i; return }
    }
  })
  return map
}

/** Name of the app a header looks like — shown back to the user so they can sanity-check. */
export function detectSource(header) {
  const h = header.map(norm)
  if (h.includes('exercise title') && h.includes('set index')) return 'Hevy'
  if (h.includes('exercise name') && h.includes('set order')) return 'Strong'
  if (h.includes('exercise') && h.includes('kind')) return 'FitNotes (iOS)'
  if (h.includes('exercise') && h.includes('weight unit')) return 'FitNotes'
  if (h.includes('exercise') && h.includes('category')) return 'FitNotes'
  return null
}

/* ------------------------------------------------------ exercise matching -- */

// Other apps bolt qualifiers onto names — Hevy writes "Leg Press (Machine)", Strong
// "Snatch (Barbell)", FitNotes "Lat Pulldown (Pulley)" — while the dataset writes
// "barbell snatch". Strip the parentheses, expand the shorthand, then compare as a
// sorted bag of words so word order stops mattering.
const SYN = [
  [/\bbb\b/g, 'barbell'], [/\bdb\b/g, 'dumbbell'], [/\bkb\b/g, 'kettlebell'],
  [/\bohp\b/g, 'overhead press'], [/\bbw\b/g, 'body weight'], [/\bbodyweight\b/g, 'body weight'],
  // 'smith machine' first: the generic machine->lever rule would otherwise eat the word
  // and leave 'smith lever', which matches no entry in the dataset.
  [/\bsmith machine\b/g, 'smith'], [/\bmachine\b/g, 'lever'], [/\bez bar\b/g, 'ez barbell'],
  [/\bpull ups?\b/g, 'pull up'], [/\bchin ups?\b/g, 'chin up'], [/\bpush ups?\b/g, 'push up'],
  [/\bsit ups?\b/g, 'sit up'], [/\bdips?\b/g, 'dip'], [/\braises?\b/g, 'raise'],
  [/\bcurls?\b/g, 'curl'], [/\bpresses\b/g, 'press'], [/\bextensions?\b/g, 'extension'],
  [/\bcables?\b/g, 'cable'], [/\bseated\b/g, 'seated'], [/\bassisted\b/g, 'assisted'],
]
// Words that say nothing about which exercise this is, so they shouldn't stop a match.
const FILLER = new Set(['the', 'a', 'with', 'and', 'v', 'variation', 'version', 'pulley', 'weighted'])

function wordsOf(name) {
  // Parentheses are unwrapped rather than dropped: "Bench Press (Barbell)" carries its
  // equipment in there, and the dataset writes that as "barbell bench press".
  let k = String(name || '').toLowerCase()
    .replace(/[()[\]]/g, ' ')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
  SYN.forEach(([re, to]) => { k = k.replace(re, to) })
  return k.split(' ').filter(w => w && !FILLER.has(w))
}
const keyOf = name => wordsOf(name).sort().join(' ')

let INDEX = null
function buildIndex() {
  if (INDEX) return INDEX
  INDEX = { exact: new Map(), all: [] }
  EXDB.forEach(e => {
    const w = wordsOf(e.n)
    const k = w.slice().sort().join(' ')
    if (!INDEX.exact.has(k)) INDEX.exact.set(k, e.id)
    INDEX.all.push({ id: e.id, set: new Set(w), n: w.length })
  })
  return INDEX
}

// Curated: the names people actually log, mapped by hand to the dataset id they mean.
//
// Other apps let you name a lift "Bench Press"; the dataset only has qualified names
// like "barbell bench press". Word-overlap alone can't resolve that — "bench press" sits
// inside thirty-three entries — and where it *is* unique it tends to be wrong, happily
// resolving "Squat" to "weighted squat" and "Leg Press" to "smith leg press". So the
// common vocabulary is spelled out. The convention is that an unqualified name means the
// canonical barbell version, which is what these apps assume when they show it to you.
// Extending this table is the intended way to improve import accuracy.
// Foreign names the word-bag cannot reach on its own, mapped onto the OlyGym catalogue.
//
// Two rules, both learned the hard way:
//   · An entry earns its place only when the automatic matcher cannot get there, so a catalogue
//     regeneration cannot leave a stale id behind — the previous table had 87 entries and every
//     single one pointed at the retired dataset.
//   · The same movement with the wrong equipment label beats no match (a Pallof press is a Pallof
//     press whatever is clipped to the cable). A *different* movement never does: a military press
//     is not a push press, and filing years of pressing under a jerk would be worse than leaving
//     the exercise as a custom the user can see and fix.
//
// Names with no counterpart are deliberately absent — treadmill, cycling, elliptical, stationary
// bike, stepmill, battle ropes, jumping jacks, machine and Smith variants, machine chest fly,
// goblet squat, leg press, calf raises, decline and close-grip bench press, sumo deadlift,
// dumbbell rows, cable crossovers, overhead and military press. They import as custom exercises.
const ALIAS_EX = {
  // Bench. Only the incline has a counterpart; there is no decline or close-grip bench here.
  'barbell bench press': 'wl806', 'flat bench press': 'wl806', 'flat barbell bench press': 'wl806',
  'incline bench press': 'wl847',
  // Squat. The bare word needs the alias: the catalogue has back, front and overhead squats, so
  // the word-bag refuses to choose between them — and a plain "Squat" from a Strong or FitNotes
  // export means the back squat.
  squat: 'wl77', 'barbell squat': 'wl77',
  // Pulls from the floor
  'romanian deadlift': 'wl101', rdl: 'wl101',
  // Upper back
  'lat pull down': 'wl725', pulldown: 'wl725',
  'reverse grip lat pulldown (cable)': 'wl876',
  'barbell row': 'wl171', 'bent over row': 'wl171', 'bent-over row': 'wl171',
  // The catalogue's plain dumbbell row is the single-arm one; without this the word-bag picks
  // "rle dumbbell row", which is a different exercise.
  'dumbbell row': 'wl568', 'one arm dumbbell row': 'wl568',
  // The catalogue's only shrugs are the Olympic ones; the movement is the same one.
  shrug: 'wl93', shrugs: 'wl93',
  // Legs. Knee flexion only: an Olympic catalogue has no machine leg curl.
  'lying leg curl': 'wl559', 'seated leg curl': 'wl559',
  lunges: 'wl354',
  // Arms
  'bicep curl': 'wl821', 'biceps curl': 'wl821', 'bicep curl (dumbbell)': 'wl821',
  'bicep curl (cable)': 'wl809',
  // The catalogue's barbell curl is an EZ-bar curl.
  'bicep curl (barbell)': 'wl838', 'barbell curl': 'wl838',
  'triceps pushdown': 'wl911', pushdown: 'wl911',
  'skull crusher': 'wl903',
  // The only triceps extension here is the overhead one.
  'lying triceps extension': 'wl804',
  // Shoulders and rear delts
  'side raise': 'wl825',
  // The catalogue carries the two-arm cable raise; one arm at a time is the same movement.
  'single arm lateral raise (cable)': 'wl811',
  'rear delt reverse fly (dumbbell)': 'wl870', 'chest supported reverse fly (dumbbell)': 'wl870',
  // A dumbbell fly and a pec deck are both a pec fly; only the dumbbell one has a counterpart.
  'chest fly (dumbbell)': 'wl866',
  // Core and lower back. The Pallof presses here are band ones; the movement is the same.
  'cable pallof press': 'wl526', 'core pallof press': 'wl526', 'cable core pallof press': 'wl526',
  'back extension (weighted hyperextension)': 'wl425',
}

let ALIAS_IDX = null
const aliasIndex = () => {
  if (!ALIAS_IDX) {
    ALIAS_IDX = new Map()
    for (const k in ALIAS_EX) ALIAS_IDX.set(wordsOf(k).sort().join(' '), ALIAS_EX[k])
  }
  return ALIAS_IDX
}

/**
 * Find the dataset exercise a foreign name refers to, or null.
 *
 * Curated alias first, then an exact word-bag match, then entries that contain every
 * word of the query — but only when exactly one candidate is that close. Guessing
 * between "barbell bench press" and "dumbbell bench press" would file years of training
 * under the wrong lift, which is worse than leaving it as a custom exercise the user can
 * see and fix.
 */
export function matchExercise(name) {
  const idx = buildIndex()
  const w = wordsOf(name)
  if (!w.length) return null
  // Compared as a sorted bag of words, so "Squat (Barbell)" finds the 'barbell squat'
  // alias — the exporters disagree about whether the equipment leads or trails.
  const sorted = w.slice().sort().join(' ')
  const aliased = aliasIndex().get(sorted)
  if (aliased && EXIDX[aliased]) return aliased
  const exact = idx.exact.get(sorted)
  if (exact) return exact
  const q = new Set(w)
  let best = null, bestExtra = Infinity, ties = 0
  for (const c of idx.all) {
    let ok = true
    for (const word of q) if (!c.set.has(word)) { ok = false; break }
    if (!ok) continue
    const extra = c.n - q.size
    if (extra > 2) continue
    if (extra < bestExtra) { best = c.id; bestExtra = extra; ties = 1 }
    else if (extra === bestExtra) ties++
  }
  return ties === 1 ? best : null
}

/**
 * Exact Hevy English title → catalogue id (from the generated title map).
 * Used when a CSV is detected as Hevy: same deterministic table as the API import,
 * keyed by title because Hevy's CSV has no template id column.
 */
export function matchHevyTitle(name) {
  const id = HEVY_TITLE_MAP[String(name || '').trim().toLowerCase()]
  return id && EXIDX[id] ? id : null
}

// Hevy exports no category column, so every invented exercise fell through to the
// 'upper legs' default and a third of an imported history was attributed to the legs in
// the muscle map. When there is no category, read the body part off the name instead.
// Order is the rule: the first match wins, so the specific word has to come before the broad one.
// A grip is a modifier on a row or a pulldown, never the movement ("Chest Supported T Row Neutral
// Grip" is a back exercise); a wrist or reverse curl is a forearm exercise that happens to say
// "curl"; a leg curl is not an arm curl; and a Romanian or stiff-leg deadlift trains the legs where
// the conventional pull is filed under the back. "grip" alone still reads as forearms — last.
// Names the exporters use, mapped onto the OlyGym catalogue's movement families — this is what a
// name that matched nothing gets stamped with, so a fabricated exercise lands under a chip someone
// can find again instead of in a category no filter offers.
//
// 'cardio' stays a value of its own even though the catalogue has no cardio entry: it is the only
// route to the cardio logging mode for a custom exercise (a burpee, a treadmill walk).
const NAME_BP = [
  [/\b(wrist|forearm|forearms|grip|reverse curl)\b/, 'Accessory - Upper Body'],
  [/\b(leg curl|leg curls|hamstring curl|nordic)\b/, 'Accessory - Lower/Whole Body'],
  [/\b(romanian|rdl|stiff leg|stiff legged|straight leg)\b.*\bdeadlifts?\b|\brdl\b/, 'Accessory - Lower/Whole Body'],
  [/\b(back extension|hyperextension)\b/, 'Trunk (Ab & Back)'],
  [/\b(curl|curls|bicep|biceps|tricep|triceps|skullcrusher|pushdown)\b/, 'Accessory - Upper Body'],
  [/\bchest supported\b/, 'Accessory - Upper Body'],   // where the chest rests, not what it trains
  [/\b(bench|chest|pec|fly|flye|crossover|crossovers|dip)\b/, 'Accessory - Upper Body'],
  // Head words that are more specific than the equipment words around them come first: a back
  // squat is a squat, not "back", and a leg press is legs, not a press.
  [/\bsquats?\b/, 'General Exercises'],
  [/\b(deadlifts?|cleans?|snatch(es)?|jerks?)\b/, 'General Exercises'],
  [/\b(shoulder|delt|delts|overhead|lateral raise|front raise|face pull|press up)\b/, 'Accessory - Upper Body'],
  [/\b(calf|calves|tibialis)\b/, 'Accessory - Lower/Whole Body'],
  [/\b(lunge|leg|glute|hamstring|quad|hip thrust)s?\b/, 'Accessory - Lower/Whole Body'],
  [/\bpress(es)?\b/, 'General Exercises'],
  [/\b(ab|abs|core|plank|crunch|sit up|oblique|russian twist)\b/, 'Trunk (Ab & Back)'],
  [/\b(farmer|suitcase|carry|carries|yoke)\b/, 'Carries'],
  [/\b(jump|jumps|hop|hops|bound|plyo)\b/, 'Jumping & Plyometrics'],
  [/\b(stretch|mobility|foam|activation|prehab|rotator)\b/, 'Accessory - Prep & Prehab'],
  [/\b(run|running|jog|bike|cycling|rope|ropes|jacks|burpee|sprint|treadmill|stair|elliptical|rower|erg)\b/, 'cardio'],
  [/\b(row|rows|pulldown|pullup|pull up|chin up|lat|lats|back|shrug)\b/, 'Accessory - Upper Body'],
  [/\bneck\b/, 'Accessory - Prep & Prehab'],
]



// Hyphens, underscores and slashes read as spaces first: "Stiff-Legged Deadlift" and
// "Chest-Supported Row" are the same names the rules above spell with a space, and the
// matcher (wordsOf) already treats the two spellings as one exercise — the body part has to agree.
export const bpFromName = name => {
  const n = String(name || '').toLowerCase().replace(/[-_/]+/g, ' ').replace(/\s+/g, ' ').trim()
  return (NAME_BP.find(([re]) => re.test(n)) || [])[1] || null
}

// Categories the exporters use -> the dataset's body parts, for exercises we invent.
// Categories the exporters use -> the catalogue's movement families, for exercises we invent.
const CATEGORY_BP = {
  chest: 'Accessory - Upper Body', back: 'Accessory - Upper Body', lats: 'Accessory - Upper Body',
  shoulders: 'Accessory - Upper Body', delts: 'Accessory - Upper Body',
  arms: 'Accessory - Upper Body', biceps: 'Accessory - Upper Body', triceps: 'Accessory - Upper Body',
  forearms: 'Accessory - Upper Body', neck: 'Accessory - Prep & Prehab',
  legs: 'Accessory - Lower/Whole Body', quads: 'Accessory - Lower/Whole Body',
  hamstrings: 'Accessory - Lower/Whole Body', glutes: 'Accessory - Lower/Whole Body',
  calves: 'Accessory - Lower/Whole Body',
  abs: 'Trunk (Ab & Back)', core: 'Trunk (Ab & Back)', obliques: 'Trunk (Ab & Back)',
  cardio: 'cardio',
  'full body': 'General Exercises', olympic: 'General Exercises',
}


/* ----------------------------------------------------------- conversion --- */

const num = v => { const n = parseFloat(String(v ?? '').replace(',', '.')); return isFinite(n) ? n : 0 }
// An effort rating out of someone else's export. A blank cell means "not rated" and has to
// stay absent rather than becoming 0 — and 0 itself means opposite things on the two scales:
// RIR 0 is a set taken to failure and worth keeping, while RPE has no 0 (the scale is 1–10),
// so an app writing 0 for "nothing here" must not be read as an effort. Ratings above the
// scale are capped rather than dropped — the set was still rated, just written oddly.
const effortNum = (raw, zeroMeansRated) => {
  const s = String(raw ?? '').trim()
  if (!s) return null
  const n = parseFloat(s.replace(',', '.'))
  if (!isFinite(n) || n < 0 || (n === 0 && !zeroMeansRated)) return null
  return Math.min(10, Math.round(n * 100) / 100)
}
const LB_TO_KG = 0.45359237
const p2 = n => String(n).padStart(2, '0')
const MON = { jan: 1, feb: 2, mar: 3, apr: 4, may: 5, jun: 6, jul: 7, aug: 8, sep: 9, oct: 10, nov: 11, dec: 12 }

/** "2020-12-30 18:51:52" · "2024-03-07" · "2024/03/07" · "2024.03.07" · "22 Dec 2025, 08:00" · "07/03/2024" -> { d, t } */
export function parseWhen(s) {
  const v = String(s || '').trim()
  let m = v.match(/^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?:[T ](\d{1,2}):(\d{2}))?/)
  if (m) return { d: `${m[1]}-${p2(m[2])}-${p2(m[3])}`, t: hm(m[4], m[5]) }
  m = v.match(/^(\d{1,2})\s+([A-Za-z]{3})[a-z]*\.?\s+(\d{4})(?:,?\s+(\d{1,2}):(\d{2}))?/)
  if (m && MON[m[2].toLowerCase()]) return { d: `${m[3]}-${p2(MON[m[2].toLowerCase()])}-${p2(m[1])}`, t: hm(m[4], m[5]) }
  m = v.match(/^([A-Za-z]{3})[a-z]*\.?\s+(\d{1,2}),?\s+(\d{4})(?:,?\s+(\d{1,2}):(\d{2}))?/)
  if (m && MON[m[1].toLowerCase()]) return { d: `${m[3]}-${p2(MON[m[1].toLowerCase()])}-${p2(m[2])}`, t: hm(m[4], m[5]) }
  // Day-first when ambiguous: FitNotes/Strong/Hevy all write unambiguous dates, so a
  // bare numeric one came through a spreadsheet, and those are usually European.
  m = v.match(/^(\d{1,2})[/.](\d{1,2})[/.](\d{4})(?:[, ]+(\d{1,2}):(\d{2}))?/)
  if (m) {
    const [, a, b, y] = m
    const day = +a > 12 ? a : +b > 12 ? b : a
    const mon = day === a ? b : a
    return { d: `${y}-${p2(mon)}-${p2(day)}`, t: hm(m[4], m[5]) }
  }
  return null
}
const hm = (h, mi) => (h === undefined ? null : (parseInt(h, 10) || 0) * 3600000 + (parseInt(mi, 10) || 0) * 60000)

/** "HH:MM:SS" · "MM:SS" · "90" -> minutes */
function toMinutes(v) {
  const s = String(v ?? '').trim()
  if (!s) return 0
  if (s.includes(':')) {
    const p = s.split(':').map(x => parseInt(x, 10) || 0)
    const sec = p.length === 3 ? p[0] * 3600 + p[1] * 60 + p[2] : p[0] * 60 + p[1]
    return Math.round(sec / 60 * 10) / 10
  }
  const m = s.match(/(\d+)\s*h/i), mm = s.match(/(\d+)\s*m/i)      // Strong's "2h 38m"
  if (m || mm) return (m ? +m[1] * 60 : 0) + (mm ? +mm[1] : 0)
  return Math.round(num(s) * 10) / 10
}
const KM = { m: 0.001, km: 1, cm: 0.00001, in: 0.0000254, ft: 0.0003048, yd: 0.0009144, mi: 1.609344 }
const toKm = (v, unit) => num(v) * (KM[String(unit || 'km').toLowerCase().trim()] ?? 1)

/* --------------------------------------------------------------- parse ---- */

/**
 * Read an export into workouts OlyGym understands, WITHOUT touching state — the caller
 * shows the summary for confirmation first. Nothing here throws on a bad row: a history
 * of several thousand sets will contain oddities, and losing the file over one of them
 * helps nobody. Bad rows are counted and reported instead.
 */
export function parseWorkoutCSV(text, { unit = 'kg' } = {}) {
  const rows = parseCSV(text)
  if (rows.length < 2) return { error: 'empty' }
  const map = mapHeader(rows[0])
  const source = detectSource(rows[0])
  const dateCol = map.date !== undefined ? 'date' : map.startTime !== undefined ? 'startTime' : null
  if (!dateCol || map.exercise === undefined) return { error: 'unrecognised' }

  const resolved = new Map()          // exercise name -> dataset id | null, resolved once
  const byDate = new Map()
  const created = new Map()
  const unmatched = new Set()
  let sets = 0, skipped = 0, matched = 0, warmups = 0, rpeSets = 0, rirSets = 0
  let sawLb = false, sawKg = false

  const cell = (r, f) => (map[f] === undefined ? '' : String(r[map[f]] ?? '').trim())

  for (let i = 1; i < rows.length; i++) {
    const r = rows[i]
    const name = cell(r, 'exercise')
    const when = parseWhen(cell(r, dateCol))
    if (!name || !when) { skipped++; continue }

    // explicit kg/lb columns beat a generic column plus a unit column
    let w = 0, rowUnit = ''
    if (map.weightKg !== undefined && cell(r, 'weightKg')) { w = num(cell(r, 'weightKg')); rowUnit = 'kg' }
    else if (map.weightLb !== undefined && cell(r, 'weightLb')) { w = num(cell(r, 'weightLb')); rowUnit = 'lb' }
    else {
      w = num(cell(r, 'weight'))
      const u = cell(r, 'weightUnit').toLowerCase()
      rowUnit = u.startsWith('lb') ? 'lb' : u.startsWith('kg') ? 'kg' : ''
    }
    if (rowUnit === 'lb') sawLb = true
    if (rowUnit === 'kg') sawKg = true

    const reps = Math.round(num(cell(r, 'reps')))
    const secs = num(cell(r, 'seconds'))
    const mins = secs > 0 ? Math.round(secs / 60 * 10) / 10 : toMinutes(cell(r, 'time'))
    const km = map.distanceKm !== undefined && cell(r, 'distanceKm')
      ? num(cell(r, 'distanceKm'))
      : toKm(cell(r, 'distance'), cell(r, 'distanceUnit'))
    if (!w && !reps && !mins && !km) { skipped++; continue }
    const warmup = /warm/i.test(cell(r, 'setType'))
    if (warmup) warmups++

    const key = keyOf(name)
    let id = resolved.get(key)
    if (id === undefined) {
      // Hevy CSV: prefer the generated English-title map (same table as the API import).
      // Localized titles still fall through to the word-bag matcher.
      id = (source === 'Hevy' ? matchHevyTitle(name) : null) || matchExercise(name)
      resolved.set(key, id)
    }
    if (id) matched++
    else {
      let c = created.get(key)
      if (!c) {
        c = {
          id: 'im' + uid(), n: name.toLowerCase(), custom: true, eq: 'custom', tg: '', desc: '',
          bp: CATEGORY_BP[cell(r, 'category').toLowerCase()] || (km || (mins && !reps) ? 'cardio' : null)
            || bpFromName(name.toLowerCase()) || 'General Exercises',
        }
        created.set(key, c)
        unmatched.add(name)
      }
      id = c.id
    }

    const isCardio = (km > 0 || mins > 0) && !reps
    // `u` carries the row's own unit into the conversion pass below and is dropped there —
    // it never reaches the stored set.
    const set = isCardio
      ? { min: mins || 0, speed: mins > 0 ? Math.round(km / (mins / 60) * 10) / 10 : 0, done: true, ...(warmup ? { phase: 'warmup' } : {}) }
      : { w, r: reps || 0, done: true, u: rowUnit, ...(warmup ? { phase: 'warmup' } : {}) }
    // Effort rides along only where the app can show it again: a weighted rep set. A treadmill
    // row with an RPE would have nowhere to put it. A set is kept on one scale, so a file
    // carrying both columns is read as RIR — the same precedence setLabel reads them back with.
    if (!isCardio) {
      const rir = effortNum(cell(r, 'rir'), true)
      const rpe = rir == null ? effortNum(cell(r, 'rpe'), false) : null
      if (rir != null) { set.rir = rir; rirSets++ }
      else if (rpe != null) { set.rpe = rpe; rpeSets++ }
    }

    let day = byDate.get(when.d)
    if (!day) {
      day = { ex: new Map(), name: cell(r, 'workoutName') || '', start: when.t, end: null }
      byDate.set(when.d, day)
    }
    if (!day.name) day.name = cell(r, 'workoutName') || ''
    if (map.endTime !== undefined) { const e = parseWhen(cell(r, 'endTime')); if (e && e.t != null) day.end = e.t }
    else if (map.time !== undefined && !map.seconds && reps) { /* FitNotes' Time is per-set */ }
    if (!day.ex.has(id)) day.ex.set(id, [])
    day.ex.get(id).push(set)
    sets++
  }

  // lb -> kg only where a row disagrees with the profile. The app never converts units on
  // its own, so importing unconverted would silently rewrite someone's numbers.
  // Converting PER ROW matters: apps like FitNotes write the unit next to every set, and a
  // history recorded partly in lb and partly in kg used to be taken over as-is, turning
  // "185 lb" into 185 kg.
  const fileUnit = sawLb && !sawKg ? 'lb' : sawKg && !sawLb ? 'kg' : ''
  const mixedUnits = sawLb && sawKg
  const toKg = x => Math.round(x * LB_TO_KG * 10) / 10
  const toLb = x => Math.round(x / LB_TO_KG * 10) / 10
  // A row without its own unit follows the file's, and a file that says nothing is taken
  // to already be in the profile's unit.
  const convRow = s => {
    const u = s.u || fileUnit
    if (!u || u === unit) return s.w
    return u === 'lb' ? toKg(s.w) : toLb(s.w)
  }
  const converted = (!!fileUnit && fileUnit !== unit) || mixedUnits

  const dates = [...byDate.keys()].sort()
  const workouts = dates.map(d => {
    const day = byDate.get(d)
    const entries = [...day.ex.entries()].map(([id, ss]) => {
      const conv2 = ss.map(({ u, ...s }) => (s.w !== undefined ? { ...s, w: convRow({ ...s, u }) } : s))
      const mx = Math.max(0, ...conv2.filter(s => !isWarmupRow(s)).map(s => s.w || 0))
      return { id, sets: conv2, topW: mx || null }
    })
    const base = new Date(d + 'T00:00:00').getTime()
    const start = base + (day.start ?? 18 * 3600000)
    const end = day.end != null ? base + day.end : start
    const w = {
      id: 'iw' + uid(), d, start, end: end > start ? end : start,
      routineId: null, name: day.name || 'Imported', entries, prs: [],
    }
    // Work sets only, like `workoutVolume` for a workout finished in the app: warm-ups are
    // promised to stay out of the volume, and this number is stored with the workout for good.
    w.vol = entries.reduce((a, e) => a + e.sets.reduce((b, s) => b + (isWarmupRow(s) ? 0 : (s.w || 0) * (s.r || 0)), 0), 0)
    return w
  })

  return {
    kind: 'workouts', source, workouts, customEx: [...created.values()],
    // distinct library exercises behind the matched rows — the summary calls this
    // "exercises matched", and counting rows there made three exercises read as five
    matched: new Set([...resolved.values()].filter(Boolean)).size,
    matchedSets: matched,
    created: created.size, unmatchedNames: [...unmatched].sort(),
    sets, skipped, warmups, fileUnit, mixedUnits, converted, rpeSets, rirSets,
    from: dates[0] || null, to: dates[dates.length - 1] || null,
  }
}

/* ------------------------------------------------------- body weight ------ */

/**
 * Body-weight history from Apple Health, or any CSV with a date and a weight.
 *
 * Health's own export is one big `export.xml` — often several hundred MB, nearly all of
 * it step counts and heart rate. Building a DOM would blow up the tab, so the body-mass
 * records are pulled out with a scan instead. Health writes weights in the unit the
 * phone is set to and labels each record, so the unit is read per record.
 */
export function parseBodyweight(text, { unit = 'kg' } = {}) {
  const s = String(text)
  const out = new Map()          // iso date -> { w, t }  (one weigh-in per day, the last)
  let fileUnit = ''

  if (s.includes('HKQuantityTypeIdentifierBodyMass')) {
    const re = /<Record[^>]*type="HKQuantityTypeIdentifierBodyMass"[^>]*>/g
    let m
    while ((m = re.exec(s))) {
      const tag = m[0]
      const val = /value="([\d.]+)"/.exec(tag)
      const dt = /startDate="([^"]+)"/.exec(tag) || /creationDate="([^"]+)"/.exec(tag)
      const u = /unit="([^"]+)"/.exec(tag)
      if (!val || !dt) continue
      const when = parseWhen(dt[1])
      if (!when) continue
      if (u) fileUnit = /lb/i.test(u[1]) ? 'lb' : 'kg'
      out.set(when.d, { w: parseFloat(val[1]), t: new Date(dt[1]).getTime() || null })
    }
  } else {
    const rows = parseCSV(s)
    if (rows.length < 2) return { error: 'empty' }
    const map = mapHeader(rows[0])
    // a weight-only CSV: whichever weight column it has
    const wCol = map.weightKg ?? map.weightLb ?? map.weight
    const dCol = map.date ?? map.startTime
    if (wCol === undefined || dCol === undefined) return { error: 'unrecognised' }
    if (map.weightKg !== undefined) fileUnit = 'kg'
    else if (map.weightLb !== undefined) fileUnit = 'lb'
    for (let i = 1; i < rows.length; i++) {
      const when = parseWhen(String(rows[i][dCol] ?? ''))
      const w = num(rows[i][wCol])
      if (!when || !w) continue
      out.set(when.d, { w, t: new Date(when.d).getTime() + (when.t ?? 0) })
    }
  }

  if (!out.size) return { error: 'unrecognised' }
  const converted = !!fileUnit && fileUnit !== unit
  const conv = converted
    ? (fileUnit === 'lb' ? x => Math.round(x * LB_TO_KG * 10) / 10 : x => Math.round(x / LB_TO_KG * 10) / 10)
    : x => Math.round(x * 10) / 10
  const dates = [...out.keys()].sort()
  return {
    kind: 'bodyweight', source: 'Apple Health',
    bodyweight: dates.map(d => ({ d, w: conv(out.get(d).w), t: out.get(d).t || new Date(d).getTime() })),
    fileUnit, converted, from: dates[0], to: dates[dates.length - 1],
  }
}

/** Sniff the file and parse it as whatever it is. */
export function parseImport(text, opts) {
  const s = String(text)
  if (s.includes('HKQuantityTypeIdentifier') || /^\s*</.test(s)) return parseBodyweight(s, opts)
  const asWorkouts = parseWorkoutCSV(s, opts)
  if (!asWorkouts.error) return asWorkouts
  const asWeights = parseBodyweight(s, opts)
  return asWeights.error ? asWorkouts : asWeights
}

/* --------------------------------------------------------------- merge ---- */

/** Merge into state. Existing days win — importing twice never duplicates a workout. */
export function mergeImport(S, parsed) {
  if (parsed.kind === 'bodyweight') {
    const have = new Set(S.bodyweight.map(b => b.d))
    const fresh = parsed.bodyweight.filter(b => !have.has(b.d))
    S.bodyweight = [...S.bodyweight, ...fresh].sort((a, b) => (a.d < b.d ? -1 : 1))
    return { added: fresh.length, skipped: parsed.bodyweight.length - fresh.length }
  }
  const have = new Set(S.workouts.map(w => w.d))
  // Every parse invents fresh ids for the names it cannot match, and a later export of the same
  // account names those exercises again. So a custom exercise is looked up by name among the ones
  // already here — from an earlier import or made by hand — and the new days are pointed at it,
  // the way mergeHevyRoutines and mergePlan do; otherwise the Library lists "Grip Trainer" twice,
  // each with half the history. Only a name with no match becomes a new exercise.
  S.customEx = S.customEx || []
  const nameKey = n => String(n || '').toLowerCase().replace(/\s+/g, ' ').trim()
  const exIdMap = {}
  parsed.customEx.forEach(c => {
    const same = S.customEx.find(x => x.id !== c.id && nameKey(x.n) === nameKey(c.n))
    if (same) exIdMap[c.id] = same.id
  })
  const fresh = parsed.workouts.filter(w => !have.has(w.d))
    .map(w => ({ ...w, entries: w.entries.map(e => (exIdMap[e.id] ? { ...e, id: exIdMap[e.id] } : e)) }))
  const used = new Set(fresh.flatMap(w => w.entries.map(e => e.id)))
  const customs = parsed.customEx.filter(c => used.has(c.id) && !EXIDX[c.id])
  S.customEx = [...S.customEx, ...customs]
  S.workouts = [...S.workouts, ...fresh].sort((a, b) => (a.d < b.d ? -1 : 1))
  // seed the weight suggestions from the newest imported set of each lift
  fresh.forEach(w => w.entries.forEach(e => {
    const mx = Math.max(0, ...e.sets.map(s => s.w || 0), e.topW || 0)
    if (mx > 0) { const cur = S.exWeights[e.id]; if (!cur || w.d >= cur.d) S.exWeights[e.id] = { w: mx, d: w.d } }
  }))
  return { added: fresh.length, skipped: parsed.workouts.length - fresh.length }
}
