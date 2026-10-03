import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useStore } from '../store/useStore.js'
import { useUI } from '../store/useUI.js'
import { DAYS, DAYN, fmtDate, fmtNum, uid, exCount, weekOrder, weekStartOf } from '../lib/format.js'
import { t, exerciseNameFor } from '../lib/i18n.js'
import { exOr } from '../lib/exercises.js'
import { supersetUnits, moveSupersetUnit, cleanupSg, exLine, defaultConfig } from '../lib/history.js'
import { exercisePicker, exConfigSheet, complexConfigSheet, confirmSheet, menuSheet } from '../sheets.jsx'
import Icon from '../components/Icon.jsx'
import TopAppBar from '../components/TopAppBar.jsx'
import { Button, Segmented, TextField } from '../components/ui.jsx'
import { Thumb } from '../components/Media.jsx'
import SwipeToDelete from '../components/SwipeToDelete.jsx'
import { tappable } from '../lib/use-sheet-keyboard.js'

/* One dated week, edited in place: its name, and one row per weekday it plans. A day's exercises
   are edited inside the day itself — a day is a routine that belongs to a concrete week instead
   of repeating. */

/** The week's days in the profile's weekday order, paired with their index in `week.days` so an
 *  edit still finds the right day after the order changes. */
const dayEntries = (week, ws) => weekOrder(ws)
  .flatMap(d => (week.days || []).flatMap((day, index) => (day.dow === d ? [{ day, index }] : [])))

const dayCount = n => t(n === 1 ? '{0} day' : '{0} days', n)

/** The first weekday this week leaves free — where "Add day" lands. */
const firstFreeDow = (week, ws) => {
  const taken = new Set((week.days || []).map(d => d.dow))
  return weekOrder(ws).find(d => !taken.has(d)) ?? 0
}

