import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useStore } from '../store/useStore.js'
import { EXIDX } from '../lib/exercises.js'
import { setsDone, workoutVolume } from '../lib/history.js'
import { fmtVol } from '../lib/format.js'
import { t, dateLocale, exerciseNameFor } from '../lib/i18n.js'
import { WorkoutRow, workoutDetailSheet, logPastWorkoutSheet } from '../sheets.jsx'
import { Button, CardHead } from '../components/ui.jsx'
import Icon from '../components/Icon.jsx'
import TopAppBar from '../components/TopAppBar.jsx'

// History is the whole log, so it opens with the four totals a list cannot give you at a
// glance, and it is the one screen where finding a particular session matters — which is what
// the field and the month headings are for. Logging a past workout moved into the app bar: it
// is an action on the screen, not the first thing on it.
export default function History() {
  const nav = useNavigate()
  const S = useStore(s => s.S)
  const [q, setQ] = useState('')
  const list = [...S.workouts].reverse()
  const query = q.trim().toLowerCase()
  const shown = !query ? list : list.filter(w =>
    String(w.name || '').toLowerCase().includes(query) ||
    (w.entries || []).some(e => {
      const ex = EXIDX[e.id]
      return ex ? exerciseNameFor(ex).toLowerCase().includes(query) : false
    }))

  // Consecutive months rather than a map: the list is already newest first, so a heading per
  // change is cheaper than bucketing and sorting again.
  const months = []
  for (const w of shown) {
    const key = String(w.d || '').slice(0, 7)
    const last = months[months.length - 1]
    if (last && last.key === key) last.items.push(w)
    else months.push({ key, items: [w] })
  }
  const monthName = key => new Date(key + '-01T00:00:00').toLocaleDateString(dateLocale(), { month: 'long', year: 'numeric' })

  const totalSets = S.workouts.reduce((n, w) => n + setsDone(w), 0)
  // through the same helper the workout row reads, rather than the stored w.vol: a workout logged
  // before that field existed still has a volume, it was just never written down
  const totalVol = S.workouts.reduce((n, w) => n + workoutVolume(w), 0)
  const totalPrs = S.workouts.reduce((n, w) => n + ((w.prs || []).length), 0)

  return <>
    <TopAppBar title={t('History')} subtitle={t('{0} workouts', S.workouts.length)}
      leading={<button className="iconbtn ab-ico" onClick={() => nav('/stats')} aria-label={t('Stats')}><Icon name="chevronLeft" /></button>}
      actions={<button className="iconbtn ab-ico" onClick={logPastWorkoutSheet} aria-label={t('Log a past workout')} title={t('Log a past workout')}><Icon name="plus" /></button>} />

    {S.workouts.length > 0 && <>
      <div className="tiles hist-tiles">
        <div className="tile"><div className="l">{t('Workouts')}</div><div className="v">{S.workouts.length}</div></div>
        <div className="tile"><div className="l">{t('Sets')}</div><div className="v">{totalSets}</div></div>
        <div className="tile"><div className="l">{t('Volume')}</div><div className="v">{fmtVol(totalVol, S.unit)}</div></div>
        <div className="tile"><div className="l">{t('PRs')}</div><div className="v">{totalPrs}</div></div>
      </div>

      <div className="search" style={{ marginBottom: 4 }}>
        <svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="7" /><path d="m21 21-4.3-4.3" /></svg>
        <input className="input" placeholder={t('Search a workout or exercise')} value={q} onChange={e => setQ(e.target.value)} />
      </div>
    </>}

    {months.map(m => <div key={m.key}>
      <div className="mlabel">{monthName(m.key)}</div>
      <div className="list">{m.items.map(w => <WorkoutRow key={w.id} w={w} onClick={() => workoutDetailSheet(w)} />)}</div>
    </div>)}

    {S.workouts.length === 0 && <div className="empty">
      <div className="ico"><Icon name="history" /></div>{t('No workouts yet.')}
      <div style={{ marginTop: 14, display: 'flex', justifyContent: 'center' }}>
        <Button icon="plus" onClick={logPastWorkoutSheet}>{t('Log a past workout')}</Button>
      </div>
    </div>}
    {S.workouts.length > 0 && shown.length === 0 && <div className="empty">
      <div className="ico"><Icon name="magnifier" /></div>{t('No match')}
    </div>}
  </>
}
