// Share a plan.
//
// The printable page (Save as PDF) where a single exercise never splits across a page break —
// each exercise, and each day that fits, stays in one place.
//
// The dated-weeks model (see lib/weeks.js) is what a plan is. A week that is already local and
// trusted (the coach's reviewed sheet) lands in the plan through `mergeWeek`.

import { EXIDX } from './exercises.js'
import { modeOf, fmtSec, isBw } from './history.js'
import { uid, todayISO, isoOf, startOfWeek, fmtDate, DAYN, MONDAY, fmtNum, exCount } from './format.js'
import { t, exerciseNameFor } from './i18n-core.js'
import { convertWeight } from './units.js'
import { MUSCLES, inMuscleOrder } from './muscles.js'

const PLAN_UNITS = new Set(['kg'])

// Kilos only. Missing unit is deliberately legacy-compatible: old files were read as already
// being in the recipient's unit, so keep their values unchanged.
const planUnit = value => PLAN_UNITS.has(value) ? value : null
const unitError = () => { throw new Error(t('this isn’t an OlyGym plan file')) }
const currentMonday = () => isoOf(startOfWeek(todayISO(), MONDAY))
const isIsoDay = v => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v)

/** A load written in `sourceUnit` as the same prescription in `destinationUnit`. */
export function convertedExercise(e, sourceUnit, destinationUnit) {
  if (!sourceUnit || sourceUnit === destinationUnit) return e
  const out = { ...e }
  if (out.weight != null) out.weight = convertWeight(out.weight, sourceUnit, destinationUnit)
  // A timed increment is seconds, not a load. Rep-mode increments are load overrides.
  if (modeOf(out) === 'reps' && out.inc > 0) out.inc = convertWeight(out.inc, sourceUnit, destinationUnit)
  return out
}

// The muscles the map can draw.
const CUSTOM_MUSCLES = new Set(MUSCLES)
const muscleList = v => inMuscleOrder([...new Set((Array.isArray(v) ? v : []).filter(m => CUSTOM_MUSCLES.has(m)))])

/** A custom exercise with its own metadata — equipment, muscles, description — in the shape
 *  CustomExForm writes, minus the recipient-side `custom`/`sm` fields mergeWeek adds. */
function cleanCustom(c) {
  const o = { id: c.id, n: c.n, bp: c.bp }
  if (c.desc) o.desc = c.desc
  if (typeof c.eq === 'string' && c.eq) o.eq = c.eq
  const prim = muscleList(c.primaries)
  const sm = muscleList(c.secondaries).filter(m => !prim.includes(m))
  // `tg` is the legacy single primary; the form keeps it equal to the first primary.
  const tg = prim[0] || (CUSTOM_MUSCLES.has(c.tg) ? c.tg : '')
  if (tg) o.tg = tg
  if (prim.length) o.primaries = prim
  if (sm.length) o.secondaries = sm
  if (prim.length || sm.length) o.muscleGroups = [...prim, ...sm]
  return o
}

/* -------------------------------- merging -------------------------------- */

/**
 * Reuse a custom you already have under the same name + body part, else add it fresh, and
 * return the old-id → new-id map the exercises are remapped through. Stored exactly as the
 * form would have created it — `custom: true` is what lets the recipient edit or delete it,
 * and `sm` mirrors the secondaries the way the form writes them.
 */
function addCustomEx(s, customs) {
  s.customEx = s.customEx || []
  const exIdMap = {}
  ;(customs || []).forEach(c => {
    const same = s.customEx.find(x => (x.n || '').toLowerCase() === (c.n || '').toLowerCase() && x.bp === c.bp)
    if (same) { exIdMap[c.id] = same.id; return }
    const nid = uid()
    exIdMap[c.id] = nid
    const clean = cleanCustom(c)
    s.customEx.push({ ...clean, id: nid, ...(clean.secondaries ? { sm: clean.secondaries } : {}), custom: true })
  })
  return exIdMap
}

/** Append one week to `s`, with a fresh id and every exercise pointed at the merged customs. */
function pushWeek(s, week, exIdMap) {
  s.weeks = s.weeks || []
  s.weeks.push({
    id: uid(),
    startIso: isIsoDay(week.startIso) ? week.startIso : currentMonday(),
    name: week.name || '',
    days: (week.days || []).map(d => ({
      dow: d.dow,
      name: d.name || '',
      ...(d.excludeFromProgression === true ? { excludeFromProgression: true } : {}),
      ex: (d.ex || []).map(e => ({ ...e, id: exIdMap[e.id] || e.id }))
    }))
  })
}

