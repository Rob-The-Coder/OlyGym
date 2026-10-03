import { useState } from 'react'
import { BODYPARTS } from '../lib/exercises.js'
import { t } from '../lib/i18n.js'
import { useUI } from '../store/useUI.js'
import Icon from './Icon.jsx'
import { Button, Row, Switch } from './ui.jsx'

/* ============================ the catalogue's filters ============================
   One sheet for every screen that filters the catalogue (WS14). The Library and the
   muscle explorer both had the same two chip strips — one body part, one equipment —
   under a search field, with nothing saying which was which, and together they took
   about 100px before the first result. This is that pair, grouped and named, plus the
   active equipment profile, which is a filter like any other rather than a banner
   carrying its own toggle.

   The sheet keeps its own state and commits on "Show N exercises": trying a body part
   and backing out changes nothing. `describeFor` is the caller's own filter function,
   so the count on that button is the count you get — the sheet cannot drift from the
   list it is filtering.

   This lives in components/ rather than in sheets.jsx because sheets.jsx imports
   MuscleExplorer: importing the opener back out of it would close an import cycle
   between two files that have nothing to say to each other.
   ================================================================================ */
export const libraryFilterSheet = props =>
  useUI.getState().openSheet(close => <LibraryFilters {...props} close={close} />)

function LibraryFilters({ bp, eq, showAll, profile, describeFor, onApply, close }) {
  const [b, setB] = useState(bp)
  const [e, setE] = useState(eq)
  const [all, setAll] = useState(showAll)
  const { count, eqOpts } = describeFor({ bp: b, eq: e, showAll: all })
  // Same guard as the screens: a body part with no dumbbell work cannot leave "Dumbbell" chosen.
  const eOn = eqOpts.includes(e) ? e : ''
  const clean = !b && !eOn
  return <>
    <h3>{t('Filters')}</h3>
    <div className="sect-t">{t('Body part')}</div>
    <div className="chips wrap">
      <button className={'chip nocap' + (!b ? ' on' : '')} aria-pressed={!b} onClick={() => setB('')}>{t('All')}</button>
      {BODYPARTS.map(x => <button key={x} className={'chip' + (b === x ? ' on' : '')} aria-pressed={b === x}
        onClick={() => setB(b === x ? '' : x)}>{t(x)}</button>)}
    </div>
    {eqOpts.length > 1 && <>
      <div className="sect-t">{t('Equipment')}</div>
      <div className="chips wrap">
        <button className={'chip nocap' + (!eOn ? ' on' : '')} aria-pressed={!eOn} onClick={() => setE('')}>{t('Any equipment')}</button>
        {eqOpts.map(x => <button key={x} className={'chip' + (eOn === x ? ' on' : '')} aria-pressed={eOn === x}
          onClick={() => setE(eOn === x ? '' : x)}>{t(x)}</button>)}
      </div>
    </>}
    {profile && <Row icon="dumbbell" title={t('Only my equipment')} subtitle={t('Leave out anything "{0}" does not cover', profile.name)}>
      <Switch checked={!all} onChange={v => setAll(!v)} />
    </Row>}
    <div style={{ height: 14 }} />
    <Button variant="primary" onClick={() => { onApply({ bp: b, eq: eOn, showAll: all }); close() }}>{t('Show {0} exercises', count)}</Button>
    {!clean && <><div style={{ height: 8 }} /><Button variant="ghost" className="dim" onClick={() => { setB(''); setE('') }}>{t('Clear filters')}</Button></>}
  </>
}

// What replaced the strips on the screens: how many results there are, and one chip to the sheet.
export function FilterBar({ count, appliedCount, onOpen }) {
  return <div className="lib-bar">
    <span className="dim">{t('{0} exercises', count)}</span>
    <button className="chip nocap lib-filters" onClick={onOpen}>
      {t('Filters')}{appliedCount ? ' · ' + appliedCount : ''}
      <Icon name="chevronDown" />
    </button>
  </div>
}

// Everything narrowing the list, as chips you can drop without reopening the sheet.
export function AppliedFilters({ applied }) {
  if (!applied.length) return null
  return <div className="chips lib-applied">
    {applied.map(a => <button key={a.key} className="chip on nocap" onClick={a.clear}>{a.label}<Icon name="xmark" /></button>)}
  </div>
}
