import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useStore } from '../store/useStore.js'
import { EXIDX, matchExercise } from '../lib/exercises.js'
import { lastBW, streakWeeks, setLabel, modeOf, effortOf, metricModeForEntry, metricRowsForEntry, bestWeightForEntry } from '../lib/history.js'
import { fmtNum, fmtDate, fmtVol, todayISO, weekStartOf } from '../lib/format.js'
import { t, exerciseNameFor } from '../lib/i18n.js'
import { bwSheet, goalSheet, calendarSheet, workoutDetailSheet, exerciseHistorySheet, WorkoutRow, bwDeltaColor } from '../sheets.jsx'
import LineChart from '../components/LineChart.jsx'
import Heatmap from '../components/Heatmap.jsx'
import Icon from '../components/Icon.jsx'
import TopAppBar from '../components/TopAppBar.jsx'
import BodyMap, { BodyMapLegend } from '../components/BodyMap.jsx'
import { loadOfWorkouts, muscleBalanceWindow, rankOf, MUSCLE_NAME } from '../lib/muscles.js'
import { fatigueOf } from '../lib/recovery.js'
import { fatigueStateOf } from '../lib/recovery-view.js'
import {
  hasEffort, displayScale, scaleName, toScale, avgRir, effortSummary, effortWeeks,
  effortHistogram, isHardSet, HARD_RIR
} from '../lib/effort.js'
import { Button, CardHead, Segmented, SelectRow, Row } from '../components/ui.jsx'
import { competitionBests, sortedMeets, totalOf } from '../lib/competition.js'
import { tappable } from '../lib/use-sheet-keyboard.js'

export const FATIGUE_LEVELS = [
  { at: 0, level: 0 },
  { at: 0.15, level: 1 },
  { at: 0.25, level: 2 },
  { at: 0.4, level: 3 },
  { at: 0.55, level: 4, exclusive: true },
]

// One set of labels for one idea. A window is a window whether it is body weight, effort or
// the balance map, so they read the same and sit in the same place in the card. The balance map
// keeps "Week" as well: the training week is the unit you plan the next one from, which is not
// the same thing as the last 30 days.
const rangeOpts = extra => [...extra,
  { value: 30, label: '1M' }, { value: 90, label: '3M' }, { value: 365, label: '1Y' }, { value: 0, label: t('All') }]

function useNow() {
  const [, setTick] = useState(0)
  useEffect(() => {
    const iv = setInterval(() => setTick(tick => tick + 1), 60000)
    return () => clearInterval(iv)
  }, [])
  return Date.now()
}

/**
 * Return the whole weeks since a completed muscle-training timestamp.
 *
 * @param {number} now Current render-time timestamp in milliseconds.
 * @param {number} lastTrained Timestamp of the latest completed training event.
 * @returns {number} Non-negative whole weeks, including zero for ages under seven days.
 */
export function weeksSinceTraining(now, lastTrained) {
  return Math.max(0, Math.floor((now - lastTrained) / 86400000 / 7))
}

function FatigueLegend() {
  return <div className="hm-legend hm-fatigue" aria-label={t('Fatigue')}>
    <span>{t('Fatigued')}</span><div className="hm-c l4" />
    <span>{t('Recovering')}</span><div className="hm-c l2" />
    <span>{t('Ready')}</span><div className="hm-c l0" />
  </div>
}

function fatigueLabel(value) {
  const state = fatigueStateOf(value)
  return t(state === 'ready' ? 'Ready' : state === 'recovering' ? 'Recovering' : 'Fatigued')
}

