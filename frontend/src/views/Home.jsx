import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useStore } from '../store/useStore.js'
import { effectiveDay, nextTrainingDay, streakWeeks, lastBW, setsDoneActive } from '../lib/history.js'
import { weekFor } from '../lib/weeks.js'
import { fmtNum, fmtDate, exCount, todayISO, isoOf, weekKey, weekStartOf, weekDayOffset, DAYS, DAYN } from '../lib/format.js'
import { t, dateLocale } from '../lib/i18n.js'
import { bwSheet, goalSheet, calendarSheet, startFlow, starterPlanSheet, bwDeltaColor } from '../sheets.jsx'
import LineChart from '../components/LineChart.jsx'
import Icon from '../components/Icon.jsx'
import TopAppBar from '../components/TopAppBar.jsx'
import { Button, Row } from '../components/ui.jsx'
import { nextMeet, daysUntil } from '../lib/competition.js'
import { tappable } from '../lib/use-sheet-keyboard.js'

// Home = what to do now + a quick glance. Deep charts & history live in Stats.
export default function Home() {
  const nav = useNavigate()
  const S = useStore(s => s.S)
  const [weekOffset, setWeekOffset] = useState(0)

  const today = new Date()
  // A day is the whole session: it already holds every exercise, so there is nothing to combine
  // and one name to show. A day planned with nothing on it is not a session to start — the same
  // guard the tab bar and the start screen apply — so it reads as nothing to train here.
  const todayDay = effectiveDay(S, todayISO())
  const todayName = todayDay?.name || ''
  const todaySession = (todayDay?.ex || []).length ? todayDay : null
  // On a rest day, saying when you train next beats leaving the row as a full stop.
  const next = !S.active && !todaySession ? nextTrainingDay(S, todayISO()) : null
  const bw = lastBW(S)
  const prevBW = S.bodyweight.length > 1 ? S.bodyweight[S.bodyweight.length - 2] : null
  const delta = bw && prevBW ? bw.w - prevBW.w : null

  const ws = weekStartOf(S)
  // The first day of the shown week. Named for the role, not for Monday — which day that is
  // is the setting.
  const wkStart = new Date(today)
  wkStart.setDate(today.getDate() - weekDayOffset(today.getDay(), ws) + weekOffset * 7)
  const doneDays = new Set(S.workouts.map(w => w.d))
  // The last session logged for today, if any — what the row below reports instead of asking
  // you to start the one you already did. Last wins, so a second session names itself.
  const doneToday = S.workouts.filter(w => w.d === todayISO()).at(-1) || null
  const strip = []
  for (let i = 0; i < 7; i++) {
    const d = new Date(wkStart); d.setDate(wkStart.getDate() + i)
    const iso = isoOf(d)
    const planned = effectiveDay(S, iso) != null, done = doneDays.has(iso)
    const dot = done ? ' done' : planned ? ' plan' : ''
    // The strip is a glance, not an editor: a day that is planned opens nothing, and the plan
    // itself (the dated weeks) is where a day gets changed.
    strip.push(<div key={i} className={'wday' + (iso === todayISO() ? ' today' : '')} {...tappable(() => nav('/plan'))}>
      <div className="lbl">{t(DAYS[d.getDay()])}</div><div className="num">{d.getDate()}</div><div className={'dot' + dot} /></div>)
  }
  const wkEnd = new Date(wkStart); wkEnd.setDate(wkStart.getDate() + 6)
  const wkLabel = weekOffset === 0 ? t('This week') : `${wkStart.getDate()} ${wkStart.toLocaleDateString(dateLocale(), { month: 'short' })} – ${wkEnd.getDate()} ${wkEnd.toLocaleDateString(dateLocale(), { month: 'short' })}`

  const wThisWeek = S.workouts.filter(w => weekKey(w.d, ws) === weekKey(todayISO(), ws)).length
  // Days scheduled in the week you are in, not routines — one day is one session, matching
  // wThisWeek (one w). A date with no week covering it simply has nothing planned.
  const plannedPerWeek = (weekFor(S, todayISO())?.days || []).length
  const bwPoints = S.bodyweight.slice(-30).map(b => ({ t: b.t || new Date(b.d).getTime(), y: b.w, d: b.d }))
  // A meet ahead is the one dated thing Home can see coming. Only shown when there is one: an
  // empty competition row would be another card of nothing on the screen about today.
  const meet = nextMeet(S.competitions, todayISO())
  const meetDays = meet ? daysUntil(meet, todayISO()) : null

  // today's session shown right under the week strip. Nothing to train (a rest day, or a day
  // planned empty) has no session to start, so the row is the door to the plan instead.
  const onToday = () => { if (S.active) nav('/workout'); else if (todaySession) startFlow(todaySession); else nav('/plan') }

  // What the hero says under the title: the one useful thing about the state we are in, or
  // nothing at all — a finished workout does not need a caption repeating that it is finished.
  const heroSub = todaySession ? exCount(todaySession.ex.length)
    : next ? t('Next session: {0}, {1}', t(DAYN[next.weekday]), next.day.name)
    : (S.active || doneToday) ? '' : wkLabel

  return <div className="narrow">
    <TopAppBar title="OlyGym" subtitle={today.toLocaleDateString(dateLocale(), { weekday: 'long', day: 'numeric', month: 'long' })}
      actions={<button className="iconbtn ab-ico" onClick={() => nav('/settings')} aria-label={t('Settings')}><Icon name="gear" /></button>} />

    {/* This screen answers one question — what am I doing today — so that answer is the surface the
        eye lands on: one card, one action, and the week it belongs to inside it. It used to share a
        card with a week navigator, a date strip and a link, and the three numbers it also shows
        were three more cards of three other shapes below. */}
    <div className="hero">
      <div className="hero-hd">
        <div className="hero-lbl">{t('Today')} · {today.toLocaleDateString(dateLocale(), { weekday: 'long', day: 'numeric', month: 'long' })}</div>
        <div className="hero-nav">
          <button className="hero-btn" onClick={() => setWeekOffset(w => w - 1)} aria-label="Previous week"><Icon name="chevronLeft" /></button>
          <button className="hero-btn" onClick={() => setWeekOffset(w => w + 1)} aria-label="Next week"><Icon name="chevronRight" /></button>
        </div>
      </div>
      <div className="row" style={{ gap: 11, alignItems: 'center' }}>
        <span className="hero-i"><Icon name={S.active ? 'timer' : doneToday ? 'checkCircle' : todaySession ? 'dumbbell' : 'moon'} /></span>
        <div style={{ minWidth: 0 }}>
          <div className="hero-t">{S.active ? t('{0} — in progress', S.active.name)
            : doneToday ? (doneToday.name ? t('{0} — done', doneToday.name) : t('Workout done'))
            : todayDay ? todayName : t('Rest day')}</div>
          {heroSub && <div className="hero-s">{heroSub}</div>}
        </div>
      </div>
      <div className="hero-acts">
        <Button variant="primary" onClick={onToday}>{S.active ? t('Resume') : todaySession ? t('Start workout') : t('Open the plan')}</Button>
        {/* The hero starts today's plan in one tap, and so does the Start button in the tab bar —
            which is the whole problem when you want something else. This is the door to the start
            screen, and it starts nothing on its own. */}
        {!S.active && <button className="hero-btn" onClick={() => nav('/workout')}
          aria-label={t('Choose a different workout')} title={t('Choose a different workout')}><Icon name="reset" /></button>}
      </div>
      {/* The week the session belongs to, inside the card that is about it. A day that is planned
          opens the plan; the strip is a glance, not an editor. */}
      <div className="hero-rail"><div className="week">{strip}</div></div>
    </div>

    {/* The three numbers were three cards. They are one row now: short values, one glance. */}
    <div className="tiles home-tiles">
      <div className="tile tappable" {...tappable(() => calendarSheet())}>
        <div className="l"><Icon name="flame" />{t('Streak')}</div><div className="v">{streakWeeks(S)}</div><div className="s">{t('weeks')}</div>
      </div>
      <div className="tile tappable" {...tappable(() => calendarSheet())}>
        <div className="l"><Icon name="calendar" />{t('This week')}</div>
        <div className="v">{wThisWeek}{plannedPerWeek ? '/' + plannedPerWeek : ''}</div><div className="s">{t('sessions')}</div>
      </div>
      <div className="tile tappable" {...tappable(() => bwSheet())}>
        <div className="l"><Icon name="scale" />{t('Weight')}</div>
        <div className="v" style={bw && delta ? { color: bwDeltaColor(delta, bw.w) } : undefined}>{bw ? fmtNum(bw.w) : '—'}</div>
        <div className="s">{bw ? S.unit : t('not logged')}</div>
      </div>
    </div>

    {/* A meet ahead is the only dated event the week strip cannot show yet, so it gets a row of
        its own under the numbers — a glance, and the door to the competitions screen. */}
    {meet && <div className="list" style={{ marginBottom: 14 }}>
      <Row icon="trophy" title={meet.name || t('Competition')}
        subtitle={[fmtDate(meet.d, true, true), meet.place].filter(Boolean).join(' · ')}
        value={meetDays === 0 ? t('Today') : meetDays === 1 ? t('Tomorrow') : t('in {0} days', meetDays)}
        accessory="chevron" onClick={() => nav('/competitions')} />
    </div>}

    <div className="card">
      <div className="row between bw-head" style={{ marginBottom: 6 }}>
        <h2 style={{ margin: 0 }}>{t('Body weight')}</h2>
        <div className="row" style={{ gap: 8 }}>
          <Button size="sm" icon="target" style={S.targetW ? { color: 'var(--yellow)' } : undefined} onClick={goalSheet}>{S.targetW ? fmtNum(S.targetW) : t('Goal')}</Button>
          <Button size="sm" icon="plus" onClick={() => bwSheet()}>{t('Log')}</Button>
        </div>
      </div>
      {bw ? <>
        <div className="row" style={{ gap: 8, alignItems: 'baseline' }}>
          <div className="big">{fmtNum(bw.w)} <span className="muted" style={{ fontSize: '1rem' }}>{S.unit}</span></div>
          {/* only when it actually moved — an unchanged weight used to read as "− 0" */}
          {!!delta && (
            <span className="small row" style={{ gap: 2, fontWeight: 500, color: bwDeltaColor(delta, bw.w) }}>
              <Icon name={delta > 0 ? 'arrowUp' : 'arrowDown'} style={{ fontSize: 12 }} />
              {fmtNum(Math.abs(delta))}
            </span>
          )}
          <span className="dim small" style={{ marginLeft: 'auto' }}>{fmtDate(bw.d, true)}</span>
        </div>
        {S.targetW && (
          <div className="small row" style={{ color: 'var(--yellow)', marginTop: 4, gap: 5 }}>
            <Icon name="target" style={{ fontSize: 13 }} />
            <span>{t('Goal')} {fmtNum(S.targetW)} {S.unit} · {Math.abs(S.targetW - bw.w) < 0.05 ? t('reached!') : t(S.targetW > bw.w ? '{0} to gain' : '{0} to lose', fmtNum(Math.abs(S.targetW - bw.w)) + ' ' + S.unit)}</span>
          </div>
        )}
        <div className="chart" style={{ marginTop: 8 }}><LineChart points={bwPoints} h={130} unit={S.unit} goal={S.targetW} /></div>
      </> : <div className="muted small">{S.weighIn === false
        ? t('No entries yet — log your weight to start the curve.')
        : t("No entries yet — log your weight to start the curve. It's also asked before every workout.")}</div>}
    </div>

    {/* No week planned and nothing running: the offer to build a plan. One action carries it; the
        second is a text button, because two full-width buttons is not a choice, it is a coin toss. */}
    {!(S.weeks || []).length && !S.active && (
      <div className="card">
        <div className="row" style={{ gap: 10, marginBottom: 4 }}>
          <span className="lrow-i"><Icon name="sparkles" /></span>
          <div>
            <div className="tt" style={{ fontWeight: 600 }}>{t('Welcome!')}</div>
            <div className="ss">{t('Set up your weekly routine to get going — or load a ready-made starter plan.')}</div>
          </div>
        </div>
        <Button variant="primary" icon="sparkles" onClick={starterPlanSheet}>{t('Load starter plan')}</Button>
        <div style={{ display: 'flex', justifyContent: 'center', marginTop: 6 }}>
          <Button variant="ghost" className="dim" onClick={() => nav('/plan')}>{t('Build my own plan')}</Button>
        </div>
      </div>
    )}
  </div>
}