/**
 * Merge one already-local week (the coach's reviewed sheet) into state.
 *  - customs: reuse one you already have with the same name + body part, else add it fresh
 *  - the week: appended as a NEW week (fresh id) — never overwrites yours
 *
 * A week built for this path carries its own `customEx` (see import-plan.js) — the id remap
 * is what keeps them from doubling.
 */
export function mergeWeek(s, week) {
  const destination = planUnit(s.unit == null ? 'kg' : s.unit)
  if (!destination) unitError()
  const exIdMap = addCustomEx(s, week.customEx)
  pushWeek(s, week, exIdMap)
  return { weeks: 1 }
}

/* ------------------------------- printable PDF ------------------------------- */

const esc = str => String(str == null ? '' : str)
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

// One exercise's scheme, e.g. "3 × 10 · 60 kg" or "3 × 0:45".
function scheme(e, unit) {
  const sets = e.sets || 1
  let s = modeOf(e) === 'time' ? `${sets} × ${fmtSec(e.sec || 45)}` : `${sets} × ${e.reps ?? 10}`
  if (e.weight) s += ` · ${isBw(e) ? '+' : ''}${fmtNum(e.weight)} ${unit}`
  return s
}

// Group consecutive exercises sharing a superset id into rendered units.
function units(ex) {
  const out = []
  ex.forEach((e, i) => {
    const prev = ex[i - 1]
    if (i > 0 && e.sg && prev?.sg === e.sg) out[out.length - 1].push(e)
    else out.push([e])
  })
  return out
}

function dayHTML(day, unit) {
  const rows = units(day.ex || []).map(u => {
    const items = u.map(e => {
      const ex = EXIDX[e.id]
      const name = ex ? exerciseNameFor(ex) : t('Unknown exercise')
      const part = ex && ex.bp ? `<span class="part">${esc(ex.bp)}</span>` : ''
      const note = e.note ? `<div class="ex-note">${esc(e.note)}</div>` : ''
      return `<div class="ex"><div class="ex-row"><div class="ex-n">${esc(name)}${part}</div><div class="ex-s">${esc(scheme(e, unit))}</div></div>${note}</div>`
    }).join('')
    return u.length > 1
      ? `<div class="ss"><div class="ss-tag">${esc(t('Complex'))}</div><div class="ss-items">${items}</div></div>`
      : items
  }).join('')
  const count = exCount((day.ex || []).length)
  const title = day.name || t(DAYN[day.dow] ?? '')
  return `<section class="routine">
    <div class="r-head"><h2>${esc(title)}</h2><span class="r-count">${esc(count)}</span></div>
    <div class="ex-list">${rows || `<div class="ex empty">${esc(t('No exercises yet.'))}</div>`}</div>
  </section>`
}

// One dated week: its own name (or the date it starts on), then each of its days.
function weekHTML(week, unit) {
  const days = (week.days || []).filter(d => d.ex && d.ex.length)
  const title = week.name || t('Week of {0}', fmtDate(week.startIso, false, true))
  const body = days.length
    ? days.map(d => dayHTML(d, unit)).join('')
    : `<p class="none">${esc(t('No workouts planned this week.'))}</p>`
  return `<section class="week-block">
    <div class="wk-head"><h2>${esc(title)}</h2><span class="wk-date">${esc(fmtDate(week.startIso, false, true))}</span></div>
    ${body}
  </section>`
}

