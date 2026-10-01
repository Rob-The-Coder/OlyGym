// @vitest-environment happy-dom
// WS5: the coach's spreadsheet, read and reviewed before it becomes a plan. The reading itself is
// covered by lib/{coach-sheet,plan-aliases,import-plan}.test.js; this is the screen — the rows on
// show, the correction that sticks, and the import landing as routines.
import React, { act } from 'react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createRoot } from 'react-dom/client'
import { DEF, useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { coachPlanSheet } from './sheets.jsx'
import { COACH_COLUMNS } from './lib/coach-sheet.js'
import { EXDB } from './lib/exercises-data.js'
import { normName } from './lib/plan-aliases.js'

const idOf = name => {
  const ex = EXDB.find(e => e.n === name)
  if (!ex) throw new Error(`"${name}" is not in the catalogue any more`)
  return ex.id
}

const grid = rows => rows.map(cells => {
  const row = Array(16).fill('')
  for (const [col, value] of Object.entries(cells)) row[COACH_COLUMNS[col]] = value
  return row
})

const week = {
  name: 'Settimana 23-29 marzo 2026',
  grid: grid([
    { day: 'Giorno 1', name: 'Snatch primer (sequenza che fai di solito)' },
    { name: 'Strappo + strappo sosp alta', reps: '1+2', sets: '4.0', load: '50kg' },
    { name: 'Strappo no piedi', reps: '3.0', sets: '4.0' },
    { day: 'Giorno 2', name: 'Piegamenti alle parallele', reps: '10.0', sets: '3.0' },
    { name: 'Pogo jump' }
  ])
}

const mounted = []
function renderTop() {
  const sheet = useUI.getState().sheets.at(-1)
  const host = document.createElement('div')
  document.body.appendChild(host)
  const root = createRoot(host)
  mounted.push(root)
  act(() => root.render(sheet.render(() => useUI.getState().closeSheet(sheet.id))))
  return host
}

const textOf = host => host.textContent
const buttonByText = (host, text) => [...host.querySelectorAll('button')].find(b => b.textContent.trim() === text)

beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  useUI.setState({ sheets: [], toastMsg: '' })
  useStore.setState({ S: structuredClone(DEF), user: null })
  document.body.innerHTML = ''
})

afterEach(() => act(() => { mounted.splice(0).forEach(root => root.unmount()) }))

describe('the coach’s plan review', () => {
  it('shows every row with the exercise it was read as', () => {
    coachPlanSheet([week])
    const host = renderTop()
    const text = textOf(host)
    // Both components of the complex, and the exercise the catalogue's own name matched.
    expect(text).toContain('snatch')
    expect(text).toContain('hang snatch')
    // "Strappo no piedi" is Catalyst's "snatch with no jump": his words are a catalogue exercise.
    expect(text).toContain('snatch with no jump')
    // The primer line is not an exercise and does not appear as one.
    expect(text).not.toContain('Snatch primer')
  })

  it('counts the days, the exercises and what needs a look', () => {
    coachPlanSheet([week])
    const host = renderTop()
    const tiles = [...host.querySelectorAll('.tile')].map(t => [t.querySelector('.l').textContent, t.querySelector('.v').textContent])
    expect(tiles).toEqual([['Days', '2'], ['Exercises', '5'], ['New', '1'], ['To check', '1']])
  })

  it('puts a correction from a previous week on screen', () => {
    // The user corrected "Piegamenti alle parallele" once; the next week repeats the phrase.
    useStore.setState(s => ({ S: { ...s.S, planAliases: { [normName('Piegamenti alle parallele')]: idOf('push press') } } }))
    coachPlanSheet([week])
    const host = renderTop()
    // It is day 2's row: the review shows one day at a time, like the sheet does.
    act(() => buttonByText(host, 'Giorno 2').click())
    expect(textOf(host)).toContain('push press')
  })

  it('imports the week as one dated week of the plan', () => {
    coachPlanSheet([week])
    const host = renderTop()
    act(() => buttonByText(host, 'Add the week to my plan').click())

    const S = useStore.getState().S
    expect(S.weeks).toHaveLength(1)
    const days = S.weeks[0].days
    expect(days.map(d => d.name)).toEqual(['Giorno 1 · 23-29 marzo', 'Giorno 2 · 23-29 marzo'])
    // The coach's days land Monday/Wednesday/Friday.
    expect(days.map(d => d.dow)).toEqual([1, 3])
    expect(S.weeks[0].name).toBe('23-29 marzo')
    expect(days[0].ex[0].id).toBe(idOf('snatch'))
    expect(days[0].ex[0].sets).toBe(4)
    expect(days[0].ex[1].sg).toBe(days[0].ex[0].sg)
    // The exercise the catalogue does not have arrives as one of the user's own.
    const custom = S.customEx.filter(c => c.n === 'Pogo jump')
    expect(custom).toHaveLength(1)
    expect(days[1].ex.some(e => e.id === custom[0].id)).toBe(true)
    // The legacy routine/week fields are gone entirely.
    expect(S.routines).toBeUndefined()
    // …and the sheet is gone, with the week waiting on the plan screen.
    expect(useUI.getState().sheets).toHaveLength(0)
  })

  it('says the week was added', () => {
    coachPlanSheet([week])
    const host = renderTop()
    act(() => buttonByText(host, 'Add the week to my plan').click())
    expect(useUI.getState().toastMsg).toBe('Added the week to your plan')
  })
})

describe('correcting a row', () => {
  it('picks another exercise, remembers it and comes back to the review', () => {
    coachPlanSheet([week])
    const review = renderTop()
    const rows = [...review.querySelectorAll('.list .item')]
    const row = rows.find(x => /snatch with no jump/i.test(x.textContent))
    expect(row, 'the row the catalogue could not name is the one to correct').toBeTruthy()
    act(() => row.click())

    // The row menu, then the picker, each rendered as the top sheet the way the app shows them.
    expect(useUI.getState().sheets).toHaveLength(2)
    const menu = renderTop()
    act(() => [...menu.querySelectorAll('.menu-item')].find(x => /Pick a different/.test(x.textContent)).click())
    expect(useUI.getState().sheets).toHaveLength(2)
    const picker = renderTop()
    const choice = [...picker.querySelectorAll('.list .item')].find(x => /2 position snatch/i.test(x.textContent))
    act(() => choice.click())

    // The correction is stored against the coach's own words, and both helper sheets are gone.
    expect(useStore.getState().S.planAliases[normName('Strappo no piedi')]).toBe(idOf('2 position snatch'))
    expect(useUI.getState().sheets).toHaveLength(1)
    expect(review.textContent).toContain('2 position snatch')
  })
})
