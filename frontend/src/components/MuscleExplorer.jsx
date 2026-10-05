import { useMemo, useState } from 'react'
import { useStore } from '../store/useStore.js'
import { allExercises } from '../lib/exercises.js'
import { activeProfile, exAvailable } from '../lib/equipment.js'
import { bestWeightFor } from '../lib/history.js'
import { libraryResults } from '../lib/library-filter.js'
import { fmtNum } from '../lib/format.js'
import { MUSCLES, MUSCLE_NAME, musclesOf } from '../lib/muscles.js'
import { t, exerciseNameFor } from '../lib/i18n.js'
import BodyMap from './BodyMap.jsx'
import { Thumb } from './Media.jsx'
import Icon from './Icon.jsx'
import { Button } from './ui.jsx'
import { libraryFilterSheet, FilterBar, AppliedFilters } from './LibraryFilters.jsx'
import { tappable } from '../lib/use-sheet-keyboard.js'
import { isFav, sortFavouritesFirst } from '../lib/favourites.js'

// One explorer for the Library and every catalogue picker. Supplying `onPick` turns
// a result into a selection; without it the explorer behaves like the normal Library.
export default function MuscleExplorer({ onPick, onDetail }) {
  const S = useStore(s => s.S)
  const [selected, setSelected] = useState(null)
  const [q, setQ] = useState('')
  const [bp, setBp] = useState('')
  const [eq, setEq] = useState('')
  const [shown, setShown] = useState(40)
  const [showAll, setShowAll] = useState(false)   // ignore the active equipment profile for this session
  // Same rule as the Library and the picker: the active equipment profile narrows the catalogue
  // (and the per-muscle counts) unless the user asks for everything.
  const profile = activeProfile(S)
  const catalog = useMemo(() => {
    const all = allExercises(S)
    return (profile && !showAll) ? all.filter(e => exAvailable(S, e)) : all
  }, [S.customEx, S.equipFilterOn, S.activeEquipId, S.equipProfiles, showAll])
  const counts = useMemo(() => Object.fromEntries(MUSCLES.map(m => [m,
    catalog.filter(e => musclesOf(e)[m]).length
  ])), [catalog])
  const pick = muscle => { setSelected(muscle === selected ? null : muscle); setEq(''); setShown(40) }
  const targeted = selected ? catalog.filter(e => musclesOf(e)[selected]) : []
  // The same pipeline the Library runs, out of the same helper: the profile has already narrowed
  // `catalog`, so there is nothing left for the availability predicate to do on this screen.
  const { list, eq: eqOn } = libraryResults({ all: targeted, q, bp, eq })
  // Favourites float to the top of whatever the filters left (issue #6), the rest keeps its order.
  const exercises = sortFavouritesFirst(list, S)
  const describeFor = next => {
    const r = libraryResults({ all: targeted, q, bp: next.bp, eq: next.eq })
    return { count: r.list.length, eqOpts: r.eqOpts }
  }
  const choose = ex => onPick ? onPick(ex) : onDetail(ex)

  // The same chips as the Library, the profile among them. Body part and equipment only mean
  // something once a muscle has been picked, so they wait until then; the profile is narrowing
  // the counts above it, so it shows either way.
  const applied = []
  if (profile && !showAll) applied.push({ key: 'kit', label: profile.name, clear: () => setShowAll(true) })
  if (selected && bp) applied.push({ key: 'bp', label: t(bp), clear: () => { setBp(''); setEq(''); setShown(40) } })
  if (selected && eqOn) applied.push({ key: 'eq', label: t(eqOn), clear: () => { setEq(''); setShown(40) } })
  const openFilters = () => libraryFilterSheet({
    bp, eq: eqOn, showAll, profile, describeFor,
    onApply: next => { setBp(next.bp); setEq(next.eq); setShowAll(next.showAll); setShown(40) },
  })

  return <>
    <div className="card">
      <BodyMap className="tappable" body={S.body} selected={selected} onMuscle={pick} />
      <div className="chips" style={{ marginTop: 10 }}>
        {MUSCLES.map(m => <button key={m} className={'chip' + (selected === m ? ' on' : '')}
          aria-pressed={selected === m} onClick={() => pick(m)}>
          {t(MUSCLE_NAME[m])} <span className="dim">{counts[m]}</span>
        </button>)}
      </div>
    </div>

    <AppliedFilters applied={applied} />

    {!selected && onPick && <div className="empty"><div className="ico"><Icon name="target" /></div>{t('Choose a muscle to see exercises that train it.')}</div>}

    {selected && <>
      <div className="row between" style={{ margin: '2px 0 10px' }}>
        <div className="sech" style={{ margin: 0 }}>{t('Exercises for {0}', t(MUSCLE_NAME[selected]))}</div>
        <Button size="sm" variant="ghost" onClick={() => pick(selected)}>{t('Clear selection')}</Button>
      </div>
      <div className="search" style={{ marginBottom: 10 }}><svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="7" /><path d="m21 21-4.3-4.3" /></svg>
        <input className="input" placeholder={t('Search…')} value={q} onChange={e => { setQ(e.target.value); setShown(40) }} />
      </div>
      {/* The two chip strips that used to sit here are the sheet now, exactly as in the Library. */}
      <FilterBar count={exercises.length} appliedCount={applied.length} onOpen={openFilters} />
      <div className="list">
        {exercises.slice(0, shown).map(e => {
          const best = bestWeightFor(S, e.id)
          const primary = musclesOf(e)[selected] === 1
          return <div key={e.id} className="item" {...tappable(() => choose(e))}>
            <Thumb ex={e} />
            <div className="grow"><div className="tt capitalize">{isFav(S, e.id) && <Icon name="starFill" className="fav-star" />}{exerciseNameFor(e)}</div><div className="ss">{t(primary ? 'Primary target' : 'Also trains')} · <span className="capitalize">{t(MUSCLE_NAME[e.tg] || e.tg || e.bp)} · {t(e.eq)}</span></div></div>
            {onPick ? <Icon name="plus" className="chev" /> : best > 0 && <span className="tag acc">{fmtNum(best)}</span>}
          </div>
        })}
        {exercises.length === 0 && <div className="empty"><div className="ico"><Icon name="magnifier" /></div>{t('No match')}</div>}
      </div>
      {exercises.length > shown && <><div style={{ height: 10 }} /><Button onClick={() => setShown(s => s + 40)}>{t('Show more')}</Button></>}
    </>}
  </>
}
