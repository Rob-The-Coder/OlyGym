import { useEffect, useMemo, useRef, useState } from 'react'
import { useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { EXDB, EXIDX, BODYPARTS, isBodyweightEq, allExercises, smOf, exOr } from './lib/exercises.js'
import { activeProfile, exAvailable, ALL_EQUIPMENT, newProfile } from './lib/equipment.js'
import { fmtDate, fmtNum, fmtVol, fmtDur, durPart, todayISO, isoOf, uid, DAYN, DAYS, weekOrder, weekStartOf, weekDayOffset, MONTHS_LONG, ACCENTS, capWords } from './lib/format.js'
import { lastEntryFor, bestWeightFor, bestWeightForEntry, buildSets, effectiveDay, workoutVolume, setsDone, setsDoneActive, setUnitsTotal, lastBW, supersetUnits, unitOf, setLabel, defaultConfig, cleanupSg, modeOf, effortOf, EFFORT, capEffort, stepEffort, isBw, workSetsDone, MAX_PLANNED_WARMUPS, NOTE_MAX } from './lib/history.js'
import { usesBar, barWeightFor, defaultBarWeight, hasBarOverride, isNoBar } from './lib/bar.js'
import { toScale, rirOf, EFFORT_PRESETS, effortColor } from './lib/effort.js'
import { beep, vibrate } from './lib/sound.js'
import { t, dateLocale, instrFor, exerciseNameFor, getLang, INSTR_LANGS } from './lib/i18n.js'
import { nav } from './lib/nav.js'
import { buildStarterPlan, starterPlanDays, starterPlanOptions } from './lib/starter.js'
import Media, { Thumb } from './components/Media.jsx'
import LineChart from './components/LineChart.jsx'
import Stepper from './components/Stepper.jsx'
import Icon from './components/Icon.jsx'
import { Button, Slider, Switch, Segmented, SelectRow, Row, TextField, NumberField, MultiSelectRow } from './components/ui.jsx'
import { DEFAULT_GLYPH } from './lib/glyphs.js'
import BodyMap from './components/BodyMap.jsx'
import MuscleExplorer from './components/MuscleExplorer.jsx'
import { libraryFilterSheet, FilterBar, AppliedFilters } from './components/LibraryFilters.jsx'
import { libraryResults } from './lib/library-filter.js'
import { exerciseMuscleSnapshot, loadOfWorkouts, MUSCLES, MUSCLE_NAME, normalizeMuscleGroups, hasExplicitMuscleMetadata, inMuscleOrder } from './lib/muscles.js'
import { parseCSV } from './lib/csv.js'
import { readXlsx } from './lib/xlsx.js'
import { pickFromDrive, fileMeta, exportSheetToXlsx, downloadFile } from './lib/drive.js'
import CoachImport from './components/CoachImport.jsx'
import { printPlan, planPrintHTML } from './lib/plan-share.js'
import { exerciseHistory } from './lib/exercise-history.js'
import { nextPrescription, applyPrescription, policyFor, defaultPolicy, defaultIncrement, POLICIES_FOR, POLICY_NAME, POLICY_DESC, weightIncrement } from './lib/progression.js'
import { MOBILE, printHtml } from './lib/mobile.js'
import { buildCompletedWorkout } from './lib/finish-workout.js'
import { attemptRows, cleanAttempts, bestAttempt, totalOf, attemptMade, upsertMeet, removeMeet, classOptions, daysUntil, classLists, listFor, cleanClasses } from './lib/competition.js'
import { isWarmupRow, hasCompletedWork } from './lib/workout-model.js'
import { nextUnfinishedUnit } from './lib/supersetFlow.js'
import { swapActiveExercise } from './lib/active-exercise-swap.js'
import { useSheetKeyboard, tappable } from './lib/use-sheet-keyboard.js'
import { isFav, toggleFav, sortFavouritesFirst } from './lib/favourites.js'
import { buildDayEntries } from './lib/session-merge.js'
import { weekFor } from './lib/weeks.js'
import { workoutsOn, backfillStart, backfillEnd, completeBackfill } from './lib/backfill.js'

const S = () => useStore.getState().S
const update = (...a) => useStore.getState().update(...a)
const ui = () => useUI.getState()
const toast = m => ui().toast(m)
const snd = () => S().sound

/* ============================ custom confirm dialog ============================ */
function ConfirmDialog({ title, message, confirmText, cancelText, danger, onConfirm, onCancel, close }) {
  const go = fn => { close(); fn && fn() }
  // The M3 basic dialog: body text on the left, the actions as text buttons on the right with the
  // confirming one last. It used to be the iOS alert shape — everything centred, the confirming
  // action a full-width filled button above a ghost Cancel — which gave the destructive choice
  // the most weight on the screen. A destructive dialog carries an icon: that is what says
  // "danger" before the sentence has been read.
  return <div className="dlg">
    {danger && <div className="dlg-ico"><Icon name="warning" /></div>}
    {title && <h3 className={danger ? 'ctr' : ''}>{title}</h3>}
    <div className={'dlg-body' + (danger ? ' ctr' : '')}>{message}</div>
    <div className="dlg-acts">
      <button className="dlg-btn" onClick={() => go(onCancel)}>{cancelText || t('Cancel')}</button>
      <button className={'dlg-btn' + (danger ? ' err' : '')} onClick={() => go(onConfirm)}>{confirmText || t('Confirm')}</button>
    </div>
  </div>
}
/* ============================ menu sheet ============================ */
// A list of actions, one per row, closing on tap. This is where the workout screen parks
// everything that is not a set you are about to log: the point of a single "more" button is
// that the ten things you do once a session stop competing with the two you do every set.
// items: [{ icon, label, sub, onClick, danger, disabled, on }] — `on` draws a check for toggles.
function MenuSheet({ title, subtitle, items, close }) {
  return <>
    {title && <h3 className="capitalize" style={{ marginBottom: subtitle ? 2 : 10 }}>{title}</h3>}
    {subtitle && <div className="muted small" style={{ marginBottom: 10 }}>{subtitle}</div>}
    <div className="list menu-list">
      {items.filter(Boolean).map((it, i) => <div key={i}
        className={'item menu-item' + (it.danger ? ' danger' : '') + (it.disabled ? ' disabled' : '')}
        aria-disabled={it.disabled || undefined}
        {...tappable(it.disabled ? null : () => { close(); it.onClick && it.onClick() })}>
        {it.icon && <span className="lrow-i"><Icon name={it.icon} /></span>}
        <div className="grow"><div className="tt">{it.label}</div>{it.sub && <div className="ss">{it.sub}</div>}</div>
        {it.on != null && <span className={'menu-on' + (it.on ? ' is-on' : '')}><Icon name="check" /></span>}
      </div>)}
    </div>
  </>
}
export function menuSheet(opts) {
  ui().openSheet(close => <MenuSheet {...opts} close={close} />)
}

// Themed replacement for window.confirm — callback-based (no blocking).
export function confirmSheet(opts) {
  ui().openSheet(close => <ConfirmDialog {...opts} close={close} />, { kind: 'center', ...(opts.locked ? { locked: true } : {}) })
}

/* ============================ starter plan ============================ */
// Plan names and blurbs live here, not in lib/starter.js: check-source-strings.mjs only finds
// string literals written inside a t() call, so copy parked in the catalog and passed in as a
// variable is invisible to it — it would quietly stay English in every language.
const PLAN_COPY = {
  ppl: () => ({ name: t('Snatch / Clean & Jerk / Squat'), about: t('Snatch, clean & jerk and squat/pull each get their own day.') }),
  'upper-lower': () => ({ name: t('Technique / Strength'), about: t('Classic-lift technique twice, squat/pull strength twice.') }),
  'full-body': () => ({ name: t('Full Body'), about: t('Three sessions, a classic lift plus squat and pull each time.') }),
  '5x5': () => ({ name: t('5×5'), about: t('Five sets of five on the main barbell lifts.') })
}

// Adds the plan's days as a fresh dated week. Existing weeks are never touched, and an id with
// no plan behind it changes nothing at all. planId is deliberately required — a default invites
// `onClick={loadStarterPlan}`, which hands the click event in as the plan and silently loads
// nothing.
export function loadStarterPlan(planId) {
  if (!starterPlanDays(planId)) return false
  const name = PLAN_COPY[planId]().name
  update(st => {
    ;(st.weeks || (st.weeks = [])).push(buildStarterPlan(planId, { name, weekStart: weekStartOf(st) }))
  })
  toast(t('{0} loaded', name))
  return true
}

// Intl joins the days the way each language does it — "and" vs "und", "、" in Chinese.
const dayList = days => new Intl.ListFormat(dateLocale()).format(days.map(d => t(DAYN[d])))

function StarterPlanChooser({ close }) {
  const st = useStore(s => s.S)
  const choose = (id, name) => {
    const days = starterPlanDays(id)
    close()
    // A confirmation is only worth showing when one of those days is already taken in the week
    // the plan would land in.
    const current = weekFor(st, todayISO())
    const taken = day => (current?.days || []).some(d => d.dow === day && (d.ex || []).length)
    if (!days.some(taken)) { loadStarterPlan(id); return }
    confirmSheet({
      title: t('Load {0}?', name),
      message: t('A new week will be added on {0}. Nothing you already have is changed.', dayList(days)),
      confirmText: t('Load plan'),
      onConfirm: () => loadStarterPlan(id)
    })
  }
  return <>
    <h3>{t('Choose starter plan')}</h3>
    <div className="list">
      {starterPlanOptions().map(({ id, days }) => {
        const { name, about } = PLAN_COPY[id]()
        return <div key={id} className="item" {...tappable(() => choose(id, name))}>
          <span className="lrow-i" style={{ background: 'var(--surface-3)' }}><Icon name="sparkles" /></span>
          <div className="grow"><div className="tt">{name}</div><div className="ss">{t('{0} days per week', days)} · {about}</div></div>
          <Icon name="chevronRight" className="chev" />
        </div>
      })}
    </div>
  </>
}

export const starterPlanSheet = () => ui().openSheet(close => <StarterPlanChooser close={close} />)

/* ============================ weight picker (shared: body weight + goal) ============================ */
// Fixed range, not a moving window — a window that resizes itself mid-drag (the previous
// attempt) makes the thumb's position unpredictable: every time it grows, everything already
// placed on it shifts toward one side. A static range never has that problem, at the cost of
// coarser precision per pixel — the +/- buttons, and typing straight into the read-out, cover
// exact values.
const W_LO = 1
const W_HI = 300
function WeightInput({ value, setValue, unit }) {
  const clamp = x => Math.max(W_LO, Math.min(W_HI, Math.round((x || 0) * 10) / 10))
  const sv = Math.max(W_LO, Math.min(W_HI, value))
  const onSlide = v => setValue(clamp(v))
  const onType = v => setValue(v)

  return <>
    <div className="bwstep">
      <button className="bw-pm" onClick={() => onSlide(value - 0.1)} aria-label="minus 0.1"><Icon name="minus" /></button>
      <label className="bw-read">
        <NumberField fit value={value} onChange={onType} aria-label={t('Weight ({0})', unit)} enterKeyHint="done"
          onKeyDown={e => { if (e.key === 'Enter') e.currentTarget.blur() }} />
        <span className="u"> {unit}</span>
      </label>
      <button className="bw-pm" onClick={() => onSlide(value + 0.1)} aria-label="plus 0.1"><Icon name="plus" /></button>
    </div>
    <Slider value={sv} min={W_LO} max={W_HI} step={0.5} onChange={onSlide} />
  </>
}

/* ============================ body weight ============================ */
function BwSheet({ required, onDone, close }) {
  const st = useStore(s => s.S)
  const unit = st.unit
  const bw = lastBW(st)
  const [v, setV] = useState(bw ? bw.w : 70)
  const save = () => {
    const n = Math.round((v || 0) * 10) / 10
    if (!n || n <= 0) { toast(t('Enter a valid weight')); return }
    update(s => {
      const iso = todayISO()
      const ex = s.bodyweight.find(b => b.d === iso)
      if (ex) { ex.w = n; ex.t = Date.now() } else s.bodyweight.push({ d: iso, w: n, t: Date.now() })
      s.bodyweight.sort((a, b) => (a.d < b.d ? -1 : 1))
    })
    close()
    if (onDone) onDone(n); else toast(t('Weight saved'))
  }
  const recent = [...st.bodyweight].reverse().slice(0, 3)
  const delEntry = d => { update(s => { s.bodyweight = s.bodyweight.filter(b => b.d !== d) }); toast(t('Weigh-in removed')) }
  // The two screens already plot this curve; the sheet that feeds it did not, so the number had no
  // context while it was being set. The point under the thumb sits at the end of the line, and the
  // goal line is the same one Home and Stats draw.
  const nv = Number(v) || 0
  const today = todayISO()
  const todayRow = st.bodyweight.find(b => b.d === today)
  const prior = st.bodyweight.filter(b => b.d !== today)
  const pts = [
    ...prior.slice(-29).map(b => ({ t: b.t || new Date(b.d).getTime(), y: b.w, d: b.d })),
    ...(nv > 0 ? [{ t: (todayRow && todayRow.t) || Date.now(), y: nv, d: today }] : []),
  ]
  const prevRow = prior.length ? prior[prior.length - 1] : null
  const since = prevRow ? Math.round((nv - prevRow.w) * 10) / 10 : 0
  return <>
    {/* This sheet opens `locked` — swipe/backdrop/Escape/Android-back all no-op on it (see
        Modals.jsx) so an accidental tap on "Start" can't be walked back by reflex the way
        every other sheet in the app can. The two buttons below already cover leaving it
        deliberately; this is the same close a normal sheet gets everywhere else, just
        opted back in explicitly instead of by omission. Plain close() — no onDone, no
        nav — so it's a true no-op: the screen underneath is exactly where you left it. */}
    {required
      ? <div className="row between" style={{ marginBottom: 14 }}>
          <h3 style={{ marginBottom: 0 }}>{t('Quick check-in')}</h3>
          <button className="iconbtn" aria-label={t('Cancel')} onClick={() => close()}><Icon name="xmark" /></button>
        </div>
      : <h3>{t('Log body weight')}</h3>}
    <div className="muted small">{required ? t('Slide or tap to set your weight — tracked before every workout so your curve stays honest.') : t('Today') + ', ' + fmtDate(todayISO(), true)}</div>
    <WeightInput value={v} setValue={setV} unit={unit} />
    {!required && pts.length > 1 && <div className="chart" style={{ marginTop: 12 }}>
      <LineChart points={pts} h={112} unit={unit} goal={st.targetW} />
    </div>}
    {!required && prevRow && (Math.abs(since) >= 0.05
      ? <div className="row" style={{ gap: 5, marginTop: 8, fontWeight: 500, color: bwDeltaColor(since, nv) }}>
          <Icon name={since > 0 ? 'arrowUp' : 'arrowDown'} style={{ fontSize: 12 }} />
          <span>{fmtNum(Math.abs(since))} {unit}</span>
          <span className="dim">{t('since {0}', fmtDate(prevRow.d, true))}</span>
        </div>
      : <div className="small dim" style={{ marginTop: 8 }}>{t('Unchanged since {0}', fmtDate(prevRow.d, true))}</div>)}
    {!required && st.targetW != null && <div className="row small" style={{ gap: 5, marginTop: 4, color: 'var(--yellow)' }}>
      <Icon name="target" style={{ fontSize: 13 }} />
      <span>{t('Goal')} {fmtNum(st.targetW)} {unit} · {Math.abs(st.targetW - nv) < 0.05 ? t('reached!') : t(st.targetW > nv ? '{0} to gain' : '{0} to lose', fmtNum(Math.abs(st.targetW - nv)) + ' ' + unit)}</span>
    </div>}
    <div style={{ height: 14 }} />
    <Button variant="primary" onClick={save}>{required ? t('Save & start workout') : t('Save')}</Button>
    {required && <>
      <div style={{ height: 8 }} /><Button variant="ghost" className="dim" onClick={() => { close(); onDone && onDone(null) }}>{t('Start without weighing in')}</Button>
      <div style={{ height: 2 }} /><Button variant="ghost" className="dim" icon="reset" onClick={() => { close(); nav('/workout') }}>{t('Choose a different workout')}</Button>
    </>}
    {!required && recent.length > 0 && <>
      <div className="sech">{t('Recent weigh-ins')}</div>
      <div className="list menu-list">
        {recent.map(b => <div key={b.d} className="lrow">
          <span className="lrow-i"><Icon name="scale" /></span>
          <span className="lrow-m"><span className="lrow-t">{fmtDate(b.d, true)}</span></span>
          <span className="lrow-v">{fmtNum(b.w)} {unit}</span>
          <button className="iconbtn bw-del" onClick={() => delEntry(b.d)} aria-label={t('Remove weigh-in from {0}', fmtDate(b.d, true))}><Icon name="trash" /></button>
        </div>)}
      </div>
    </>}
  </>
}
export function bwSheet(opts = {}) {
  const h = ui().openSheet(close => <BwSheet {...opts} close={close} />, { locked: !!opts.required })
  return h
}

/* ============================ target weight ============================ */
export function bwDeltaColor(delta, currentW) {
  if (!delta) return 'var(--label-2)'
  if (!S().targetW) return 'var(--label)'
  const up = S().targetW > currentW
  return (delta > 0) === up ? 'var(--acc)' : 'var(--red)'
}
function GoalSheet({ close }) {
  const st = useStore(s => s.S)
  const bw = lastBW(st)
  const [v, setV] = useState(st.targetW || (bw ? bw.w : 70))
  // The sheet asked for a number with nothing to measure it against, then put the destructive
  // action at the foot as a button the full width of the sheet. The distance is a pair of tiles
  // now, the curve it draws the line through is underneath, and Remove goal is a ⋯ like it is on
  // the workout, the week and the config.
  const nv = Number(v) || 0
  const cur = bw ? bw.w : null
  const gap = cur != null ? Math.round((nv - cur) * 10) / 10 : null
  // With no goal saved the field opens on today's weight, so a gap of 0 means "the same number you
  // already are" — not "reached", which would congratulate you for not having set anything.
  const gapText = gap == null ? '' : gap === 0 ? (st.targetW == null ? t('same as today') : t('reached!'))
    : gap > 0 ? t('{0} to gain', fmtNum(Math.abs(gap)) + ' ' + st.unit) : t('{0} to lose', fmtNum(Math.abs(gap)) + ' ' + st.unit)
  const pts = st.bodyweight.slice(-30).map(b => ({ t: b.t || new Date(b.d).getTime(), y: b.w, d: b.d }))
  // What the line through the charts means is read once and was two lines of prose in the form
  // from then on. It sits behind the ⓘ on the tile it is about, like the warm-up and rest help.
  const help = () => ui().openSheet(close2 => <>
    <h3>{t('Target weight')}</h3>
    <div className="muted small" style={{ lineHeight: 1.5 }}>{t('Your goal is drawn as a line through the weight charts, and gains/losses are colored by whether they move toward it.')}</div>
    <div style={{ height: 8 }} />
  </>, { })
  const remove = () => confirmSheet({
    title: t('Remove the goal?'),
    message: t('The weight charts stop drawing the line. Your weigh-ins are not touched.'),
    confirmText: t('Remove goal'), danger: true,
    onConfirm: () => { update(s => { s.targetW = null }); close(); toast(t('Goal removed')) },
  })
  return <>
    <div className="row between" style={{ gap: 8, alignItems: 'flex-start' }}>
      <h3 style={{ margin: 0 }}>{t('Target weight')}</h3>
      {st.targetW != null && <button className="iconbtn ab-ico" aria-label={t('Goal options')}
        onClick={() => menuSheet({
          title: t('Target weight'),
          items: [{ icon: 'trash', label: t('Remove goal'), danger: true, onClick: remove }],
        })}><Icon name="more" /></button>}
    </div>
    {cur != null && <div className="tiles">
      <div className="tile"><div className="l"><Icon name="scale" />{t('Today')}</div>
        <div className="v">{fmtNum(cur)}</div><div className="s">{st.unit}</div></div>
      <div className="tile"><div className="l"><Icon name="target" />{t('Goal')}
        <button className="helpbtn" style={{ marginLeft: 'auto' }} aria-label={t('What the goal does')} onClick={help}><Icon name="info" /></button></div>
        <div className="v" style={{ color: 'var(--yellow)' }}>{fmtNum(nv)}</div>
        <div className="s">{gapText}</div></div>
    </div>}
    <WeightInput value={v} setValue={setV} unit={st.unit} />
    {pts.length > 1 && <div className="chart" style={{ marginTop: 12 }}><LineChart points={pts} h={112} unit={st.unit} goal={nv} /></div>}
    <div style={{ height: 14 }} />
    <Button variant="primary" onClick={() => {
      const n = Math.round((v || 0) * 10) / 10
      if (!n || n <= 0) { toast(t('Enter a valid weight')); return }
      update(s => { s.targetW = n }); close()
      const b = lastBW(S()); toast(t('Goal set: {0}', fmtNum(n) + ' ' + st.unit) + (b ? ' (' + t('{0} to go', fmtNum(Math.abs(n - b.w))) + ')' : ''))
    }}>{t('Save goal')}</Button>
  </>
}
export const goalSheet = () => ui().openSheet(close => <GoalSheet close={close} />)

/* ============================ bar weight ============================ */
// One editor for every place the bar weight shows up (exercise detail, exercise config,
// mid-workout sheet): a stepper over the effective value. What it saves is per exercise
// and lives in S.barWeights, in the profile unit (see lib/bar.js) — stepping or typing
// down to 0 clears the override and the field falls back to the default for the bar
// type, the same "0 drops the key" shape a nullable set field has.
function BarWeightEditor({ ex, extra }) {
  const st = useStore(s => s.S)
  const explicit = hasBarOverride(st, ex.id)
  const def = defaultBarWeight(ex.eq, st.unit)
  const noBar = isNoBar(st, ex.id)
  const setBar = v => update(s => {
    s.barWeights = s.barWeights || {}
    const n = Math.max(0, Math.round((v || 0) * 100) / 100)
    if (n > 0) s.barWeights[ex.id] = n; else delete s.barWeights[ex.id]
  })
  // "No bar" is a stored 0, which is a different thing from no entry at all: a counterbalanced
  // Smith carriage weighs nothing in your hands, so the plate math must
  // start from what you logged (issue #138). Clearing it goes back to the bar type's default.
  const setNoBar = on => update(s => {
    s.barWeights = s.barWeights || {}
    if (on) s.barWeights[ex.id] = 0; else delete s.barWeights[ex.id]
  })
  return <>
    {!noBar && <div className="row cfgrow" style={{ marginBottom: 6 }}>
      <Stepper label={t('Bar ({0})', st.unit)} value={barWeightFor(st, ex) || 0} step={2.5} onChange={setBar} />
    </div>}
    <div className="list menu-list" style={{ marginBottom: 6 }}>
      <Row icon="barbell" title={t('No bar')} subtitle={t('The weight you log is all plates.')}>
        <Switch checked={noBar} onChange={setNoBar} />
      </Row>
    </div>
    <div className="small dim" style={{ marginBottom: 18 }}>
      {noBar ? t('Plates are counted from 0 — turn this off for the default ({0}).', fmtNum(def) + ' ' + st.unit)
        : explicit ? t('Set to 0 to go back to the default ({0}).', fmtNum(def) + ' ' + st.unit)
          : t('Default for this bar type.')}
      {extra ? ' ' + extra : ''}
    </div>
  </>
}

// Tiny mid-workout sheet behind the "Bar … · … per side" chip — same value, same editor.
function BarWeightSheet({ exId, close }) {
  const ex = exOr(exId)
  return <>
    <h3>{t('Bar weight')}</h3>
    <div className="muted small capitalize" style={{ marginBottom: 12 }}>{exerciseNameFor(ex)}</div>
    <BarWeightEditor ex={ex} extra={t('Applies to this exercise everywhere, not just this plan.')} />
    <Button variant="primary" onClick={close}>{t('Done')}</Button>
  </>
}
export const barWeightSheet = exId => ui().openSheet(close => <BarWeightSheet exId={exId} close={close} />)

/* ============================ exercise detail ============================ */
function ExerciseDetail({ ex, close }) {
  const st = useStore(s => s.S)
  const last = lastEntryFor(st, ex.id)
  const best = bestWeightFor(st, ex.id)
  const fav = isFav(st, ex.id)
  const flipFav = () => {
    let on = false
    update(s => { on = toggleFav(s, ex.id) })
    toast(on ? t('Added to favourites') : t('Removed from favourites'))
  }
  // The dataset's first instruction is the exercise's own description, which the card above has
  // already said. Printing the same sentence twice, 300px apart, was this sheet's worst habit.
  const steps = instrFor(ex)
  const how = steps[0] === ex.desc ? steps.slice(1) : steps
  const lastLine = last ? last.sets.map(s => setLabel(ex.id, s, last.target)).join(', ') : ''
  // A logged set carries .w and .r, not .weight and .reps — those are the *config*'s names.
  const lastTop = last ? Math.max(0, ...last.sets.map(s => s.w || 0)) : 0
  // The two things you can do to your own exercise, out of the foot of a read-only sheet and into
  // a menu — where the week, the day and the config sheet keep theirs.
  const actions = [
    ex.custom && { key: 'edit', icon: 'pencil', label: t('Edit this exercise'), onClick: () => { close(); customExSheet(ex) } },
    ex.custom && { key: 'delete', icon: 'trash', label: t('Delete this exercise'), danger: true, onClick: () => deleteCustomEx(ex, close) },
  ].filter(Boolean)
  return <>
    <div className="row between" style={{ gap: 8, alignItems: 'flex-start' }}>
      <h3 className="capitalize">{exerciseNameFor(ex)}</h3>
      <div className="row" style={{ gap: 4, flex: 'none' }}>
        <button className={'iconbtn fav-btn' + (fav ? ' on' : '')} aria-pressed={fav}
          aria-label={fav ? t('Remove from favourites') : t('Add to favourites')} onClick={flipFav}>
          <Icon name={fav ? 'starFill' : 'star'} />
        </button>
        {actions.length > 0 && <button className="iconbtn ab-ico" aria-label={t('Exercise options')}
          onClick={() => menuSheet({ title: exerciseNameFor(ex), subtitle: t('Your own exercise'), items: actions })}>
          <Icon name="more" /></button>}
      </div>
    </div>
    <Media ex={ex} />
    <div className="row" style={{ gap: 6, flexWrap: 'wrap', margin: '10px 0' }}>
      <span className="tag acc">{t(ex.bp)}</span>
      {(ex.primaries?.length ? ex.primaries : (ex.tg ? [ex.tg] : [])).map((s, i) => <span key={i} className="tag"><Icon name="target" />{t(MUSCLE_NAME[s]  || s)}</span>)}
      <span className="tag"><Icon name="dumbbell" />{t(ex.eq)}</span>
      {(ex.secondaries?.length ? ex.secondaries : smOf(ex)).slice(0, 3).map((s, i) => <span key={i} className="tag">{t(MUSCLE_NAME[s] || s)}</span>)}
    </div>
    {ex.desc && <div className="exnote">{ex.desc}</div>}
    {/* Your best and your last session are the two things this sheet is opened for, and they were
        a run-on line of small dim prose. Two labelled tiles, and the way to the full history is a
        row with a chevron rather than a 40dp button. */}
    {last && <div className="tiles" style={{ marginBottom: 10 }}>
      {best > 0 && <div className="tile"><div className="l">{t('Best')}</div><div className="v">{fmtNum(best)} {st.unit}</div></div>}
      <div className="tile"><div className="l">{t('Last')}</div>
        <div className="v">{lastTop > 0 ? `${fmtNum(lastTop)} ${st.unit}` : lastLine}</div>
        {/* The date only. The sets themselves ran to a line and a half in a half-width tile, and
            they are one tap away in History where a row can hold them. */}
        <div className="s">{fmtDate(last.d)}</div></div>
    </div>}
    {last && <div style={{ marginBottom: 10 }}>
      <Row icon="history" title={t('History')} accessory="chevron" onClick={() => exerciseHistorySheet(ex.id)} />
    </div>}
    {usesBar(ex) && <>
      <div className="sech">{t('Bar')}</div>
      <BarWeightEditor ex={ex} extra={t('You still log the total weight — the bar only feeds the per-side plate math.')} />
    </>}
    {how.length > 0 && <><div className="sech">{t('How to')}{!INSTR_LANGS.includes(getLang()) && <span className="dim" style={{ textTransform: 'none', letterSpacing: 0 }}> · {t('instructions in English')}</span>}</div><ol className="steps-list">{how.map((s, i) => <li key={i}>{s}</li>)}</ol></>}
  </>
}
export const exerciseDetailSheet = ex => ui().openSheet(close => <ExerciseDetail ex={ex} close={close} />)

/* ============================ exercise history ============================ */
// What you did on this exercise before, reachable mid-workout (issue #43): the curve first,
// then the last sessions set by set, so the question "what did I do last month" is answered
// without leaving the workout for Stats. Derived once per log change — the sheet re-renders on
// every store tick while a session runs, and LineChart drops its hover whenever `points`
// changes identity, so a series rebuilt per render would lose the tooltip under your finger.
function ExerciseHistory({ exId }) {
  const st = useStore(s => s.S)
  const ex = exOr(exId)
  const h = useMemo(() => exerciseHistory(st, exId), [st.workouts, exId])
  const unit = h.metric === 'weight' ? st.unit : h.metric === 'reps' ? t('reps') : h.metric === 'sec' ? 's' : t('min')
  // The exercise's own standing note — the one shown every time you train it. It lived only inside
  // the note editor, which is the one place you are not standing when you are reading the numbers.
  // Read here, not written: the editor is keyed to a session entry and only exists mid-workout.
  // Read here and corrected here: the block is the control. It was read-only because the only
  // editor was keyed to an entry in the running session, so a cue could be seen and not fixed.
  const standing = (st.exNotes || {})[exId]
  const noteBlock = <div className="fnote note-edit" style={{ marginBottom: 14 }}
    {...tappable(() => exerciseNoteSheet({ exId }))}>
    <div className="flabel">{t('Every session')}<Icon name="pencil" className="note-pen" /></div>
    {standing
      ? <div className="note-read">{standing}</div>
      : <div className="fhint" style={{ marginBottom: 0 }}>{t('Seat height, pin position, a form cue.')}</div>}
  </div>
  if (!h.total) return <>
    <h3 className="capitalize">{exerciseNameFor(ex)}</h3>
    {noteBlock}
    <div className="empty"><div className="ico"><Icon name="history" /></div>{t('No sessions logged yet')}</div>
  </>
  const tail = s => [
    s.volume > 0 && t('Volume') + ' ' + fmtVol(s.volume, st.unit),
  ].filter(Boolean).join(' · ')
  // Best and Last are the two numbers the sheet is opened for. Best was a run-on line above a list
  // whose first row already carried the same figure; they are the pair of tiles the exercise sheet
  // uses now, so the two screens read the same way round.
  const byId = new Map(st.workouts.map(w => [w.id, w]))
  const last = h.sessions.find(s => s.value != null && s.value > 0)
  const prDay = (byId.get(h.prId) || {}).d
  return <>
    <h3 className="capitalize" style={{ marginBottom: 2 }}>{exerciseNameFor(ex)}</h3>
    <div className="muted small" style={{ marginBottom: 12 }}>{t('Exercise history')} · {t(h.total === 1 ? '{0} session' : '{0} sessions', h.total)}</div>
    {noteBlock}
    <div className="tiles">
      <div className="tile"><div className="l">{t('Best')}</div><div className="v">{fmtNum(h.best)} {unit}</div>
        {prDay && <div className="s">{fmtDate(prDay)}</div>}</div>
      <div className="tile"><div className="l">{t('Last')}</div>
        <div className="v">{last ? `${fmtNum(last.value)} ${unit}` : '—'}</div>
        {last && <div className="s">{fmtDate(last.d)}</div>}</div>
    </div>
    <div className="chart" style={{ marginTop: 12 }}>
      <LineChart points={h.points} h={140} unit={unit} color="var(--blue)" />
    </div>
    <div className="sech">{h.sessions.length < h.total ? t('Last {0} sessions', h.sessions.length) : t('Sessions')}</div>
    <div className="list">
      {/* A session you are looking at is a session you can open — every other list in the app
          does it, and this one was the only place a logged workout could not be reached. */}
      {h.sessions.map(s => <div key={s.id} className="item" {...tappable(byId.has(s.id) ? () => workoutDetailSheet(byId.get(s.id)) : null)}>
        <div className="grow">
          <div className="tt">{fmtDate(s.d, true)} {s.pr && <span className="pr"><Icon name="trophy" />PR</span>}</div>
          <div className="ss">{s.sets.map(x => setLabel(exId, x, s.target)).join('  ·  ')}</div>
          {tail(s) && <div className="small dim" style={{ marginTop: 3 }}>{tail(s)}</div>}
        </div>
        {s.value != null && s.value > 0 && <b className="accent nocap" style={{ whiteSpace: 'nowrap' }}>{fmtNum(s.value)} {unit}</b>}
        {byId.has(s.id) && <Icon name="chevronRight" className="chev" />}
      </div>)}
    </div>
  </>
}
export const exerciseHistorySheet = exId => ui().openSheet(close => <ExerciseHistory exId={exId} close={close} />)

/* ============================ custom exercises (issue #11) ============================ */
// Name + body part is all it takes — the exercise then behaves like any built-in one
// (planning, logging, PRs, stats), just without a demo video.
// One option per pickable value, with the current one kept even if the list has never heard of it.
const pickable = (list, value) => (value && !list.includes(value) ? [value, ...list] : list)
  .map(v => ({ value: v, label: capWords(t(v)) }))

function CustomExForm({ existing, prefill, onDone, close }) {
  const nameRef = useRef(null)
  const onNameFocus = useSheetKeyboard(nameRef)
  const [n, setN] = useState(existing ? existing.n : (prefill || ''))
  const [bp, setBp] = useState(existing ? existing.bp : '')
  const [eq, setEq] = useState(existing ? (existing.eq || '') : '')
  const [desc, setDesc] = useState(existing ? (existing.desc || '') : '')
  const [primaries, setPrimaries] = useState(() => {
    if (existing && Array.isArray(existing.primaries) && existing.primaries.length) return [...existing.primaries]
    const norm = hasExplicitMuscleMetadata(existing || {}) ? normalizeMuscleGroups(existing || {}) : []
    return norm.length ? [norm[0]] : []
  })
  const [secondaries, setSecondaries] = useState(() => {
    if (existing && Array.isArray(existing.primaries) && existing.primaries.length) return [...(existing.secondaries || [])]
    const norm = hasExplicitMuscleMetadata(existing || {}) ? normalizeMuscleGroups(existing || {}) : []
    return norm.slice(1)
  })
  // Every primary chip this sheet saw a tap on, in that order. `primaries` alone cannot say what the
  // user reached for first: an existing exercise seeds it in the map's order, because that is how the
  // muscles are stored. The taps are the only record of the user's own order, and they decide the
  // target when the one the exercise had is un-ticked.
  const [primaryTaps, setPrimaryTaps] = useState([])
  const togglePrimary = value => {
    setPrimaryTaps(current => [...current, value])
    setPrimaries(current => current.includes(value) ? current.filter(m => m !== value) : [...current, value])
  }
  const toggleSecondary = value => setSecondaries(current => current.includes(value) ? current.filter(m => m !== value) : [...current, value])
  const save = () => {
    const name = n.trim()
    if (!name) { toast(t('Give it a name')); return }
    if (!bp) { toast(t('Pick a body part')); return }
    if (!eq) { toast(t('Pick equipment')); return }
    const dup = allExercises(S()).find(e => e.n.toLowerCase() === name.toLowerCase() && e.id !== (existing || {}).id)
    if (dup) { toast(t('“{0}” already exists', dup.n)); return }
    const d = desc.trim().slice(0, 1000)
    // Stored in the map's order, not the order the chips were tapped in — the tags on the exercise
    // used to shuffle with every edit.
    const prim = inMuscleOrder(primaries)
    const sm = inMuscleOrder(secondaries.filter(m => !prim.includes(m)))
    const groups = [...prim, ...sm]
    // The one-word target (the library row, the picker, the Muscles view) is not the sorted list's
    // head — a hip thrust with Traps as an extra primary is not a Traps exercise. It is the primary
    // tapped first, and an edit keeps the exercise's target as long as that muscle is still a primary.
    // Once that muscle is gone the next answer is the first one tapped here that survived — a tap that
    // only turned a chip off says nothing and is skipped with it. The sorted list is the last resort,
    // for the user who drops the target and adds nothing in its place.
    const tg = (existing && prim.includes(existing.tg)) ? existing.tg : (primaryTaps.find(m => prim.includes(m)) || prim[0] || '')
    let id = existing && existing.id
    if (existing) update(s => { const c = (s.customEx || []).find(x => x.id === id); if (c) {
      c.n = name; c.bp = bp; c.desc = d; c.tg = tg; c.sm = sm; c.muscleGroups = groups; c.primaries = prim; c.secondaries = sm; c.eq = eq
    } })
    else {
      id = 'c' + uid()
      update(s => { (s.customEx = s.customEx || []).push({ id, n: name, bp, desc: d, tg, sm, muscleGroups: groups, primaries: prim, secondaries: sm, eq, custom: true }) })
    }
    close()
    toast(existing ? t('Saved') : t('“{0}” created', name))
    onDone && onDone(EXIDX[id])
  }
  return <>
    <div className="row between" style={{ gap: 8, alignItems: 'flex-start' }}>
      <h3 style={{ margin: 0 }}>{existing ? t('Edit custom exercise') : t('Create your own exercise')}</h3>
      {/* Deleting your own exercise lives in the same ⋯ the exercise detail sheet keeps it in. It was
          the last full-width danger button at the foot of a sheet, where a thumb lands scrolling. */}
      {existing && <button className="iconbtn ab-ico" aria-label={t('Exercise options')}
        onClick={() => menuSheet({
          title: n.trim() || t('Exercise'),
          items: [{ icon: 'trash', label: t('Delete exercise'), danger: true, onClick: () => { close(); deleteCustomEx(existing) } }],
        })}><Icon name="more" /></button>}
    </div>
    <div className="muted small" style={{ marginBottom: 12 }}>{t('Name it and pick a body part — it behaves like any other exercise, just without a demo video.')}</div>
    <input ref={nameRef} className="input" placeholder={t('Exercise name')} value={n} onFocus={onNameFocus} onChange={e => setN(e.target.value)} />
    {/* Body part and equipment were two horizontal scroll strips with the scrollbar hidden — three of
        ten body parts visible and five of eight kinds of equipment, in a form, beside two muscle rows
        that were already using the app's own picker. One vocabulary for the four of them. */}
    <div className="list menu-list" style={{ marginTop: 12 }}>
      {/* The ids are the catalogue's own and mostly lower case — 'barbell', 'body weight'. The chip
          got its capital from text-transform on .chip; a row has no such element, so capWords does
          it, the same way a toast says its own text. A value the list does not hold (a body part
          that is a muscle-map word, or one written before this list existed) goes in too, so the row
          shows it and can replace it rather than printing a raw id. */}
      <SelectRow title={t('Body part')} value={bp} sheetTitle={t('Body part')}
        options={[{ value: '', label: t('Choose…') }, ...pickable(BODYPARTS, bp)]} onChange={setBp} />
      <SelectRow title={t('Equipment')} value={eq} sheetTitle={t('Equipment')}
        options={[{ value: '', label: t('Choose…') }, ...pickable(ALL_EQUIPMENT, eq)]} onChange={setEq} />
      {bp && <>
        <MultiSelectRow title={t('Primary muscle groups')} sheetTitle={t('Primary muscle groups')}
          values={primaries}
          options={MUSCLES.map(m => ({ value: m, label: t(MUSCLE_NAME[m]) }))}
          onToggle={togglePrimary} noneLabel={t('No explicit muscle group')} doneLabel={t('Done')} />
        <MultiSelectRow title={t('Additional muscle groups')} sheetTitle={t('Additional muscle groups')}
          values={secondaries}
          options={MUSCLES.filter(m => !primaries.includes(m)).map(m => ({ value: m, label: t(MUSCLE_NAME[m]) }))}
          onToggle={toggleSecondary} noneLabel={t('No explicit muscle group')} doneLabel={t('Done')} />
      </>}
    </div>
    <div style={{ height: 12 }} />
    <textarea className="input" rows={4} maxLength={1000} placeholder={t('Description (optional) — setup, cues, anything you want to remember')}
      value={desc} onChange={e => setDesc(e.target.value)} />
    <div style={{ height: 14 }} />
    <Button variant="primary" onClick={save}>{existing ? t('Save') : t('Create exercise')}</Button>
  </>
}
export const customExSheet = (existing, onDone, prefill) => ui().openSheet(close => <CustomExForm existing={existing} prefill={prefill} onDone={onDone} close={close} />)

export function deleteCustomEx(ex, afterDelete) {
  if (S().active?.entries.some(e => e.id === ex.id)) { toast(t('Finish your current workout first')); return }
  confirmSheet({
    title: t('Delete “{0}”?', ex.n),
    message: t('It will be removed from your routines. Already-logged workouts keep their sets.'),
    confirmText: t('Delete'), danger: true,
    onConfirm: () => {
      update(s => {
        // Keep display and muscle metadata in history before the custom catalogue row disappears.
        const snapshot = exerciseMuscleSnapshot(ex)
        s.workouts.forEach(w => w.entries.forEach(e => {
          if (e.id !== ex.id) return
          e.n = ex.n
          if (!e.muscleSnapshot || !Object.keys(e.muscleSnapshot).length) e.muscleSnapshot = snapshot
        }))
        s.customEx = (s.customEx || []).filter(x => x.id !== ex.id)
        ;(s.weeks || []).forEach(w => (w.days || []).forEach(d => {
          if (!d.ex) return
          d.ex = d.ex.filter(e => e.id !== ex.id)
          cleanupSg(d.ex)
        }))
        delete s.exWeights[ex.id]
        s.favEx = (s.favEx || []).filter(id => id !== ex.id)
      })
      toast(t('Exercise deleted'))
      afterDelete && afterDelete()
    }
  })
}

/* ============================ exercise picker ============================ */
/** Every exercise in the plan — the days of every dated week, flattened. */
const plannedEx = st => (st.weeks || []).flatMap(w => (w.days || []).flatMap(d => d.ex || []))
// Exercises already used in your plan or past workouts (for the "Chosen" filter + a marker).
function usageMap(st) {
  const u = {}
  plannedEx(st).forEach(e => { u[e.id] = (u[e.id] || 0) + 1 })
  st.workouts.forEach(w => w.entries.forEach(e => { u[e.id] = (u[e.id] || 0) + 1 }))
  return u
}
function ExercisePicker({ onPick, close }) {
  const st = useStore(s => s.S)
  const usage = usageMap(st)
  const [q, setQ] = useState('')
  const [view, setView] = useState('')      // '' = the whole catalogue, '☆' = favourites, '★' = chosen
  const [part, setPart] = useState('')      // '' = every body part. Both of these come from the sheet
  const [eq, setEq] = useState('')          // '' = any equipment
  const [showAll, setShowAll] = useState(false)
  const [shown, setShown] = useState(50)
  const [byMuscle, setByMuscle] = useState(false)
  const searchRef = useRef(null)
  const onSearchFocus = useSheetKeyboard(searchRef)
  const all = allExercises(st)
  const profile = activeProfile(st)
  const chosenCount = Object.keys(usage).length
  const favCount = (st.favEx || []).length
  const special = view === '★' || view === '☆'
  const reset = () => setShown(50)
  // Favourites and Chosen are views over the catalogue, not filters on it, so they narrow the
  // set the filters are then applied to.
  const scopeFor = v => v === '★' ? all.filter(e => usage[e.id]) : v === '☆' ? all.filter(e => isFav(st, e.id)) : all
  // One pass, the same one the Library makes (lib/library-filter.js). The equipment dead-end
  // guard, the filter order and the favourites-first ranking used to be reimplemented here, and
  // the two screens drifted: this file owned a copy of the guard that the Library had already
  // moved into the helper.
  const pass = ({ v = view, b = part, e = eq, sa = showAll, text = q } = {}) =>
    libraryResults({ all: scopeFor(v), q: text, bp: b, eq: e,
      available: profile && !sa ? (x => exAvailable(st, x)) : null })
  // One pass per render: the catalogue is 624 lifts and this list re-renders on every keystroke.
  const { list: listed, eq: eqOn } = pass()
  const f = view === '★'
    ? [...listed].sort((a, b) => (usage[b.id] - usage[a.id]) || exerciseNameFor(a).localeCompare(exerciseNameFor(b)))
    : sortFavouritesFirst(listed, st)
  // What is narrowing the list, so it can be dropped without reopening the sheet. The sheet
  // commits on "Show N exercises", so its count is the count you get.
  const describeFor = ({ bp: b, eq: e, showAll: sa }) => {
    const r = pass({ b, e, sa })
    return { count: r.list.length, eqOpts: r.eqOpts }
  }
  const applied = [
    part && { key: 'part', label: t(part), clear: () => { setPart(''); reset() } },
    eqOn && { key: 'eq', label: t(eqOn), clear: () => { setEq(''); reset() } },
    profile && !showAll && { key: 'mine', label: t('My equipment'), clear: () => setShowAll(true) },
  ].filter(Boolean)
  if (byMuscle) return <>
    <div className="row between" style={{ marginBottom: 10 }}><h3>{t('Add exercise')}</h3>
      <Button size="sm" variant="ghost" onClick={() => setByMuscle(false)}>{t('All')}</Button>
    </div>
    <MuscleExplorer onPick={onPick} />
  </>

  return <>
    <div className="row between" style={{ marginBottom: 10 }}><h3>{t('Add exercise')}</h3>
      <Button size="sm" variant="tinted" icon="target" onClick={() => setByMuscle(true)}>{t('By muscle')}</Button>
    </div>
    {/* .picker-search is what index.css keys the keyboard-aware sheet layout on: the sheet
        lifts above the keys and the search stays put while the list scrolls under it. */}
    <div className="picker-search"><div className="search"><svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="7" /><path d="m21 21-4.3-4.3" /></svg>
      <input ref={searchRef} className="input" placeholder={t('Search {0} exercises…', all.length)} value={q} onFocus={onSearchFocus} onChange={e => { setQ(e.target.value); reset() }} /></div></div>
    <FilterBar count={f.length} appliedCount={applied.length}
      onOpen={() => libraryFilterSheet({ bp: part, eq: eqOn, showAll, profile, describeFor,
        onApply: ({ bp: b, eq: e, showAll: sa }) => { setPart(b); setEq(e); setShowAll(sa); reset() } })} />
    {/* Favourites and Chosen stay on the surface: they are the fast path — pick from the twenty
        or so lifts you actually train — and a shortcut you have to open a sheet for is not one.
        They compose with the catalogue filters rather than clearing them (the old chips wiped the
        equipment choice), because those filters are now chips below and nothing narrows the list
        without saying so. */}
    <div className="chips">
      {favCount > 0 && <button className={'chip' + (view === '☆' ? ' on' : '')} aria-pressed={view === '☆'}
        onClick={() => { setView(view === '☆' ? '' : '☆'); reset() }}><Icon name="starFill" className="fav-star" />{t('Favourites')} ({favCount})</button>}
      {chosenCount > 0 && <button className={'chip' + (view === '★' ? ' on' : '')} aria-pressed={view === '★'}
        onClick={() => { setView(view === '★' ? '' : '★'); reset() }}><Icon name="starFill" className="fav-star" />{t('Chosen')} ({chosenCount})</button>}
    </div>
    <AppliedFilters applied={applied} />
    <div className="list">
      {!special && <div className="item" {...tappable(() => customExSheet(null, ex => onPick(ex), q.trim()))}>
        <div className="thumb thumb-x"><Icon name="sparkles" /></div>
        <div className="grow"><div className="tt">{t('Create your own exercise')}</div><div className="ss">{t('name + body part, no video')}</div></div><Icon name="plus" className="chev" />
      </div>}
      {f.slice(0, shown).map(e => <div key={e.id} className="item" {...tappable(() => onPick(e))}>
        <Thumb ex={e} /><div className="grow"><div className="tt capitalize">{isFav(st, e.id) && <Icon name="starFill" className="fav-star" />}{exerciseNameFor(e)}</div><div className="ss capitalize">{t(MUSCLE_NAME[e.tg] || e.tg || e.bp)} · {t(e.eq)}</div></div>
        {/* Accent tag = already in a routine/log ("Chosen"); the yellow star by the name = favourite. */}
        {usage[e.id] && <span className="tag acc"><Icon name="starFill" /></span>}
        {/* A "+" glyph reads as "add this now" — it used to just open the same detail sheet as
            tapping the row, so it added nothing until you'd scrolled past the sets/reps config
            and found the real button. Now it does what it looks like: adds with the default
            config right away. Tapping the row itself still opens the detail/config sheet, for
            when you want to set sets/reps before adding. */}
        <button className="iconbtn chev" aria-label={t('Add “{0}”', exerciseNameFor(e))} style={{ padding: 8, margin: -8 }}
          onClick={ev => { ev.stopPropagation(); onPick(e, true) }}><Icon name="plus" /></button>
      </div>)}
      {f.length === 0 && view === '★' && <div className="empty">{t('Nothing chosen yet — add exercises and they’ll show up here.')}</div>}
      {f.length === 0 && view === '☆' && <div className="empty">{t('No favourites here — tap the star on an exercise to add it.')}</div>}
    </div>
    {f.length > shown && <><div style={{ height: 8 }} /><Button onClick={() => setShown(s => s + 50)}>{t('Show more')}</Button></>}
  </>
}
export const exercisePicker = onPick => ui().openSheet(close => <ExercisePicker onPick={onPick} close={close} />)

/** Start a safe swap for one exact active-workout occurrence. */
export function swapActiveWorkoutExercise(index) {
  const active = S().active
  if (!active?.entries?.[index]) return
  // The slot's plan is the one the session was started from — the day behind it (null for a
  // freestyle session). Every entry in a session shares that one day.
  const day = active.weekId != null ? effectiveDay(S(), active.d) : null

  // The "+" on a picker row commits with the default config, exactly as it does in the add
  // flows; tapping the row still opens the config sheet first.
  const picker = exercisePicker((ex, quick) => quick ? swapTo(ex, defaultConfig(ex.id)) : exConfigSheet(ex, null, cfg => swapTo(ex, cfg), null, day))
  function swapTo(ex, cfg) {
    // The picker is a chooser here, not a stack you keep adding from: one swap, then back to
    // the workout. (The add flow deliberately leaves it open.)
    picker.close()
    const full = { ...cfg, id: ex.id }
    const st = S()
    const current = st.active?.entries?.[index]
    if (!current) return
    const freestyle = !day
    // Same rows the add flow builds: last time's loads and, in a planned session, the
    // prescription — swapping barbell for dumbbell bench must not start you at an empty bar.
    const step = modeOf(full) === 'reps' ? weightIncrement(full, st.unit) : defaultIncrement(ex.id, st.unit)
    const plan = freestyle ? null : nextPrescription(st, full, day)
    const built = buildSets(st, full, { step, ...(freestyle ? { preferLast: true } : {}), ...(plan?.kind === 'off' ? { useTarget: true } : {}) })
    const replacement = {
      id: ex.id,
      target: { ...cfg },
      plan,
      sets: freestyle ? built : applyPrescription(built, plan, step),
    }

    const apply = options => {
      // A timed callback closes over entry/set indexes. Invalidate it, and the current rest,
      // before the selected occurrence can be replaced or a new entry shifts those indexes.
      ui().stopWork()
      ui().stopRest()
      update(state => { swapActiveExercise(state.active, index, replacement, options) }, true)
    }
    const logged = (current.sets || []).some(set => set.done === true)
    if (!logged) { apply(); return }

    if (current.sg) {
      ui().openSheet(close => <>
        <h3>{t('Swap exercise?')}</h3>
        <div className="muted small" style={{ marginBottom: 12 }}>
          {t('Logged sets stay with the original exercise. Choose where the replacement belongs.')}
        </div>
        <Button variant="primary" onClick={() => { close(); apply({ loggedConfirmed: true, groupDisposition: 'keep' }) }}>
          {t('Keep replacement in this group')}
        </Button>
        <div style={{ height: 8 }} />
        <Button variant="ghost" onClick={() => { close(); apply({ loggedConfirmed: true, groupDisposition: 'detach' }) }}>
          {t('Insert after this group')}
        </Button>
      </>)
      return
    }

    confirmSheet({
      title: t('Swap exercise?'),
      message: t('Logged sets stay with the original exercise. The replacement will be inserted afterward.'),
      confirmText: t('Continue'),
      onConfirm: () => apply({ loggedConfirmed: true })
    })
  }
}

/* ============================ equipment profiles ============================ */
// Create or edit one profile ("Home", "Gym", ...): a name plus a checklist of what you have.
function EquipmentProfileSheet({ profile, close }) {
  const update = useStore(s => s.update)
  const nameRef = useRef(null)
  const [checked, setChecked] = useState(new Set(profile?.equipment || []))
  const toggle = k => setChecked(s => { const n = new Set(s); n.has(k) ? n.delete(k) : n.add(k); return n })
  const save = () => {
    const name = (nameRef.current.value || '').trim()
    // It used to return in silence: the button looked dead on an empty name, with nothing on screen
    // saying why. Every other form in the module names the missing thing.
    if (!name) { toast(t('Give it a name')); return }
    update(s => {
      s.equipProfiles = s.equipProfiles || []
      const equipment = [...checked]
      if (profile) {
        const p = s.equipProfiles.find(x => x.id === profile.id)
        if (p) { p.name = name; p.equipment = equipment }
      } else {
        const p = newProfile(name); p.equipment = equipment
        s.equipProfiles.push(p)
        if (!s.activeEquipId) s.activeEquipId = p.id
      }
    })
    close()
  }
  return <>
    <h3>{profile ? t('Edit profile') : t('New equipment profile')}</h3>
    <div className="muted small" style={{ marginBottom: 14 }}>
      {t('Name it after where you train — e.g. "Home" or "Gym" — then check what you have there.')}
    </div>
    <TextField ref={nameRef} defaultValue={profile?.name || ''} placeholder={t('Profile name')} maxLength={40} />
    <div style={{ height: 12 }} />
    {/* The chip set is this sheet's own body, not a strip of filters travelling beside a list, so it
        wraps — .chips.wrap is the modifier the filter sheet already uses. As a strip it left nine of
        the thirteen kinds of equipment off-screen behind a scrollbar that is never drawn. */}
    <div className="chips wrap">
      {ALL_EQUIPMENT.map(k => (
        <button key={k} className={'chip' + (checked.has(k) ? ' on' : '')} onClick={() => toggle(k)}>{t(k)}</button>
      ))}
    </div>
    <div className="dim small" style={{ marginTop: 10 }}>
      {t('Body-weight exercises are always available, in every profile.')}
    </div>
    <div style={{ height: 14 }} /><Button variant="primary" onClick={save}>{t('Save')}</Button>
  </>
}
export const equipmentProfileSheet = profile => ui().openSheet(close => <EquipmentProfileSheet profile={profile} close={close} />)

/* ============================ exercise config ============================ */
// Progression settings for one exercise (issue #17). Shown inside the config sheet because
// "how does this lift go up" belongs next to sets and reps, not in a separate screen. Left
// on "follow the routine" it inherits the profile's own setting (Settings → Automatic
// progression), so most people never touch it.
const progressionStepOf = (c, mode, ex, unit) =>
  c.inc >= 0 ? c.inc : (mode === 'time' ? 5 : defaultIncrement(ex.id, unit))
const progressionStepIsValid = (step, policy) =>
  policy === 'off' || (Number.isFinite(step) && step > 0)

function ProgressionFields({ ex, mode, c, setC, routine, unit, fallback }) {
  const options = POLICIES_FOR[mode] || ['off']
  if (options.length < 2) return null
  const inherited = policyFor({ id: ex.id }, routine, mode, fallback)
  const active = policyFor({ ...c, id: ex.id }, routine, mode, fallback)
  const inc = progressionStepOf(c, mode, ex, unit)
  const invalid = !progressionStepIsValid(inc, active)
  const setRule = v => setC(x => ({ ...x, prog: v || undefined }))
  return <>
    <div className="sech">{t('Progression')}</div>
    <div className="sect-b" style={{ marginBottom: 8 }}>
      <SelectRow title={t('Rule')} sheetTitle={t('Progression')} value={c.prog || ''} onChange={setRule}
        options={[{ value: '', label: t('Follow the routine ({0})', t(POLICY_NAME[inherited])) },
          ...options.map(p => ({ value: p, label: t(POLICY_NAME[p]) }))]} />
    </div>
    <div className="small dim" style={{ marginBottom: active === 'off' ? 18 : 10 }}>{t(POLICY_DESC[active])}</div>
    {active !== 'off' && <div className="row cfgrow" style={{ marginBottom: 18 }}>
      <Stepper label={mode === 'time' ? t('Step (seconds)') : t('Step ({0})', unit)} value={inc}
        step={mode === 'time' ? 5 : 1.25} decimal={mode !== 'time'} invalid={invalid} className={invalid ? 'invalid' : ''}
        onChange={v => setC(x => ({ ...x, inc: v }))} />
    </div>}
    {invalid && <div className="small" role="alert" style={{ color: 'var(--red)', marginTop: -10, marginBottom: 18 }}>
      {t('Enter a positive step to use this progression rule.')}
    </div>}
  </>
}

function ExConfig({ ex: exProp, existing, onSave, onDelete, close, routine, initial }) {
  const st = useStore(s => s.S)
  // A caller can hand us an id the catalogue no longer resolves — a plan written against another
  // dataset, an exercise deleted on another device. exOr keeps the sheet usable.
  const ex = exProp || exOr(existing?.id || initial?.id)
  const seed = existing || initial || defaultConfig(ex.id)
  const [c, setC] = useState(() => ({ ...seed }))
  const mode = modeOf({ ...c, id: ex.id })
  // Both default from the dataset and are then whatever the config says — see isBw.
  const bw = isBw({ ...c, id: ex.id })
  const progressionPolicy = policyFor({ ...c, id: ex.id }, routine, mode, defaultPolicy(st))
  const progressionStepInvalid = !progressionStepIsValid(progressionStepOf(c, mode, ex, st.unit), progressionPolicy)
  // Keep whatever the other mode already had (sets, weight) and fill only what is missing.
  const setMode = m => setC(x => ({ ...defaultConfig(ex.id, m), ...x, mode: m }))
  const save = () => {
    if (progressionStepInvalid) return
    close()
    const sets = Math.max(1, Math.round(c.sets) || 3)
    // Only carry progression settings that differ from the inherited default, so a plan file
    // stays readable and "follow the routine" keeps meaning exactly that.
    const prog = {}
    if (c.prog) prog.prog = c.prog
    if (c.inc > 0) prog.inc = c.inc
    // Written only when it differs from what the dataset already says.
    const flags = {}
    if (bw !== isBodyweightEq(ex.id)) flags.bodyweight = bw
    const note = (c.note || '').trim().slice(0, 500)
    const withNote = note ? { note } : {}
    const warmupSets = Math.max(0, Math.min(MAX_PLANNED_WARMUPS, Math.round(c.warmupSets) || 0))
    const withWarmups = warmupSets ? { warmupSets } : {}
    const restSec = Math.max(0, Math.round(c.restSec) || 0)
    const withRest = restSec ? { restSec } : {}
    if (mode === 'time') onSave({ sets, mode: 'time', sec: Math.max(1, Math.round(c.sec) || 45), weight: Math.max(0, c.weight || 0), ...flags, ...prog, ...withNote, ...withWarmups, ...withRest })
    else onSave({ sets, mode: 'reps', reps: Math.max(1, Math.round(c.reps) || 10), weight: Math.max(0, c.weight || 0), ...flags, ...prog, ...withNote, ...withWarmups, ...withRest })
  }
  // Two actions that were full-width buttons at the end of the form, under the primary one, where
  // a thumb reaches them on the way to Save. They are the week editor's and the day card's
  // pattern now: the destructive and the once-a-plan actions go behind a ⋯ in the title row.
  const actions = [
    ex.custom && { key: 'edit', icon: 'pencil', label: t('Edit or delete this exercise'), onClick: () => { close(); customExSheet(ex) } },
    onDelete && { key: 'remove', icon: 'trash', label: t('Remove from routine'), danger: true, onClick: () => { close(); onDelete() } },
  ].filter(Boolean)
  // Warm-ups and rest are the two fields nobody guesses, and they each carried a two-line
  // paragraph inside the form. The paragraphs live here now, so the form can stay a form.
  const setHelp = () => ui().openSheet(close2 => <>
    <h3>{t('Warm-ups and rest')}</h3>
    <div className="muted small" style={{ lineHeight: 1.5, display: 'grid', gap: 10 }}>
      <div>{t('Ramp-up sets are added before your work sets and left out of volume, records and progression. Each one closes half the gap to the work weight, and you can still change any of them mid-session.')}</div>
      <div>{t('Rest runs after each set of this exercise. Leave it at 0 to use your default rest timer.')}</div>
    </div>
    <div style={{ height: 8 }} />
  </>, { })
  return <>
    <div className="row between" style={{ marginBottom: 0 }}>
      <h3 className="capitalize" style={{ margin: 0 }}>{exerciseNameFor(ex)}</h3>
      {actions.length > 0 && <button className="iconbtn ab-ico" aria-label={t('Exercise options')}
        onClick={() => menuSheet({ title: exerciseNameFor(ex), subtitle: t('This routine entry'), items: actions })}>
        <Icon name="more" /></button>}
    </div>
    <Media ex={ex} />
    {/* The same tags the exercise detail sheet shows, secondaries included: choosing what goes
        into a plan is exactly when "what else does this hit" matters. */}
    <div className="row" style={{ gap: 6, flexWrap: 'wrap', margin: '10px 0 14px' }}>
      <span className="tag acc">{t(ex.bp)}</span>
      {(ex.primaries?.length ? ex.primaries : (ex.tg ? [ex.tg] : [])).map((s, i) => <span key={i} className="tag"><Icon name="target" />{t(MUSCLE_NAME[s] || s)}</span>)}
      <span className="tag"><Icon name="dumbbell" />{t(ex.eq)}</span>
      {(ex.secondaries?.length ? ex.secondaries : smOf(ex)).slice(0, 3)
        .map((s, i) => <span key={i} className="tag">{t(MUSCLE_NAME[s] || s)}</span>)}
    </div>
    {ex.desc && <div className="exnote">{ex.desc}</div>}
    <div className="sech">{t('The set')}</div>
    <div style={{ marginBottom: 12 }}>
      <Segmented className="seg-range" value={mode} onChange={setMode}
        options={[{ value: 'reps', label: t('Reps') }, { value: 'time', label: t('Time') }]} />
    </div>
    <div className="row cfgrow" style={{ marginBottom: 12 }}>
      {mode === 'time' ? <>
        <Stepper label={t('Sets')} value={c.sets} step={1} decimal={false} onChange={v => setC(x => ({ ...x, sets: v }))} />
        <Stepper label={t('Seconds')} value={c.sec} step={5} decimal={false} onChange={v => setC(x => ({ ...x, sec: v }))} />
        <Stepper label={t('Weight ({0})', st.unit)} value={c.weight} step={2.5} onChange={v => setC(x => ({ ...x, weight: v }))} />
      </> : <>
        <Stepper label={t('Sets')} value={c.sets} step={1} decimal={false} onChange={v => setC(x => ({ ...x, sets: v }))} />
        <Stepper label={t('Reps')} value={c.reps} step={1} decimal={false} onChange={v => setC(x => ({ ...x, reps: v }))} />
        {/* On bodyweight work the weight stepper is the click #32 is about, so it is not here
            until there is a belt to describe — see the added-weight row below. */}
        {!bw && <Stepper label={t('Weight ({0})', st.unit)} value={c.weight} step={2.5} onChange={v => setC(x => ({ ...x, weight: v }))} />}
      </>}
    </div>
    {/* Planned warm-ups (the session used to start at the work weight and you added every warm-up
        by hand) and per-exercise rest (issue #10). One row of two, the same control as the three
        above: they were full width with a two-line paragraph under each. */}
    <div className="row cfgrow" style={{ marginBottom: 8 }}>
      <Stepper label={t('Warm-up sets')} value={c.warmupSets || 0} step={1} decimal={false}
        onChange={v => setC(x => ({ ...x, warmupSets: Math.max(0, Math.min(MAX_PLANNED_WARMUPS, Math.round(v) || 0)) }))} />
      <Stepper label={t('Rest (s)')} value={c.restSec || 0} step={15} decimal={false}
        onChange={v => setC(x => ({ ...x, restSec: v }))} />
    </div>
    <div className="small dim" style={{ marginBottom: 8 }}>
      {(c.warmupSets || 0) > 0 ? t('Warm-ups stay out of volume, records and progression.') : t('Ramp-up sets are added before the work sets.')}
      {' '}
      {(c.restSec || 0) > 0 ? t('Rest runs after each set here.') : t('Rest at 0 uses your default timer.')}
    </div>
    {mode === 'time' && !bw && <div className="small dim" style={{ marginBottom: 8 }}>
      {t('A timer runs while you hold the set. Leave the weight at 0 for bodyweight holds.')}
    </div>}
    <button className="fieldhelp" onClick={setHelp}>
      <Icon name="info" /> {t('What do warm-ups and rest do?')}
    </button>
    {/* bodyweight (issues #31/#32) */}
    <div className="sech">{t('Bodyweight')}</div>
    <div className="sect-b" style={{ marginBottom: 8 }}>
      <Row icon="figureStrength" title={t('Bodyweight')}
        subtitle={bw ? t('No weight to enter — just log the reps.') : t('Ask for a weight on every set.')}>
        <Switch checked={bw} onChange={v => setC(x => ({ ...x, bodyweight: v, weight: v ? 0 : x.weight }))} />
      </Row>
    </div>
    {bw && <>
      <div className="row cfgrow" style={{ marginBottom: 8 }}>
        <Stepper label={t('Added ({0})', st.unit)} value={c.weight || 0} step={2.5}
          onChange={v => setC(x => ({ ...x, weight: v }))} />
      </div>
      <div className="small dim" style={{ marginBottom: 8 }}>
        {t('For dips or pull-ups with a belt. Progression then follows the weight.')}
      </div>
    </>}
    {/* The bar's own weight, for the plate math — per exercise, not per plan. */}
    {usesBar(ex) && <>
      <div className="sech">{t('Bar')}</div>
      <BarWeightEditor ex={ex} extra={t('Applies to this exercise everywhere, not just this plan.')} />
    </>}
    <ProgressionFields ex={ex} mode={mode} c={c} setC={setC} routine={routine} unit={st.unit} fallback={defaultPolicy(st)} />
    <div className="sech">{t('Note')}</div>
    <textarea className="input" rows={3} maxLength={500} style={{ marginBottom: 18 }}
      placeholder={t('Loading cues, anything worth remembering here')}
      value={c.note || ''} onChange={e => setC(x => ({ ...x, note: e.target.value }))} />
    <Button variant="primary" disabled={progressionStepInvalid} onClick={save}>{existing ? t('Save') : t('Add to routine')}</Button>
  </>
}

export const exConfigSheet = (ex, existing, onSave, onDelete, routine, initial) => ui().openSheet(close => <ExConfig ex={ex} existing={existing} initial={initial} onSave={onSave} onDelete={onDelete} routine={routine} close={close} />)



/* ============================ effort quick picker (RIR / RPE) ============================ */
// Rating a set used to mean walking a +/- stepper up the scale — eleven taps to log "5 reps
// left". This is the one-tap replacement: a colour-coded button per preset, plus a free field
// for the value between two presets. Presets are stored in RIR internally; a profile that logs
// RPE sees the same buttons labelled on its own scale (toScale), coloured identically — the
// colour is the effort, not the number, so 0 RIR and 10 RPE are both the "went to failure" end.
function EffortPicker({ kind, value, onPick, close }) {
  // Local mirror so the ticked preset and the exact field track typing live; the store is
  // written on every change through onPick, the same as the stepper did.
  const [v, setV] = useState(value ?? null)
  const set = nv => { setV(nv); onPick(nv) }
  // `v` is in the profile's own scale (whatever sits on the set: s.rir or s.rpe). Compare in
  // RIR so the tick lands on the right preset on either scale, and so a typed RPE colours the
  // same as the RIR it equals.
  const curRir = rirOf(kind === 'rpe' ? { rpe: v } : { rir: v })
  const curColor = effortColor(curRir)
  const commit = nv => { close(); onPick(nv) }
  const pick = rir => commit(toScale(kind, rir))
  const hd = EFFORT[kind].hd
  // Same list the ⋯ menus use: a tinted square with the value where the icon goes, the sentence
  // as the row title, a tick on the current one. The exact field is the app's own stepper,
  // tinted like the logged cell in the set row, so the sheet and the row read as one thing.
  return <>
    <h3 style={{ marginBottom: 2 }}>{t('How hard was that set?')}</h3>
    <div className="muted small">{t('Tap how many reps you had left, or type an exact {0}.', hd)}</div>
    {/* The (i) that used to sit in the Settings row lives here now: this is where the choice is
        actually made, and a value row cannot hold a second button inside itself. */}
    <button className="fieldhelp" onClick={effortHelpSheet}>
      <Icon name="info" /> {t('What are RIR and RPE?')}
    </button>
    <div className="list menu-list effpick">
      {EFFORT_PRESETS.map(p => {
        const label = fmtNum(toScale(kind, p.rir)) + (p.tail ? '+' : '')
        const on = curRir != null && curRir === p.rir
        return <div key={p.rir} className={'item menu-item' + (on ? ' on' : '')} style={{ '--bc': p.color }}
          {...tappable(() => pick(p.rir))}>
          <span className="lrow-i effpick-n">{label}</span>
          <div className="grow"><div className="tt">{t(p.feel)}</div></div>
          <span className={'menu-on' + (on ? ' is-on' : '')}><Icon name="check" /></span>
        </div>
      })}
      <div className="item menu-item effpick-free">
        <div className="grow"><div className="tt">{t('Exact {0}', hd)}</div></div>
        <div className="stp effcell-stp"
          style={curColor ? { color: curColor, background: `color-mix(in srgb, ${curColor} 20%, var(--surface-2))` } : undefined}>
          <button aria-label="Decrease" onClick={() => set(stepEffort(kind, v, -1))}><Icon name="minus" /></button>
          <span className="val"><NumberField decimal nullable value={v ?? ''} placeholder="–"
            onChange={nv => set(capEffort(kind, nv))} /></span>
          <button aria-label="Increase" onClick={() => set(stepEffort(kind, v, 1))}><Icon name="plus" /></button>
        </div>
      </div>
    </div>
    {v != null && <>
      <div style={{ height: 10 }} />
      <Button variant="ghost" className="dim" icon="xmark" onClick={() => commit(null)}>{t('Clear rating')}</Button>
    </>}
    <div style={{ height: 4 }} />
  </>
}
// The two scales are one judgement counted from opposite ends, and a paragraph is a bad way to
// say that — the table shows it in one look, and reading down a column answers "what do I put
// here". Opened from the picker above; it used to hang off the Settings row.
const EFFORT_ROWS = [
  ['0', '10', 'Nothing left — went to failure'],
  ['1', '9', 'One more rep in the tank'],
  ['2', '8', 'Two more reps'],
  ['3', '7', 'Three more reps'],
  ['4+', '≤6', 'Easy — warm-up territory'],
]
// RIR 2 / RPE 8: the row a working set usually lands on — the anchor the others are read against.
const EFFORT_TYPICAL = 2

export function effortHelpSheet() {
  ui().openSheet(close => <>
    <h3>{t('Effort per set')}</h3>
    <div className="muted small" style={{ lineHeight: 1.5 }}>
      {t('How hard a set was, logged next to weight and reps. Two scales for the same judgement, counted from opposite ends.')}
    </div>
    <div className="efftbl">
      <div className="r hd"><span className="n">{t('RIR')}</span><span className="n">{t('RPE')}</span><span className="f">{t('How it felt')}</span></div>
      {EFFORT_ROWS.map(([rir, rpe, feel], i) => (
        <div key={rir} className={'r' + (i === EFFORT_TYPICAL ? ' on' : '')}>
          <span className="n">{rir}</span><span className="n">{rpe}</span><span className="f">{t(feel)}</span>
        </div>
      ))}
    </div>
    <div className="dim small" style={{ lineHeight: 1.5, display: 'grid', gap: 8 }}>
      <div>{t('RIR counts the reps you left; RPE reads the same effort off a 10-point scale — so RPE ≈ 10 − RIR. Pick the one you already think in.')}</div>
      <div>{t('The highlighted row is where most working sets land. Sets you have already logged keep their own scale, and nothing else reads the value — progression is unaffected.')}</div>
    </div>
    <div style={{ height: 8 }} />
  </>, { })
}

// kind is 'rir' | 'rpe'; value is the set's current rating on that scale (or null); onPick
// receives the new value on that same scale (null to clear). The caller stores it exactly as
// weight/reps are stored — a null drops the key rather than writing a zero.
export const effortPickerSheet = (kind, value, onPick) =>
  ui().openSheet(close => <EffortPicker kind={kind} value={value} onPick={onPick} close={close} />)

/* ============================ print / import a plan ============================ */
export const planToolsSheet = () => ui().openSheet(close => <PlanTools close={close} />)

/* ============================ the complex ============================ */
// A complex is one card in a day, and its sets and load belong to the whole thing: the coach
// writes "3+3 @ 30kg" once, not once per movement. The rows keep their own reps (that is what
// makes a complex a complex), so this sheet only writes the two shared numbers.
//
// `target` says where the superset lives: a day of the dated weeks — what a session is built
// from — addressed as { weekId, dayIndex }.
export const complexConfigSheet = (target, unitIndex) =>
  ui().openSheet(close => <ComplexConfig target={target} unitIndex={unitIndex} close={close} />)

function ComplexConfig({ target, unitIndex, close }) {
  const st = useStore(s => s.S)
  const update = useStore(s => s.update)
  // The exercise list the superset lives in, read fresh on every render so the steppers and the
  // rows behind the sheet agree; the same lookup repeats inside the writer because the store
  // replaces the state tree on every update.
  const locate = s => (s.weeks || []).find(w => w.id === target?.weekId)?.days?.[target?.dayIndex]?.ex
  const ex = locate(st)
  const unit = ex ? (supersetUnits(ex)[unitIndex] || []) : []
  const first = unit.length ? ex[unit[0]] : null
  if (!first) return <><h3>{t('Complex')}</h3><Button variant="primary" onClick={close}>{t('Done')}</Button></>
  const apply = patch => update(s => {
    const list = locate(s)
    if (!list) return
    const members = supersetUnits(list)[unitIndex] || []
    members.forEach(i => { list[i] = { ...list[i], ...patch } })
  })
  return <>
    <h3>{t('Complex')}</h3>
    <div className="muted small" style={{ marginBottom: 14 }}>{t('Sets and load are the same for every exercise in the complex — each one keeps its own reps.')}</div>
    <Stepper label={t('Sets')} value={first.sets || 1} step={1} decimal={false} onChange={v => apply({ sets: v })} />
    <div style={{ height: 8 }} />
    <Stepper label={t('Weight ({0})', st.unit)} value={first.weight || 0} step={2.5} onChange={v => apply({ weight: v })} />
    <div style={{ height: 10 }} />
    <Button variant="primary" onClick={close}>{t('Done')}</Button>
  </>
}

/* ============================ the coach's spreadsheet ============================ */
// The reading lives in lib/plan-aliases.js and lib/import-plan.js; this is the way in. The review
// screen is where a wrong guess gets fixed, and the fix is remembered in S.planAliases — the same
// Italian phrases come back every week, so the second week arrives already corrected.
export function coachPlanSheet(sheets) {
  // The picker stacks on top of the review (that is how the add-to-routine flow wants it); a
  // correction pops whatever was opened for it, so the review comes back into view.
  const pick = onPick => {
    const before = ui().sheets.length
    exercisePicker(ex => { onPick(ex); ui().sheets.slice(before).forEach(s => ui().closeSheet(s.id)) })
  }
  ui().openSheet(close => <CoachImport sheets={sheets} close={close} pick={pick} menu={menuSheet} />)
}

/** Read a coach's workbook (one sheet per week) or a CSV, then show the review. The file comes
 *  from the local file input; a native Google Sheets file goes through importCoachPlanFromDrive
 *  instead, which exports it before handing the bytes to the same readers. */
export function importCoachPlan(file) {
  const sheets = /\.csv$/i.test(file.name || '')
    ? file.text().then(text => [{ name: String(file.name).replace(/\.[a-z]+$/i, ''), grid: parseCSV(text) }])
    : file.arrayBuffer().then(buf => readXlsx(buf)).then(wb => wb.sheets)
  return sheets.then(all => {
    // A sheet with nothing in it is a tab the coach never used.
    const usable = (all || []).filter(s => Array.isArray(s.grid) && s.grid.some(r => r.some(c => c)))
    if (!usable.length) { toast(t('That file has no training in it')); return }
    coachPlanSheet(usable)
  }).catch(() => toast(t('Could not read that file. A coach’s plan is an .xlsx, .csv or Google Sheets file.')))
}

/** Pick a spreadsheet through Google's native Picker, then read it the same way as a local file.
 *  A native Google Sheet holds no bytes of its own, so it is exported to .xlsx first; an .xlsx or
 *  .csv already in Drive is downloaded as-is. */
export async function importCoachPlanFromDrive() {
  try {
    const { token, fileId } = await pickFromDrive()
    const meta = await fileMeta(fileId, token)
    const name = meta.name || 'coach'
    const base = name.replace(/\.[a-z]+$/i, '')
    const isSheet = meta.mimeType === 'application/vnd.google-apps.spreadsheet'
    const sheets = isSheet
      ? (await readXlsx(await exportSheetToXlsx(fileId, token))).sheets
      : /\.csv$/i.test(name)
        ? [{ name: base, grid: parseCSV(await (await downloadFile(fileId, token)).text()) }]
        : (await readXlsx(await (await downloadFile(fileId, token)).arrayBuffer())).sheets
    const usable = (sheets || []).filter(s => Array.isArray(s.grid) && s.grid.some(r => r.some(c => c)))
    if (!usable.length) { toast(t('That file has no training in it')); return }
    coachPlanSheet(usable)
  } catch (e) {
    console.error('importCoachPlanFromDrive failed:', e)
    toast(t('Could not read that file. A coach’s plan is an .xlsx, .csv or Google Sheets file.'))
  }
}

function PlanTools({ close }) {
  const st = useStore(s => s.S)
  const coachRef = useRef(null)
  const hasRoutines = (st.weeks || []).some(w => (w.days || []).some(d => d.ex && d.ex.length))

  return <>
    <h3>{t('Share your plan')}</h3>
    <div className="muted small" style={{ marginBottom: 16 }}>{t('Put your week on paper.')}</div>
    <Button variant="tinted" icon="download" onClick={() => {
      close()
      // Web: the browser's print dialog (→ Save as PDF). Mobile: the OS print flow via the
      // native Print plugin — Android WebView has no window.print(). Same printable HTML both ways.
      if (MOBILE) printHtml(planPrintHTML(st, ''), t('Weekly Training Plan')).catch(() => { /* dismissed */ })
      else printPlan(st, '')
    }} disabled={!hasRoutines}>{t('Print / Save as PDF')}</Button>
    <div className="dim small" style={{ margin: '7px 2px 0', lineHeight: 1.4 }}>{t('A clean one-page-per-plan printout — no exercise ever splits across a page.')}</div>
    {!hasRoutines && <div className="dim small" style={{ margin: '12px 2px 0' }}>{t('Add an exercise to a routine first — an empty plan has nothing to share.')}</div>}
    <div className="sech">{t('Training from a coach?')}</div>
    <Button variant="ghost" icon="upload" onClick={() => coachRef.current?.click()}>{t('Import a coach’s plan')}</Button>
    <div className="dim small" style={{ margin: '7px 2px 0', lineHeight: 1.4 }}>{t('His spreadsheet, one sheet per week — read, reviewed and turned into routines.')}</div>
    <input ref={coachRef} type="file" accept=".xlsx,.csv" onChange={ev => { const f = ev.target.files[0]; ev.target.value = ''; if (f) { close(); importCoachPlan(f) } }} hidden />
    <div style={{ height: 8 }} />
    <Button variant="ghost" icon="folder" onClick={() => { close(); importCoachPlanFromDrive() }}>{t('Import from Google Drive')}</Button>
  </>
}

/* ============================ workout detail ============================ */
function WorkoutDetail({ w, close }) {
  const noteRef = useRef(null)
  const onNoteFocus = useSheetKeyboard(noteRef)
  const st = useStore(s => s.S)
  const update = useStore(s => s.update)
  // The session note is editable here rather than only at the finish sheet: what you want to
  // record about a session is often clearer once you have looked at what you actually did.
  const [note, setNote] = useState(w.note || '')
  const saveNote = () => update(s => {
    const rec = s.workouts.find(x => x.id === w.id)
    if (!rec) return
    const text = note.trim().slice(0, NOTE_MAX)
    if (text) rec.note = text; else delete rec.note
  })
  // onBlur alone loses the note: Escape, the Android back gesture and swipe-to-dismiss all
  // close the sheet without ever moving focus out of the textarea. Flush on unmount too. The
  // ref is what makes that work — a cleanup closes over the note from its own render, which
  // is the empty string this started with.
  const latest = useRef(note)
  latest.current = note
  const initial = useRef(w.note || '')
  useEffect(() => () => {
    const text = latest.current.trim().slice(0, NOTE_MAX)
    if (text === initial.current) return
    update(s => {
      const rec = s.workouts.find(x => x.id === w.id)
      if (!rec) return                       // deleted from this very sheet
      if (text) rec.note = text; else delete rec.note
    })
  }, [])
  // A legacy workout listed several routines and stamped each entry's `rid`, so it reads back as
  // per-routine sections. A workout logged since the dated-weeks model carries `weekId`/`dow` and
  // its entries have no `rid` at all, so it falls through to the flat list below — one day is one
  // session and there is nothing left to group by.
  const entryRow = (e, i) => {
    const ex = EXIDX[e.id]
    return <div key={i} className="row" style={{ marginBottom: 12, alignItems: 'flex-start' }}>
      {ex && <Thumb ex={ex} />}
      <div className="grow"><div className="tt capitalize" style={{ fontWeight: 600 }}>{ex ? exerciseNameFor(ex) : (e.n || e.id)} {w.prs && w.prs.includes(e.id) && <span className="pr"><Icon name="trophy" />PR</span>}</div>
        <div className="ss">{e.sets.filter(hasCompletedWork).map(s => setLabel(e.id, s, e.target)).join('  ·  ') || t('no sets')}</div>
        {e.note && <div className="small dim" style={{ marginTop: 3 }}>
          {e.notePin && <Icon name="flag" style={{ fontSize: 12, marginRight: 4, verticalAlign: '-1px', color: 'var(--yellow)' }} />}{e.note}
        </div>}</div>
    </div>
  }
  const groups = []
  w.entries.forEach((e, i) => {
    // Legacy shape only — see above.
    const key = e.rid || '__none'
    let g = groups.find(x => x.key === key)
    if (!g) { g = { key, rid: e.rid || null, items: [] }; groups.push(g) }
    g.items.push([e, i])
  })
  const grouped = groups.length > 1 || (groups[0] && groups[0].rid && (w.routineIds || []).length > 1)
  const del = () => confirmSheet({ title: t('Delete workout?'), message: t('This removes it from your history for good.'), confirmText: t('Delete'), danger: true, onConfirm: () => { update(s => { s.workouts = s.workouts.filter(x => x.id !== w.id) }); close(); toast(t('Workout deleted')) } })
  return <>
    <div className="row between" style={{ gap: 8, alignItems: 'flex-start' }}>
      <h3 style={{ margin: 0 }}>{w.name}</h3>
      {/* The last full-width destructive button in the module, at the foot of a sheet where a
          thumb lands while scrolling. It is a ⋯ here as it is on the week, the day, the config
          and the exercise detail. */}
      <button className="iconbtn ab-ico" aria-label={t('Workout options')}
        onClick={() => menuSheet({ title: w.name, items: [{ icon: 'trash', label: t('Delete workout'), danger: true, onClick: del }] })}>
        <Icon name="more" /></button>
    </div>
    {/* The date and the bodyweight have no tile; the numbers that do are the same four the finish
        summary shows, so a session reads the same on the day and a month later. */}
    <div className="muted small" style={{ marginBottom: 12 }}>{[fmtDate(w.d, true), ...(w.bw ? [fmtNum(w.bw) + ' ' + st.unit] : [])].join(' · ')}</div>
    <div className="tiles">
      <div className="tile"><div className="l">{t('Duration')}</div><div className="v">{fmtDur(w.end - w.start)}</div></div>
      <div className="tile"><div className="l">{t('Volume')}</div><div className="v">{fmtVol(w.vol, st.unit)}</div></div>
      <div className="tile"><div className="l">{t('Sets')}</div><div className="v">{setsDone(w)}</div></div>
      <div className="tile"><div className="l">{t('PRs')}</div><div className="v">{(w.prs || []).length || '—'}</div></div>
    </div>
    {grouped ? groups.map(g => {
      // A group's routine is long gone from the profile (the legacy routine model is), so the
      // section shows the session's own stored name and the default glyph.
      const setN = g.items.reduce((n, [e]) => n + e.sets.filter(s => s.done && !isWarmupRow(s)).length, 0)
      const vol = workoutVolume({ entries: g.items.map(([e]) => e) })
      return <div key={g.key}>
        <div className="row between wd-group" style={{ margin: '2px 0 8px', paddingBottom: 6, borderBottom: '1px solid var(--sep)' }}>
          <div className="row" style={{ gap: 7, fontWeight: 600 }}>
            <Icon name={DEFAULT_GLYPH} />{g.rid ? w.name : t('Freestyle')}
          </div>
          <div className="small dim">{t('{0} sets', setN)} · {fmtVol(vol, st.unit)}</div>
        </div>
        {g.items.map(([e, i]) => entryRow(e, i))}
      </div>
    }) : w.entries.map((e, i) => entryRow(e, i))}
    <div className="fnote">
      <div className="flabel">{t('Session note')}</div>
      <div className="fhint">{t('How the whole workout went. Editable here any time afterwards.')}</div>
      <textarea ref={noteRef} className="input" rows={2} maxLength={NOTE_MAX} value={note}
        placeholder={t('How the session went as a whole.')}
        onFocus={onNoteFocus} onChange={e => setNote(e.target.value)} onBlur={saveNote} />
    </div>
    <div style={{ height: 14 }} />
  </>
}
export const workoutDetailSheet = w => ui().openSheet(close => <WorkoutDetail w={w} close={close} />)

/* ============================ calendar ============================ */
// One month grid, two callers: the calendar reads it and the date field picks from it. They were
// the same twenty lines of arithmetic twice over; the point of keeping them together is that the
// day you pick and the day you look at are drawn by the same thing.
function MonthGrid({ y, mo, byDay = {}, plannedOn = () => false, value = null, disabledOn = () => false, onPick }) {
  const st = useStore(s => s.S)
  const ws = weekStartOf(st)
  // Which column the 1st sits in, and therefore how many blanks come before it.
  const startOffset = weekDayOffset(new Date(y, mo, 1).getDay(), ws)
  const daysIn = new Date(y, mo + 1, 0).getDate()
  const cells = []
  for (let i = 0; i < startOffset; i++) cells.push(<div key={'e' + i} />)
  for (let d = 1; d <= daysIn; d++) {
    const iso = isoOf(new Date(y, mo, d))
    const dayws = byDay[iso]
    const dotCls = dayws ? 'done' : plannedOn(iso) ? 'plan' : ''
    cells.push(<button key={d} type="button" aria-label={fmtDate(iso, true)}
      className={'cal-d' + (dayws ? ' has' : '') + (iso === todayISO() ? ' today' : '') + (iso === value ? ' sel' : '')}
      disabled={disabledOn(iso) || undefined}
      onClick={() => onPick(iso, dayws)}>
      <span>{d}</span><i className={dotCls} />
    </button>)
  }
  return <div className="cal-grid">
    {weekOrder(ws).map(d => <div key={d} className="cal-h">{t(DAYS[d])}</div>)}{cells}
  </div>
}

function Calendar({ start, close }) {
  const st = useStore(s => s.S)
  const now = new Date()
  // `start` is an ISO date when the Stats heatmap opens the calendar on one, and a Date when a
  // caller has already built one. The ISO form parses at noon: at UTC midnight, a day near the 1st
  // is the previous month west of UTC, which opened the heatmap's own day in the wrong month.
  const [cur, setCur] = useState(() => {
    const d = start ? (typeof start === 'string' ? new Date(start + 'T12:00:00') : new Date(start)) : new Date()
    d.setDate(1); return d
  })
  const y = cur.getFullYear(), mo = cur.getMonth()
  const byDay = {}
  st.workouts.forEach(w => (byDay[w.d] = byDay[w.d] || []).push(w))
  const monthWs = st.workouts.filter(w => w.d.startsWith(y + '-' + String(mo + 1).padStart(2, '0')))
  const monthVol = monthWs.reduce((a, w) => a + (w.vol || 0), 0)
  const monthMs = monthWs.reduce((a, w) => a + Math.max(0, (w.end || w.start) - w.start), 0)
  const onNowMonth = y === now.getFullYear() && mo === now.getMonth()

  // What a day means depends on which side of today it is. A day with sessions opens them; a day
  // still to come is where one would be planned; a day already gone is where one would be logged —
  // and only this screen knows which date you meant, so it hands it to the backfill with the date
  // already in it rather than sending you to the plan to go and find it.
  const openDay = (iso, dayws) => {
    if (dayws && dayws.length === 1) { close(); workoutDetailSheet(dayws[0]); return }
    if (dayws && dayws.length > 1) { close(); daySessionsSheet(iso, dayws); return }
    if (iso < todayISO()) { close(); logPastWorkoutSheet(iso); return }
    close(); nav('/plan')
  }

  return <>
    <div className="row between" style={{ marginBottom: 2 }}>
      <button className="iconbtn" onClick={() => setCur(new Date(y, mo - 1, 1))} aria-label={t('Previous month')}><Icon name="chevronLeft" /></button>
      <h3 style={{ margin: 0 }}>{t(MONTHS_LONG[mo])} {y}</h3>
      <button className="iconbtn" onClick={() => setCur(new Date(y, mo + 1, 1))} aria-label={t('Next month')}><Icon name="chevronRight" /></button>
    </div>
    <div className="small muted" style={{ textAlign: 'center' }}>{monthWs.length ? `${t(monthWs.length === 1 ? '{0} workout' : '{0} workouts', monthWs.length)} · ${fmtDur(monthMs)} · ${fmtVol(monthVol, st.unit)}` : t('No workouts this month')}</div>
    {/* Six months back there was no way forward again but six taps on the same arrow. */}
    {!onNowMonth && <div style={{ textAlign: 'center', marginTop: 6 }}>
      <Button size="sm" variant="ghost" onClick={() => setCur(new Date(now.getFullYear(), now.getMonth(), 1))}>{t('Back to this month')}</Button>
    </div>}
    <MonthGrid y={y} mo={mo} byDay={byDay} plannedOn={iso => effectiveDay(st, iso) != null} onPick={openDay} />
    <div className="cal-legend">
      <span><i style={{ background: 'var(--acc)' }} />{t('Trained')}</span>
      <span><i style={{ background: 'var(--label-3)' }} />{t('Planned')}</span>
    </div>
    <div className="small dim" style={{ textAlign: 'center', marginTop: 10 }}>{t('Tap a day you trained for it · a past day to log it · a day to come to plan it')}</div>
  </>
}
export const calendarSheet = start => ui().openSheet(close => <Calendar start={start} close={close} />)

// A day holding more than one session. It was an anonymous sheet built inline in the grid cell.
function DaySessions({ iso, dayws, close }) {
  const st = useStore(s => s.S)
  return <>
    <h3 style={{ marginBottom: 2 }}>{fmtDate(iso, true)}</h3>
    <div className="muted small" style={{ marginBottom: 10 }}>{t('{0} workouts', dayws.length)}</div>
    <div className="list">{dayws.map(w => <WorkoutRow key={w.id} w={w} onClick={() => { close(); workoutDetailSheet(w) }} />)}</div>
  </>
}
const daySessionsSheet = (iso, dayws) => ui().openSheet(close => <DaySessions iso={iso} dayws={dayws} close={close} />)

/* ============================ date and time fields ============================ */
// The backfill sheet's Date and Start time were <input type="date"> and <input type="time"> — the
// only two platform widgets left inside a sheet, drawn in the platform's colours and the platform's
// format ("02/10/2026", never "Fri 2 Oct"), against the rule the rest of this control set is built
// on. The date is the month grid above, so the days that already have sessions are visible while
// you pick one; the time is the app's own stepper.
function DatePicker({ value, max, onPick, close }) {
  const st = useStore(s => s.S)
  const [cur, setCur] = useState(() => { const d = value ? new Date(value + 'T12:00:00') : new Date(); d.setDate(1); return d })
  const y = cur.getFullYear(), mo = cur.getMonth()
  const byDay = {}
  st.workouts.forEach(w => (byDay[w.d] = byDay[w.d] || []).push(w))
  return <>
    <div className="row between" style={{ marginBottom: 2 }}>
      <button className="iconbtn" onClick={() => setCur(new Date(y, mo - 1, 1))} aria-label={t('Previous month')}><Icon name="chevronLeft" /></button>
      <h3 style={{ margin: 0 }}>{t(MONTHS_LONG[mo])} {y}</h3>
      <button className="iconbtn" onClick={() => setCur(new Date(y, mo + 1, 1))} aria-label={t('Next month')}><Icon name="chevronRight" /></button>
    </div>
    <MonthGrid y={y} mo={mo} byDay={byDay} plannedOn={iso => effectiveDay(st, iso) != null}
      value={value} disabledOn={iso => !!max && iso > max} onPick={iso => { onPick(iso); close() }} />
    <div className="cal-legend">
      <span><i style={{ background: 'var(--acc)' }} />{t('Trained')}</span>
      <span><i style={{ background: 'var(--label-3)' }} />{t('Planned')}</span>
    </div>
  </>
}
const datePickerSheet = opts => ui().openSheet(close => <DatePicker {...opts} close={close} />)

// The other half of the pair: an hour and a minute on the stepper every other number in the app
// uses, plus the handful of times a session actually starts at.
const START_TIMES = ['06:30', '12:00', '17:30', '18:00', '19:30']
// The reminder is a different job from a session start — one of these is the stored default — so it
// carries its own handful rather than the gym times above.
export const REMINDER_TIMES = ['07:00', '08:00', '12:00', '18:00', '20:00']
function TimePicker({ value, onPick, close, title = t('Start time'), presets = START_TIMES }) {
  const [h0, m0] = String(value || '18:00').split(':').map(Number)
  const [h, setH] = useState(isFinite(h0) ? h0 : 18)
  const [m, setM] = useState(isFinite(m0) ? m0 : 0)
  const pad = n => String(n).padStart(2, '0')
  const now = pad(h) + ':' + pad(m)
  const pick = x => { const [a, b] = x.split(':').map(Number); setH(a); setM(b) }
  return <>
    <h3>{title}</h3>
    <div className="row cfgrow" style={{ marginBottom: 10 }}>
      <Stepper label={t('Hour')} value={h} step={1} decimal={false} onChange={v => setH(Math.min(23, Math.max(0, Math.round(v))))} />
      <Stepper label={t('Minute')} value={m} step={5} decimal={false} onChange={v => setM(Math.min(59, Math.max(0, Math.round(v))))} />
    </div>
    <div className="chips" style={{ marginBottom: 16 }}>
      {presets.map(x => <button key={x} className={'chip' + (x === now ? ' on' : '')} onClick={() => pick(x)}>{x}</button>)}
    </div>
    <Button variant="primary" onClick={() => { onPick(now); close() }}>{t('Done')}</Button>
  </>
}
export const timePickerSheet = opts => ui().openSheet(close => <TimePicker {...opts} close={close} />)

/* shared small workout row (used in lists) */
export function WorkoutRow({ w, onClick }) {
  const st = useStore(s => s.S)
  // A workout filed under a legacy routine has no name to look up any more — it shows its own
  // stored name and the default glyph, like a session logged since the dated weeks.
  const glyph = DEFAULT_GLYPH
  return <div className="item" {...tappable(onClick)}>
    <span className="lrow-i" style={{ width: 34, height: 34, borderRadius: 8, fontSize: 19 }}><Icon name={glyph} /></span>
    <div className="grow"><div className="tt">{w.name}</div>
      <div className="ss">{[fmtDate(w.d, true), ...durPart(w.end - w.start), t('{0} sets', setsDone(w)), fmtVol(w.vol, st.unit)].join(' · ')}</div></div>
    {w.prs && w.prs.length > 0 && <span className="pr"><Icon name="trophy" />{w.prs.length} PR</span>}
    <Icon name="chevronRight" className="chev" />
  </div>
}

/* ============================ workout lifecycle ============================ */
// `day` is a day object from the dated weeks (S.weeks[].days), or null for an explicit freestyle
// session. A day holds the whole session, so there is nothing to merge and no per-entry routine.
export function startFlow(day) {
  // The weigh-in is a setting (Settings → Workout, issue #137): off goes straight
  // into the session with no body weight on it, same as "Start without weighing in".
  if (S().weighIn === false) { beginWorkout(day, null); return }
  bwSheet({ required: true, onDone: bw => beginWorkout(day, bw) })
}
export function beginWorkout(day, bw) {
  const st = S()
  const { entries, name } = buildDayEntries(st, day)
  update(s => {
    s.active = {
      id: uid(), d: todayISO(), start: Date.now(),
      // Where the session came from: the dated week and weekday, both null for freestyle. The
      // day's exercises are copied into `entries`; nothing here needs a per-entry routine id,
      // and no top-level `excludeFromProgression` — per-entry `noProg` does it.
      weekId: day ? (weekFor(st, todayISO())?.id ?? null) : null,
      dow: day?.dow ?? null,
      name, bw: bw || null, cur: 0, entries,
      // Snapshot the layout at start so the header ⋮ can change it for this session only —
      // changing the saved default (Settings → Workout view) mid-session leaves it alone.
      workoutView: st.workoutView || 'cards',
    }
  })
  useUI.getState().stopRest()
  nav('/workout')
}

/* ============================ log a past workout ============================ */
// The same screen as a live session, pointed at another day. `backfill` on the active
// session is what tells the workout screen to drop the clock and the rest timers, and tells
// the finish path to file the workout where its date belongs instead of at the end.
// `initialDate` is set when the calendar's own day tap opened this sheet: the date it already
// knows, which is the one thing the sheet used to make you find again in a platform date picker.
function LogPastWorkout({ initialDate, close }) {
  const st = useStore(s => s.S)
  const yesterday = new Date(); yesterday.setDate(yesterday.getDate() - 1)
  const [date, setDate] = useState(initialDate || isoOf(yesterday))
  const [time, setTime] = useState('18:00')
  const [dur, setDur] = useState(60)
  const today = todayISO()
  // Whatever the dated plan has on that date, or an empty session to fill in by hand — there is
  // no routine to pick any more, so the date alone decides what the screen opens with.
  const planned = effectiveDay(st, date)
  // The same list the calendar would show for that day, read while the date is being chosen
  // rather than after Continue has been tapped.
  const existing = workoutsOn(st, date)

  const go = replaceId => {
    close()
    beginBackfill({ iso: date, time, durationMin: dur, replaceId })
  }
  const submit = () => {
    if (!date || date > today) { toast(t('Pick a day up to today')); return }
    if (!existing.length) { go(null); return }
    // Deciding what to do about a day that is already spoken for is a list of actions, not the
    // centred alert with two full-width buttons it was: the destructive action carries the trash
    // and the danger role, the one that adds a second session reads first, and dismissing it
    // cancels — which is all the third full-width button ever did.
    menuSheet({
      title: fmtDate(date, true),
      subtitle: t('There is already a workout on that day.'),
      items: [
        { icon: 'plus', label: t('Add as second workout'), onClick: () => go(null) },
        ...existing.map(w => ({
          icon: 'trash', danger: true, onClick: () => go(w.id),
          label: existing.length > 1 ? t('Replace {0}', w.name) : t('Replace'),
        })),
      ],
    })
  }

  return <>
    <h3>{t('Log a past workout')}</h3>
    <div className="muted small" style={{ marginBottom: 12 }}>{t('Logged on the usual workout screen, without timers.')}</div>
    <div className="list menu-list">
      <Row icon="calendar" title={t('Date')} value={fmtDate(date, true)} accessory="chevron"
        onClick={() => datePickerSheet({ value: date, max: today, onPick: setDate })} />
      <Row icon="clock" title={t('Start time')} value={time} accessory="chevron"
        onClick={() => timePickerSheet({ value: time, onPick: setTime })} />
    </div>
    <div style={{ height: 12 }} />
    <Stepper label={t('Duration')} unit="min" value={dur} step={5} decimal={false} onChange={v => setDur(Math.max(1, Math.round(v)))} />
    <div style={{ height: 10 }} />
    <Row icon="dumbbell" title={t('Plan')} value={planned ? planned.name : t('Freestyle')} />
    {existing.length > 0 && <>
      <div className="sech">{t('Already on this day')}</div>
      <div className="list menu-list">
        {existing.map(w => <Row key={w.id} icon={DEFAULT_GLYPH} title={w.name}
          subtitle={[fmtDur((w.end || w.start) - w.start), fmtVol(w.vol || workoutVolume(w), st.unit)].join(' · ')} />)}
      </div>
    </>}
    <div style={{ height: 18 }} />
    <Button variant="primary" onClick={submit}>{t('Continue')}</Button>
  </>
}
export function logPastWorkoutSheet(initialDate) {
  if (S().active) { toast(t('Finish the current workout first.')); return }
  ui().openSheet(close => <LogPastWorkout initialDate={initialDate} close={close} />)
}
// A backfilled session is the day that date is planned to have, exactly like a live start: the
// same builder walks up to the same entries. A date with no day planned opens empty.
function beginBackfill({ iso, time, durationMin, replaceId }) {
  const st = S()
  const day = effectiveDay(st, iso)
  const { entries, name } = buildDayEntries(st, day)
  update(s => {
    s.active = {
      id: uid(), d: iso, start: backfillStart(iso, time),
      weekId: day ? (weekFor(st, iso)?.id ?? null) : null,
      dow: day?.dow ?? null,
      name, bw: null, cur: 0, entries,
      backfill: { durationMin, replaceId: replaceId || null },
      // Same layout snapshot as a live session (see beginWorkout).
      workoutView: st.workoutView || 'cards',
    }
  })
  useUI.getState().stopRest()
  nav('/workout')
}



/* ============================ exercise notes ============================
   Two notes, one sheet, because from the user's side it is one question — "what do I want to
   remember about this exercise?" — with two different lifetimes:

     · today's note belongs to this session and is stored on the workout entry. It is history:
       what happened, how it felt. The PIN is the user saying "this one is for next time", which
       only they can know at the moment of writing — see pinnedNoteFor.
     · the standing note belongs to the exercise itself and lives in S.exNotes. Seat height, pin
       position, a form cue. True every session, so it is shown every session and never expires.

   A routine's own `note` (a plan's instruction for this exercise) is edited in the config sheet
   and is deliberately not here: it belongs to the plan, not to the day or to the movement. */
function ExerciseNote({ entryIdx, exId, close }) {
  const noteRef = useRef(null)
  const onNoteFocus = useSheetKeyboard(noteRef)
  const st = useStore(s => s.S)
  const update = useStore(s => s.update)
  const A = st.active
  const entry = entryIdx == null ? null : A?.entries?.[entryIdx]
  // Two ways in. From the workout screen the sheet is about one entry and carries both notes: what
  // happened today, and the cue that outlives it. From the exercise history it is about the
  // exercise alone — the standing note belongs to the movement, not to a session, so it can be read
  // and corrected there with nothing running and nothing logged.
  const id = entry ? entry.id : exId
  const ex = id ? exOr(id) : null
  const [note, setNote] = useState(entry?.note || '')
  const [pin, setPin] = useState(!!entry?.notePin)
  const [standing, setStanding] = useState(id ? (st.exNotes?.[id] || '') : '')
  // An entry that goes away takes the sheet with it: the session ended, or the exercise was removed
  // from it. An exercise id has no such state to watch.
  useEffect(() => { if (entryIdx != null && !entry) close() }, [!entry, entryIdx])
  if (!ex) return null

  const save = () => {
    const always = standing.trim().slice(0, NOTE_MAX)
    update(s => {
      // The session note is written through the entry index rather than the id read above, so a
      // session that moved under us is still written to the entry this sheet was opened for.
      const e = entryIdx == null ? null : s.active?.entries?.[entryIdx]
      if (e) {
        const today = note.trim().slice(0, NOTE_MAX)
        if (today) { e.note = today; if (pin) e.notePin = true; else delete e.notePin }
        else { delete e.note; delete e.notePin }
      }
      s.exNotes = s.exNotes || {}
      if (always) s.exNotes[id] = always
      else delete s.exNotes[id]
    })
    close()
  }

  // Both lifetimes used to be explained in the placeholders — "kept with today's workout",
  // "shown every session" — and a placeholder goes the moment you start typing, so the sentence
  // telling you which note you are writing disappeared exactly when you were writing it. It sits
  // under the label now, where it stays. The labels themselves were .small.muted: 13px of muted
  // prose where a label wants the label role.
  return <>
    <h3 className="capitalize">{exerciseNameFor(ex)}</h3>
    {/* Only when there is a session to attach it to. The standing note below is the exercise's own
        and is the whole sheet when it was opened from the history. */}
    {entry && <div className="fnote">
      <div className="flabel">{t('This session')}</div>
      <div className="fhint">{t('Kept with today’s workout — what happened, how it felt.')}</div>
      <textarea ref={noteRef} className="input" rows={3} maxLength={NOTE_MAX} value={note}
        placeholder={t('How it went, what to change.')}
        onFocus={onNoteFocus} onChange={e => setNote(e.target.value)} />
      <div className="sect-b" style={{ marginTop: 10 }}>
        <Row icon="flag" title={t('Show this next time')}
          subtitle={t('Brings it up again the next time you train this exercise.')}>
          <Switch checked={pin} onChange={setPin} disabled={!note.trim()} />
        </Row>
      </div>
    </div>}
    <div className="fnote">
      <div className="flabel">{t('Every session')}</div>
      <div className="fhint">{t('Shown every time you train this exercise — seat height, pin position, a form cue.')}</div>
      <textarea className="input" rows={entry ? 2 : 3} maxLength={NOTE_MAX} value={standing}
        placeholder={t('Seat height, pin position, a form cue.')}
        onChange={e => setStanding(e.target.value)} />
    </div>
    <div style={{ height: 18 }} />
    <Button variant="primary" onClick={save}>{t('Save')}</Button>
  </>
}
export const exerciseNoteSheet = opts => ui().openSheet(close => <ExerciseNote {...opts} close={close} />)

/* The session note: how the whole workout went, as opposed to how one exercise went. It lives
   on the active session, so buildCompletedWorkout carries it onto the finished workout and it
   shows up again in history — where it stays editable. Written here rather than only after the
   fact because "notes you can write during a workout" is the point; a note you can only add
   once the session is filed is a different, smaller feature. */
function SessionNote({ close }) {
  const noteRef = useRef(null)
  const onNoteFocus = useSheetKeyboard(noteRef)
  const st = useStore(s => s.S)
  const update = useStore(s => s.update)
  const A = st.active
  const [note, setNote] = useState(A?.note || '')
  useEffect(() => { if (!A) close() }, [!A])
  if (!A) return null

  const save = () => {
    const text = note.trim().slice(0, NOTE_MAX)
    update(s => { if (!s.active) return; if (text) s.active.note = text; else delete s.active.note })
    close()
  }

  return <>
    <h3>{t('Session note')}</h3>
    <div className="fnote">
      <div className="flabel">{t('The session')}</div>
      <div className="fhint">{t('How the whole workout went. Kept with today’s workout, and editable from History afterwards.')}</div>
      <textarea ref={noteRef} className="input" rows={4} maxLength={NOTE_MAX} value={note}
        placeholder={t('How the session went as a whole.')}
        onFocus={onNoteFocus} onChange={e => setNote(e.target.value)} />
    </div>
    <div style={{ height: 18 }} />
    <Button variant="primary" onClick={save}>{t('Save')}</Button>
  </>
}
export const sessionNoteSheet = () => ui().openSheet(close => <SessionNote close={close} />)

function RenameWorkout({ close }) {
  const inputRef = useRef(null)
  const onFocus = useSheetKeyboard(inputRef)
  const st = useStore(s => s.S)
  const update = useStore(s => s.update)
  const A = st.active
  const [name, setName] = useState(A?.name || '')
  useEffect(() => { if (!A) close() }, [!A])
  if (!A) return null

  const trimmed = name.trim().slice(0, 60)
  const canSave = trimmed.length > 0

  const save = () => {
    if (!canSave) return
    update(s => {
      if (!s.active) return
      s.active.name = trimmed
      s.active.customName = true
    })
    close()
  }

  return <>
    <h3>{t('Rename workout')}</h3>
    <input ref={inputRef} className="input" type="text" autoFocus maxLength={60} value={name}
      placeholder={t('Workout title')}
      onFocus={onFocus} onChange={e => setName(e.target.value)}
      onKeyDown={e => { if (e.key === 'Enter' && canSave) save() }} />
    <div style={{ height: 18 }} />
    <Button variant="primary" disabled={!canSave} onClick={save}>{t('Save')}</Button>
  </>
}
export const renameWorkoutSheet = () => ui().openSheet(close => <RenameWorkout close={close} />)

// Shown when the last exercise's last set is checked — finish, or keep going.
function WorkoutComplete({ close }) {
  // The same dialog the confirm uses. It was a centred block with a bare 44px glyph — centred on
  // one axis only, which is the shape that put the empty state's icon at the top of its circle —
  // and two full-width buttons, one of which looked as important as the other.
  return <div className="dlg">
    <div className="dlg-ico ok"><Icon name="checkCircle" /></div>
    <h3 className="ctr">{t("That's the whole workout!")}</h3>
    <div className="dlg-body ctr">{t('Finish up, or keep going and add another exercise.')}</div>
    <div className="dlg-acts">
      <button className="dlg-btn" onClick={() => { close(); useUI.getState().toast(t('Keep going — tap “+ Add exercise” below')) }}>{t('Keep going')}</button>
      <button className="dlg-btn" onClick={() => { close(); finishWorkout() }}>{t('Finish workout')}</button>
    </div>
  </div>
}
export const workoutCompleteSheet = () => ui().openSheet(close => <WorkoutComplete close={close} />, { kind: 'center' })

function FinishSummary({ w, prs, close }) {
  const st = useStore(s => s.S)
  // The one moment every session ends on. Same dialog as the confirm and the "whole workout"
  // prompt, so the three read as one component; the four tiles share one type size instead of
  // three at 1.1rem and a fourth at 20px; and the records are chips rather than a stack of
  // identical small-accent lines.
  return <div className="dlg">
    <div className="dlg-ico ok"><Icon name="trophy" /></div>
    <h3 className="ctr">{t('Workout complete!')}</h3>
    <div className="tiles">
      <div className="tile"><div className="l">{t('Duration')}</div><div className="v">{fmtDur(w.end - w.start)}</div></div>
      <div className="tile"><div className="l">{t('Volume')}</div><div className="v">{fmtVol(w.vol, st.unit)}</div></div>
      <div className="tile"><div className="l">{t('Sets')}</div><div className="v">{setsDone(w)}</div>
        <div className="s">{t('{0} work', workSetsDone(w))}</div></div>
      <div className="tile"><div className="l">{t('PRs')}</div><div className="v">{prs.length || '—'}</div></div>
    </div>
    {prs.length > 0 && <>
      <div className="sech">{t('New records')}</div>
      <div className="chips">
        {prs.map(id => <span key={id} className="chip on nocap capitalize">{EXIDX[id] ? exerciseNameFor(EXIDX[id]) : id}</span>)}
      </div>
    </>}
    <div className="sech">{t('What you just trained')}</div>
    <BodyMap load={loadOfWorkouts([w])} body={st.body} />
    <div className="dlg-acts">
      <button className="dlg-btn" onClick={() => { close(); nav('/home') }}>{t('Done')}</button>
    </div>
  </div>
}
export function finishWorkout() {
  const A = S().active
  if (!A) return
  const done = setsDoneActive(A)
  const total = setUnitsTotal(A.entries)
  if (!done) { confirmSheet({ title: t('Nothing logged yet'), message: t('You haven’t checked off any sets. Finish the workout anyway?'), confirmText: t('Finish anyway'), onConfirm: doFinishWorkout }); return }
  if (done < total) { confirmSheet({ title: t('Finish early?'), message: t(total - done === 1 ? '{0} set still unchecked. Finish the workout now?' : '{0} sets still unchecked. Finish the workout now?', total - done), confirmText: t('Finish workout'), onConfirm: doFinishWorkout }); return }
  doFinishWorkout()
}
function doFinishWorkout() {
  const st = S()
  const A = st.active
  if (!A) return
  const past = !!A.backfill
  const prs = []
  // A workout logged into the past cannot claim records against the history that came after
  // it, so a backfilled session reports none and leaves the confirmed weights alone.
  if (!past) A.entries.forEach(e => {
    const loads = e.sets.filter(s => s.done && !isWarmupRow(s)).map(s => s.w).filter(w => w > 0)
    const mx = loads.length ? Math.max(...loads) : 0
    if (mx > 0 && mx > bestWeightFor(st, e.id)) prs.push(e.id)
  })
  const w = buildCompletedWorkout(A, {
    end: past ? backfillEnd(A) : Date.now(),
    prs,
    snapshotFor: e => EXIDX[e.id]?.custom ? exerciseMuscleSnapshot(EXIDX[e.id]) : null,
  })
  w.vol = workoutVolume(w)
  update(s => {
    if (past) {
      s.workouts = completeBackfill(s.workouts, A, w)
    } else {
      w.entries.forEach(e => {
        const mx = bestWeightForEntry(e)
        if (mx > 0 && mx > ((s.exWeights[e.id] || {}).w || 0)) s.exWeights[e.id] = { w: mx, d: w.d }
      })
      s.workouts.push(w)
    }
    s.active = null
  })
  useStore.getState().autoBackupNow()
  useUI.getState().stopRest()
  beep(snd(), 880, 0.15); beep(snd(), 1100, 0.15, 0.18); beep(snd(), 1320, 0.3, 0.36)
  ui().openSheet(close => <FinishSummary w={w} prs={prs} close={close} />, { kind: 'center', locked: true })
}
/* ============================ competitions (meets) ============================
   A competition is its own record, not a training session: a dated event with a bodyweight
   category and up to three snatch and three clean & jerk attempts, each good or no lift.
   lib/competition.js owns the reading (what a total is, which meet is next); nothing here
   touches S.workouts, so a meet can never be mistaken for a training day. The attempts are
   the one custom control: a set row (weight x reps) is the wrong shape for "was it good".
   ============================================================================ */

// One attempt: which one, the weight on the bar, and the judgement. The number badge names the
// row, the stepper takes the kilos, and the segmented control — the app's existing choice for
// two exclusive short options — carries the decision. A no-lift keeps its weight: it was on the
// bar, and what a meet teaches includes what did not go up.
function AttemptRow({ n, a, lift, onChange }) {
  return <div className="att" role="group" aria-label={t('{0} attempt {1}', t(lift), n)}>
    <span className="att-n" aria-hidden="true">{n}</span>
    <Stepper value={a.w} step={1} decimal={false} onChange={w => onChange({ ...a, w })} />
    <Segmented className="seg-inline att-mk" value={!!a.made}
      onChange={made => onChange({ ...a, made })}
      options={[{ value: true, label: t('Good'), ariaLabel: t('Good lift') },
                { value: false, label: t('No lift'), ariaLabel: t('No lift') }]} />
  </div>
}

function AttemptSet({ lift, attempts, setAttempts }) {
  const change = (i, a) => setAttempts(list => list.map((x, j) => (j === i ? a : x)))
  return <div className="att-set">
    {attempts.map((a, i) => <AttemptRow key={i} n={i + 1} a={a} lift={lift} onChange={a2 => change(i, a2)} />)}
  </div>
}

function MeetSheet({ meet, close }) {
  const st = useStore(s => s.S)
  const unit = st.unit
  const editing = !!meet
  const [name, setName] = useState(meet?.name || '')
  const [date, setDate] = useState(meet?.d || todayISO())
  const [place, setPlace] = useState(meet?.place || '')
  const [cls, setCls] = useState(meet?.class ?? null)
  const [bw, setBw] = useState(meet?.bw ?? null)
  const [placing, setPlacing] = useState(meet?.placing ?? null)
  const [snatch, setSnatch] = useState(() => attemptRows(meet, 'snatch'))
  const [cj, setCj] = useState(() => attemptRows(meet, 'cj'))
  const [note, setNote] = useState(meet?.note || '')
  // The total reads off the draft as it is typed, so the sheet answers the meet's only number
  // while there is still time to change an attempt.
  const draft = { snatch: cleanAttempts(snatch), cj: cleanAttempts(cj) }
  const bestS = bestAttempt(draft.snatch)
  const bestC = bestAttempt(draft.cj)
  const total = totalOf(draft)

  const save = () => {
    const record = {
      id: meet?.id || uid(),
      d: date,
      name: name.trim(),
      place: place.trim(),
      class: cls,
      bw: bw || null,
      snatch: draft.snatch,
      cj: draft.cj,
      placing: placing || null,
      note: note.trim(),
    }
    update(s => { s.competitions = upsertMeet(s.competitions, record) })
    close()
    toast(editing ? t('Competition saved') : t('Competition added'))
  }

  return <>
    <h3>{editing ? t('Edit competition') : t('Add a competition')}</h3>
    <div className="muted small" style={{ marginBottom: 12 }}>{t('A meet is its own record — nothing here changes your training log.')}</div>
    <TextField placeholder={t('Competition name')} aria-label={t('Competition name')} value={name}
      onChange={e => setName(e.target.value)} />
    <div style={{ height: 10 }} />
    <div className="list menu-list">
      <Row icon="calendar" title={t('Date')} value={fmtDate(date, true, true)} accessory="chevron"
        onClick={() => datePickerSheet({ value: date, onPick: setDate })} />
      <SelectRow icon="trophy" title={t('Weight class')} sheetTitle={t('Weight class')} value={cls}
        onChange={setCls} options={[{ value: null, label: t('Not set') }, ...classOptions(listFor(st))]}
        action={{ icon: 'pencil', label: t('Edit categories'), onClick: weightClassesSheet }} />
    </div>
    <div style={{ height: 10 }} />
    <div className="stp-set">
      <Stepper label={t('Bodyweight at weigh-in')} unit={unit} value={bw ?? 0} step={0.1} onChange={v => setBw(v || null)} />
      <Stepper label={t('Placing')} value={placing ?? 0} step={1} decimal={false} onChange={v => setPlacing(v || null)} />
    </div>
    <div className="sech">{t('Snatch')}</div>
    <AttemptSet lift="Snatch" attempts={snatch} setAttempts={setSnatch} />
    <div className="sech">{t('Clean & jerk')}</div>
    <AttemptSet lift="Clean & jerk" attempts={cj} setAttempts={setCj} />
    <div className="tiles meet-tiles" style={{ marginTop: 14 }}>
      <div className="tile"><div className="l">{t('Snatch')}</div><div className="v">{bestS == null ? '—' : fmtNum(bestS)}</div><div className="s">{unit}</div></div>
      <div className="tile"><div className="l">{t('Clean & jerk')}</div><div className="v">{bestC == null ? '—' : fmtNum(bestC)}</div><div className="s">{unit}</div></div>
      <div className="tile"><div className="l">{t('Total')}</div><div className="v">{total == null ? '—' : fmtNum(total)}</div><div className="s">{unit}</div></div>
    </div>
    <div className="sech">{t('Note')}</div>
    <TextField placeholder={t('How the meet went…')} aria-label={t('Note')} value={note} onChange={e => setNote(e.target.value)} />
    <div style={{ height: 18 }} />
    <Button variant="primary" onClick={save}>{editing ? t('Save competition') : t('Add a competition')}</Button>
  </>
}

function MeetDetail({ meet, close }) {
  const st = useStore(s => s.S)
  const unit = st.unit
  const total = totalOf(meet)
  const bestS = bestAttempt(meet.snatch)
  const bestC = bestAttempt(meet.cj)
  const when = daysUntil(meet, todayISO())
  const edit = () => { close(); meetSheet(meet) }
  const del = () => confirmSheet({
    title: t('Delete this competition?'),
    message: t('The meet and its attempts are removed. Your training log is untouched.'),
    confirmText: t('Delete'), danger: true,
    onConfirm: () => { update(s => { s.competitions = removeMeet(s.competitions, meet.id) }); close(); toast(t('Competition deleted')) },
  })
  const lift = (label, key) => <>
    <div className="sech">{t(label)}</div>
    <div className="list">
      {(meet[key] || []).map((a, i) => {
        const made = attemptMade(a)
        return <Row key={i} icon={made ? 'check' : 'xmark'} title={t('Attempt {0}', i + 1)}
          subtitle={fmtNum(a.w) + ' ' + unit} value={made ? t('Good lift') : t('No lift')} danger={!made} />
      })}
      {!(meet[key] || []).length && <div className="muted small">{t('No attempts logged.')}</div>}
    </div>
  </>
  return <>
    <div className="row between" style={{ gap: 8, alignItems: 'flex-start' }}>
      <h3 style={{ margin: 0 }}>{meet.name || t('Competition')}</h3>
      <button className="iconbtn ab-ico" aria-label={t('Competition options')}
        onClick={() => menuSheet({ title: meet.name || t('Competition'), items: [
          { icon: 'pencil', label: t('Edit'), onClick: edit },
          { icon: 'trash', label: t('Delete'), danger: true, onClick: del },
        ] })}><Icon name="more" /></button>
    </div>
    <div className="muted small" style={{ marginBottom: 4 }}>
      {fmtDate(meet.d, true, true)}{meet.place ? ' · ' + meet.place : ''}
      {when != null && when >= 0 ? ' · ' + (when === 0 ? t('Today') : when === 1 ? t('Tomorrow') : t('in {0} days', when)) : ''}
    </div>
    {meet.class && <div className="muted small" style={{ marginBottom: 4 }}>
      {t('Weight class')} {meet.class} {unit}
      {meet.bw ? ' · ' + t('Bodyweight') + ' ' + fmtNum(meet.bw) + ' ' + unit : ''}
      {meet.placing ? ' · ' + t('Placing') + ' ' + meet.placing : ''}
    </div>}
    <div className="tiles meet-tiles" style={{ marginTop: 12 }}>
      <div className="tile"><div className="l">{t('Snatch')}</div><div className="v">{bestS == null ? '—' : fmtNum(bestS)}</div><div className="s">{unit}</div></div>
      <div className="tile"><div className="l">{t('Clean & jerk')}</div><div className="v">{bestC == null ? '—' : fmtNum(bestC)}</div><div className="s">{unit}</div></div>
      <div className="tile"><div className="l">{t('Total')}</div><div className="v">{total == null ? '—' : fmtNum(total)}</div><div className="s">{unit}</div></div>
    </div>
    {lift('Snatch', 'snatch')}
    {lift('Clean & jerk', 'cj')}
    {meet.note && <><div className="sech">{t('Note')}</div><div className="small">{meet.note}</div></>}
    <div style={{ height: 18 }} />
    <Button variant="primary" onClick={edit}>{t('Edit')}</Button>
  </>
}

export const meetSheet = meet => ui().openSheet(close => <MeetSheet meet={meet} close={close} />)
/* The federation's categories, editable, one list per body. They change every few years and
   the app must not need a release to follow; a meet keeps the string it was saved with, so
   editing this list never rewrites a logged result. Reached from Settings → Competition and
   from the meet's own class picker (SelectRow's `action`). */
function WeightClassesSheet({ close }) {
  const st = useStore(s => s.S)
  const [both, setBoth] = useState(() => classLists(st.classes))
  const [who, setWho] = useState(st.body === 'female' ? 'female' : 'male')
  const list = both[who]
  const setList = fn => setBoth(b => ({ ...b, [who]: fn(b[who]) }))
  const save = () => {
    update(s => { s.classes = { male: cleanClasses(both.male), female: cleanClasses(both.female) } })
    close()
    toast(t('Weight classes saved'))
  }
  return <>
    <h3>{t('Weight classes')}</h3>
    <div className="muted small" style={{ marginBottom: 10 }}>{t('What your federation runs right now. A meet keeps the category it was logged with.')}</div>
    <Segmented value={who} onChange={setWho}
      options={[{ value: 'male', label: t('Male') }, { value: 'female', label: t('Female') }]} />
    <div className="list menu-list" style={{ marginTop: 10 }}>
      {list.map((c, i) => <div key={who + i} className="row" style={{ gap: 8, marginBottom: 8 }}>
        <TextField className="grow" value={c} placeholder="73" aria-label={t('Category {0}', i + 1)}
          onChange={e => setList(l => l.map((x, j) => (j === i ? e.target.value : x)))} />
        <button className="iconbtn" aria-label={t('Remove category {0}', c || i + 1)}
          onClick={() => setList(l => l.filter((_, j) => j !== i))}><Icon name="trash" /></button>
      </div>)}
      {!list.length && <div className="muted small">{t('No categories yet.')}</div>}
    </div>
    <div style={{ height: 10 }} />
    <Button icon="plus" onClick={() => setList(l => [...l, ''])}>{t('Add a category')}</Button>
    <div style={{ height: 18 }} />
    <Button variant="primary" onClick={save}>{t('Save')}</Button>
  </>
}
export const weightClassesSheet = () => ui().openSheet(close => <WeightClassesSheet close={close} />)

export const meetDetailSheet = meet => ui().openSheet(close => <MeetDetail meet={meet} close={close} />)

