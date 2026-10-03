import { useNavigate } from 'react-router-dom'
import { useStore } from '../store/useStore.js'
import { DAYN, weekOrder, weekStartOf, startOfWeek, isoOf, todayISO, fmtDate, uid, exCount } from '../lib/format.js'
import { addDays, weeksInOrder, weekFor } from '../lib/weeks.js'
import { t } from '../lib/i18n.js'
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

export default function Plan() {
  const nav = useNavigate()
  const S = useStore(s => s.S)
  const update = useStore(s => s.update)

  // The whole plan, oldest first: one block per dated week. The week you are in is tagged, and a
  // block opens its editor — the exercises themselves live there, not on this screen.
  const weeks = weeksInOrder(S)
  const thisWeek = weekFor(S, todayISO())

  const addWeek = () => {
    const id = uid()
    // `weeks` is in DEF, but a state written before the field existed (or replaced wholesale)
    // can still arrive without it — the migration of those paths lands in a later stage.
    update(s => { (s.weeks || (s.weeks = [])).push({ id, startIso: nextWeekStart(s), name: '', days: [] }) })
    nav('/plan/w/' + id)
  }

  return <>
    <TopAppBar title={t('Plan')} subtitle={t('Your weeks')}
      actions={<button className="iconbtn ab-ico" onClick={planToolsSheet} aria-label={t('Share your plan')} title={t('Share your plan')}><Icon name="upload" /></button>} />
    {weeks.length ? <div className="list">
      {weeks.map(w => {
        const days = daysInOrder(w.days, weekStartOf(S))
        return <div key={w.id} className="item day-card" data-week={w.id}>
          <div className="row between" style={{ marginBottom: 6 }} {...tappable(() => nav('/plan/w/' + w.id))}>
            <div className="grow" style={{ minWidth: 0 }}>
              <div className="tt">{w.name || t('Week of {0}', fmtDate(w.startIso, false, true))}</div>
              <div className="ss">{days.length ? dayCount(days.length) : t('No days yet')}</div>
            </div>
            {thisWeek?.id === w.id && <span className="tag">{t('This week')}</span>}
            <Icon name="chevronRight" className="chev" />
          </div>
          {/* Each day of the week, in the profile's weekday order. A day with no exercises is
              still a day — it is what the week plans for that weekday. */}
          {days.map((d, i) => <div key={`${d.dow}-${i}`} className="row day-row">
            <div className="grow" style={{ minWidth: 0 }}>
              <div className="tt">{d.name || t(DAYN[d.dow])}</div>
              <div className="ss">{exCount((d.ex || []).length)}</div>
            </div>
          </div>)}
        </div>
      })}
    </div> : <div className="empty">
      <div className="ico"><Icon name="calendar" /></div>
      {t('No weeks yet.')}
      <div style={{ height: 14 }} />
      <Button icon="sparkles" onClick={starterPlanSheet}>{t('Load starter plan')}</Button>
      <div className="dim small" style={{ marginTop: 8 }}>{t('Or start from a ready-made plan.')}</div>
    </div>}

    <div style={{ height: 10 }} />
    <Button icon="plus" onClick={addWeek}>{t('New week')}</Button>
  </>
}
