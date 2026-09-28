#!/usr/bin/env node
/**
 * Build frontend/src/lib/hevy-id-map.js — a complete, deterministic
 * Hevy template-id → openGym catalogue-id table.
 *
 * Usage:
 *   HEVY_API_KEY=… node scripts/build-hevy-id-map.mjs
 *   # or put HEVY_API_KEY in .env and run: node scripts/build-hevy-id-map.mjs
 *   # or: node scripts/build-hevy-id-map.mjs /path/to/templates.json
 *
 * Import resolution uses ONLY this table (lookup by template id). Titles are
 * never guessed at runtime — regenerate this file when Hevy adds templates.
 * End users paste their own key in Settings; this env var is only for regenerating
 * the committed map.
 */
import { writeFileSync, readFileSync, existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import { matchExercise } from '../frontend/src/lib/import-csv.js'
import { HEVY_ID_MAP, HEVY_TITLE_MAP } from '../frontend/src/lib/hevy-id-map.js'
import { EXIDX } from '../frontend/src/lib/exercises.js'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const OUT = join(ROOT, 'frontend/src/lib/hevy-id-map.js')
const HEVY_API = 'https://api.hevyapp.com'

/** Read HEVY_API_KEY from the environment, or from a local `.env` if present. */
function hevyApiKey() {
  const fromEnv = (process.env.HEVY_API_KEY || '').trim()
  if (fromEnv) return fromEnv
  const envPath = join(ROOT, '.env')
  if (!existsSync(envPath)) return ''
  const m = readFileSync(envPath, 'utf8').match(/^HEVY_API_KEY\s*=\s*(.*)$/m)
  if (!m) return ''
  return m[1].trim().replace(/^['"]|['"]$/g, '')
}

/**
 * Exact Hevy English title (lowercased) → catalogue id.
 * Only entries we have verified against EXIDX. Gaps (hip thrust, rower, …)
 * are intentionally omitted so they import as custom exercises.
 */
// Titles whose closest catalogue entry is a different movement — better a custom exercise than
// a wrong one. Keep in sync with the note at the top of the generated map.
const NEVER_BY_TITLE = new Set(['crunch', 'side plank', 'squat (machine)', 'triceps extension (cable)', 'rear delt reverse fly (cable)'])

// Curated Hevy titles, kept as titles and NOT as ids on purpose: the OlyGym catalogue is
// regenerated from the Catalyst CSV (scripts/oly-catalogue/), so an id written here is a time
// bomb — the previous table had 283 template ids and every one of them pointed at the retired
// dataset. A title that resolves today resolves tomorrow.
//
// Every title below goes through the same matcher the CSV import uses, so this table can only
// ever produce ids the catalogue actually has.
const BY_TITLE_TITLES = [
  'ab wheel',
  'back extension (weighted hyperextension)',
  'battle ropes',
  'behind the back bicep wrist curl (barbell)',
  'bench press (barbell)',
  'bench press (dumbbell)',
  'bench press (smith machine)',
  'bent over row (barbell)',
  'bent over row (dumbbell)',
  'bicep curl (barbell)',
  'bicep curl (cable)',
  'bicep curl (dumbbell)',
  'bulgarian split squat (barbell)',
  'bulgarian split squat (dumbbell)',
  'butterfly (pec deck)',
  'cable crunch',
  'cable fly crossovers',
  'cable pallof press',
  'chest dip',
  'chest fly (dumbbell)',
  'chest fly (machine)',
  'chest supported incline row (dumbbell)',
  'chest supported reverse fly (dumbbell)',
  'chin up',
  'chin-up',
  'concentration curl (dumbbell)',
  'cycling',
  'deadlift (barbell)',
  'elliptical trainer',
  'exercise bike',
  'face pull',
  'front squat (barbell)',
  'goblet squat (dumbbell)',
  'goblet squat (kettlebell)',
  'good morning (barbell)',
  'hack squat (machine)',
  'hammer curl (dumbbell)',
  'hanging knee raise',
  'hanging leg raise',
  'incline bench press (barbell)',
  'incline bench press (dumbbell)',
  'incline chest fly (dumbbell)',
  'iso-lateral row (machine)',
  'jumping jack',
  'jumping jacks',
  'kickback (dumbbell)',
  'knee raise parallel bars',
  'lat pulldown (cable)',
  'lat pulldown - close grip (cable)',
  'lat pulldown - wide grip (cable)',
  'lateral raise (dumbbell)',
  'leg extension (machine)',
  'leg press (machine)',
  'lunge (dumbbell)',
  'lunges (dumbbell)',
  'lying leg curl (machine)',
  'overhead press (barbell)',
  'overhead press (dumbbell)',
  'overhead press (smith machine)',
  'pallof press',
  'plank',
  'plate front raise',
  'preacher curl (barbell)',
  'pull up',
  'pull-up',
  'push up',
  'push-up',
  'rear delt reverse fly (dumbbell)',
  'rear delt reverse fly (machine)',
  'reverse grip lat pulldown (cable)',
  'reverse lunge (barbell)',
  'reverse lunge (dumbbell)',
  'romanian deadlift (barbell)',
  'romanian deadlift (dumbbell)',
  'russian twist',
  'seated cable row - bar grip',
  'seated calf raise (machine)',
  'seated incline curl (dumbbell)',
  'seated leg curl (machine)',
  'seated shoulder press (machine)',
  'shoulder press (barbell)',
  'shoulder press (dumbbell)',
  'shoulder press (machine)',
  'shrug (barbell)',
  'shrug (dumbbell)',
  'single arm lateral raise (cable)',
  'single leg standing calf raise (machine)',
  'skull crusher (barbell)',
  'skullcrusher (barbell)',
  'squat (barbell)',
  'stair machine',
  'stair machine (steps)',
  'standing calf raise (dumbbell)',
  'standing calf raise (machine)',
  'stationary bike',
  'sumo deadlift (barbell)',
  'treadmill',
  'tricep kickback (dumbbell)',
]

const CURATED = new Set(BY_TITLE_TITLES.map(t => t.toLowerCase()))

/** Catalogue exercise name (lowercased) → id, for the exact-name bridge below. */
const NAME_IDX = new Map(Object.values(EXIDX).map(e => [e.n.toLowerCase(), e.id]))

/**
 * Bridge-mode resolution: the title must name a catalogue exercise exactly, with the trailing
 * equipment parenthetical allowed to fall off ("bench press (barbell)" → "bench press").
 *
 * The matcher's fuzzy paths are deliberately NOT used here: run over the whole Hevy vocabulary
 * they turned "clean and press" into "press in clean" and "dumbbell row" into "rle dumbbell row".
 * A wrong lift is worse than a custom exercise, so only an exact name bridges.
 */
function exactId(title) {
  const key = String(title || '').trim().toLowerCase()
  const bare = key.replace(/\s*\([^)]*\)\s*$/, '').trim()
  return NAME_IDX.get(key) || NAME_IDX.get(bare) || null
}

const EQ_PAREN = {
  barbell: 'Barbell', dumbbell: 'Dumbbell', kettlebell: 'Kettlebell',
  machine: 'Machine', resistance_band: 'Band', none: null, other: null,
  plate: 'Plate', suspension: null,
}

function resolve(t) {
  const title = String(t.title || '').trim()
  if (t.exact) return exactId(title)
  const key = title.toLowerCase()
  if (CURATED.has(key)) {
    const id = matchExercise(key)
    if (id && EXIDX[id]) return id
    return null
  }
  const paren = EQ_PAREN[t.equipment]
  const tries = []
  if (paren) tries.push(`${title} (${paren})`)
  tries.push(title)
  const bare = title.replace(/\s*\([^)]*\)\s*$/, '').trim()
  if (bare !== title) {
    if (paren) tries.push(`${bare} (${paren})`)
    tries.push(bare)
  }
  if (t.equipment === 'machine') tries.push(`lever ${bare}`)
  for (const c of tries) {
    const id = matchExercise(c)
    if (id && EXIDX[id]) return id
  }
  return null
}

async function fetchTemplates(apiKey) {
  const items = []
  let page = 1, pageCount = 1
  while (page <= pageCount) {
    const url = `${HEVY_API}/v1/exercise_templates?page=${page}&pageSize=100`
    const res = await fetch(url, { headers: { 'api-key': apiKey } })
    if (!res.ok) throw new Error(`Hevy API ${res.status}`)
    const data = await res.json()
    pageCount = data.page_count || 1
    items.push(...(data.exercise_templates || []))
    page++
  }
  return items
}

/**
 * Template ids out of the committed map, re-resolved through their own titles.
 *
 * Without a Hevy API key the template ids are all we have: the committed map pairs a template id
 * with a catalogue id, and the title map pairs a title with that same catalogue id, so inverting
 * the latter turns each template id back into a title — which the matcher resolves against the
 * catalogue as it stands today. A template whose lift is not in an Olympic catalogue is dropped
 * and imports as a custom exercise.
 */
function bridgedTemplates() {
  const titleOf = new Map()
  for (const [title, id] of Object.entries(HEVY_TITLE_MAP)) if (!titleOf.has(id)) titleOf.set(id, title)
  const out = []
  for (const [id, oldId] of Object.entries(HEVY_ID_MAP)) {
    const title = titleOf.get(oldId)
    if (title) out.push({ id, title, equipment: null, exact: true })
  }
  return out
}

/** Curated titles the catalogue cannot reach — reported, never fatal (they import as custom). */
function reportCurated() {
  const missing = BY_TITLE_TITLES.filter(t => !matchExercise(t))
  if (!missing.length) return
  console.log(`Curated titles with no catalogue match (${missing.length}) — they import as custom:`)
  missing.forEach(t => console.log('  -', t))
}

async function main() {
  reportCurated()

  const arg = process.argv[2]
  let templates
  let bridged = false
  if (arg && existsSync(arg)) {
    templates = JSON.parse(readFileSync(arg, 'utf8'))
  } else {
    const key = hevyApiKey()
    if (key) {
      templates = await fetchTemplates(key)
    } else {
      bridged = true
      templates = bridgedTemplates()
      console.log(`No HEVY_API_KEY — bridging ${templates.length} template ids through their titles.`)
    }
  }

  const map = {}
  const titleMap = {}
  const unmatched = []

  // In bridge mode the template list only carries the titles that are attached to a template id,
  // so the committed title map is the other half of the input: resolve it too, or every
  // regeneration would quietly drop the titles no template happens to name.
  if (bridged) {
    for (const title of Object.keys(HEVY_TITLE_MAP)) {
      const key = title.toLowerCase()
      if (titleMap[key]) continue
      const id = resolve({ title, equipment: null, exact: true })
      if (id) titleMap[key] = id
    }
  }
  for (const t of templates) {
    if (!t?.id || t.is_custom) { if (t?.id && t.is_custom) unmatched.push(t); continue }
    const id = resolve(t)
    if (id) {
      map[t.id] = id
      const titleKey = String(t.title || '').trim().toLowerCase()
      if (titleKey && !NEVER_BY_TITLE.has(titleKey) && !titleMap[titleKey]) titleMap[titleKey] = id
    } else unmatched.push(t)
  }

  // Stable key order for readable diffs.
  const keys = Object.keys(map).sort()
  const lines = keys.map(k => `  ${JSON.stringify(k)}: ${JSON.stringify(map[k])},`)
  const titleKeys = Object.keys(titleMap).sort()
  const titleLines = titleKeys.map(k => `  ${JSON.stringify(k)}: ${JSON.stringify(titleMap[k])},`)

  const how = bridged
    ? `// Rebuilt WITHOUT a Hevy API key: the previous map's template ids were resolved through their
// own titles, accepting only a title that names a catalogue exercise exactly (an equipment
// parenthetical may fall off, so "bench press (barbell)" is the bench press). Anything else is left
// out and imports as a custom exercise: the matcher's fuzzy paths turned "clean and press" into
// "press in clean", and a wrong lift is worse than a custom. Set HEVY_API_KEY and re-run for a full
// regeneration — the API path also reaches the templates this file never had a title for.`
    : '// Regenerated from the Hevy templates API + verified title aliases.'
  const body = `// AUTO-GENERATED by scripts/build-hevy-id-map.mjs — do not edit by hand.
// Hevy exercise_template id → OlyGym catalogue id, plus English title → id for CSV exports
// (Hevy CSV has titles, not template ids).
${how}
// Every id below resolves in the catalogue; anything else imports as a custom exercise.
// ${keys.length} template ids and ${titleKeys.length} titles mapped.

export const HEVY_ID_MAP = {
${lines.join('\n')}
}

/** Lowercased Hevy English title → catalogue id (CSV / name path). */
export const HEVY_TITLE_MAP = {
${titleLines.join('\n')}
}
`

  writeFileSync(OUT, body)
  console.log(`Wrote ${OUT}`)
  console.log(`Mapped ${keys.length} / ${templates.filter(t => !t.is_custom).length} (titles ${titleKeys.length})`)
  console.log(`Unmatched ${unmatched.length} (become custom on import)`)
  if (process.env.VERBOSE) {
    unmatched.slice(0, 40).forEach(t => console.log('  -', t.title, `[${t.equipment}]`))
  }
}

main().catch(e => { console.error(e); process.exit(1) })