/** Full self-contained HTML for the print/PDF view. */
export function planPrintHTML(S, owner) {
  const unit = S.unit || 'kg'
  const weeks = (S.weeks || []).filter(w => (w.days || []).some(d => d.ex && d.ex.length))
  const body = weeks.length
    ? weeks.map(w => weekHTML(w, unit)).join('')
    : `<p class="none">${esc(t('No routines yet.'))}</p>`
  const sub = [owner, todayISO()].filter(Boolean).map(esc).join(' · ')
  return `<!doctype html><html><head><meta charset="utf-8">
<title>${esc(t('Weekly Training Plan'))}</title>
<style>
  @page { margin: 16mm 15mm; }
  * { box-sizing: border-box; }
  html { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  body {
    margin: 0; color: #16181d; background: #fff;
    font: 14px/1.5 -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    font-variant-numeric: tabular-nums;
  }
  .doc { max-width: 720px; margin: 0 auto; }
  header { border-bottom: 2px solid #16181d; padding-bottom: 12px; margin-bottom: 20px; }
  header .kicker { font-size: 11px; letter-spacing: .14em; text-transform: uppercase; color: #6a7a3a; font-weight: 700; }
  header h1 { font-size: 27px; letter-spacing: -.02em; margin: 3px 0 0; }
  header .sub { color: #6b7180; font-size: 13px; margin-top: 4px; }

  h3.block { font-size: 12px; letter-spacing: .1em; text-transform: uppercase; color: #8a90a0; margin: 0 0 8px; font-weight: 700; }

  .week-block { margin-bottom: 26px; }
  .wk-head { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; border-bottom: 1px solid #e4e6ec; padding-bottom: 6px; margin-bottom: 12px; break-after: avoid; page-break-after: avoid; }
  .wk-head h2 { font-size: 19px; letter-spacing: -.01em; margin: 0; text-transform: capitalize; }
  .wk-date { font-size: 12px; color: #8a90a0; white-space: nowrap; }

  .routine { break-inside: avoid; page-break-inside: avoid; margin-bottom: 20px; padding: 14px 16px; border: 1px solid #e4e6ec; border-radius: 12px; }
  .r-head { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; border-bottom: 1px solid #eef0f4; padding-bottom: 8px; margin-bottom: 8px; break-after: avoid; page-break-after: avoid; }
  .r-head h2 { font-size: 18px; letter-spacing: -.01em; margin: 0; text-transform: capitalize; }
  .r-count { font-size: 12px; color: #8a90a0; white-space: nowrap; }

  .ex-list { display: flex; flex-direction: column; }
  .ex { display: flex; flex-direction: column; padding: 6px 0; break-inside: avoid; page-break-inside: avoid; }
  .ex + .ex, .ss + .ex, .ex + .ss { border-top: 1px solid #f2f3f6; }
  .ex-row { display: flex; align-items: baseline; justify-content: space-between; gap: 14px; }
  .ex-n { text-transform: capitalize; font-weight: 500; }
  .ex-n .part { text-transform: capitalize; color: #9aa0ae; font-weight: 400; font-size: 12px; margin-left: 8px; }
  .ex-s { color: #3d424e; white-space: nowrap; font-variant-numeric: tabular-nums; }
  .ex-note { color: #6a7080; font-size: 12px; margin-top: 2px; }
  .ex.empty, .none { color: #a2a8b6; }

  .ss { break-inside: avoid; page-break-inside: avoid; border-left: 3px solid #cfe08a; padding-left: 12px; margin: 4px 0; }
  .ss-tag { font-size: 10px; letter-spacing: .08em; text-transform: uppercase; color: #6a7a3a; font-weight: 700; padding-top: 4px; }
  .ss .ex:first-of-type { padding-top: 2px; }

  footer { margin-top: 26px; padding-top: 10px; border-top: 1px solid #eef0f4; color: #a2a8b6; font-size: 11px; text-align: center; }
</style></head>
<body><div class="doc">
  <header>
    <div class="kicker">OlyGym</div>
    <h1>${esc(t('Weekly Training Plan'))}</h1>
    ${sub ? `<div class="sub">${sub}</div>` : ''}
  </header>
  <h3 class="block">${esc(t('Your weeks'))}</h3>
  ${body}
  <footer>${esc(t('Made with OlyGym'))} · opengym.duarte-santos.ch</footer>
</div></body></html>`
}

/**
 * Render the plan and open the browser's print dialog (→ Save as PDF).
 * Uses a hidden iframe so we never navigate away or trip a popup blocker.
 */
export function printPlan(S, owner) {
  const ifr = document.createElement('iframe')
  ifr.setAttribute('aria-hidden', 'true')
  ifr.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;opacity:0;'
  document.body.appendChild(ifr)
  const cleanup = () => { try { ifr.remove() } catch (e) { /* */ } }
  const run = () => {
    const w = ifr.contentWindow
    if (!w) { cleanup(); return }
    w.onafterprint = cleanup
    setTimeout(cleanup, 60000)   // safety net if afterprint never fires
    w.focus()
    try { w.print() } catch (e) { cleanup() }
  }
  const doc = ifr.contentWindow.document
  doc.open(); doc.write(planPrintHTML(S, owner)); doc.close()
  // Give the iframe a tick to lay out before printing.
  if (doc.readyState === 'complete') setTimeout(run, 120)
  else ifr.onload = () => setTimeout(run, 120)
}
