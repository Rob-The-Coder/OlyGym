// Turn one week of the coach's sheet into a week of the dated plan.
//
// Two steps, kept apart on purpose. `reviewWeek` reads a sheet and proposes an exercise, a rep
// scheme, a load and a note for every row — nothing is written, and the review screen renders
// exactly this. `bundleFromWeek` turns the reviewed week into a week of `S.weeks`: each of the
// coach's days becomes a day on Monday/Wednesday/Friday, a custom exercise the coach asked for
// is created once, and the weights are converted to whatever unit the account is in. The screen
// merges it straight into state with `mergeWeek` — the review is already local, so nothing is
// re-serialised through a plan file.
//
// What the sheet says is kept where the catalogue has no word for it. A load written as a
// sentence ("Trova peso congruo", "se ok ultime due a 75kg") becomes a note, and only a load the
// text *starts* with becomes a weight: "poi togli 10kg" is an instruction, not a 10 kg bar, and a
// wrong number nobody reads twice is worse than a note that says what the coach actually wrote.

import { uid, todayISO, isoOf, startOfWeek, MONDAY } from './format.js'
import { readCoachSheet, splitComplex } from './coach-sheet.js'
import { matchName } from './plan-aliases.js'
import { convertedExercise } from './plan-share.js'
import { NOTE_MAX, normalizeSetWeights } from './history.js'

export const DEFAULT_SETS = 3
export const DEFAULT_REPS = 10

// Monday, Wednesday, Friday first — where a three-day program normally goes — then the other
// weekdays in order (see migrate-weeks.js's weekDayForPosition).
export const WEEK_PLAN = [1, 3, 5, 2, 4, 6, 0]

// "Settimana 23-29 marzo 2026" is a week, not a routine name: the day and the dates are enough.
export function sheetLabel(name) {
  return String(name == null ? '' : name)
    .replace(/^settimana\s+/i, '')
    .replace(/\s+\d{4}\s*$/, '')
    .trim()
}

// "50kg, se ok ultime due a 75kg" is a 50 kg bar with an instruction; "poi togli 10kg" is only an
// instruction, and so is "1RM". A number only counts as a load when the text opens with it and it
// is not the "1" of "1RM" or the "5" of "5 minuti".
const LOAD_KG = /^\s*(?:max\s*)?(\d+(?:[.,]\d+)?)(?:\s*-\s*\d+(?:[.,]\d+)?)?\s*(?:kg|k)\b/i
const LOAD_PLAIN = /^\s*(?:max\s*)?(\d+)(?:\s*-\s*\d+)?(?=[,.\s]|$)/i
const NOT_LOAD = /^\s*(?:max\s*)?\d+(?:[.,]\d+)?\s*(?:rm\b|rep|serie|minut|secondi|per lato)/i

/**
 * The load the text starts with, or null when it opens with something else.
 *
 * The comma is a decimal separator only in front of a unit: "2,5kg" is two and a half kilos, while
 * the coach's "90,95 95" is the three loads of the day, and only the first one is the set's weight.
 */
export function loadWeight(text) {
  const s = String(text == null ? '' : text).trim()
  if (!s || NOT_LOAD.test(s)) return null
  const m = LOAD_KG.exec(s) || LOAD_PLAIN.exec(s)
  if (!m) return null
  const n = Number(m[1].replace(',', '.'))
  return Number.isFinite(n) && n > 0 ? n : null
}

// One set scheme: "4", "4.0", "30 secondi", "1 minuto", "10 per lato", "Amrap", "Trova 5RM".
function schemeOf(token) {
  const s = String(token == null ? '' : token).trim()
  if (!s) return null
  const num = /(\d+(?:[.,]\d+)?)/
  if (/^amrap/i.test(s)) return { reps: DEFAULT_REPS, amrap: true }
  if (/(?:trova|cerca)\s*\d+\s*rm/i.test(s)) return { reps: Number(num.exec(s)[1]) || DEFAULT_REPS, rmax: true }
  if (/second/i.test(s)) { const m = num.exec(s); return m ? { mode: 'time', sec: Math.round(Number(m[1])) } : null }
  if (/minut|\bmin\b/i.test(s)) { const m = num.exec(s); return m ? { mode: 'time', sec: Math.round(Number(m[1]) * 60) } : null }
  if (/per lato/i.test(s)) { const m = num.exec(s); return m ? { reps: Math.round(Number(m[1])), side: true } : null }
  if (/^\d+(?:[.,]\d+)?$/.test(s)) return { reps: Math.round(Number(s.replace(',', '.'))) }
  if (/rep/i.test(s)) { const m = num.exec(s); return m ? { reps: Math.round(Number(m[1])) } : null }
  return null
}

