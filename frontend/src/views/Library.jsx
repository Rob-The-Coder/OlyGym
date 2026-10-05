import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useStore } from '../store/useStore.js'
import { EXDB, allExercises } from '../lib/exercises.js'
import { MUSCLE_NAME } from '../lib/muscles.js'
import { libraryResults } from '../lib/library-filter.js'
import { activeProfile, exAvailable } from '../lib/equipment.js'
import { bestWeightFor } from '../lib/history.js'
import { fmtNum } from '../lib/format.js'
import { t, exerciseNameFor } from '../lib/i18n.js'
import { Thumb } from '../components/Media.jsx'
import { exerciseDetailSheet, customExSheet } from '../sheets.jsx'
import { libraryFilterSheet, FilterBar, AppliedFilters } from '../components/LibraryFilters.jsx'
import Icon from '../components/Icon.jsx'
import TopAppBar from '../components/TopAppBar.jsx'
import { Button } from '../components/ui.jsx'
import { tappable } from '../lib/use-sheet-keyboard.js'
import { isFav, sortFavouritesFirst } from '../lib/favourites.js'

export default function Library() {
  const nav = useNavigate()
  const S = useStore(s => s.S)
  const [q, setQ] = useState('')
  const [bp, setBp] = useState('')
  const [eq, setEq] = useState('')
  const [showAll, setShowAll] = useState(false)   // ignore the active equipment profile for this session
  const [shown, setShown] = useState(40)
  const profile = activeProfile(S)
  const all = allExercises(S)
  // One function answers for any candidate filters, and both the screen and the sheet go through
  // it — so the count on the sheet's button is the count you get, never an estimate of it.
  const resultsFor = next => libraryResults({
    all, q, bp: next.bp, eq: next.eq,
    available: (profile && !next.showAll) ? (e => exAvailable(S, e)) : null,
  })
  const { list, eq: eqOn } = resultsFor({ bp, eq, showAll })
  const describeFor = next => { const r = resultsFor(next); return { count: r.list.length, eqOpts: r.eqOpts } }
  // Favourites float to the top of whatever the filters left (issue #6), the rest keeps its order.
  const f = sortFavouritesFirst(list, S)

  // Everything narrowing the list, as a chip you can drop without reopening the sheet. The active
  // equipment profile is one of them: it used to be a banner carrying its own toggle, which is a
  // lot of furniture for a filter that is on by default.
  const applied = []
  if (bp) applied.push({ key: 'bp', label: t(bp), clear: () => { setBp(''); setEq(''); setShown(40) } })
  if (eqOn) applied.push({ key: 'eq', label: t(eqOn), clear: () => { setEq(''); setShown(40) } })
  if (profile && !showAll) applied.push({ key: 'kit', label: profile.name, clear: () => setShowAll(true) })
  const clearAll = () => { setBp(''); setEq(''); setShowAll(true); setShown(40) }

  const openFilters = () => libraryFilterSheet({
    bp, eq: eqOn, showAll, profile, describeFor,
    onApply: next => { setBp(next.bp); setEq(next.eq); setShowAll(next.showAll); setShown(40) },
  })

  return <>
    <TopAppBar title={t('Exercises')} subtitle={t('{0} exercises with video demos', EXDB.length)}
      actions={<Button size="sm" variant="tinted" icon="target" onClick={() => nav('/muscles')}>{t('By muscle')}</Button>} />

    {/* The search field is the way into 624 exercises, and it used to leave the screen with the
        first swipe. It now sticks under the bar and travels with the list. */}
    <div className="lib-search">
      <div className="search"><svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="7" /><path d="m21 21-4.3-4.3" /></svg>
        <input className="input" placeholder={t('Search…')} value={q} onChange={e => { setQ(e.target.value); setShown(40) }} /></div>
    </div>

    {/* Two chip strips used to sit here — one body part, one equipment — with nothing saying which
        was which, and between them they took about 100px before the first exercise. One row and
        one sheet instead, with the count saying what you are looking at. */}
    <FilterBar count={f.length} appliedCount={applied.length} onOpen={openFilters} />
    <AppliedFilters applied={applied} />

    <div className="list">
      <div className="item" {...tappable(() => customExSheet(null, ex => exerciseDetailSheet(ex), q.trim()))}>
        <div className="thumb thumb-x"><Icon name="sparkles" /></div>
        <div className="grow"><div className="tt">{t('Create your own exercise')}</div><div className="ss">{t('name + body part, no video')}</div></div><Icon name="plus" className="chev" />
      </div>
      {f.slice(0, shown).map(e => {
        const best = bestWeightFor(S, e.id)
        return <div key={e.id} className="item" {...tappable(() => exerciseDetailSheet(e))}>
          <Thumb ex={e} />
          <div className="grow"><div className="tt capitalize">{isFav(S, e.id) && <Icon name="starFill" className="fav-star" />}{exerciseNameFor(e)}</div><div className="ss capitalize">{t(MUSCLE_NAME[e.tg] || e.tg || e.bp)} · {t(e.eq)}</div></div>
          {best > 0 && <span className="tag acc">{fmtNum(best)}</span>}
        </div>
      })}
      {/* Nothing matched: say which of the three things is narrowing the list, and offer the one
          tap that undoes all of it rather than making the reader hunt for the right chip. */}
      {f.length === 0 && <div className="empty"><div className="ico"><Icon name="magnifier" /></div>{t('No match')}
        {applied.length > 0 && <div style={{ marginTop: 12 }}><Button size="sm" onClick={clearAll}>{t('Clear filters')}</Button></div>}
      </div>}
    </div>
    {f.length > shown && <><div style={{ height: 10 }} /><Button onClick={() => setShown(s => s + 40)}>{t('Show more')}</Button></>}
  </>
}
