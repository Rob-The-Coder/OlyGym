// @vitest-environment happy-dom
// QA C10. Since the muscles of a custom exercise are stored in the map's order (83b2a14), the
// one-word target `tg` was silently taken from the sorted list: a hip thrust with Traps as an
// extra primary became a "Traps" exercise in the library, the picker and the Muscles view, and
// tap order no longer let the user correct it. The target is the primary picked first; an edit
// keeps the old target while it is still a primary.
import React, { act } from 'react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createRoot } from 'react-dom/client'
import { EXIDX, registerCustom } from './lib/exercises.js'
import { DEF, useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { _setLangState } from './lib/i18n-core.js'
import { bindUI } from './components/ui.jsx'
import { customExSheet } from './sheets.jsx'

bindUI(useUI)   // the multi-select rows open their sheet through the shared controls

const mounted = []
const S = () => useStore.getState().S

// Renders whatever sheet is on top and returns its host element.
function renderTop() {
  const sheet = useUI.getState().sheets.at(-1)
  const host = document.createElement('div')
  document.body.appendChild(host)
  const root = createRoot(host)
  mounted.push(root)
  act(() => root.render(sheet.render(() => useUI.getState().closeSheet(sheet.id))))
  return host
}
function type(el, value) {
  Object.getOwnPropertyDescriptor(el.constructor.prototype, 'value').set.call(el, value)
  el.dispatchEvent(new Event('input', { bubbles: true }))
}
const byText = (host, sel, text) => [...host.querySelectorAll(sel)].find(e => e.textContent.trim() === text)
const click = (host, sel, text) => {
  const el = byText(host, sel, text)
  if (!el) throw new Error(`nothing with text "${text}"`)
  act(() => el.click())
}
// Body part and Equipment open the single-choice picker, which closes on the tap.
function pick(form, rowTitle, label) {
  const row = [...form.querySelectorAll('.lrow')].find(e => e.textContent.includes(rowTitle))
  act(() => row.click())
  click(renderTop(), 'button', label)
}
// The form's multi-select rows open a nested sheet; tap the given labels there, in order, then Done.
function pickMuscles(form, rowTitle, labels) {
  const row = [...form.querySelectorAll('.lrow')].find(e => e.textContent.includes(rowTitle))
  act(() => row.click())
  const sub = renderTop()
  for (const l of labels) click(sub, 'button', l)
  click(sub, 'button', 'Done')
}
const custom = (over = {}) => ({
  id: 'cqa1', n: 'QA Custom Thrust', bp: 'Accessory - Lower/Whole Body', eq: 'barbell', custom: true, desc: '',
  tg: 'gluteal', sm: ['forearm', 'hip-flexors'], primaries: ['gluteal'], secondaries: ['forearm', 'hip-flexors'],
  muscleGroups: ['gluteal', 'forearm', 'hip-flexors'], ...over,
})
function seed(ex) {
  useStore.setState(s => ({ S: { ...s.S, customEx: [ex] } }))
  registerCustom([ex])
}

beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  useUI.setState({ sheets: [], toastMsg: '' })
  useStore.setState({ S: structuredClone(DEF), user: null })
  registerCustom([])
  _setLangState('en', null, null, null)
  document.body.innerHTML = ''
})

afterEach(() => {
  act(() => { mounted.splice(0).forEach(root => root.unmount()) })
  _setLangState('en', null, null, null)
  registerCustom([])
})

describe('the custom exercise form', () => {
  // Body part and equipment were two horizontal scroll strips with the scrollbar hidden — three of
  // ten body parts on screen, five of eight kinds of equipment — beside two muscle rows that were
  // already using the app's own picker.
  it('picks the body part and the equipment from rows, not from scroll strips', () => {
    customExSheet(null)
    const form = renderTop()
    expect(form.querySelectorAll('.chip')).toHaveLength(0)
    const row = title => [...form.querySelectorAll('.lrow')].find(r => r.textContent.includes(title))
    expect(row('Body part').textContent).toContain('Choose…')
    pick(form, 'Equipment', 'Barbell')
    // The capital belongs to the label now — the id in the catalogue is 'barbell', and the chip was
    // borrowing a text-transform from .chip that a row does not have.
    expect(row('Equipment').textContent).toContain('Barbell')
  })

  // A body part the catalogue's list does not hold — a muscle-map word, or one written before the
  // list existed. SelectRow falls back to printing the raw value, which for these is a lower-case id.
  it('shows a body part the list does not hold, rather than printing its id', () => {
    const ex = custom({ bp: 'upper legs', eq: 'machine' })
    seed(ex)
    customExSheet(EXIDX[ex.id])
    const form = renderTop()
    const row = title => [...form.querySelectorAll('.lrow')].find(r => r.textContent.includes(title))
    expect(row('Body part').textContent).toContain('Upper Legs')
    expect(row('Equipment').textContent).toContain('Machine')
  })

  // The last full-width danger button at the foot of a sheet. The exercise detail sheet keeps the
  // same action in a ⋯.
  it('keeps Delete exercise in the ⋯, not at the foot of the form', () => {
    const ex = custom()
    seed(ex)
    customExSheet(EXIDX[ex.id])
    const form = renderTop()
    expect([...form.querySelectorAll('button')].some(b => b.textContent === 'Delete exercise')).toBe(false)
    act(() => form.querySelector('button[aria-label="Exercise options"]').click())
    const menu = renderTop()
    expect(menu.textContent).toContain('Delete exercise')
    expect([...menu.querySelectorAll('.menu-item')].some(r => r.className.includes('danger'))).toBe(true)
  })
})