/**
 * One scheme per exercise in the row. A complex writes one number per component ("1+2+1+1"), but
 * the coach also writes "1+1" for a single clean and jerk and "3" for a whole complex, so: one
 * number covers every component, a short list repeats its last, a long one is cut and reported.
 *
 *   { schemes: [...], note: '' }   // `note` is the scheme worth keeping when it did not line up
 */
export function schemesFor(repsText, count) {
  const tokens = String(repsText == null ? '' : repsText).split('+').map(s => s.trim())
  const parsed = tokens.map(schemeOf)
  if (!count) return { schemes: [], note: '' }
  // A rep max or an AMRAP is a scheme the plan has no field for: the count is kept (five reps for
  // "Trova 5RM", ten for "Amrap"), but the coach's own words go in the note so the review shows
  // them instead of silently turning "Amrap" into ten reps.
  const flagged = parsed.some(s => s && (s.amrap || s.rmax))
  const keep = flagged ? String(repsText == null ? '' : repsText).trim() : ''
  if (parsed.length === count) return { schemes: parsed, note: keep }
  if (parsed.length === 1) return { schemes: Array(count).fill(parsed[0]), note: keep }
  if (parsed.length > count) {
    return { schemes: parsed.slice(0, count), note: String(repsText).trim() }
  }
  // "3+1" over four components is three of the first and one of the last.
  const filled = [...Array(count - parsed.length).fill(parsed[0]), ...parsed]
  return { schemes: filled, note: String(repsText).trim() }
}

const joinNote = parts => parts.filter(Boolean).join(' · ').slice(0, NOTE_MAX)

/**
 * The coach's own row, as he wrote it: "Strappo sosp alta · 4.0 × 1+2 · 50kg".
 *
 * One string with two readers: the review prints it under what it read, and `bundleFromWeek` keeps
 * it on the exercise so the week editor can show it after the review sheet is gone. Display only —
 * nothing reads it back as data.
 */
export function coachSource({ raw, setsText, reps, load }) {
  // Excel hands a whole number back as "4.0" when the column is formatted that way. It is the same
  // four, and the review is not showing the workbook back — it is showing the coach his plan. Only a
  // cell that is nothing but a number with a zero fraction is touched: "1+2", "1 minuto", "100kg"
  // and "80,95 95" stay exactly as he wrote them.
  const n = text => String(text == null ? '' : text).trim().replace(/^(\d+)[.,]0+$/, '$1')
  const scheme = [setsText, reps].map(n).filter(Boolean).join(' × ')
  return [raw, scheme, n(load)].filter(Boolean).join(' · ')
}

// The prescription keys a number correction may touch. The rest of an entry is what the
// correction is not about: his words, the exercise it was read as, the note and the warnings that
// are not about a number.
const CORRECTION_NUMBERS = ['sets', 'reps', 'sec']

/**
 * One review entry with a numeric correction merged in, as a new object.
 *
 * The review screen lets the user fix the sets, reps, seconds and load a row was read as; this is
 * the one place that merge happens, so the screen and `bundleFromWeek` cannot disagree about what
 * a correction means. The entry is derived, never stored: the one given is not mutated (nor its
 * `warns` array), an unknown key is ignored, and so is any value that is not a finite number (a
 * string, a boolean, an array, a Symbol — `Number()` would accept or throw on those, and they are
 * not counts).
 *
 * A correction answers the warning about it — once a set count has been typed, "no sets given" is
 * no longer true and a chip saying otherwise would contradict the number right above it.
 */
