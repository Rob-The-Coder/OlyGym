// Review the coach's week before any of it becomes a routine.
//
// The reading is a guess in places — an Italian gym phrase the catalogue has no word for, a load
// written as a sentence, a "1+1" that is one clean and jerk and not two exercises — so every row
// of his sheet is on screen with the exercise it was read as, and the rows where something stayed
// in the note are marked. Tapping a row opens the correction menu: the exercise can be swapped,
// and it is remembered in S.planAliases under the coach's own words because the next week's sheet
// repeats the same phrases; the sets, reps and load can be corrected too, but only for this import
// (local state below), because the same phrase comes back with different numbers.
//
// Every row also prints his own row under the reading (import-plan.js's `coachSource`): the only way
// to answer "was this ported right?" is to read the two next to each other. The trailing checkbox is
// the mark that a row has been looked at, and the summary line counts what is left — session state
// only, because the review is derived and re-read on every correction.
//
// The picker, the row menu and the number-correction sheet are passed in rather than imported:
// they are the sheets.jsx flows, and this file stays a screen.

import { useMemo, useState } from 'react'
import { useStore } from '../store/useStore.js'
import { useUI } from '../store/useUI.js'
import { Button, Check, Segmented } from './ui.jsx'
import Icon from './Icon.jsx'
import { t, exerciseNameFor } from '../lib/i18n.js'
import { exOr } from '../lib/exercises.js'
import { exLine, repsOnly, supersetUnits, complexShared } from '../lib/history.js'
import { DAYS, fmtNum } from '../lib/format.js'
import { normName } from '../lib/plan-aliases.js'
import { WEEK_PLAN, bundleFromWeek, reviewWeek } from '../lib/import-plan.js'
import { mergeWeek } from '../lib/plan-share.js'
import { convertWeight } from '../lib/units.js'
import { nav } from '../lib/nav.js'
import { tappable } from '../lib/use-sheet-keyboard.js'

