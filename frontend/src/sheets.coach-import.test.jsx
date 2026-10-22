// @vitest-environment happy-dom
// WS5: the coach's spreadsheet, read and reviewed before it becomes a plan. The reading itself is
// covered by lib/{coach-sheet,plan-aliases,import-plan}.test.js; this is the screen — the rows on
// show, the correction that sticks, and the import landing as routines.
import React, { act } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRoot } from 'react-dom/client'
import { DEF, useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { coachEntrySheet, coachPlanSheet } from './sheets.jsx'
import { COACH_COLUMNS } from './lib/coach-sheet.js'
import { EXDB } from './lib/exercises-data.js'
import { normName } from './lib/plan-aliases.js'
import { convertWeight } from './lib/units.js'

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

// The correction sheet's fields are the shared Stepper by the label above them; typing into one
// goes through the same NumberField every other sheet uses.
const field = (host, label) => [...host.querySelectorAll('.stp-w')]
  .find(w => w.querySelector('.stp-l')?.textContent === label)?.querySelector('input')
const type = (el, value) => act(() => {
  Object.getOwnPropertyDescriptor(el.constructor.prototype, 'value').set.call(el, value)
  el.dispatchEvent(new Event('input', { bubbles: true }))
})
const menuItem = (host, re) => [...host.querySelectorAll('.menu-item')].find(x => re.test(x.textContent))

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

  it('summarises the week in one line instead of four cards', () => {
    coachPlanSheet([week])
    const host = renderTop()
    expect(host.querySelectorAll('.tile')).toHaveLength(0)
    expect(host.textContent).toContain('2 days · 5 exercises · 1 new')
    expect(host.textContent).toContain('0 of 5 checked')
  })

  it('prints the coach’s own row under every exercise it read', () => {
    coachPlanSheet([week])
    const host = renderTop()
    // Day 1: the two halves of his complex, each with the row he wrote around it, and the load
    // exactly as he wrote it — not the 50 kg the app took out of it.
    expect([...host.querySelectorAll('.coach-src')].map(e => e.textContent)).toEqual([
      'Strappo · 4 × 1+2 · 50kg',
      'strappo sosp alta · 4 × 1+2 · 50kg',
      'Strappo no piedi · 4 × 3'
    ])
  })

  // "Strappo + strappo sosp alta" is one thing the coach wrote on one row. Drawn as two loose rows it
  // read as two independent exercises, so it takes the app's own complex card: his numbers once in the
  // head, the movements numbered inside, each keeping the reps that are its own.
  it('draws his complex as one card, not two exercises', () => {
    coachPlanSheet([week])
    const host = renderTop()
    const card = host.querySelector('.list .cx-card')
    expect(card).toBeTruthy()
    expect(card.querySelector('.cx-title').textContent).toBe('Complex')
    expect(card.querySelector('.cx-load').textContent).toBe('4 sets · 50 kg')
    expect([...card.querySelectorAll('.cx-row .cx-step')].map(e => e.textContent)).toEqual(['1', '2'])
    expect([...card.querySelectorAll('.cx-item .tt')].map(e => e.textContent)).toEqual(['snatch', 'hang snatch'])
    // The movement keeps its own reps, and the sets and load are the card's, not repeated on the row.
    expect([...card.querySelectorAll('.cx-reps')].map(e => e.textContent)).toEqual(['×1', '×2'])
    // The day's third row is a plain exercise: it is not in the card, and it keeps its own line.
    expect(host.querySelectorAll('.list > .item')).toHaveLength(1)
    expect(host.querySelector('.list > .item .ss').textContent).toBe('4 × 3')
  })

  // The tip the coach wrote once for the whole row is read on every movement: this screen is read
  // movement by movement, and the head is a scroll away once the day is long.
  it('prints his tip on each movement of the complex', () => {
    coachPlanSheet([{ name: 'Settimana', grid: grid([
      { day: 'Giorno 1', name: 'Strappo + strappo sosp alta', reps: '1+2', sets: '4.0', load: '50kg , se leggeri le ultime due a 55kg' }
    ]) }])
    const host = renderTop()
    const card = host.querySelector('.cx-card')
    expect([...card.querySelectorAll('.cx-item .coach-note')].map(e => e.textContent))
      .toEqual(['50kg , se leggeri le ultime due a 55kg', '50kg , se leggeri le ultime due a 55kg'])
    // The head keeps his numbers only; the tip is not left up there as a second copy.
    expect(card.querySelector('.cx-note')).toBeNull()
    expect(card.querySelector('.cx-load').textContent).toBe('4 sets · 50 kg')
  })

  it('ticks a row off and counts what is left to read', () => {
    coachPlanSheet([week])
    const host = renderTop()
    const row = () => host.querySelectorAll('.list .item')[0]
    expect(row().querySelector('.chk').getAttribute('aria-checked')).toBe('false')
    // The checkbox has no visible label of its own, so it is named after the exercise it ticks.
    expect(row().querySelector('.chk').getAttribute('aria-label')).toBe(row().querySelector('.tt').textContent)
    expect(host.textContent).toContain('0 of 5 checked')

    act(() => row().querySelector('.chk').click())
    expect(row().querySelector('.chk').getAttribute('aria-checked')).toBe('true')
    expect(row().hasAttribute('data-checked')).toBe(true)
    expect(host.textContent).toContain('1 of 5 checked')

    act(() => row().querySelector('.chk').click())
    expect(host.textContent).toContain('0 of 5 checked')
    // Ticking is not tapping the row: the correction sheet never opened.
    expect(useUI.getState().sheets).toHaveLength(1)
  })

  it('numbers the days and names the open one above its rows', () => {
    coachPlanSheet([week])
    const host = renderTop()
    expect([...host.querySelectorAll('.seg button')].map(b => b.textContent.trim())).toEqual(['1', '2'])
    expect(host.querySelector('.sech').textContent).toBe('Giorno 1 · 23-29 marzo')
    // The segment says the number, the overline says which day of the sheet that is.
    expect(host.querySelector('.seg button').getAttribute('aria-label')).toBe('Giorno 1')
    act(() => [...host.querySelectorAll('.seg button')].find(b => b.textContent.trim() === '2').click())
    expect(host.querySelector('.sech').textContent).toBe('Giorno 2 · 23-29 marzo')
  })

  it('flags the sets and reps the coach never gave as the app’s guess', () => {
    coachPlanSheet([week])
    const host = renderTop()
    act(() => [...host.querySelectorAll('.seg button')].find(b => b.textContent.trim() === '2').click())
    // "Pogo jump" is a row with no sets and no reps: the 3 × 10 beside it is filled in.
    expect([...host.querySelectorAll('.mchip.miss')].map(c => c.textContent))
      .toEqual(['no sets given', 'no reps given'])
  })

  it('puts a correction from a previous week on screen', () => {
    // The user corrected "Piegamenti alle parallele" once; the next week repeats the phrase.
    useStore.setState(s => ({ S: { ...s.S, planAliases: { [normName('Piegamenti alle parallele')]: idOf('push press') } } }))
    coachPlanSheet([week])
    const host = renderTop()
    // It is day 2's row: the review shows one day at a time, like the sheet does.
    act(() => [...host.querySelectorAll('.seg button')].find(b => b.textContent.trim() === '2').click())
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
    // And that row is marked as read: correcting it is what having looked at it meant.
    expect(review.textContent).toContain('1 of 5 checked')
  })
})