function DayExercises({ weekId, day, index, unit, editEx, toast }) {
  const ex = day.ex || []
  const units = supersetUnits(ex)
  const unitIndex = new Map(units.flatMap((group, n) => group.map(i => [i, n])))

  const move = (i, dir) => {
    // Guard on the live list first: a stale or boundary activation must not persist anything.
    if (!moveSupersetUnit(ex, i, dir)) return
    editEx(index, list => {
      const reordered = moveSupersetUnit(list, i, dir)
      if (!reordered) return
      list.splice(0, list.length, ...reordered)
      cleanupSg(list)
    })
  }

  const remove = i => editEx(index, list => { list.splice(i, 1); cleanupSg(list) })

  const toggleLink = i => editEx(index, list => {
    if (i < 1) return
    const cur = list[i], prev = list[i - 1]
    if (cur.sg && prev.sg && cur.sg === prev.sg) delete cur.sg
    else { const gid = prev.sg || ('sg' + uid()); prev.sg = gid; cur.sg = gid }
    cleanupSg(list)
  })

  const add = () => exercisePicker((picked, quick) => {
    const name = day.name || t(DAYN[day.dow])
    if (quick) {
      editEx(index, list => list.push({ id: picked.id, ...defaultConfig(picked.id) }))
      toast(t('“{0}” added to {1}', exerciseNameFor(picked), name))
    } else {
      exConfigSheet(picked, null, cfg => editEx(index, list => list.push({ id: picked.id, ...cfg })), null, day)
    }
  })

  const row = (entry, i, inside) => {
    // An unresolvable id is shown rather than skipped, the way the routine editor shows it.
    const exOrId = exOr(entry.id)
    const linkedPrev = i > 0 && entry.sg && ex[i - 1].sg === entry.sg
    return <SwipeToDelete className={'item' + (inside ? ' cx-item' : '')}
      deleteLabel={t('Remove')}
      onDelete={() => remove(i)}
      onClick={() => exConfigSheet(exOrId, entry, cfg => editEx(index, list => { list[i] = { id: list[i].id, sg: list[i].sg, ...cfg } }), () => remove(i), day)}>
      <Thumb ex={exOrId} />
      <div className="grow"><div className="tt capitalize">{exerciseNameFor(exOrId)}</div>
        <div className="ss">{exLine(entry, unit)}</div>
        {entry.note && <div className="small dim" style={{ marginTop: 2 }}>{entry.note}</div>}</div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 2, flex: 'none', alignItems: 'center' }}>
        {i > 0 && <button className={'iconbtn' + (linkedPrev ? ' on-ss' : '')} title={t('Complex with exercise above')}
          style={{ width: 32, height: 28, borderRadius: 8, fontSize: 15 }}
          onClick={ev => { ev.stopPropagation(); toggleLink(i) }}><Icon name="link" /></button>}
        <div style={{ display: 'flex', gap: 2 }}>
          <button className="iconbtn" aria-label={t('Move up')} title={t('Move up')} disabled={unitIndex.get(i) === 0}
            style={{ width: 28, height: 24, borderRadius: 7, fontSize: 12 }}
            onClick={ev => { ev.stopPropagation(); move(i, -1) }}><Icon name="chevronUp" /></button>
          <button className="iconbtn" aria-label={t('Move down')} title={t('Move down')} disabled={unitIndex.get(i) === units.length - 1}
            style={{ width: 28, height: 24, borderRadius: 7, fontSize: 12 }}
            onClick={ev => { ev.stopPropagation(); move(i, 1) }}><Icon name="chevronDown" /></button>
        </div>
      </div>
    </SwipeToDelete>
  }

  return <div className="day-ex">
    {ex.length ? <div className="list">{units.map((group, gi) => {
      const head = ex[group[0]]
      // A complex is one card; its sets and load belong to the whole thing.
      const shared = group.every(i => ex[i].sets === head.sets && (ex[i].weight || 0) === (head.weight || 0))
      if (group.length === 1) return <div key={group[0]}>{row(ex[group[0]], group[0], false)}</div>
      return <div key={group[0]} className="cx-card">
        {/* The heading writes the two numbers the whole complex shares, into this day's own ex —
            a day is what a session is built from, so this is the live plan. */}
        <div className="cx-head" {...tappable(() => complexConfigSheet({ weekId, dayIndex: index }, gi))}>
          <Icon name="link" className="cx-ico" />
          <span className="cx-title">{t('Complex')}</span>
          <span className="cx-load">{shared
            ? [t((head.sets || 1) === 1 ? '{0} set' : '{0} sets', head.sets || 1), head.weight ? fmtNum(head.weight) + ' ' + unit : null].filter(Boolean).join(' · ')
            : t('{0} exercises', group.length)}</span>
        </div>
        {group.map((i, k) => <div key={i} className="cx-row">
          <span className="cx-step">{k + 1}</span>
          {row(ex[i], i, true)}
        </div>)}
      </div>
    })}</div> : <div className="small dim" style={{ margin: '8px 2px' }}>{t('No exercises yet — add your first one.')}</div>}
    <div style={{ height: 10 }} />
    <Button variant="primary" icon="plus" onClick={add}>{t('Add exercise')}</Button>
  </div>
}