describe('custom exercise target (QA C10)', () => {
  it('is the primary tapped first, while the stored list keeps the map order', () => {
    customExSheet(null)
    const form = renderTop()
    act(() => type(form.querySelector('input.input'), 'QA Hip Thrust Pull'))
    pick(form, 'Body part', 'Accessory - Lower/Whole Body')
    pick(form, 'Equipment', 'Barbell')
    pickMuscles(form, 'Primary muscle groups', ['Hamstrings', 'Glutes', 'Traps'])
    click(form, 'button', 'Create exercise')
    const c = S().customEx.find(x => x.n === 'QA Hip Thrust Pull')
    expect(c.primaries).toEqual(['trapezius', 'gluteal', 'hamstring'])
    expect(c.tg).toBe('hamstring')
    expect(c.muscleGroups).toEqual(['trapezius', 'gluteal', 'hamstring'])
  })

  it('survives an edit that adds a primary higher up the body', () => {
    const ex = custom({ tg: 'hamstring', primaries: ['gluteal', 'hamstring'], secondaries: [], sm: [], muscleGroups: ['gluteal', 'hamstring'] })
    seed(ex)
    customExSheet(EXIDX[ex.id])
    const form = renderTop()
    pickMuscles(form, 'Primary muscle groups', ['Traps'])
    click(form, 'button', 'Save')
    const c = S().customEx[0]
    expect(c.primaries).toEqual(['trapezius', 'gluteal', 'hamstring'])
    expect(c.tg).toBe('hamstring')
  })

  it('moves to another primary only when the old target is dropped', () => {
    const ex = custom({ tg: 'hamstring', primaries: ['gluteal', 'hamstring'], secondaries: [], sm: [], muscleGroups: ['gluteal', 'hamstring'] })
    seed(ex)
    customExSheet(EXIDX[ex.id])
    const form = renderTop()
    pickMuscles(form, 'Primary muscle groups', ['Hamstrings'])   // untick it
    click(form, 'button', 'Save')
    const c = S().customEx[0]
    expect(c.primaries).toEqual(['gluteal'])
    expect(c.tg).toBe('gluteal')
  })

  // Dropping the target used to hand it to the head of the map-sorted list, which is where C10
  // started: an upper-legs exercise read "Traps · barbell" again the moment its target went away.
  it('hands a dropped target to the primary tapped in this sheet, not to the topmost muscle', () => {
    const ex = custom({ tg: 'hamstring', primaries: ['trapezius', 'gluteal', 'hamstring'], secondaries: ['forearm'], sm: ['forearm'],
      muscleGroups: ['trapezius', 'gluteal', 'hamstring', 'forearm'] })
    seed(ex)
    customExSheet(EXIDX[ex.id])
    const form = renderTop()
    pickMuscles(form, 'Primary muscle groups', ['Hamstrings', 'Quads'])   // untick the target, tap another
    click(form, 'button', 'Save')
    const c = S().customEx[0]
    expect(c.primaries).toEqual(['trapezius', 'gluteal', 'quadriceps'])
    expect(c.tg).toBe('quadriceps')
  })

  // Tapping the same chip twice leaves the selection as it was, so it says nothing about what the
  // user wants the target to be — the muscle actually added does.
  it('ignores a primary that was tapped on and off again', () => {
    const ex = custom({ tg: 'hamstring', primaries: ['trapezius', 'hamstring'], secondaries: [], sm: [], muscleGroups: ['trapezius', 'hamstring'] })
    seed(ex)
    customExSheet(EXIDX[ex.id])
    const form = renderTop()
    pickMuscles(form, 'Primary muscle groups', ['Hamstrings', 'Chest', 'Chest', 'Glutes'])
    click(form, 'button', 'Save')
    const c = S().customEx[0]
    expect(c.primaries).toEqual(['trapezius', 'gluteal'])
    expect(c.tg).toBe('gluteal')
  })
})
