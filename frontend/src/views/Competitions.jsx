import { useStore } from '../store/useStore.js'
import { upcomingMeets, pastMeets, competitionBests, totalOf, daysUntil } from '../lib/competition.js'
import { fmtDate, fmtNum, todayISO } from '../lib/format.js'
import { t } from '../lib/i18n.js'
import { meetSheet, meetDetailSheet } from '../sheets.jsx'
import { Button } from '../components/ui.jsx'
import Icon from '../components/Icon.jsx'
import TopAppBar from '../components/TopAppBar.jsx'
import PlanTabs from '../components/PlanTabs.jsx'
import { tappable } from '../lib/use-sheet-keyboard.js'

// Competitions = the meets, kept apart from the training log. A meet is an event with a total,
// not a session with sets, so it gets its own screen rather than a corner of History. It is a
// second-level destination reached from Stats (and from Home while a meet is ahead), which is
// what keeps the bottom bar at five destinations.
function MeetRow({ m, unit, today }) {
  const total = totalOf(m)
  const up = String(m.d || '') >= today
  const days = daysUntil(m, today)
  const value = up
    ? (days === 0 ? t('Today') : days === 1 ? t('Tomorrow') : t('in {0} days', days))
    : total != null ? fmtNum(total) + ' ' + unit : t('No result')
  return <div className="item" {...tappable(() => meetDetailSheet(m))}>
    <span className="lrow-i" style={{ width: 34, height: 34, borderRadius: 8, fontSize: 19 }}>
      <Icon name={total != null ? 'medal' : 'trophy'} />
    </span>
    <div className="grow">
      <div className="tt">{m.name || t('Competition')}</div>
      <div className="ss">{[fmtDate(m.d, true, true), m.place].filter(Boolean).join(' · ')}</div>
    </div>
    <span className="lrow-v">{value}</span>
    <Icon name="chevronRight" className="chev" />
  </div>
}

export default function Competitions() {
  const S = useStore(s => s.S)
  const unit = S.unit
  const today = todayISO()
  const list = S.competitions || []
  const upcoming = upcomingMeets(list, today)
  const past = pastMeets(list, today)
  const bests = competitionBests(list)
  const subtitle = upcoming.length
    ? t('Next: {0}', upcoming[0].name || fmtDate(upcoming[0].d, true, true))
    : t(list.length === 1 ? '{0} competition' : '{0} competitions', list.length)

  return <>
    <TopAppBar title={t('Competitions')}
      subtitle={list.length ? subtitle : null}
      actions={<button className="iconbtn ab-ico" onClick={() => meetSheet()} aria-label={t('Add a competition')} title={t('Add a competition')}><Icon name="plus" /></button>} />

    <PlanTabs current="competitions" />

    {list.length === 0 && <div className="empty">
      <div className="ico"><Icon name="trophy" /></div>
      {t('No competitions yet.')}
      <div className="small dim" style={{ marginTop: 6, maxWidth: 320, marginLeft: 'auto', marginRight: 'auto' }}>
        {t('Add the meet you are training for, then log the attempts when it is done.')}
      </div>
      <div style={{ marginTop: 14, display: 'flex', justifyContent: 'center' }}>
        <Button variant="primary" icon="plus" onClick={() => meetSheet()}>{t('Add a competition')}</Button>
      </div>
    </div>}

    {list.length > 0 && <>
      <div className="tiles meet-tiles" style={{ marginBottom: 4 }}>
        <div className="tile"><div className="l"><Icon name="dumbbell" />{t('Snatch')}</div><div className="v">{bests.snatch == null ? '—' : fmtNum(bests.snatch)}</div><div className="s">{bests.snatch == null ? t('not done') : unit}</div></div>
        <div className="tile"><div className="l"><Icon name="dumbbell" />{t('Clean & jerk')}</div><div className="v">{bests.cj == null ? '—' : fmtNum(bests.cj)}</div><div className="s">{bests.cj == null ? t('not done') : unit}</div></div>
        <div className="tile"><div className="l"><Icon name="trophy" />{t('Best total')}</div><div className="v">{bests.total == null ? '—' : fmtNum(bests.total)}</div><div className="s">{bests.total == null ? t('not done') : unit}</div></div>
      </div>
    </>}

    {upcoming.length > 0 && <>
      <div className="sech">{t('Upcoming')}</div>
      <div className="list">{upcoming.map(m => <MeetRow key={m.id} m={m} unit={unit} today={today} />)}</div>
    </>}

    {past.length > 0 && <>
      <div className="sech">{t('Past')}</div>
      <div className="list">{past.map(m => <MeetRow key={m.id} m={m} unit={unit} today={today} />)}</div>
    </>}

    {list.length > 0 && <div style={{ height: 18 }} />}
  </>
}