export default function WeekEdit() {
  const nav = useNavigate()
  const { id } = useParams()
  const S = useStore(s => s.S)
  const update = useStore(s => s.update)
  const toast = useUI(s => s.toast)
  const [open, setOpen] = useState(null)
  const week = (S.weeks || []).find(w => w.id === id)
  useEffect(() => { if (!week) nav('/plan') }, [!!week])
  if (!week) return null

  const ws = weekStartOf(S)
  const edit = fn => update(s => { const w = (s.weeks || []).find(x => x.id === id); if (w) fn(w) })
  const editDay = (index, fn) => edit(w => { const d = (w.days || [])[index]; if (d) fn(d) })
  const editEx = (index, fn) => editDay(index, d => { if (!d.ex) d.ex = []; fn(d.ex) })

  const weekName = () => (week.name || '').trim() || t('Week of {0}', fmtDate(week.startIso, false, true))

  const addDay = () => {
    const index = (week.days || []).length
    edit(w => { if (!w.days) w.days = []; w.days.push({ dow: firstFreeDow(w, ws), name: t('New day'), ex: [] }) })
    setOpen(index)
  }

  const deleteDay = index => {
    const day = (week.days || [])[index]
    if (!day) return
    confirmSheet({
      title: t('Delete day?'), message: t('“{0}” and its exercises will be removed.', day.name || t(DAYN[day.dow])),
      confirmText: t('Delete'), danger: true,
      onConfirm: () => { edit(w => { if (w.days) w.days.splice(index, 1) }); setOpen(null) }
    })
  }

  const deleteWeek = () => confirmSheet({
    title: t('Delete week?'), message: t('“{0}” and its exercises will be removed.', weekName()),
    confirmText: t('Delete'), danger: true,
    onConfirm: () => {
      update(s => { s.weeks = (s.weeks || []).filter(x => x.id !== id) })
      nav('/plan')
    }
  })

  const dowOptions = weekOrder(ws).map(d => ({ value: d, label: t(DAYS[d]) }))

  const fallback = t('Week of {0}', fmtDate(week.startIso, false, true))
  const dayMenu = (day, index) => menuSheet({
    title: day.name || t(DAYN[day.dow]),
    items: [{ icon: 'trash', label: t('Remove this day'), danger: true, onClick: () => deleteDay(index) }],
  })
  const weekMenu = () => menuSheet({
    title: weekName(), subtitle: fmtDate(week.startIso, true, true),
    items: [{ icon: 'trash', label: t('Delete this week'), danger: true, onClick: deleteWeek }],
  })

  return <div className="narrow">
    <TopAppBar
      leading={<button className="iconbtn ab-ico" onClick={() => nav('/plan')} aria-label={t('Plan')}><Icon name="chevronLeft" /></button>}
      smallTitle={week.name || fallback}
      subtitle={[
        fmtDate(week.startIso, true, true),
        (week.days || []).length ? dayCount((week.days || []).length) : null,
        (week.days || []).length ? exCount((week.days || []).reduce((n, d) => n + (d.ex || []).length, 0)) : null,
      ].filter(Boolean).join(' · ')}
      title={<TextField className="ab-title" value={week.name || ''} placeholder={fallback}
        aria-label={fallback} onChange={e => edit(w => { w.name = e.target.value })} />}
      actions={<button className="iconbtn ab-ico" onClick={weekMenu} aria-label={t('Week options')} title={t('Week options')}><Icon name="more" /></button>} />

    <h4 className="sec">{t('Weekdays')}</h4>
    <div className="list">
      {dayEntries(week, ws).map(({ day, index }) => <div key={index} className="item day-card" data-day={index}>
        <div className="row between" style={{ marginBottom: 6, gap: 10 }}>
          <Segmented className="seg7" options={dowOptions} value={day.dow} onChange={v => editDay(index, d => { d.dow = v })} />
          <button className="iconbtn sm" aria-label={t('Day options')} title={t('Day options')}
            onClick={() => dayMenu(day, index)}><Icon name="more" /></button>
        </div>
        {/* An editable title that does not look like one: the dashed rule under the text is the
            whole affordance, so the day keeps reading as a name and not as a form. The wrapper is
            what keeps the exercise count on the next line — a shrink-to-fit field would otherwise
            let it ride up beside the name. */}
        <div className="row">
          <TextField className="day-name" value={day.name || ''} aria-label={t(DAYN[day.dow])}
            onChange={e => editDay(index, d => { d.name = e.target.value })} />
        </div>
        {/* The day's body is this row: it opens the exercise editor for that weekday. */}
        <button className="btn ghost sm day-add" aria-expanded={open === index} onClick={() => setOpen(open === index ? null : index)}>
          {exCount((day.ex || []).length)} <Icon name={open === index ? 'chevronUp' : 'chevronDown'} />
        </button>
        {open === index && <DayExercises weekId={id} day={day} index={index} unit={S.unit} editEx={editEx} toast={toast} />}
      </div>)}
    </div>

    <div style={{ height: 10 }} />
    <Button icon="plus" onClick={addDay}>{t('Add day')}</Button>
  </div>
}
