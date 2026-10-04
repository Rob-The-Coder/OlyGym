// @vitest-environment happy-dom
// QA C9. Custom exercises and the newer exercise metadata name muscles by the map's ids. The
// picker row, the routine config sheet and the Muscles rows printed those ids ("gluteal",
// "forearm", "hip-flexors") where the detail sheet already said Glutes / Forearms / Hip flexors.
import React, { act } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRoot } from 'react-dom/client'
import { EXIDX, registerCustom } from './lib/exercises.js'
import { DEF, useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { _setLangState } from './lib/i18n-core.js'
import { exercisePicker, exConfigSheet } from './sheets.jsx'
import MuscleExplorer from './components/MuscleExplorer.jsx'

const mounted = []
const S = () => useStore.getState().S

// A real Olympic lift out of the shipped catalogue, for the "catalogue metadata" half of these
// cases: it names its muscles by the map's ids, which is what MUSCLE_NAME has to translate.
const OLY = Object.values(EXIDX).find(e => e.n === 'power clean').id

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
function render(node) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  const root = createRoot(host)
  mounted.push(root)
  act(() => root.render(node))
  return host
}
function type(el, value) {
  Object.getOwnPropertyDescriptor(el.constructor.prototype, 'value').set.call(el, value)
  el.dispatchEvent(new Event('input', { bubbles: true }))
}
const rowFor = (host, name) => [...host.querySelectorAll('.item')].find(e => e.textContent.includes(name))
const custom = (over = {}) => ({
  id: 'cqa1', n: 'QA Custom Thrust', bp: 'upper legs', eq: 'barbell', custom: true, desc: '',
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

describe('muscle names in the rows and tags (QA C9)', () => {
  it('picker row shows the display name, not the map id', () => {
    seed(custom())
    exercisePicker(vi.fn())
    const host = renderTop()
    act(() => type(host.querySelector('input.input'), 'QA Custom'))
    expect(rowFor(host, 'QA Custom Thrust').querySelector('.ss').textContent).toBe('Glutes · barbell')
  })

  it('config sheet tags read like the detail sheet, for a custom exercise and for an Olympic lift', () => {
    seed(custom())
    exConfigSheet(EXIDX.cqa1, null, vi.fn())
    const tags = h => [...h.querySelectorAll('.tag')].map(e => e.textContent.trim())
    expect(tags(renderTop())).toEqual(['upper legs', 'Glutes', 'barbell', 'Forearms', 'Hip flexors'])
    // Both sheets now lead with the body part and carry the target muscle, the equipment and the
    // secondaries, so the same exercise cannot report a different summary in each.
    //
    // The leading tag is the only one that is still raw: a catalogue exercise's body part is a
    // category the locale knows ("Clean", "Snatch"), but a custom exercise's is a muscle-group id
    // out of lib/muscles.js ("upper legs") that no locale has a name for. Pre-existing on the
    // detail sheet; the config sheet inherits it now that the two rows are the same row.
    //
    // #207 The catalogue names muscles by the map's ids (trapezius, quadriceps, gluteal), which only
    // MUSCLE_NAME turns into a translatable label — the config sheet must show the labels the detail
    // sheet shows, secondaries included.
    exConfigSheet(EXIDX[OLY], null, vi.fn())
    expect(tags(renderTop())).toEqual(['Clean', 'Traps', 'barbell', 'Quads', 'Glutes'])
  })

  it('Muscles explorer row names the target the same way', () => {
    seed(custom())
    const host = render(<MuscleExplorer onDetail={vi.fn()} onPlan={vi.fn()} />)
    const chip = [...host.querySelectorAll('.chip[aria-pressed]')].find(e => e.textContent.startsWith('Glutes'))
    act(() => chip.click())
    act(() => type(host.querySelector('.search input'), 'QA Custom'))
    expect(rowFor(host, 'QA Custom Thrust').querySelector('.ss').textContent).toBe('Primary target · Glutes · barbell')
  })
})