export default function CoachImport({ sheets, close, pick, menu, editNumbers }) {
  const st = useStore(s => s.S)
  const update = useStore(s => s.update)
  const [week, setWeek] = useState(0)
  const [dayIdx, setDayIdx] = useState(0)
  // Which rows have been read over. Keyed by sheet and row rather than stored anywhere: the review
  // itself is derived, and a tick is about this sitting, not about the plan.
  const [checked, setChecked] = useState(() => new Set())
  // Numbers fixed in the review, keyed by week and then by the entry's key. Local state on
  // purpose: a set count is not remembered for the next sheet the way an exercise name is, but it
  // has to survive switching weeks and days in this sitting. Never written to S or localStorage.
  const [corrections, setCorrections] = useState({})
  // The review is derived, never stored: correcting a row rewrites S.planAliases or these local
  // numbers and the whole week is read again, so one fix covers every row that repeats those words.
  const aliases = st.planAliases || {}
  const weekCorrections = corrections[week]
  const review = useMemo(() => reviewWeek(sheets[week], { aliases, corrections: weekCorrections }), [sheets, week, aliases, weekCorrections])
  const day = review.days[Math.min(dayIdx, review.days.length - 1)]
  const unit = st.unit || 'kg'
  const shown = w => (!w || unit === 'kg' ? w : convertWeight(w, 'kg', unit))

  const keyOf = entry => `${week}:${entry.key}`
  const tickOf = entry => setChecked(prev => {
    const next = new Set(prev)
    if (next.has(keyOf(entry))) next.delete(keyOf(entry))
    else next.add(keyOf(entry))
    return next
  })

  const correct = (entry, id) => {
    update(s => {
      const key = normName(entry.part)
      s.planAliases = { ...(s.planAliases || {}) }
      if (id) s.planAliases[key] = id
      else delete s.planAliases[key]
    })
    // Picking a different exercise is this row looked at; there is nothing to undo, it is a reading aid.
    setChecked(prev => new Set(prev).add(keyOf(entry)))
  }

  // The movements a tapped row shares its sets and load with: its complex, or just itself.
  const groupOf = entry => {
    const list = day?.entries || []
    const group = supersetUnits(list).find(g => g.some(i => list[i].key === entry.key))
    return group ? group.map(i => list[i]) : [entry]
  }

  // Whether "Back to the coach's numbers" would actually remove something from this row: a
  // sets/load correction on the complex (his numbers there are one prescription), or a reps/second
  // correction on the movement itself. Asking the whole complex "has anything?" was wrong: a
  // reps-only fix on a sibling left the reset item on a row it could do nothing for.
  const hasNumbers = entry => {
    const byKey = corrections[week] || {}
    const own = byKey[entry.key] || {}
    if (own.reps != null || own.sec != null) return true
    return groupOf(entry).some(e => {
      const patch = byKey[e.key]
      return patch && (patch.sets != null || patch.weight != null)
    })
  }

  // Saving from a movement goes through the derived read like every other correction. Only the
  // fields that actually differ are recorded: an untouched one must not claim a correction on the
  // siblings of a complex (a reps-only fix would otherwise show a reset item there that does
  // nothing). A changed sets/load goes to every movement — he wrote it once for the whole row —
  // while reps/seconds belong to the tapped movement alone.
  const saveNumbers = (entry, patch) => {
    const group = groupOf(entry)
    setCorrections(prev => {
      const byKey = { ...(prev[week] || {}) }
      group.forEach(e => {
        const next = { ...(byKey[e.key] || {}) }
        if (patch.sets != null && patch.sets !== e.sets) next.sets = patch.sets
        if (patch.weight != null && patch.weight !== e.weight) next.weight = patch.weight
        if (e.key === entry.key) {
          if (patch.reps != null && patch.reps !== e.reps) next.reps = patch.reps
          if (patch.sec != null && patch.sec !== e.sec) next.sec = patch.sec
        }
        if (Object.keys(next).length) byKey[e.key] = next
        else delete byKey[e.key]
      })
      return { ...prev, [week]: byKey }
    })
    // Fixing the numbers is looking the row over, exactly like picking its exercise is.
    setChecked(prev => new Set(prev).add(keyOf(entry)))
  }

  // Back to the coach's numbers: the shared sets and load on every movement, plus the tapped
  // movement's own reps (or seconds). It does not un-tick the row — it was still read.
  const resetNumbers = entry => {
    const group = groupOf(entry)
    setCorrections(prev => {
      const byKey = { ...(prev[week] || {}) }
      if (group.length === 1) delete byKey[entry.key]
      else group.forEach(e => {
        const patch = { ...(byKey[e.key] || {}) }
        delete patch.sets
        delete patch.weight
        if (e.key === entry.key) { delete patch.reps; delete patch.sec }
        if (Object.keys(patch).length) byKey[e.key] = patch
        else delete byKey[e.key]
      })
      return { ...prev, [week]: byKey }
    })
  }

  const entries = review.days.flatMap(d => d.entries)
  const done = entries.filter(e => checked.has(keyOf(e))).length

  const chosen = entry => t('Read as “{0}”', entry.custom ? entry.name : exerciseNameFor(exOr(entry.id)))

  const rowMenu = entry => menu({
    title: entry.part,
    subtitle: chosen(entry),
    items: [
      { label: t('Edit sets, reps and weight'), icon: 'pencil', onClick: () => editNumbers(entry, patch => saveNumbers(entry, patch)) },
      { label: t('Pick a different exercise'), icon: 'shuffle', onClick: () => pick(ex => correct(entry, ex.id)) },
      aliases[normName(entry.part)] && { label: t('Back to what it read'), icon: 'reset', onClick: () => correct(entry, null) },
      // Only where there is a number to go back to: after a save, and gone once reset.
      hasNumbers(entry) && { label: t("Back to the coach's numbers"), icon: 'reset', onClick: () => resetNumbers(entry) }
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

  // One row of the list: what it was read as, then his own row, then what he asked for in words.
  // `sh` is the complex this row belongs to, or nothing when it stands alone: inside one the card
  // above carries the sets and the load every movement shares, so a movement prints those numbers
  // only when it disagrees with the rest. His tip is printed on every movement rather than once at
  // the top of the card — this screen is read movement by movement, and on a long day a line in the
  // head is a scroll away from the row it explains.
  const entryRow = (entry, sh) => {
    const cfg = { id: entry.id, sets: entry.sets, reps: entry.reps, mode: entry.mode, sec: entry.sec, side: entry.side, weight: shown(entry.weight) }
    const on = checked.has(keyOf(entry))
    const name = entry.custom ? entry.name : exerciseNameFor(exOr(entry.id))
    const ownLine = !sh || (!sh.load && ((entry.sets || 0) !== sh.sets || (entry.weight || 0) !== sh.weight))
    return <div key={entry.key} className={'item' + (sh ? ' cx-item' : '')} data-checked={on ? '' : undefined} {...tappable(() => rowMenu(entry))}>
      <div className="grow">
        <div className="tt">{name}</div>
        {ownLine && <div className="ss">{exLine(cfg, unit)}</div>}
        {/* His row, as he wrote it: the one thing that says whether the line above is right. */}
        {entry.src && <div className="coach-src">{entry.src}</div>}
        {entry.note && <div className="coach-note">{entry.note}</div>}
        <div className="mchips" style={{ marginTop: 4 }}>
          {entry.tier === 3 && <span className="mchip">{t('New exercise')}</span>}
          {entry.tier === 2 && <span className="mchip">{t('His words in the note')}</span>}
          {entry.tier === 1 && !entry.exact && <span className="mchip">{t('Check')}</span>}
          {entry.warns.includes('sets') && <span className="mchip miss">{t('no sets given')}</span>}
          {entry.warns.includes('reps') && <span className="mchip miss">{t('no reps given')}</span>}
        </div>
      </div>
      {!ownLine && <span className="cx-reps">×{repsOnly(entry)}</span>}
      {/* The row itself opens the correction; this only marks the row as read. The wrapper stops
          the tap before the row sees it — the same way every nested control in a tappable row does. */}
      <span className="coach-check" onClick={ev => ev.stopPropagation()}>
        <Check checked={on} label={name} onChange={() => tickOf(entry)} />
      </span>
    </div>
  }

  const units = supersetUnits(day?.entries || [])

  return <>
    <h3>{t('The coach’s plan')}</h3>
    <div className="muted small" style={{ marginBottom: 12 }}>
      {t('Read out of his spreadsheet, one week at a time. Nothing reaches your plan until you say so.')}
      {' '}
      {t('Tap a row to change the exercise it was read as, or fix its sets, reps and weight.')}
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

    {/* The counts were four equal cards and only "to check" asked for anything. As a line of
        supporting text they stop outranking the rows this screen exists to show. */}
    <div className="muted small" style={{ margin: '12px 0' }}>
      {t('{0} days · {1} exercises · {2} new', review.days.length, review.stats.exercises, review.stats.custom)}
      {/* What is left to look at. The rows the matcher guessed carry their own chips; this counts the
          sitting, and goes quiet (accent) the moment there is nothing left. */}
      {entries.length > 0 && <span className={done === entries.length ? 'coach-count-done' : undefined}> · {t('{0} of {1} checked', done, entries.length)}</span>}
    </div>

    {/* One cell per day the coach wrote, so the labels are numbers: six "Giorno N" cells wrap onto
        two lines at 360px, and a sheet is not always five days. The day's full name is the overline
        below — also what says which day you are reading once the strip scrolls off. */}
    {review.days.length > 1 && <Segmented value={dayIdx} onChange={setDayIdx}
      options={review.days.map((d, i) => ({ value: i, label: String(i + 1), ariaLabel: d.name.split(' · ')[0] }))} />}

    {day && <div className="sech">{day.name}</div>}
    <div className="list">
      {units.map(group => {
        const rows = group.map(i => day.entries[i])
        if (rows.length === 1) return entryRow(rows[0])
        // A complex is one card, the way the week editor and the workout draw one: the numbers he
        // wrote once for the whole row head it, its movements are numbered inside, and a movement
        // keeps only the reps that are its own. Two rows the coach wrote as one movement are never
        // two exercises on this screen.
        const sh = complexShared(group, day.entries)
        return <div className="cx-card" key={rows[0].key}>
          <div className="cx-head is-static">
            <Icon name="link" className="cx-ico" />
            <span className="cx-title">{t('Complex')}</span>
            {sh.load && <span className="cx-load">{[t(sh.sets === 1 ? '{0} set' : '{0} sets', sh.sets), sh.weight ? fmtNum(shown(sh.weight)) + ' ' + unit : null].filter(Boolean).join(' · ')}</span>}
          </div>
          {rows.map((entry, k) => <div className="cx-row" key={entry.key}>
            <span className="cx-step">{k + 1}</span>
            {entryRow(entry, sh)}
          </div>)}
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