export function applyEntryCorrection(entry, patch) {
  if (!entry || typeof entry !== 'object') return entry
  const out = { ...entry, warns: Array.isArray(entry.warns) ? [...entry.warns] : [] }
  if (!patch || typeof patch !== 'object') return out
  const merged = {}
  for (const key of CORRECTION_NUMBERS) {
    const raw = patch[key]
    // Only a finite number is a correction. Coercing is wrong: Number(null) is 0, Number(true) is
    // 1, Number([7]) is 7, and Number(Symbol()) throws.
    if (typeof raw !== 'number' || !Number.isFinite(raw)) continue
    // A set/reps/second count is a whole number of at least one, whatever the field handed over.
    merged[key] = Math.max(1, Math.round(raw))
  }
  const weight = patch.weight
  const weightTouched = typeof weight === 'number' && Number.isFinite(weight)
  if (weightTouched) merged.weight = Math.max(0, weight)
  Object.assign(out, merged)
  if (merged.sets != null) out.warns = out.warns.filter(w => w !== 'sets')
  if (merged.reps != null || merged.sec != null) out.warns = out.warns.filter(w => w !== 'reps')
  // The load is one number or one ladder, never both: a weight correction clears the ladder, a
  // ladder correction decides every set (setting the uniform weight to its first value), and a
  // set-count correction resizes whatever ladder the entry already carried.
  if (weightTouched && !Array.isArray(patch.setWeights)) delete out.setWeights
  if (Array.isArray(patch.setWeights)) out.setWeights = patch.setWeights
  if (Array.isArray(out.setWeights)) {
    const values = out.setWeights
      .filter(v => typeof v === 'number' && Number.isFinite(v))
      .map(v => Math.max(0, v))
    if (values.length) {
      const ladder = normalizeSetWeights(values, out.sets, out.weight)
      if (ladder) {
        out.setWeights = ladder
        out.weight = ladder[0]
      } else {
        delete out.setWeights
        out.weight = values[0]
      }
    } else delete out.setWeights
  }
  return out
}

/**
 * Read one week and propose, for every row, an exercise and how to train it.
 *
 *   { name, label, days: [{ n, name, entries: [...] }], skipped, stats }
 *
 * Every entry carries the coach's own row (`raw`, `row`, `src`) next to what was understood (`tier`,
 * `exact`, `warns`), because this is what the review screen shows and what the user corrects.
 *
 * `corrections` maps an entry's `key` to a numeric patch (`sets`, `reps`/`sec`, `weight` or a
 * `setWeights` ladder) applied through `applyEntryCorrection` before the entry is returned: the
 * review is derived on every read, so a corrected number is re-derived exactly like a corrected
 * exercise name. Nothing in `sheet` or `corrections` is mutated.
 */
