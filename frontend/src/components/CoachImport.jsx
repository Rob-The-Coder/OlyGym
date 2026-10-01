// Review the coach's week before any of it becomes a routine.
//
// The reading is a guess in places — an Italian gym phrase the catalogue has no word for, a load
// written as a sentence, a "1+1" that is one clean and jerk and not two exercises — so every row
// of his sheet is on screen with the exercise it was read as, and the rows where something stayed
// in the note are marked. Tapping a row picks a different exercise, and the choice is remembered
// in S.planAliases under the coach's own words: the next week's sheet repeats the same phrases, so
// it arrives already corrected.
//
// The picker and the row menu are passed in rather than imported: they are the sheets.jsx flows,
// and this file stays a screen.

import { useMemo, useState } from 'react'
import { useStore } from '../store/useStore.js'
import { useUI } from '../store/useUI.js'
import { Button, Segmented } from './ui.jsx'
import Icon from './Icon.jsx'
import { t, exerciseNameFor } from '../lib/i18n.js'
import { exOr } from '../lib/exercises.js'
import { exLine } from '../lib/history.js'
import { DAYS } from '../lib/format.js'
import { normName } from '../lib/plan-aliases.js'
import { WEEK_PLAN, bundleFromWeek, reviewWeek } from '../lib/import-plan.js'
import { mergeWeek } from '../lib/plan-share.js'
import { convertWeight } from '../lib/units.js'
import { nav } from '../lib/nav.js'
import { tappable } from '../lib/use-sheet-keyboard.js'

export default function CoachImport({ sheets, close, pick, menu }) {
  const st = useStore(s => s.S)
  const update = useStore(s => s.update)
  const [week, setWeek] = useState(0)
  const [dayIdx, setDayIdx] = useState(0)
  // The review is derived, never stored: correcting a row rewrites S.planAliases and the whole
  // week is read again, so one fix covers every row that repeats those words.
  const aliases = st.planAliases || {}
  const review = useMemo(() => reviewWeek(sheets[week], { aliases }), [sheets, week, aliases])
  const day = review.days[Math.min(dayIdx, review.days.length - 1)]
  const unit = st.unit || 'kg'
  const shown = w => (!w || unit === 'kg' ? w : convertWeight(w, 'kg', unit))

  const correct = (entry, id) => update(s => {
    const key = normName(entry.part)
    s.planAliases = { ...(s.planAliases || {}) }
    if (id) s.planAliases[key] = id
    else delete s.planAliases[key]
  })

  const chosen = entry => t('Read as “{0}”', entry.custom ? entry.name : exerciseNameFor(exOr(entry.id)))

  const rowMenu = entry => menu({
    title: entry.part,
    subtitle: chosen(entry),
    items: [
      { label: t('Pick a different exercise'), icon: 'shuffle', onClick: () => pick(ex => correct(entry, ex.id)) },
      aliases[normName(entry.part)] && { label: t('Back to what it read'), icon: 'reset', onClick: () => correct(entry, null) }
    ]
  })

  const apply = () => {
    // The sheet is in kilos whatever unit the account is in: bundleFromWeek converts on the way in.
    const wk = bundleFromWeek(review, { unit })
    update(s => { mergeWeek(s, wk) })
    close()
    useUI.getState().toast(t('Added the week to your plan'))
    nav('/plan')
  }

  return <>
    <h3>{t('The coach’s plan')}</h3>
    <div className="muted small" style={{ marginBottom: 12 }}>
      {t('Read out of his spreadsheet, one week at a time. Nothing reaches your plan until you say so.')}
    </div>

    {sheets.length > 1 && <div className="item" {...tappable(() => menu({
      title: t('Which week'),
      items: sheets.map((s, i) => ({ label: s.name, on: i === week, onClick: () => { setWeek(i); setDayIdx(0) } }))
    }))}>
      <span className="lrow-i"><Icon name="calendar" /></span>
      <div className="grow">
        <div className="tt">{sheets[week].name}</div>
        <div className="ss">{t('{0} weeks in this file', sheets.length)}</div>
      </div>
      <Icon name="chevronRight" className="dim" />
    </div>}

    <div className="tiles" style={{ textAlign: 'left', margin: '12px 0' }}>
      <div className="tile"><div className="l">{t('Days')}</div><div className="v" style={{ fontSize: '1.1rem' }}>{review.days.length}</div></div>
      <div className="tile"><div className="l">{t('Exercises')}</div><div className="v" style={{ fontSize: '1.1rem' }}>{review.stats.exercises}</div></div>
      <div className="tile"><div className="l">{t('New')}</div><div className="v" style={{ fontSize: '1.1rem' }}>{review.stats.custom}</div></div>
      <div className="tile"><div className="l">{t('To check')}</div><div className="v" style={{ fontSize: '1.1rem' }}>{review.stats.fuzzy}</div></div>
    </div>

    {review.days.length > 1 && <div style={{ marginBottom: 10 }}>
      <Segmented value={dayIdx} onChange={setDayIdx} options={review.days.map((d, i) => ({ value: i, label: d.name.split(' · ')[0] }))} />
    </div>}

    <div className="list">
      {day?.entries.map(entry => {
        const cfg = { id: entry.id, sets: entry.sets, reps: entry.reps, mode: entry.mode, sec: entry.sec, side: entry.side, weight: shown(entry.weight) }
        return <div key={entry.key} className="item" {...tappable(() => rowMenu(entry))}>
          <div className="grow">
            <div className="tt">{entry.custom ? entry.name : exerciseNameFor(exOr(entry.id))}</div>
            <div className="ss">{exLine(cfg, unit)}{entry.sg ? ' · ' + t('Complex') : ''}</div>
            {entry.note && <div className="ss dim" style={{ marginTop: 2 }}>{entry.note}</div>}
            <div className="mchips" style={{ marginTop: 4 }}>
              {entry.tier === 3 && <span className="mchip">{t('New exercise')}</span>}
              {entry.tier === 2 && <span className="mchip">{t('His words in the note')}</span>}
              {entry.tier === 1 && !entry.exact && <span className="mchip">{t('Check')}</span>}
              {entry.warns.includes('sets') && <span className="mchip">{t('no sets given')}</span>}
              {entry.warns.includes('reps') && <span className="mchip">{t('no reps given')}</span>}
            </div>
          </div>
          <Icon name="shuffle" className="dim" />
        </div>
      })}
      {!day?.entries.length && <div className="empty">{t('Nothing in this day.')}</div>}
    </div>

    <div className="row between" style={{ padding: '12px 2px', gap: 12, borderTop: '1px solid var(--sep)', marginTop: 8 }}>
      <div>
        <div className="tt" style={{ fontSize: 15 }}>{t('Train it {0}', WEEK_PLAN.slice(0, Math.max(1, review.days.length)).map(d => DAYS[d]).join(' / '))}</div>
        <div className="small dim">{t('The week lands on those days of this week’s plan.')}</div>
      </div>
    </div>

    <Button variant="primary" onClick={apply} disabled={!review.days.length}>
      {t('Add the week to my plan')}
    </Button>
    <div style={{ height: 8 }} />
    <Button variant="ghost" className="dim" onClick={close}>{t('Cancel')}</Button>
  </>
}
