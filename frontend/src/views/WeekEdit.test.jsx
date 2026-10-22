// @vitest-environment happy-dom
// The week editor behind /plan/w/:id: the name and the days of one dated week, written straight
// into S.weeks through update() like every other editor in the app.
import React, { act } from 'react'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { createRoot } from 'react-dom/client'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import WeekEdit from './WeekEdit.jsx'
import { exercisePicker, exConfigSheet, complexConfigSheet } from '../sheets.jsx'
import { DEF, useStore } from '../store/useStore.js'

const mocks = vi.hoisted(() => ({ confirmSheet: vi.fn(), complexConfigSheet: vi.fn(), menuSheet: vi.fn() }))
vi.mock('../sheets.jsx', () => ({
  effortHelpSheet: vi.fn(),
  exercisePicker: vi.fn(), exConfigSheet: vi.fn(), complexConfigSheet: mocks.complexConfigSheet,
  confirmSheet: mocks.confirmSheet, menuSheet: mocks.menuSheet,
}))
vi.mock('../components/Media.jsx', () => ({ Thumb: () => null }))

globalThis.IS_REACT_ACT_ENVIRONMENT = true
const clone = value => JSON.parse(JSON.stringify(value))

function type(el, value) {
  Object.getOwnPropertyDescriptor(el.constructor.prototype, 'value').set.call(el, value)
  el.dispatchEvent(new Event('input', { bubbles: true }))
}

let host, root
beforeEach(() => {
  localStorage.clear()
  mocks.confirmSheet.mockClear()
  mocks.complexConfigSheet.mockClear()
  mocks.menuSheet.mockClear()
  useStore.setState({
    S: {
      ...clone(DEF),
      weeks: [{ id: 'w1', startIso: '2026-02-09', name: 'Block 1', days: [{ dow: 1, name: 'Push', ex: [] }] }],
    },
    user: null,
  })
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
})
afterEach(() => {
  act(() => root.unmount())
  host.remove()
})

const mount = () => act(() => root.render(
  <MemoryRouter initialEntries={['/plan/w/w1']}>
    <Routes><Route path="/plan/w/:id" element={<WeekEdit />} /></Routes>
  </MemoryRouter>
))
const week = () => useStore.getState().S.weeks.find(w => w.id === 'w1')
const button = text => [...host.querySelectorAll('button')].find(b => b.textContent.includes(text))