function MuscleBalance({ S }) {
  const [view, setView] = useState('balance')
  const [win, setWin] = useState(7)
  const [hard, setHard] = useState(false)
  const [sel, setSel] = useState(null)
  const now = useNow()
  const workouts = S.workouts
  // The user's own last registered bodyweight drives bodyweight-exercise tonnage.
  const bodyweightKg = useMemo(() => {
    const entries = S.bodyweight || []
    if (!entries.length) return null
    const last = entries.slice().sort((a, b) => String(a.d).localeCompare(String(b.d))).at(-1)
    if (!last || !(last.w > 0)) return null
    return last.w
  }, [S.bodyweight, S.unit])
  const fatigue = useMemo(() => fatigueOf(workouts, now, { bodyweightKg, unit: S.unit }), [workouts, now, bodyweightKg, S.unit])
  const toggleSel = m => setSel(s => (s === m ? null : m))
  const inWin = muscleBalanceWindow(S.workouts, win, now, todayISO(), weekStartOf(S))
  // Counting only the sets taken near failure turns the map from "where did the volume go"
  // into "where did the stimulus go" — a muscle can lead on sets and still never be trained
  // hard. Offered only when the window holds ratings at all, since with none the hard map
  // would just be empty and read as "you trained nothing".
  const rated = inWin.some(w => w.entries.some(e => e.sets.some(s => s.done && isHardSet(s))))
  const on = hard && rated
  const load = loadOfWorkouts(inWin, on ? isHardSet : null)
  const { worked, missed } = rankOf(load)
  const top = worked.slice(0, 4)
  const max = worked.length ? load[worked[0]] : 0
  const sets = m => fmtNum(Math.round((load[m] || 0) * 10) / 10)
  return <div className="card">
    <CardHead title={t('Muscle balance')} subtitle={on ? t('by hard sets') : t('by sets worked')}>
      <Segmented className="seg-two" value={view} onChange={v => { setView(v); setSel(null) }}
        options={[{ value: 'balance', label: t('Balance') }, { value: 'fatigue', label: t('Fatigue') }]} />
    </CardHead>
    {view === 'balance' ? <>
      <div className="seg-row">
        <Segmented className="seg-range" value={win} onChange={v => { setWin(v); setSel(null) }}
          options={rangeOpts([{ value: 7, label: t('Week') }])} />
        {rated && <Button size="sm" icon="flame" style={on ? { color: 'var(--yellow)' } : undefined}
          onClick={() => { setHard(h => !h); setSel(null) }}>{on ? t('Hard sets') : t('All sets')}</Button>}
      </div>
      {inWin.length ? <>
        <BodyMap className="tappable" load={load} body={S.body} selected={sel}
          onMuscle={m => setSel(s => (s === m ? null : m))} />
        <BodyMapLegend />
        {sel && <div className="mrow" style={{ borderTop: 'var(--hair) solid var(--sep)', marginTop: 4, paddingTop: 10 }}>
          <span className="nm"><b>{t(MUSCLE_NAME[sel])}</b></span>
          <span className="v">{sets(sel) ? t('{0} sets', sets(sel)) : on ? t('no hard sets') : t('not trained')}</span>
        </div>}
        {!sel && top.map(m => <div key={m} className="mrow">
          <span className="nm">{t(MUSCLE_NAME[m])}</span>
          <span className="bar"><i style={{ width: Math.round(load[m] / max * 100) + '%', background: on ? 'var(--yellow)' : undefined }} /></span>
          <span className="v">{t('{0} sets', sets(m))}</span>
        </div>)}
        {missed.length > 0 && <>
          <h4 className="sec" style={{ marginTop: 12 }}>{on ? t('No hard sets in this period') : t('Not trained in this period')}</h4>
          <div className="mchips">{missed.map(m => <span key={m} className="mchip miss">{t(MUSCLE_NAME[m])}</span>)}</div>
        </>}
        {!missed.length && worked.length > 0 &&
          <div className="muted small" style={{ marginTop: 10 }}>{on
            ? t('Every muscle group got at least one hard set in this period.')
            : t('Every muscle group got some work in this period.')}</div>}
      </> : <div className="muted small">{t('No workouts in this period yet.')}</div>}
    </> : view === 'fatigue' ? <>
      <h2 style={{ margin: 0 }}>{t('Fatigue')}</h2>
      <BodyMap className="tappable hm-fatigue" load={fatigue} thresholds={FATIGUE_LEVELS} body={S.body} selected={sel} onMuscle={toggleSel} />
      <FatigueLegend />
      <div className="muted small" style={{ marginTop: 10 }}>{t('Fatigue shows how recently each muscle was trained. High means rest.')}</div>
      {sel && <div className="mrow" style={{ borderTop: 'var(--hair) solid var(--sep)', marginTop: 4, paddingTop: 10 }}>
        <span className="nm"><b>{t(MUSCLE_NAME[sel])}</b></span>
        <span className="v">{fatigueLabel(fatigue[sel])}</span>
      </div>}
    </> : null}
  </div>
}


