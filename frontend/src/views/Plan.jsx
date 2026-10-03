import { useNavigate } from 'react-router-dom'
import { useStore } from '../store/useStore.js'
import { DAYS, DAYN, weekOrder, weekStartOf, startOfWeek, isoOf, todayISO, fmtDate, uid, exCount, weekDayOffset } from '../lib/format.js'
import { addDays, weeksInOrder, weekFor } from '../lib/weeks.js'
import { t, dateLocale } from '../lib/i18n.js'
import { planToolsSheet, starterPlanSheet } from '../sheets.jsx'
import Icon from '../components/Icon.jsx'
import TopAppBar from '../components/TopAppBar.jsx'
import { Button } from '../components/ui.jsx'
import { tappable } from '../lib/use-sheet-keyboard.js'

/** Where the next week begins: seven days after the last one we have, or this week's first day
 *  when there are none. A week is one concrete calendar week, so a new one follows the plan
 *  instead of repeating it. */
function nextWeekStart(S) {
  const weeks = weeksInOrder(S)
  const latest = weeks[weeks.length - 1]
  return latest ? addDays(latest.startIso, 7) : isoOf(startOfWeek(todayISO(), weekStartOf(S)))
}

/** The week's days in the profile's weekday order — [1..6, 0] for a Monday start. */
const daysInOrder = (days, ws) => weekOrder(ws).flatMap(d => (days || []).filter(x => x.dow === d))
const dayCount = n => t(n === 1 ? '{0} day' : '{0} days', n)

/** "28 Sept – 4 Oct". Two weeks can both be unnamed, so the dates are what tells them apart. */
const rangeLabel = iso => {
  const a = new Date(iso + 'T12:00:00'), b = new Date(addDays(iso, 6) + 'T12:00:00')
  const m = d => d.toLocaleDateString(dateLocale(), { month: 'short' })
  return a.getDate() + ' ' + m(a) + ' – ' + b.getDate() + ' ' + m(b)
}

// The plan, as a plan rather than a log. It used to run oldest first, so the week you are in sat
// below every week you had already trained and you scrolled past your own history to reach it —
// and every week was the same card at the same weight, with no totals to compare one against the
// next. The week that covers today leads now, its days are on it, and the rest are one line each
// with what they planned and how much of it happened.
export default function Plan() {
  const nav = useNavigate()
  const S = useStore(s => s.S)
  const update = useStore(s => s.update)
  const ws = weekStartOf(S)
  const weeks = weeksInOrder(S)
  const cur = weekFor(S, todayISO())
  const done = new Set(S.workouts.map(w => w.d))
  const open = id => nav('/plan/w/' + id)
  const nameOf = w => w.name || t('Week of {0}', fmtDate(w.startIso, false, true))

  // A week's days are dates, so "done" is per date rather than per weekday: the same Wednesday in
  // two weeks is two different sessions.
  const statsOf = w => {
    const days = daysInOrder(w.days, ws)
    return {
      days,
      ex: days.reduce((n, d) => n + (d.ex || []).length, 0),
      done: days.filter(d => done.has(addDays(w.startIso, weekDayOffset(d.dow, ws)))).length,
    }
  }
  // Newest first on both sides, so the plan reads outward from today.
  const later = (cur ? weeks.filter(w => w.startIso > cur.startIso) : []).slice().reverse()
  const earlier = (cur ? weeks.filter(w => w.startIso < cur.startIso) : weeks).slice().reverse()

  const addWeek = () => {
    const id = uid()
    // `weeks` is in DEF, but a state written before the field existed (or replaced wholesale)
    // can still arrive without it — the migration of those paths lands in a later stage.
    update(s => { (s.weeks || (s.weeks = [])).push({ id, startIso: nextWeekStart(s), name: '', days: [] }) })
    open(id)
  }

  const row = w => {
    const st = statsOf(w)
    return <div key={w.id} className="item" data-week={w.id} {...tappable(() => open(w.id))}>
      <span className="lrow-i"><Icon name="calendar" /></span>
      <div className="grow">
        <div className="tt">{nameOf(w)}</div>
        <div className="ss">{rangeLabel(w.startIso)} · {st.days.length ? dayCount(st.days.length) : t('No days yet')}
          {st.days.length ? ' · ' + t('{0} of {1} done', st.done, st.days.length) : ''}</div>
      </div>
      <Icon name="chevronRight" className="chev" />
    </div>
  }

  const hero = cur && (() => {
    const st = statsOf(cur)
    return <>
      <div className="sech">{t('This week')}</div>
      <div className="hero" data-week={cur.id}>
        <div className="row between" style={{ marginBottom: 10 }}>
          <div className="hero-lbl">{rangeLabel(cur.startIso)}</div>
          <button className="hero-btn" aria-label={t('Edit this week')} onClick={() => open(cur.id)}><Icon name="chevronRight" /></button>
        </div>
        <div className="hero-t">{nameOf(cur)}</div>
        <div className="hero-s">{[st.days.length ? dayCount(st.days.length) : t('No days yet'), exCount(st.ex)].join(' · ')}</div>
        {st.days.length > 0 && <div className="hero-rail">
          {st.days.map((d, i) => <div key={d.dow + '-' + i} className="day-row" {...tappable(() => open(cur.id))}>
            {/* the short weekday: the full name does not fit the column, and the row already
                carries the day's own name */}
            <span className="ddow">{t(DAYS[d.dow])}</span>
            <div className="grow">
              <div className="tt">{d.name || t(DAYN[d.dow])}</div>
              <div className="ss">{exCount((d.ex || []).length)}</div>
            </div>
            {done.has(addDays(cur.startIso, weekDayOffset(d.dow, ws))) && <span className="dtick"><Icon name="check" /></span>}
            <Icon name="chevronRight" className="chev" />
          </div>)}
        </div>}
        <Button variant="tinted" className="hero-cta" onClick={() => open(cur.id)}>{t('Edit this week')}</Button>
      </div>
    </>
  })()

  return <>
    <TopAppBar title={t('Plan')} subtitle={t('Your weeks')}
      actions={<>
        <button className="iconbtn ab-ico" onClick={addWeek} aria-label={t('New week')} title={t('New week')}><Icon name="plus" /></button>
        <button className="iconbtn ab-ico" onClick={planToolsSheet} aria-label={t('Share your plan')} title={t('Share your plan')}><Icon name="upload" /></button>
      </>} />

    {weeks.length === 0 ? <div className="empty">
      <div className="ico"><Icon name="calendar" /></div>
      {t('No weeks yet.')}
      <div style={{ height: 14 }} />
      <Button icon="sparkles" onClick={starterPlanSheet}>{t('Load starter plan')}</Button>
      <div className="dim small" style={{ marginTop: 8 }}>{t('Or start from a ready-made plan.')}</div>
    </div> : <>
      {hero}
      {later.length > 0 && <><div className="sech">{t('Upcoming')}</div><div className="list">{later.map(row)}</div></>}
      {earlier.length > 0 && <><div className="sech">{cur ? t('Earlier') : t('Your weeks')}</div><div className="list">{earlier.map(row)}</div></>}
    </>}
  </>
}