// The other half of the review: the exercise can be right and the numbers wrong. These pin the
// correction sheet (opened from the row menu), what it is seeded with, what it writes back into
// the derived review and what the import then carries.
describe('correcting a row’s numbers', () => {
  const plainWeek = { name: 'Settimana', grid: grid([
    { day: 'Giorno 1', name: 'Strappo', reps: '3.0', sets: '4.0', load: '50kg' }
  ]) }

  // Tap a row, pick "Edit sets, reps and weight" from the menu and return the correction sheet.
  function openCorrection(review, row) {
    act(() => row.click())
    const menu = renderTop()
    expect(useUI.getState().sheets).toHaveLength(2)
    const item = menuItem(menu, /Edit sets, reps and weight/)
    expect(item, 'the row menu offers the number correction').toBeTruthy()
    act(() => item.click())
    expect(useUI.getState().sheets).toHaveLength(2)
    return renderTop()
  }
  const save = host => act(() => buttonByText(host, 'Save').click())

  it('offers the number correction first and keeps the picker beside it', () => {
    coachPlanSheet([plainWeek])
    const review = renderTop()
    act(() => review.querySelector('.list .item').click())
    const menu = renderTop()
    expect([...menu.querySelectorAll('.menu-item .tt')].map(e => e.textContent))
      .toEqual(['Edit sets, reps and weight', 'Pick a different exercise'])
  })

  it('opens seeded with the row’s numbers and writes the corrected line back', () => {
    coachPlanSheet([plainWeek])
    const review = renderTop()
    const sheet = openCorrection(review, review.querySelector('.list .item'))

    // The name the row shows and his own row are the sheet's reference.
    expect(sheet.querySelector('h3').textContent.toLowerCase()).toContain('snatch')
    expect(sheet.querySelector('.coach-src').textContent).toBe('Strappo · 4 × 3 · 50kg')
    expect(field(sheet, 'Sets').value).toBe('4')
    expect(field(sheet, 'Reps').value).toBe('3')
    expect(field(sheet, 'Weight (kg)').value).toBe('50')
    expect(sheet.textContent).toContain('Applies to this import only.')

    type(field(sheet, 'Sets'), '2')
    type(field(sheet, 'Reps'), '5')
    type(field(sheet, 'Weight (kg)'), '55')
    save(sheet)

    // The row re-derives from the corrected review; his row stays printed as he wrote it.
    expect(review.querySelector('.list .item .ss').textContent).toBe('2 × 5 · 55 kg')
    expect(review.querySelector('.coach-src').textContent).toBe('Strappo · 4 × 3 · 50kg')
    expect(review.textContent).toContain('1 of 1 checked')
    // Only the review is left open, and no identity correction was written anywhere.
    expect(useUI.getState().sheets).toHaveLength(1)
    expect(useStore.getState().S.planAliases).toEqual({})
  })

  it('dismisses without changing anything', () => {
    coachPlanSheet([plainWeek])
    const review = renderTop()
    const sheet = openCorrection(review, review.querySelector('.list .item'))
    type(field(sheet, 'Sets'), '9')
    act(() => useUI.getState().closeSheet(useUI.getState().sheets.at(-1).id))
    expect(review.querySelector('.list .item .ss').textContent).toBe('4 × 3 · 50 kg')
    expect(review.textContent).toContain('0 of 1 checked')
  })

  it('seeds a timed row in seconds and saves the hold', () => {
    const timed = { name: 'Settimana', grid: grid([{ day: 'Giorno 1', name: 'Plank', reps: '1 minuto', sets: '3.0' }]) }
    coachPlanSheet([timed])
    const review = renderTop()
    const sheet = openCorrection(review, review.querySelector('.list .item'))
    expect(field(sheet, 'Seconds').value).toBe('60')
    expect(field(sheet, 'Reps')).toBeUndefined()
    type(field(sheet, 'Seconds'), '45')
    save(sheet)
    expect(review.querySelector('.list .item .ss').textContent).toBe('3 × 0:45')
  })

  it('answers the warnings the corrected numbers were about', () => {
    coachPlanSheet([week])
    const review = renderTop()
    act(() => [...review.querySelectorAll('.seg button')].find(b => b.textContent.trim() === '2').click())
    const row = () => [...review.querySelectorAll('.list .item')].find(x => /Pogo jump/.test(x.textContent))
    expect([...row().querySelectorAll('.mchip.miss')].map(c => c.textContent))
      .toEqual(['no sets given', 'no reps given'])
    const sheet = openCorrection(review, row())
    type(field(sheet, 'Sets'), '4')
    type(field(sheet, 'Reps'), '6')
    save(sheet)
    expect(row().querySelectorAll('.mchip.miss')).toHaveLength(0)
    expect(row().querySelector('.ss').textContent).toBe('4 × 6')
  })

  it('applies a complex’s sets and load to every movement, and reps only to the tapped one', () => {
    coachPlanSheet([week])
    const review = renderTop()
    const sheet = openCorrection(review, review.querySelector('.cx-card .cx-item'))
    // The sheet says the two numbers are the complex's, not the movement's.
    expect(sheet.textContent).toContain('Sets and load are the same for every exercise in the complex')
    type(field(sheet, 'Sets'), '2')
    type(field(sheet, 'Reps'), '3')
    type(field(sheet, 'Weight (kg)'), '55')
    save(sheet)

    const card = review.querySelector('.cx-card')
    expect(card.querySelector('.cx-load').textContent).toBe('2 sets · 55 kg')
    expect([...card.querySelectorAll('.cx-reps')].map(e => e.textContent)).toEqual(['×3', '×2'])
    // Every movement still shares the head's numbers: no per-movement disagreement line.
    expect(card.querySelectorAll('.cx-item .ss')).toHaveLength(0)
    expect(review.textContent).toContain('1 of 5 checked')
  })

  // Progressive sets: the correction sheet carries the same per-set row the exercise config has,
  // and a ladder is the complex's shared prescription like its sets and load.
  describe('per-set loads', () => {
    // Open the correction sheet, then its "Weights per set" sheet.
    const openLadder = (review, row) => {
      const sheet = openCorrection(review, row)
      act(() => [...sheet.querySelectorAll('.lrow')].find(r => r.textContent.includes('Weights per set')).click())
      return { sheet, ladderSheet: renderTop() }
    }
    const setLadder = ladderSheet => {
      type(field(ladderSheet, 'Set 3'), '55')
      type(field(ladderSheet, 'Set 4'), '55')
      act(() => buttonByText(ladderSheet, 'Done').click())
    }

    it('corrects a plain row into a ladder and prints it on the review', () => {
      coachPlanSheet([plainWeek])
      const review = renderTop()
      const row = () => review.querySelector('.list .item')
      const { sheet, ladderSheet } = openLadder(review, row())
      expect(ladderSheet.querySelector('h3').textContent).toBe('Weights per set')
      expect([...ladderSheet.querySelectorAll('.stp-l')].map(el => el.textContent))
        .toEqual(['Set 1', 'Set 2', 'Set 3', 'Set 4'])

      setLadder(ladderSheet)
      save(sheet)
      expect(row().querySelector('.ss').textContent).toBe('4 × 3 · 50/50/55/55 kg')
    })

    it('gives every movement of a complex the same ladder', () => {
      coachPlanSheet([week])
      const review = renderTop()
      const { sheet, ladderSheet } = openLadder(review, review.querySelector('.cx-card .cx-item'))
      setLadder(ladderSheet)
      save(sheet)

      const card = review.querySelector('.cx-card')
      expect(card.querySelector('.cx-load').textContent).toBe('4 sets · 50/50/55/55 kg')
      // both movements share it, so neither prints a disagreeing line
      expect(card.querySelectorAll('.cx-item .ss')).toHaveLength(0)

      act(() => buttonByText(review, 'Add the week to my plan').click())
      const ex = useStore.getState().S.weeks[0].days[0].ex
      expect(ex[0]).toMatchObject({ sets: 4, weight: 50, setWeights: [50, 50, 55, 55] })
      expect(ex[1]).toMatchObject({ sets: 4, weight: 50, setWeights: [50, 50, 55, 55] })
    })

    it('imports the ladder with its first load as the week’s weight', () => {
      coachPlanSheet([plainWeek])
      const review = renderTop()
      const { sheet, ladderSheet } = openLadder(review, review.querySelector('.list .item'))
      setLadder(ladderSheet)
      save(sheet)
      act(() => buttonByText(review, 'Add the week to my plan').click())

      const cfg = useStore.getState().S.weeks[0].days[0].ex[0]
      expect(cfg.setWeights).toEqual([50, 50, 55, 55])
      expect(cfg.weight).toBe(cfg.setWeights[0])
    })

    it('takes the ladder back to the coach’s numbers', () => {
      coachPlanSheet([plainWeek])
      const review = renderTop()
      const row = () => review.querySelector('.list .item')
      const { sheet, ladderSheet } = openLadder(review, row())
      setLadder(ladderSheet)
      save(sheet)
      expect(row().querySelector('.ss').textContent).toBe('4 × 3 · 50/50/55/55 kg')

      act(() => row().click())
      const menu = renderTop()
      expect(menuItem(menu, /Back to the coach's numbers/)).toBeTruthy()
      act(() => menuItem(menu, /Back to the coach's numbers/).click())
      expect(row().querySelector('.ss').textContent).toBe('4 × 3 · 50 kg')
    })
  })

  // Resetting must offer only what it can actually remove. A reps-only fix is the tapped
  // movement's own, so it must not put "Back to the coach's numbers" on a sibling, and the item
  // must disappear once resetting the row leaves it nothing (round-1 review, ux/code finding 1).
  describe('resetting across a complex', () => {
    const setup = () => {
      coachPlanSheet([week])
      const review = renderTop()
      return {
        review,
        items: () => [...review.querySelectorAll('.cx-card .cx-item')],
        reps: () => [...review.querySelectorAll('.cx-card .cx-reps')].map(e => e.textContent),
        head: () => review.querySelector('.cx-load').textContent,
        openMenu: row => { act(() => row.click()); return renderTop() },
        closeMenu: () => act(() => useUI.getState().closeSheet(useUI.getState().sheets.at(-1).id))
      }
    }

    it('keeps the item off a sibling when only the tapped movement’s reps were fixed', () => {
      const { review, items, reps, openMenu, closeMenu } = setup()
      // His sets/load are untouched; only the second movement's reps change.
      const sheet = openCorrection(review, items()[1])
      type(field(sheet, 'Reps'), '6')
      save(sheet)
      expect(reps()).toEqual(['×1', '×6'])

      // The first movement has nothing of its own to reset, so the item is not offered there.
      expect(menuItem(openMenu(items()[0]), /Back to the coach's numbers/)).toBeUndefined()
      closeMenu()

      // The corrected movement has, and resetting it clears the fix and the item.
      const own = openMenu(items()[1])
      expect(menuItem(own, /Back to the coach's numbers/)).toBeTruthy()
      act(() => menuItem(own, /Back to the coach's numbers/).click())
      expect(reps()).toEqual(['×1', '×2'])
      expect(menuItem(openMenu(items()[1]), /Back to the coach's numbers/)).toBeUndefined()
    })

    it('resets the shared sets from any movement and leaves a sibling’s own reps fix', () => {
      const { review, items, reps, head, openMenu, closeMenu } = setup()
      // reps on the first movement, then the complex's shared sets.
      let sheet = openCorrection(review, items()[0])
      type(field(sheet, 'Reps'), '3')
      save(sheet)
      sheet = openCorrection(review, items()[0])
      type(field(sheet, 'Sets'), '2')
      save(sheet)
      expect(head()).toBe('2 sets · 50 kg')
      expect(reps()).toEqual(['×3', '×2'])

      // The second movement can reset the shared sets (they are the complex's) but not the
      // first movement's reps; once it does, it has nothing left to reset.
      const second = openMenu(items()[1])
      expect(menuItem(second, /Back to the coach's numbers/)).toBeTruthy()
      act(() => menuItem(second, /Back to the coach's numbers/).click())
      expect(head()).toBe('4 sets · 50 kg')
      expect(reps()).toEqual(['×3', '×2'])
      expect(menuItem(openMenu(items()[1]), /Back to the coach's numbers/)).toBeUndefined()
      closeMenu()

      // The first movement still owns its reps fix; resetting there clears it too.
      const first = openMenu(items()[0])
      expect(menuItem(first, /Back to the coach's numbers/)).toBeTruthy()
      act(() => menuItem(first, /Back to the coach's numbers/).click())
      expect(reps()).toEqual(['×1', '×2'])
      expect(menuItem(openMenu(items()[0]), /Back to the coach's numbers/)).toBeUndefined()
    })
  })

  it('does not claim a correction when Save changed nothing', () => {
    coachPlanSheet([plainWeek])
    const review = renderTop()
    const row = () => review.querySelector('.list .item')
    const sheet = openCorrection(review, row())
    save(sheet)
    expect(row().querySelector('.ss').textContent).toBe('4 × 3 · 50 kg')
    act(() => row().click())
    expect(menuItem(renderTop(), /Back to the coach's numbers/)).toBeUndefined()
    // Saving is still looking the row over, even when the numbers were already right.
    expect(review.textContent).toContain('1 of 1 checked')
  })

  // Requirement 3: the numbers live for the sitting, per week — switching weeks and days must not
  // drop them, and they must not leak into the next week's sheet.
  it('keeps each week’s corrections when switching weeks and back', () => {
    const secondWeek = { name: 'Settimana 2', grid: grid([{ day: 'Giorno 1', name: 'Strappo', reps: '1.0', sets: '3.0' }]) }
    coachPlanSheet([plainWeek, secondWeek])
    const review = renderTop()
    const row = () => review.querySelector('.list .item')
    const weekCard = () => [...review.querySelectorAll('.item')].find(x => !x.closest('.list'))
    const pickWeek = name => {
      act(() => weekCard().click())
      const menu = renderTop()
      act(() => [...menu.querySelectorAll('.menu-item')].find(x => x.textContent.trim() === name).click())
    }

    const sheet = openCorrection(review, row())
    type(field(sheet, 'Sets'), '2')
    type(field(sheet, 'Weight (kg)'), '55')
    save(sheet)
    expect(row().querySelector('.ss').textContent).toBe('2 × 3 · 55 kg')

    pickWeek('Settimana 2')
    // The other sheet keeps its own reading: this week's numbers do not leak into it.
    expect(row().querySelector('.ss').textContent).toBe('3 × 1')
    pickWeek('Settimana')
    // …and the first week's correction is still there, tick included.
    expect(row().querySelector('.ss').textContent).toBe('2 × 3 · 55 kg')
    expect(review.textContent).toContain('1 of 1 checked')
  })

  it('puts the coach’s numbers back and then takes the item away', () => {
    coachPlanSheet([plainWeek])
    const review = renderTop()
    const row = () => review.querySelector('.list .item')
    let sheet = openCorrection(review, row())
    type(field(sheet, 'Sets'), '2')
    type(field(sheet, 'Weight (kg)'), '55')
    save(sheet)
    expect(row().querySelector('.ss').textContent).toBe('2 × 3 · 55 kg')

    act(() => row().click())
    const menu = renderTop()
    expect(menuItem(menu, /Back to the coach's numbers/)).toBeTruthy()
    act(() => menuItem(menu, /Back to the coach's numbers/).click())
    expect(useUI.getState().sheets).toHaveLength(1)
    expect(review.querySelector('.list .item .ss').textContent).toBe('4 × 3 · 50 kg')
    // Resetting is not un-reading the row.
    expect(review.textContent).toContain('1 of 1 checked')

    act(() => review.querySelector('.list .item').click())
    const menu2 = renderTop()
    expect(menuItem(menu2, /Back to the coach's numbers/)).toBeUndefined()
  })

  it('imports the corrected numbers and keeps his row', () => {
    coachPlanSheet([plainWeek])
    const review = renderTop()
    const sheet = openCorrection(review, review.querySelector('.list .item'))
    type(field(sheet, 'Sets'), '2')
    type(field(sheet, 'Reps'), '5')
    type(field(sheet, 'Weight (kg)'), '55')
    save(sheet)
    act(() => buttonByText(review, 'Add the week to my plan').click())
    expect(useStore.getState().S.weeks[0].days[0].ex[0])
      .toMatchObject({ sets: 2, reps: 5, weight: 55, src: 'Strappo · 4 × 3 · 50kg' })
  })

  it('imports a corrected complex as one shared prescription', () => {
    coachPlanSheet([week])
    const review = renderTop()
    const sheet = openCorrection(review, review.querySelector('.cx-card .cx-item'))
    type(field(sheet, 'Sets'), '2')
    type(field(sheet, 'Reps'), '3')
    type(field(sheet, 'Weight (kg)'), '55')
    save(sheet)
    act(() => buttonByText(review, 'Add the week to my plan').click())
    const ex = useStore.getState().S.weeks[0].days[0].ex
    expect(ex[0]).toMatchObject({ sets: 2, reps: 3, weight: 55 })
    expect(ex[1]).toMatchObject({ sets: 2, reps: 2, weight: 55 })
    expect(ex[0].sg).toBe(ex[1].sg)
  })

  // The plan itself is kilos only today (plan-share's mergeWeek refuses another unit), so the
  // import is confirmed in a kg account to read what the correction stored: the point here is the
  // number the sheet hands over, in kilograms.
  describe('in pounds', () => {
    // 51.3 kg is not on the quarter-kilo grid: a kg → lb → kg round trip would land on 51.25, so
    // the untouched save keeping 51.3 is what says the kilogram value is passed through exactly.
    const oddWeek = { name: 'Settimana', grid: grid([
      { day: 'Giorno 1', name: 'Strappo', reps: '3.0', sets: '4.0', load: '51,3kg' }
    ]) }
    const importInKg = review => {
      act(() => useStore.setState(s => ({ S: { ...s.S, unit: 'kg' } })))
      act(() => buttonByText(review, 'Add the week to my plan').click())
      return useStore.getState().S.weeks[0].days[0].ex[0].weight
    }
    beforeEach(() => useStore.setState(s => ({ S: { ...s.S, unit: 'lb' } })))

    it('shows the weight converted but keeps the coach’s kilos when untouched', () => {
      coachPlanSheet([oddWeek])
      const review = renderTop()
      const sheet = openCorrection(review, review.querySelector('.list .item'))
      expect(field(sheet, 'Weight (lb)').value).toBe(String(convertWeight(51.3, 'kg', 'lb')))
      save(sheet)
      expect(importInKg(review)).toBe(51.3)
    })

    it('converts a changed pound weight back to kilos before the import', () => {
      coachPlanSheet([plainWeek])
      const review = renderTop()
      const sheet = openCorrection(review, review.querySelector('.list .item'))
      type(field(sheet, 'Weight (lb)'), '120')
      save(sheet)
      expect(importInKg(review)).toBe(convertWeight(120, 'lb', 'kg'))
    })

    it('hands its save a kilogram weight in either case', () => {
      // The sheet's own contract, without the review around it: display unit in, kilograms out.
      const entry = { key: 'k', id: idOf('snatch'), sets: 4, reps: 3, weight: 50, mode: 'reps', warns: [], custom: false, src: '' }
      const onSave = vi.fn()
      coachEntrySheet(entry, onSave)
      const sheet = renderTop()
      expect(field(sheet, 'Weight (lb)').value).toBe('110')
      act(() => buttonByText(sheet, 'Save').click())
      expect(onSave).toHaveBeenCalledWith({ sets: 4, reps: 3, weight: 50 })

      coachEntrySheet(entry, onSave)
      const sheet2 = renderTop()
      type(field(sheet2, 'Weight (lb)'), '120')
      act(() => buttonByText(sheet2, 'Save').click())
      expect(onSave).toHaveBeenLastCalledWith({ sets: 4, reps: 3, weight: convertWeight(120, 'lb', 'kg') })
    })
  })
})