describe('WeekEdit', () => {
  it('writes the week name back into S.weeks', () => {
    mount()
    act(() => type(host.querySelector('.ab-big input'), 'Deload'))
    expect(week().name).toBe('Deload')
  })

  it('adds a day on the first free weekday', () => {
    mount()
    act(() => { button('Add day').click() })
    expect(week().days).toHaveLength(2)
    expect(week().days[1]).toEqual({ dow: 2, name: 'New day', ex: [] })
  })

  // Removing a day is destructive, so it is behind the day's own menu rather than an unlabelled
  // X sitting on the card face next to the weekday control (WS17).
  it('deletes a day through its menu and the confirm sheet', () => {
    mount()
    act(() => { host.querySelector('[data-day="0"] button[aria-label="Day options"]').click() })
    expect(mocks.menuSheet).toHaveBeenCalledTimes(1)
    const menu = mocks.menuSheet.mock.calls[0][0]
    expect(menu.title).toBe('Push')
    expect(menu.items.map(i => i.label)).toEqual(['Remove this day'])
    expect(menu.items[0].danger).toBe(true)

    act(() => { menu.items[0].onClick() })
    expect(mocks.confirmSheet).toHaveBeenCalledTimes(1)
    const dialog = mocks.confirmSheet.mock.calls[0][0]
    expect(dialog.title).toBe('Delete day?')
    expect(dialog.message).toBe('“Push” and its exercises will be removed.')
    act(() => { dialog.onConfirm() })
    expect(week().days).toEqual([])
  })

  // The week's own destructive action lives in the app bar menu now: it used to be a full-width
  // red button at the end of the screen, the largest target on it.
  it('keeps deleting the week in the app bar menu, not on the page', () => {
    mount()
    expect([...host.querySelectorAll('button')].some(b => b.textContent === 'Delete week')).toBe(false)
    act(() => { host.querySelector('button[aria-label="Week options"]').click() })
    const menu = mocks.menuSheet.mock.calls[0][0]
    expect(menu.subtitle).toBe('Mon, 9 Feb 2026')
    expect(menu.items[0].label).toBe('Delete this week')
    act(() => { menu.items[0].onClick() })
    expect(mocks.confirmSheet.mock.calls[0][0].title).toBe('Delete week?')
  })

  // The body is in the DOM either way, so it can be animated shut rather than unmounted: what says
  // which state it is in is the reveal's own attribute, and `inert` is what keeps a shut day's rows
  // out of the tab order.
  it('opens a day into its exercise editor, and shuts it again', () => {
    mount()
    const body = () => host.querySelector('[data-day="0"] button[aria-expanded]')
    const reveal = () => host.querySelector('[data-day="0"] .day-reveal')
    expect(reveal().hasAttribute('data-open')).toBe(false)
    expect(reveal().hasAttribute('inert')).toBe(true)

    act(() => { body().click() })
    expect(reveal().hasAttribute('data-open')).toBe(true)
    expect(reveal().hasAttribute('inert')).toBe(false)
    expect(body().getAttribute('aria-expanded')).toBe('true')
    expect(button('Add exercise')).toBeTruthy()
    expect(host.querySelector('[data-day="0"]').textContent).toContain('No exercises yet — add your first one.')

    act(() => { body().click() })
    expect(reveal().hasAttribute('data-open')).toBe(false)
    expect(reveal().hasAttribute('inert')).toBe(true)
    expect(body().getAttribute('aria-expanded')).toBe('false')
  })

  // The day editor is RoutineEdit's flow: the picker adds, and a row opens the exercise config.
  it('adds through the exercise picker and configures from the row', () => {
    const entry = { id: '0025', sets: 3, reps: 10, weight: 40, mode: 'reps' }
    const S = useStore.getState().S
    S.weeks[0].days[0].ex = [entry]
    useStore.setState({ S })
    mount()
    act(() => { host.querySelector('[data-day="0"] button[aria-expanded]').click() })
    act(() => { button('Add exercise').click() })
    expect(exercisePicker).toHaveBeenCalledTimes(1)
    act(() => { host.querySelector('[data-day="0"] .item').click() })
    expect(exConfigSheet).toHaveBeenCalledTimes(1)
    expect(exConfigSheet.mock.calls[0][1]).toEqual(entry)
  })

  // The three actions stay on the row — this is the screen where order is worked out — but at the
  // app's own 36px .iconbtn instead of the 32x28 / 28x24 the inline styles had them at, and as one
  // group at the end of the row rather than a column that shifted when the link was missing.
  it('keeps the row actions on the row, at the app icon-button size', () => {
    const S = useStore.getState().S
    S.weeks[0].days[0].ex = [
      { id: '0025', sets: 3, reps: 3, weight: 30, mode: 'reps' },
      { id: '0031', sets: 3, reps: 3, weight: 30, mode: 'reps' },
    ]
    useStore.setState({ S })
    mount()
    act(() => { host.querySelector('[data-day="0"] button[aria-expanded]').click() })
    const rows = [...host.querySelectorAll('[data-day="0"] .item')]
    expect(rows).toHaveLength(2)
    // The first row has nothing above it to pair with, so it carries two controls and the rest three.
    expect(rows[0].querySelectorAll('.ex-acts button')).toHaveLength(2)
    expect(rows[1].querySelectorAll('.ex-acts button')).toHaveLength(3)
    for (const b of rows[1].querySelectorAll('.ex-acts button')) {
      expect(b.className).toContain('iconbtn')
      expect(b.getAttribute('style')).toBeNull()
    }
    expect(rows[0].querySelector('[aria-label="Move up"]').disabled).toBe(true)
    expect(rows[0].querySelector('[aria-label="Move down"]').disabled).toBe(false)
    expect(rows[1].querySelector('[aria-label="Move down"]').disabled).toBe(true)
  })

  // A complex is one card: the sets and load its movements share are written once, the unit's own
  // controls sit on that line, and a movement keeps only the reps that are its own.
  it('draws a complex as one card, with its shared numbers and its controls once', () => {
    const S = useStore.getState().S
    S.weeks[0].days[0].ex = [
      { id: '0025', sets: 3, reps: 3, weight: 30, mode: 'reps', sg: 'g1' },
      { id: '0031', sets: 3, reps: 1, weight: 30, mode: 'reps', sg: 'g1' },
    ]
    useStore.setState({ S })
    mount()
    act(() => { host.querySelector('[data-day="0"] button[aria-expanded]').click() })
    const card = host.querySelector('[data-day="0"] .cx-card')
    expect(card.querySelector('.cx-load').textContent).toBe('3 sets · 30 kg')
    // the unit's own controls: its order, and the menu holding unpair and remove
    expect(card.querySelectorAll('.cx-head .ex-acts button')).toHaveLength(3)
    // never repeated on a movement row, where they acted on the whole complex anyway
    expect(card.querySelectorAll('.cx-item .ex-acts')).toHaveLength(0)
    expect([...card.querySelectorAll('.cx-reps')].map(r => r.textContent)).toEqual(['×3', '×1'])
  })

  // The coach writes the tip once for the complex. Repeating it on every movement is noise that
  // pushes the reps off the row.
  it('reads a shared tip once, under the numbers, and not on every movement', () => {
    const S = useStore.getState().S
    S.weeks[0].days[0].ex = [
      { id: '0025', sets: 3, reps: 3, weight: 30, mode: 'reps', sg: 'g1', note: 'Pause in the bottom' },
      { id: '0031', sets: 3, reps: 1, weight: 30, mode: 'reps', sg: 'g1', note: 'Pause in the bottom' },
    ]
    useStore.setState({ S })
    mount()
    act(() => { host.querySelector('[data-day="0"] button[aria-expanded]').click() })
    const card = host.querySelector('[data-day="0"] .cx-card')
    expect([...card.querySelectorAll('.cx-note')].map(n => n.textContent)).toEqual(['Pause in the bottom'])
    // …wearing the same ruled row his tip wears in the review, so it is never the quietest thing here.
    expect(card.querySelector('.cx-note').className).toContain('coach-note')
    expect(card.querySelectorAll('.cx-item .dim')).toHaveLength(0)
  })

  // A note belongs to the exercise it was written on. Outside a complex nothing is shared, so the
  // line and the note both stay on the row.
  it('keeps a note on a row that is not part of a complex', () => {
    const S = useStore.getState().S
    S.weeks[0].days[0].ex = [
      { id: '0025', sets: 3, reps: 5, weight: 40, mode: 'reps', note: 'Bar only, then a plate a side' },
    ]
    useStore.setState({ S })
    mount()
    act(() => { host.querySelector('[data-day="0"] button[aria-expanded]').click() })
    const row = host.querySelector('[data-day="0"] .item')
    expect(row.querySelector('.ss').textContent).toBe('3 × 5 · 40 kg')
    expect(row.querySelector('.coach-note').textContent).toBe('Bar only, then a plate a side')
  })

  // What the coach wrote for the movement, kept from the import: the row says what it is now, his
  // line says what it was, and the note under it is what he asked for.
  it('shows the coach’s own row on the exercise it was read as', () => {
    const S = useStore.getState().S
    S.weeks[0].days[0].ex = [
      { id: '0025', sets: 3, reps: 10, weight: 40, mode: 'reps', src: 'Strappo sosp alta · 4 × 1+2', note: 'sotto il ginocchio' },
    ]
    useStore.setState({ S })
    mount()
    act(() => { host.querySelector('[data-day="0"] button[aria-expanded]').click() })
    const row = host.querySelector('[data-day="0"] .item')
    expect(row.querySelector('.coach-src').textContent).toBe('Strappo sosp alta · 4 × 1+2')
    expect(row.querySelector('.coach-note').textContent).toBe('sotto il ginocchio')
  })

  it('keeps the coach’s row when the exercise is edited', () => {
    const S = useStore.getState().S
    S.weeks[0].days[0].ex = [{ id: '0025', sets: 3, reps: 10, weight: 40, mode: 'reps', src: 'Strappo · 4 × 1+2' }]
    useStore.setState({ S })
    mount()
    act(() => { host.querySelector('[data-day="0"] button[aria-expanded]').click() })
    act(() => { host.querySelector('[data-day="0"] .item').click() })
    const onSave = exConfigSheet.mock.calls[0][2]
    act(() => onSave({ sets: 5, reps: 5, weight: 45, mode: 'reps' }))
    expect(week().days[0].ex[0]).toMatchObject({ sets: 5, reps: 5, weight: 45, src: 'Strappo · 4 × 1+2' })
  })

  it('marks the day open for a reader as well as for the eye', () => {
    mount()
    const state = () => host.querySelector('[data-day="0"] button[aria-expanded]').getAttribute('aria-expanded')
    expect(state()).toBe('false')
    act(() => { host.querySelector('[data-day="0"] button[aria-expanded]').click() })
    expect(state()).toBe('true')
  })

  // The expander is one glyph turned by its own state rather than two icons swapped, and the body
  // grows into the space it needs and back out of it. Both live in the sheet, so they are read from
  // it — a grid row is the only height here that can be animated, since `auto` cannot be.
  it('turns the expander chevron, and animates the body shut as well as open', () => {
    const css = readFileSync(resolve(process.cwd(), 'src/m3.components.css'), 'utf8')
    expect(css).toMatch(/\.day-add\[aria-expanded="true"\] \.icn\{transform:rotate\(180deg\)\}/)
    expect(css).toMatch(/\.day-add \.icn\{transition:transform var\(--m3-dur-long\) var\(--m3-ease-spring\)\}/)
    // one transition on the row covers both directions; the attribute is the whole state
    expect(css).toMatch(/\.day-reveal\{display:grid;grid-template-rows:0fr;transition:grid-template-rows var\(--m3-dur-long\) var\(--m3-ease-emphasized\)\}/)
    expect(css).toMatch(/\.day-reveal\[data-open\]\{grid-template-rows:1fr\}/)
    expect(css).toMatch(/\.day-reveal \.day-ex\{[^}]*opacity:0;transform:translateY\(-14px\)/)
    expect(css).toMatch(/\.day-reveal\[data-open\] \.day-ex\{opacity:1;transform:none\}/)
  })

  // The card is the prescription: a movement loading differently gets its own line rather than
  // erasing the numbers the coach wrote for the whole thing.
  it('keeps the shared numbers when one movement disagrees', () => {
    const S = useStore.getState().S
    S.weeks[0].days[0].ex = [
      { id: '0025', sets: 3, reps: 3, weight: 30, mode: 'reps', sg: 'g1' },
      { id: '0031', sets: 3, reps: 1, weight: 40, mode: 'reps', sg: 'g1' },
    ]
    useStore.setState({ S })
    mount()
    act(() => { host.querySelector('[data-day="0"] button[aria-expanded]').click() })
    const card = host.querySelector('[data-day="0"] .cx-card')
    expect(card.querySelector('.cx-load').textContent).toBe('3 sets · 30 kg')
    const rows = [...card.querySelectorAll('.cx-item')]
    expect(rows[0].querySelector('.ss')).toBeNull()
    expect(rows[1].querySelector('.ss').textContent).toBe('3 × 1 · 40 kg')
    // that line already carries the reps, so the row drops the chip beside it
    expect(rows[1].querySelector('.cx-reps')).toBeNull()
  })

  // Unpairing acts on the whole complex, so it is one control on the card rather than a link button
  // repeated on every movement — where it read as that movement's and unlinked all of them.
  it('unpairs the whole complex from the card menu', () => {
    const S = useStore.getState().S
    S.weeks[0].days[0].ex = [
      { id: '0025', sets: 3, reps: 3, weight: 30, mode: 'reps', sg: 'g1' },
      { id: '0031', sets: 3, reps: 1, weight: 30, mode: 'reps', sg: 'g1' },
    ]
    useStore.setState({ S })
    mount()
    act(() => { host.querySelector('[data-day="0"] button[aria-expanded]').click() })
    act(() => { host.querySelector('[data-day="0"] [aria-label="Complex options"]').click() })
    const menu = mocks.menuSheet.mock.calls[0][0]
    expect(menu.title).toBe('Complex')
    expect(menu.items.map(i => i.label)).toEqual(['Unpair', 'Remove complex'])

    act(() => { menu.items[0].onClick() })
    expect(week().days[0].ex.map(e => e.sg)).toEqual([undefined, undefined])
  })

  // Removing a complex is removing several exercises at once, so it asks first.
  it('asks before removing the whole complex', () => {
    const S = useStore.getState().S
    S.weeks[0].days[0].ex = [
      { id: '0025', sets: 3, reps: 3, weight: 30, mode: 'reps', sg: 'g1' },
      { id: '0031', sets: 3, reps: 1, weight: 30, mode: 'reps', sg: 'g1' },
    ]
    useStore.setState({ S })
    mount()
    act(() => { host.querySelector('[data-day="0"] button[aria-expanded]').click() })
    act(() => { host.querySelector('[data-day="0"] [aria-label="Complex options"]').click() })
    const menu = mocks.menuSheet.mock.calls[0][0]
    expect(menu.items[1].danger).toBe(true)

    act(() => { menu.items[1].onClick() })
    expect(mocks.confirmSheet).toHaveBeenCalledTimes(1)
    const dialog = mocks.confirmSheet.mock.calls[0][0]
    expect(dialog.title).toBe('Remove this complex?')
    act(() => { dialog.onConfirm() })
    expect(week().days[0].ex).toEqual([])
  })

  // Linking still starts from the row — it is the only way to make a complex — and the pair it
  // makes is a card, so taking it apart moves to that card's menu.
  it('links from the row and unpairs the pair it made', () => {
    const S = useStore.getState().S
    S.weeks[0].days[0].ex = [
      { id: '0025', sets: 3, reps: 3, weight: 30, mode: 'reps' },
      { id: '0031', sets: 3, reps: 3, weight: 30, mode: 'reps' },
    ]
    useStore.setState({ S })
    mount()
    act(() => { host.querySelector('[data-day="0"] button[aria-expanded]').click() })
    act(() => { [...host.querySelectorAll('[data-day="0"] .item')][1]
      .querySelector('[aria-label="Complex with exercise above"]').click() })
    const after = week().days[0].ex.map(e => e.sg)
    expect(after[0]).toBeTruthy()
    expect(after[1]).toBe(after[0])

    act(() => { host.querySelector('[data-day="0"] [aria-label="Complex options"]').click() })
    act(() => { mocks.menuSheet.mock.calls[0][0].items[0].onClick() })
    expect(week().days[0].ex.map(e => e.sg)).toEqual([undefined, undefined])
  })

  // A collapsed day used to say only how many exercises it held.
  it('shows what a collapsed day holds, and stops once it is open', () => {
    const S = useStore.getState().S
    S.weeks[0].days[0].ex = [
      { id: '0025', sets: 3, reps: 3, weight: 30, mode: 'reps' },
      { id: '0031', sets: 3, reps: 1, weight: 30, mode: 'reps' },
    ]
    useStore.setState({ S })
    mount()
    const preview = host.querySelector('[data-day="0"] .day-preview')
    expect(preview).toBeTruthy()
    expect(preview.textContent).toContain('·')
    act(() => { host.querySelector('[data-day="0"] button[aria-expanded]').click() })
    expect(host.querySelector('[data-day="0"] .day-preview')).toBeNull()
  })

  // The week's name is an input, so it arrived wearing .field: a 32px filled box, the largest thing
  // on the screen and visibly a form. An editable title has one look here — see .day-name.
  it('draws the week name as a title, not as a filled field', () => {
    const css = readFileSync(resolve(process.cwd(), 'src/m3.components.css'), 'utf8')
    const rule = css.match(/^\.field\.ab-title\{([^}]*)\}/m)
    expect(rule?.[1]).toContain('background:none')
    expect(rule[1]).toContain('border-bottom:1.5px dashed')
    // and the plain h1 rule is untouched, so the screens that pass a string keep it
    expect(css).toMatch(/^\.ab-title\{[^}]*font-size:32px/m)
  })

  // A complex is one card, and the sheet that writes its shared sets/load edits this day's own
  // ex — a day is what a session is built from, so that is the live plan.
  it('opens the complex sheet addressed at this week’s day', () => {
    const S = useStore.getState().S
    S.weeks[0].days[0].ex = [
      { id: '0025', sets: 3, reps: 3, weight: 30, sg: 'g1' },
      { id: '0031', sets: 3, reps: 1, weight: 30, sg: 'g1' },
    ]
    useStore.setState({ S })
    mount()
    act(() => { host.querySelector('[data-day="0"] button[aria-expanded]').click() })
    act(() => { host.querySelector('.cx-head').click() })
    expect(mocks.complexConfigSheet).toHaveBeenCalledWith({ weekId: 'w1', dayIndex: 0 }, 0)
  })

  // Progressive sets: the row line carries the whole ladder through exLine, so the week editor
  // needed no extra surface for it.
  it('prints a progressive load as its ladder on the row', () => {
    const S = useStore.getState().S
    S.weeks[0].days[0].ex = [
      { id: '0025', sets: 4, reps: 10, weight: 60, mode: 'reps', setWeights: [60, 60, 65, 65] },
    ]
    useStore.setState({ S })
    mount()
    act(() => { host.querySelector('[data-day="0"] button[aria-expanded]').click() })
    expect(host.querySelector('[data-day="0"] .item .ss').textContent).toBe('4 × 10 · 60/60/65/65 kg')
  })

  // The card carries the shared numbers once: a ladder is part of them, and a movement whose
  // ladder differs still prints its own line.
  it('heads a complex with the ladder every movement shares', () => {
    const S = useStore.getState().S
    S.weeks[0].days[0].ex = [
      { id: '0025', sets: 4, reps: 3, weight: 60, mode: 'reps', sg: 'g1', setWeights: [60, 60, 65, 65] },
      { id: '0031', sets: 4, reps: 1, weight: 60, mode: 'reps', sg: 'g1', setWeights: [60, 60, 65, 65] },
    ]
    useStore.setState({ S })
    mount()
    act(() => { host.querySelector('[data-day="0"] button[aria-expanded]').click() })
    const card = host.querySelector('[data-day="0"] .cx-card')
    expect(card.querySelector('.cx-load').textContent).toBe('4 sets · 60/60/65/65 kg')
    expect(card.querySelectorAll('.cx-item .ss')).toHaveLength(0)
  })

  it('keeps the card’s ladder and prints the movement whose ladder differs', () => {
    const S = useStore.getState().S
    S.weeks[0].days[0].ex = [
      { id: '0025', sets: 4, reps: 3, weight: 60, mode: 'reps', sg: 'g1', setWeights: [60, 60, 65, 65] },
      { id: '0031', sets: 4, reps: 1, weight: 60, mode: 'reps', sg: 'g1', setWeights: [60, 60, 65, 70] },
    ]
    useStore.setState({ S })
    mount()
    act(() => { host.querySelector('[data-day="0"] button[aria-expanded]').click() })
    const card = host.querySelector('[data-day="0"] .cx-card')
    expect(card.querySelector('.cx-load').textContent).toBe('4 sets · 60/60/65/65 kg')
    const rows = [...card.querySelectorAll('.cx-item')]
    expect(rows[0].querySelector('.ss')).toBeNull()
    expect(rows[1].querySelector('.ss').textContent).toBe('4 × 1 · 60/60/65/70 kg')
  })
})