// How hard the training was — the half of the picture a volume chart cannot show. Everything
// is computed in RIR (lib/effort.js) and converted to whichever scale this profile reads.
// Every number carries how much of the training it speaks for: rating is optional and off by
// default, so a partly rated history is the normal case, and an average without its
// denominator would quietly speak for sets that were never rated.
function EffortCard({ S }) {
  const [win, setWin] = useState(90)
  const kind = displayScale(S)
  const hd = scaleName(kind)
  const sum = effortSummary(S, win)
  const weeks = effortWeeks(S, win)
  const hist = effortHistogram(S, win)
  const maxBin = Math.max(1, ...hist.map(b => b.n))
  // The week's set count rides along in the tooltip, because the pair is the reading:
  // volume up with effort up is fatigue piling up, volume up with effort flat is adaptation.
  const pts = weeks.map(w => ({ t: w.t, y: toScale(kind, w.rir), note: t('{0} sets', w.sets) }))
  // Bins run hardest-first in both scales: RIR 0 and RPE 10 are the same set.
  const binLabel = b => kind === 'rpe' ? (b.tail ? '≤ 6' : String(10 - b.rir)) : (b.tail ? b.rir + '+' : String(b.rir))

  return <div className="card">
    <CardHead title={t('Effort')} subtitle={t('how close to failure')} />
    <Segmented className="seg-range" value={win} onChange={setWin} options={rangeOpts([])} />
    {sum.rated === 0 ? <div className="muted small">{t('No rated sets in this period.')}</div> : <>
      <div className="row between" style={{ alignItems: 'flex-end', gap: 12 }}>
        <div>
          <div className="stat-v">{sum.avg == null ? '—' : fmtNum(toScale(kind, sum.avg)) + ' ' + hd}</div>
          <div className="small dim">{t('average effort')}</div>
        </div>
        <div style={{ textAlign: 'right' }}>
          <div className="stat-v" style={{ color: 'var(--yellow)' }}>{sum.hardPct == null ? '—' : Math.round(sum.hardPct * 100) + '%'}</div>
          <div className="small dim">{t('at {0} {1} or harder', hd, fmtNum(toScale(kind, HARD_RIR)))}</div>
        </div>
      </div>
      <div className="small dim" style={{ marginTop: 8 }}>{t('{0} of {1} finished sets rated', sum.rated, sum.done)}</div>
      {effortOf(S) === 'none' && <div className="small" style={{ color: 'var(--yellow)', marginTop: 4 }}>
        {t('Effort per set is switched off — turn it on in Settings to keep rating.')}
      </div>}
      {pts.length > 1 && <>
        <h4 className="sec" style={{ marginTop: 12 }}>{t('Week by week')}</h4>
        <div className="chart"><LineChart points={pts} h={140} unit={hd} color="var(--yellow)" invert={kind === 'rir'} /></div>
      </>}
      <h4 className="sec" style={{ marginTop: 12 }}>{t('Where the sets land')}</h4>
      {hist.map(b => <div key={b.rir} className="mrow">
        <span className="nm">{hd} {binLabel(b)}</span>
        <span className="bar"><i style={{ width: Math.round(b.n / maxBin * 100) + '%', background: b.rir <= HARD_RIR ? 'var(--yellow)' : 'var(--label-3)' }} /></span>
        <span className="v">{b.n ? b.n + ' · ' + Math.round(b.pct * 100) + '%' : '—'}</span>
      </div>)}
      <div className="small dim" style={{ marginTop: 8 }}>
        {t('Most working sets belong close to failure without living there — half at the floor and half at the top average out to a healthy-looking middle.')}
      </div>
    </>}
  </div>
}

