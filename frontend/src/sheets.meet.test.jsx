// @vitest-environment happy-dom
// The meet sheet (sheets.jsx MeetSheet). What this pins is the reading that only a competition
// has: a no-lift keeps its weight and never counts, a total exists only when both lifts were
// made, and a saved meet lands in S.competitions with the app's own list helpers able to read
// it back.
import React, { act } from 'react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createRoot } from 'react-dom/client'
import { useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { totalOf } from './lib/competition.js'
import { todayISO } from './lib/format.js'
import { meetSheet, meetDetailSheet, weightClassesSheet } from './sheets.jsx'

const mounted = []

function type(el, value) {
  Object.getOwnPropertyDescriptor(el.constructor.prototype, 'value').set.call(el, value)
  el.dispatchEvent(new Event('input', { bubbles: true }))
}

function renderSheet(open) {
  act(() => open())
  const sheet = useUI.getState().sheets.at(-1)
  const host = document.createElement('div')
  document.body.appendChild(host)
  const root = createRoot(host)
  mounted.push(root)
  act(() => root.render(sheet.render(() => useUI.getState().closeSheet(sheet.id))))
  const button = label => [...host.querySelectorAll('button')].find(b => (b.getAttribute('aria-label') || b.textContent.trim()) === label)
  const weight = i => host.querySelectorAll('.att .num')[i]
  const verdict = (i, j) => host.querySelectorAll('.att')[i].querySelectorAll('.seg-inline button')[j]
  return { host, button, weight, good: i => verdict(i, 0), noLift: i => verdict(i, 1) }
}

const meet = (over = {}) => ({
  id: 'm1', d: todayISO(), name: 'Nazionale', place: '', class: '73', bw: null,
  snatch: [{ w: 105, made: true }], cj: [{ w: 135, made: true }], placing: null, note: '', ...over,
})

describe('the meet sheet', () => {
  beforeEach(() => {
    globalThis.IS_REACT_ACT_ENVIRONMENT = true
    useUI.setState({ sheets: [], toasts: [] })
    useStore.setState(s => ({ S: { ...s.S, competitions: [] } }))
    document.body.innerHTML = ''
  })
  afterEach(() => { act(() => { mounted.splice(0).forEach(root => root.unmount()) }) })

  it('saves a meet with its attempts and total', () => {
    const { host, button, weight, good } = renderSheet(() => meetSheet())
    act(() => type(host.querySelector('input.field'), 'Regionale'))
    act(() => type(weight(0), '100'))
    act(() => good(0).click())
    act(() => type(weight(3), '130'))
    act(() => good(3).click())
    act(() => button('Add a competition').click())
    const stored = useStore.getState().S.competitions
    expect(stored).toHaveLength(1)
    expect(stored[0]).toMatchObject({
      name: 'Regionale',
      snatch: [{ w: 100, made: true }],
      cj: [{ w: 130, made: true }],
    })
    expect(totalOf(stored[0])).toBe(230)
    expect(useUI.getState().sheets).toHaveLength(0)
  })

  it('keeps a no-lift on the record without letting it count', () => {
    const { button, weight, good, noLift } = renderSheet(() => meetSheet())
    act(() => type(weight(0), '110'))
    act(() => noLift(0).click())
    act(() => type(weight(1), '100'))
    act(() => good(1).click())
    act(() => button('Add a competition').click())
    const m = useStore.getState().S.competitions[0]
    expect(m.snatch).toEqual([{ w: 110, made: false }, { w: 100, made: true }])
    expect(totalOf(m)).toBeNull()
  })

  it('opens an existing meet with its attempts in place and updates it rather than duplicating', () => {
    useStore.setState(s => ({ S: { ...s.S, competitions: [meet()] } }))
    const { host, button, weight } = renderSheet(() => meetSheet(meet()))
    expect(host.querySelector('input.field').value).toBe('Nazionale')   // an input value is not text
    expect(weight(0).value).toBe('105')
    act(() => type(weight(0), '107'))
    act(() => button('Save competition').click())
    const stored = useStore.getState().S.competitions
    expect(stored).toHaveLength(1)
    expect(stored[0].snatch).toEqual([{ w: 107, made: true }])
  })

  it('edits one body’s category list and leaves the other alone', () => {
    useStore.setState(s => ({ S: { ...s.S, body: 'male', classes: { male: ['60', '65'], female: ['55'] } } }))
    const { host, button } = renderSheet(() => weightClassesSheet())
    act(() => button('Add a category').click())
    act(() => type(host.querySelectorAll('input.field')[2], '70'))
    act(() => button('Save').click())
    expect(useStore.getState().S.classes).toEqual({ male: ['60', '65', '70'], female: ['55'] })
  })

  it('opens on the list for the profile’s body, and switches', () => {
    useStore.setState(s => ({ S: { ...s.S, body: 'male', classes: { male: ['60'], female: ['55'] } } }))
    const show = host => [...host.querySelectorAll('input.field')].map(i => i.value)
    const { host, button } = renderSheet(() => weightClassesSheet())
    expect(show(host)).toEqual(['60'])
    act(() => button('Female').click())
    expect(show(host)).toEqual(['55'])
  })

  it('reads a logged meet back with a word for every attempt', () => {
    const { host } = renderSheet(() => meetDetailSheet(meet({ snatch: [{ w: 105, made: true }, { w: 110, made: false }] })))
    expect(host.textContent).toContain('Good lift')
    expect(host.textContent).toContain('No lift')
    expect(host.textContent).toContain('240')
  })
})
