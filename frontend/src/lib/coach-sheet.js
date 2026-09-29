// Read the coach's weekly sheet.
//
// One sheet is one week, and inside a sheet the shape is fixed: column A carries the day marker
// ("Giorno 1"), B the exercise, G the reps, H the sets, I the load or a note, J a cue and P the
// coach's own comment. The same column A also carries the labels of the primer and of the legend
// block at the bottom of the sheet — "Jerk Primer:", "Snatch primer (sequenza che fai di solito)",
// "Plio work" — which are instructions, not exercises.
//
// Two traps the real workbook sets, both handled here rather than by the caller:
//   * the day marker and the day's first entry share a row (row 3 is "Giorno 1" in A *and*
//     "Pogo jump" in B), so a marker row still gets read for an exercise;
//   * the sets column holds whatever landed there: "4.0" is four sets, and one week has a stray
//     date serial (46115) that must not become a 46115-set exercise. Anything outside 1..20 reads
//     as "no sets given" and the importer says so.
//
// Nothing here decides what an exercise *is*: this file says which rows are exercises and hands
// back what they say, verbatim. plan-aliases.js is what turns a name into a catalogue entry.

/** A, B, G, H, I, J, P as 0-based column indexes. */
export const COACH_COLUMNS = { day: 0, name: 1, reps: 6, sets: 7, load: 8, cue: 9, comment: 15 }

const DAY = /^giorno\s*(\d+)/i
const LABEL = /^(due serie|esercizi fisio|jerk primer|plio work|snatch primer)/i
const MAX_SETS = 20

/** The complexes the coach writes with "+": "Strappo + strappo sosp alta" is two exercises. */
export function splitComplex(name) {
  return String(name == null ? '' : name).split('+').map(s => s.trim()).filter(Boolean)
}

/** 4, "4.0" and " 4 " are four sets. "46115.0" and "" are no sets at all. */
export function setsOf(text) {
  const n = Math.round(Number(String(text == null ? '' : text).trim()))
  return Number.isFinite(n) && n >= 1 && n <= MAX_SETS ? n : null
}

/**
 * Split a sheet grid into its days and their entries.
 *
 *   { days: [{ n, entries: [{ row, name, reps, sets, load, cue, comment }] }],
 *     skipped: [{ row, text }] }
 *
 * `row` is 1-based, so it points at the line in the sheet the reader can go and look at.
 * A sheet whose day has no entries keeps the day (the review screen shows three days); a row
 * before any "Giorno" marker belongs to day 1 rather than being dropped.
 */
export function readCoachSheet(grid) {
  const days = []
  const skipped = []
  let day = null
  let pastLegend = false

  const rows = Array.isArray(grid) ? grid : []
  for (let i = 0; i < rows.length; i++) {
    const row = rows[i] || []
    const cell = k => String(row[k] == null ? '' : row[k]).trim()
    const at = cell(COACH_COLUMNS.day)
    const name = cell(COACH_COLUMNS.name)

    const marker = DAY.exec(at)
    if (marker) {
      // A day marker seen twice (a program split over two blocks in one sheet) continues the
      // same day instead of shadowing it with an empty second one.
      const n = Number(marker[1])
      day = days.find(d => d.n === n) || null
      if (!day) { day = { n, entries: [] }; days.push(day) }
      pastLegend = false
    } else if (at) {
      // "Jerk Primer:" and the four labels under it. Everything from here down is reference
      // material: a row's worth of B text below it is a legend, not the day's next exercise.
      pastLegend = true
      skipped.push({ row: i + 1, text: at })
    }

    if (!name) continue
    if (pastLegend || LABEL.test(name)) { skipped.push({ row: i + 1, text: name }); continue }
    if (!day) { day = { n: 1, entries: [] }; days.push(day) }

    day.entries.push({
      row: i + 1,
      name,
      reps: cell(COACH_COLUMNS.reps),
      sets: setsOf(cell(COACH_COLUMNS.sets)),
      load: cell(COACH_COLUMNS.load),
      cue: cell(COACH_COLUMNS.cue),
      comment: cell(COACH_COLUMNS.comment)
    })
  }
  return { days, skipped }
}
