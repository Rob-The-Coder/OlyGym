// Turn one week of the coach's sheet into routines this app can train.
//
// Two steps, kept apart on purpose. `reviewWeek` reads a sheet and proposes an exercise, a rep
// scheme, a load and a note for every row — nothing is written, and the review screen renders
// exactly this. `bundleFromWeek` turns the reviewed week into the same plan bundle a shared plan
// file uses, so importing it goes through parsePlan/mergePlan: the routines arrive as new
// routines, a custom exercise the coach asked for is created once, and the weights are converted
// to whatever unit the account is in.
//
// What the sheet says is kept where the catalogue has no word for it. A load written as a
// sentence ("Trova peso congruo", "se ok ultime due a 75kg") becomes a note, and only a load the
// text *starts* with becomes a weight: "poi togli 10kg" is an instruction, not a 10 kg bar, and a
// wrong number nobody reads twice is worse than a note that says what the coach actually wrote.

import { uid, todayISO } from './format.js'
import { readCoachSheet, splitComplex } from './coach-sheet.js'
import { matchName } from './plan-aliases.js'
import { NOTE_MAX } from './history.js'

export const DEFAULT_SETS = 3
export const DEFAULT_REPS = 10

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
  if (parsed.length === count) return { schemes: parsed, note: '' }
  if (parsed.length === 1) return { schemes: Array(count).fill(parsed[0]), note: '' }
  if (parsed.length > count) {
    return { schemes: parsed.slice(0, count), note: String(repsText).trim() }
  }
  // "3+1" over four components is three of the first and one of the last.
  const filled = [...Array(count - parsed.length).fill(parsed[0]), ...parsed]
  return { schemes: filled, note: String(repsText).trim() }
}

const joinNote = parts => parts.filter(Boolean).join(' · ').slice(0, NOTE_MAX)

/**
 * Read one week and propose, for every row, an exercise and how to train it.
 *
 *   { name, label, days: [{ n, name, entries: [...] }], skipped, stats }
 *
 * Every entry carries the coach's own row (`raw`, `row`) next to what was understood (`tier`,
 * `exact`, `warns`), because this is what the review screen shows and what the user corrects.
 */
export function reviewWeek(sheet, { aliases = {} } = {}) {
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
        entries.push({
          key: `${day.n}:${row.row}:${i}`,
          row: row.row,
          raw: row.name,
          part: item.raw,
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
        })
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
 * The reviewed week as a plan bundle — the same shape a shared plan file has, so it goes in
 * through parsePlan/mergePlan and arrives as new routines with fresh ids.
 *
 *   days   weekday numbers (0 = Sunday) to put the routines on, in order, or null for none
 */
export function bundleFromWeek(review, { unit = 'kg', name = '', days = null } = {}) {
  const made = new Map()
  const routines = review.days.map(day => {
    const ex = day.entries.map(entry => {
      const out = { id: entry.custom ? customOf(review, entry, made) : entry.id, sets: entry.sets }
      if (entry.mode === 'time') { out.mode = 'time'; out.sec = entry.sec || 45 } else { out.reps = entry.reps || DEFAULT_REPS }
      if (entry.weight) out.weight = entry.weight
      if (entry.side) out.side = true
      if (entry.sg) out.sg = entry.sg
      if (entry.note) out.note = entry.note
      return out
    })
    return { id: uid(), name: day.name, ex }
  })
  const week = {}
  if (days) routines.forEach((r, i) => {
    const d = days[i]
    if (d == null) return
    week[d] = [].concat(week[d] || [], r.id)
  })
  return {
    opengym_plan: 1,
    exported: todayISO(),
    name: name || review.label,
    unit,
    week,
    routines,
    customEx: [...made.values()]
  }
}