// Stats = the analytics hub: all charts, progress and history live here.
export default function Stats() {
  const nav = useNavigate()
  const S = useStore(s => s.S)
  const [range, setRange] = useState(90)
  const [exId, setExId] = useState(null)
  const [exMetric, setExMetric] = useState('top')
  const now = Date.now()
  const kind = displayScale(S)
  const hd = scaleName(kind)

  const bwPts = S.bodyweight.filter(b => range === 0 || (b.t || new Date(b.d).getTime()) > now - range * 86400000)
    .map(b => ({ t: b.t || new Date(b.d).getTime(), y: b.w, d: b.d }))
  const bw30 = S.bodyweight.filter(b => (b.t || new Date(b.d).getTime()) > now - 30 * 86400000)
  const bwDelta30 = bw30.length > 1 ? bw30[bw30.length - 1].w - bw30[0].w : null
  const workouts = S.workouts
  const monthW = workouts.filter(w => String(w.d || '').slice(0, 7) === todayISO().slice(0, 7)).length

  const meets = S.competitions || []
  const meetsBests = competitionBests(meets)
  const totalPts = sortedMeets(meets).filter(m => totalOf(m) != null)
    .map(m => ({ t: new Date(m.d + 'T12:00:00').getTime(), y: totalOf(m), d: m.d }))
  const entryOf = id => workouts.flatMap(w => w.entries).find(e => e.id === id)
  const listOf = value => Array.isArray(value) ? value : value == null || value === '' ? [] : [value]
  const firstAvailable = (...values) => {
    for (const value of values) {
      const list = listOf(value)
      if (list.length) return list
    }
    return []
  }
  const nameOf = id => {
    if (EXIDX[id]) return exerciseNameFor(EXIDX[id])
    const entry = entryOf(id)
    return entry?.muscleSnapshot?.n || entry?.n || id
  }
  const matcherOf = id => {
    if (EXIDX[id]) return EXIDX[id]
    const entry = entryOf(id)
    const snapshot = entry?.muscleSnapshot || {}
    const primaries = firstAvailable(snapshot.primaries, entry?.primaries)
    const secondaries = firstAvailable(
      snapshot.sm, snapshot.secondaries, snapshot.muscleGroups,
      entry?.sm, entry?.secondaries, entry?.muscleGroups,
    )
    return {
      n: snapshot.n || entry?.n || id,
      bp: snapshot.bp || entry?.bp || '',
      tg: primaries[0] || snapshot.tg || entry?.tg || '',
      sm: secondaries,
      eq: snapshot.eq || entry?.eq || '',
      desc: snapshot.desc || entry?.desc || '',
    }
  }
  const currentOf = id => {
    for (let i = workouts.length - 1; i >= 0; i--) {
      const en = workouts[i].entries.find(e => e.id === id)
      if (!en) continue
      const mode = metricModeForEntry(en) || modeOf({ id })
      const rows = metricRowsForEntry(en, mode)
      const mx = mode === 'reps' ? bestWeightForEntry(en) : Math.max(0, ...rows.map(s => mode === 'time' ? (s.sec || 0) : (s.w || 0)))
      if (mx > 0) return { mx, unit: mode === 'time' ? 's' : S.unit }
      // Unloaded reps work still has a current figure — its rep count. Without this the whole
      // picker label went blank and the exercise sorted to the bottom as if it had no history.
      if (mode === 'reps') {
        const reps = Math.max(0, ...rows.map(s => Number(s.r) || 0))
        if (reps > 0) return { mx: reps, unit: t('reps') }
      }
    }
    return { mx: 0, unit: S.unit }
  }
  const exHist = [...new Set(workouts.flatMap(w => w.entries.map(e => e.id)))].filter(id => EXIDX[id] || nameOf(id) !== id)
  const exCurrent = Object.fromEntries(exHist.map(id => [id, currentOf(id)]))
  exHist.sort((a, b) => exCurrent[b].mx - exCurrent[a].mx || nameOf(a).localeCompare(nameOf(b)))
  const curEx = exId && exHist.includes(exId) ? exId : exHist[0] || null
  // A completed reps work row is authoritative for strength metrics, even when the parent
  // target also contains timed work. Entries without reps rows use their selected mode.
  const curMode = curEx ? (() => {
    for (let i = workouts.length - 1; i >= 0; i--) {
      const en = workouts[i].entries.find(e => e.id === curEx)
      if (en) {
        const mode = metricModeForEntry(en)
        if (mode) return mode
      }
    }
    return modeOf({ id: curEx })
  })() : 'reps'
  const curTimed = curMode === 'time'
  // A pull-up or a push-up carries no weight, so its "best weight" is 0 — and dropping every
  // zero point left the card reading "No data yet" for exercises with a full history behind
  // them (issue #5). When nothing in an exercise's history was ever loaded, the progress IS
  // the rep count, so plot that. Add a weighted set later and it switches back to weight on
  // its own, which is also the honest reading: that is when load became the thing improving.
  const repsOnly = curEx && curMode === 'reps' && !workouts.some(w => {
    const en = w.entries.find(e => e.id === curEx)
    return en && bestWeightForEntry(en) > 0
  })
  const bestRepsOf = en => Math.max(0, ...metricRowsForEntry(en, 'reps').map(s => Number(s.r) || 0))
  const metric = s => (curTimed ? (s.sec || 0) : (s.w || 0))
  const exUnit = curTimed ? 's' : repsOnly ? t('reps') : S.unit
  let exPts = [], exList = [], exBest = 0
  if (curEx) {
    workouts.forEach(w => {
      const en = w.entries.find(e => e.id === curEx)
      if (en) {
        const loggedMode = metricModeForEntry(en)
        if (loggedMode !== curMode) return
        const doneSets = metricRowsForEntry(en, curMode)
        const mx = curMode === 'reps'
          ? (repsOnly ? bestRepsOf(en) : bestWeightForEntry(en))
          : Math.max(0, ...doneSets.map(metric))
        if (mx > 0) {
          exPts.push({ t: w.start, y: mx, d: w.d, sets: doneSets, target: en.target })
          exBest = Math.max(exBest, mx)
        }
      }
    })
    exList = exPts.slice(-5).reverse()
  }
  // Effort on this exercise, per session. It rides on the top-set curve as well as having a
  // curve of its own, because the two only mean something together: the same weight moved
  // with more left in the tank is progress a weight-only chart draws as a flat line.
  const exRir = exPts.map(p => avgRir(p.sets))
  const showEff = exRir.filter(v => v != null).length >= 3
  const effPts = exPts.map((p, i) => (exRir[i] == null ? null : { t: p.t, y: toScale(kind, exRir[i]), d: p.d })).filter(Boolean)
  const onEff = showEff && exMetric === 'effort'
  const topPts = exPts.map((p, i) => ({
    t: p.t, y: p.y, d: p.d,
    // 0 RIR (nothing left) is a full dot, 4+ a faint one; unrated sessions keep the plain line.
    m: exRir[i] == null ? null : 1 - Math.min(4, Math.max(0, exRir[i])) / 4,
    note: exRir[i] == null ? undefined : hd + ' ' + fmtNum(toScale(kind, exRir[i]))
  }))
  const exOpts = [{ value: 'top', label: t('Top set') }]
  if (showEff) exOpts.push({ value: 'effort', label: t('Effort') })

  return <>
    <TopAppBar title={t('Stats')} subtitle={t('Progress & history')}
      actions={<button className="iconbtn ab-ico" onClick={() => nav('/history')} aria-label={t('History')}><Icon name="history" /></button>} />

    <div className="sech">{t('Overview')}</div>
    <div className="tiles">
      <div className="tile"><div className="l"><Icon name="dumbbell" />{t('Workouts')}</div><div className="v">{workouts.length}</div></div>
      <div className="tile"><div className="l"><Icon name="calendar" />{t('This month')}</div><div className="v">{monthW}</div></div>
      <div className="tile"><div className="l"><Icon name="flame" />{t('Week streak')}</div><div className="v">{streakWeeks(S)}</div></div>
      <div className="tile"><div className="l"><Icon name="scale" />{t('Weight 30d')}</div><div className="v" style={{ fontSize: 22, color: bwDelta30 === null ? 'inherit' : bwDeltaColor(bwDelta30, (lastBW(S) || {}).w || 0) }}>{bwDelta30 === null ? '—' : (bwDelta30 > 0 ? '+' : '') + fmtNum(bwDelta30) + ' ' + S.unit}</div></div>

    </div>

    <div className="card">
      <CardHead title={t('Activity — last 12 months')} subtitle={t('by time trained')} />
      <Heatmap S={S} onDay={iso => { const ws = workouts.filter(w => w.d === iso); if (ws.length === 1) workoutDetailSheet(ws[0]); else if (ws.length) calendarSheet(iso) }} />
    </div>

    {workouts.length > 0 && <>
      <div className="sech">{t('Balance')}</div>
      <MuscleBalance S={S} />
    </>}
    {hasEffort(S) && <>
      <div className="sech">{t('Effort')}</div>
      <EffortCard S={S} />
    </>}

    <div className="sech">{t('Competition')}</div>
    <div className="card">
      <CardHead title={t('Competitions')} subtitle={meets.length ? t(meets.length === 1 ? '{0} competition' : '{0} competitions', meets.length) : null} />
      {meets.length === 0
        ? <div className="muted small">{t('No competitions yet.')}</div>
        : <>
          <div className="row between" style={{ alignItems: 'flex-end', gap: 12 }}>
            <div>
              <div className="stat-v">{meetsBests.total == null ? '—' : fmtNum(meetsBests.total) + ' ' + S.unit}</div>
              <div className="small dim">{t('Best total')}</div>
            </div>
          </div>
          {totalPts.length > 1 && <div className="chart" style={{ marginTop: 8 }}>
            <LineChart points={totalPts} h={150} unit={S.unit} color="var(--yellow)" />
          </div>}
          <div className="list" style={{ marginTop: 10 }}>
            <Row icon="medal" title={t('All competitions')} value={String(meets.length)} accessory="chevron" onClick={() => nav('/competitions')} />
          </div>
        </>}
    </div>

    <div className="sech">{t('Progress')}</div>
    <div className="cols">
      <div className="card">
        <CardHead title={t('Body weight')}>
          <div className="row" style={{ gap: 8 }}>
            <Button size="sm" icon="target" style={S.targetW ? { color: 'var(--yellow)' } : undefined} onClick={goalSheet}>{S.targetW ? fmtNum(S.targetW) : t('Goal')}</Button>
            <Button size="sm" icon="plus" onClick={() => bwSheet()}>{t('Log')}</Button>
          </div>
        </CardHead>
        <Segmented className="seg-range" value={range} onChange={setRange} options={rangeOpts([])} />
        <div className="chart"><LineChart points={bwPts} h={160} unit={S.unit} goal={S.targetW} /></div>
      </div>

      <div className="card">
        <CardHead title={t('Exercise progress')} />
        {exHist.length ? <>
          <div className="sect-b" style={{ marginBottom: 10 }}>
            <SelectRow title={t('Exercise')} sheetTitle={t('Exercise progress')} value={curEx} onChange={setExId} stackedValue
              options={exHist.map(id => ({ value: id, label: nameOf(id) + (exCurrent[id].mx ? ' ' + '—' + ' ' + fmtNum(exCurrent[id].mx) + ' ' + exCurrent[id].unit : '') }))}
              search={{
                placeholder: t('Search…'),
                label: t('Search…'),
                emptyLabel: t('No match'),
                match: (option, query) => matchExercise(matcherOf(option.value), query),
              }} />
          </div>
          {exOpts.length > 1 && <Segmented className="seg-range" value={onEff ? 'effort' : 'top'} onChange={setExMetric} options={exOpts} />}
          <div className="chart">
            {onEff
              ? <LineChart points={effPts} h={150} unit={hd} color="var(--yellow)" invert={kind === 'rir'} />
              : <LineChart points={topPts} h={150} unit={exUnit} color="var(--blue)" />}
          </div>
          <div style={{ marginTop: 8 }}>{exList.map((p, i) => <div key={i} className="row between small" style={{ padding: '6px 0', borderBottom: 'var(--hair) solid var(--sep)' }}>
            <span className="muted">{fmtDate(p.d, true)}</span><span>{p.sets.map(s => setLabel(curEx, s, p.target)).join('  ')}</span></div>)}</div>
          <div className="small dim" style={{ marginTop: 8 }}>
            {onEff ? t('Average effort per workout') : curTimed ? t('Longest hold per workout') : repsOnly ? t('Most reps in a set per workout') : t('Best set weight per workout')}
            {onEff ? '' : <> · {t('Best:')}{' '}<b className="accent">{fmtNum(exBest)} {exUnit}</b></>}
          </div>
          {!onEff && showEff && <div className="small dim" style={{ marginTop: 4 }}>
            {t('A fuller dot means less left in the tank — the same weight at a lower {0} is progress the line alone does not show.', hd)}
          </div>}
        </> : <div className="muted small">{t('Finish your first workout to see progress curves here.')}</div>}
      </div>
    </div>

    {workouts.length > 0 && <>
      <div className="row between" style={{ marginBottom: 10 }}>
        <div className="sech" style={{ margin: 0 }}>{t('Recent workouts')}</div>
        <Button size="sm" variant="ghost" trailingIcon="chevronRight" onClick={() => nav('/history')}>{t('All')} {workouts.length}</Button>
      </div>
      <div className="list">{[...workouts].reverse().slice(0, 6).map(w => <WorkoutRow key={w.id} w={w} onClick={() => workoutDetailSheet(w)} />)}</div>
    </>}
  </>
}