export function reviewWeek(sheet, { aliases = {}, corrections = {} } = {}) {
  const { days, skipped } = readCoachSheet(sheet.grid)
  const label = sheetLabel(sheet.name)
  const stats = { rows: 0, exercises: 0, custom: 0, fuzzy: 0, warned: 0 }
  const out = []

  for (const day of days) {
    const entries = []
    for (const row of day.entries) {
      stats.rows++
      const { items } = matchName(row.name, aliases)
      if (!items.length) continue
      const { schemes, note: schemeNote } = schemesFor(row.reps, items.length)
      const weight = loadWeight(row.load)
      // The load text is worth keeping unless it was nothing but the number already taken.
      const loadNote = row.load && !(weight != null && /^\s*(?:max\s*)?\d+(?:[.,]\d+)?\s*(?:kg|k)?\s*$/i.test(row.load)) ? row.load : ''
      items.forEach((item, i) => {
        const scheme = schemes[i] || {}
        const warns = []
        if (!row.sets) warns.push('sets')
        if (!row.reps) warns.push('reps')
        if (row.reps && !scheme.reps && !scheme.mode) warns.push('reps')
        stats.exercises++
        if (item.tier === 3) stats.custom++
        if (!item.exact) stats.fuzzy++
        if (warns.length) stats.warned++
        const entry = {
          key: `${day.n}:${row.row}:${i}`,
          row: row.row,
          raw: row.name,
          part: item.raw,
          // His row, minus whatever this row already prints: his own words are the title when the
          // catalogue had nothing for them and are in the note when the matcher kept them, and the load
          // sentence is the note when it could not become a number. What is left is what he wrote that
          // is not on screen yet, and printing it twice in one row is what the review is not for.
          src: coachSource({
            raw: item.tier === 3 || String(item.note || '').includes(item.raw) ? '' : item.raw,
            setsText: row.setsText,
            reps: row.reps,
            load: loadNote ? '' : row.load
          }),
          tier: item.tier,
          exact: item.exact,
          id: item.id,
          name: item.name,
          bp: item.bp,
          custom: item.tier === 3,
          sets: row.sets || DEFAULT_SETS,
          reps: scheme.reps != null ? scheme.reps : (scheme.mode ? null : DEFAULT_REPS),
          mode: scheme.mode || null,
          sec: scheme.sec || null,
          side: !!scheme.side,
          weight,
          sg: items.length > 1 ? `coach-${day.n}-${row.row}` : null,
          note: joinNote([item.note, schemeNote, loadNote, row.cue, row.comment]),
          warns
        }
        // A number the user fixed is applied here, on the same derived entry the screen draws:
        // the row above his own row then shows what will actually be imported.
        entries.push(corrections[entry.key] ? applyEntryCorrection(entry, corrections[entry.key]) : entry)
      })
    }
    if (entries.length) out.push({ n: day.n, name: `Giorno ${day.n}${label ? ' · ' + label : ''}`, entries })
  }
  return { name: sheet.name, label, days: out, skipped, stats }
}

/** The bundle's custom exercise for a name, created once however many weeks use it. */
function customOf(review, entry, made) {
  const name = entry.name.trim()
  const key = name.toLowerCase()
  if (!made.has(key)) {
    const ex = { id: 'coach-' + made.size, n: name, bp: entry.bp }
    made.set(key, ex)
  }
  return made.get(key).id
}

/**
 * The reviewed week as one week of `S.weeks` — the coach's days are scheduled Monday/Wednesday/
 * Friday (or the `days` list given), each carrying the exercise configs a routine would have had.
 *
 *   unit   the account's unit; the sheet itself is always kilos, so the weights are converted here
 *   days   weekday numbers (0 = Sunday), in order, to put the days on; defaults to WEEK_PLAN
 *
 * The returned week carries `customEx` — the user's own exercises it references, consumed and
 * stripped by mergeWeek. The review is already local, so there is no plan file to parse.
 */
export function bundleFromWeek(review, { unit = 'kg', name = '', days = null } = {}) {
  const made = new Map()
  const dows = days && days.length ? days : WEEK_PLAN
  const out = review.days.map((day, i) => {
    const ex = day.entries.map(entry => {
      const cfg = { id: entry.custom ? customOf(review, entry, made) : entry.id, sets: entry.sets }
      if (entry.mode === 'time') { cfg.mode = 'time'; cfg.sec = entry.sec || 45 } else { cfg.reps = entry.reps || DEFAULT_REPS }
      if (entry.weight) cfg.weight = entry.weight
      // The reviewed ladder goes into the week as the plan's per-set load; its first value is
      // the one the uniform weight shows, so the two can never disagree.
      if (Array.isArray(entry.setWeights) && entry.setWeights.length) {
        cfg.setWeights = entry.setWeights
        cfg.weight = entry.weight ?? cfg.setWeights[0]
      }
      if (entry.side) cfg.side = true
      if (entry.sg) cfg.sg = entry.sg
      if (entry.note) cfg.note = entry.note
      // The note is what he asked for; this is the row it was read out of, kept so the week editor
      // can still show it once the review sheet is gone.
      if (entry.src) cfg.src = entry.src
      return convertedExercise(cfg, 'kg', unit)
    })
    return { dow: dows[i] ?? WEEK_PLAN[i % WEEK_PLAN.length], name: day.name, ex }
  })
  return {
    id: uid(),
    startIso: isoOf(startOfWeek(todayISO(), MONDAY)),
    name: name || review.label,
    days: out,
    customEx: [...made.values()]
  }
}
