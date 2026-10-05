// @vitest-environment happy-dom
import React, { act } from 'react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createRoot } from 'react-dom/client'
import { ALL_EQUIPMENT } from './lib/equipment.js'
import { DEF, useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { equipmentProfileSheet } from './sheets.jsx'

// Building a profile is naming it and ticking what you have. The checklist is the body of the sheet,
// so it is the thing that has to be visible: as a .chips strip — sideways, scrollbar hidden — nine of
// the thirteen kinds sat off-screen with nothing on screen to say so.

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
const type = (el, value) => {
  Object.getOwnPropertyDescriptor(el.constructor.prototype, 'value').set.call(el, value)
  el.dispatchEvent(new Event('input', { bubbles: true }))
}
const button = (host, text) => [...host.querySelectorAll('button')].find(b => b.textContent.trim() === text)
const chip = (host, text) => [...host.querySelectorAll('.chip')].find(c => c.textContent.trim() === text)

beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  useUI.setState({ sheets: [], toastMsg: '' })
  useStore.setState({ S: structuredClone(DEF), user: null })
  document.body.innerHTML = ''
})
afterEach(() => { act(() => { mounted.splice(0).forEach(r => r.unmount()) }) })

describe('the equipment profile sheet', () => {
  it('shows every kind of equipment at once, in a chip set that wraps', () => {
    equipmentProfileSheet(null)
    const host = renderTop()
    const set = host.querySelector('.chips')
    expect(set.className).toContain('wrap')
    expect(set.querySelectorAll('.chip')).toHaveLength(ALL_EQUIPMENT.length)
    expect(ALL_EQUIPMENT.length).toBeGreaterThan(8)
  })

  it('ticks and unticks a kind of equipment', () => {
    equipmentProfileSheet(null)
    const host = renderTop()
    act(() => chip(host, 'barbell').click())
    expect(chip(host, 'barbell').className).toContain('on')
    act(() => chip(host, 'barbell').click())
    expect(chip(host, 'barbell').className).not.toContain('on')
  })

  // It returned in silence: Save looked dead on an empty name and nothing on screen said why.
  it('says the name is missing rather than doing nothing', () => {
    equipmentProfileSheet(null)
    const host = renderTop()
    act(() => button(host, 'Save').click())
    expect(useUI.getState().toastMsg).toBe('Give it a name')
    expect(useStore.getState().S.equipProfiles).toHaveLength(0)
    expect(useUI.getState().sheets).toHaveLength(1)
  })

  it('saves the name and what is ticked', () => {
    equipmentProfileSheet(null)
    const host = renderTop()
    act(() => type(host.querySelector('input.field'), 'Home'))
    act(() => chip(host, 'barbell').click())
    act(() => chip(host, 'body weight').click())
    act(() => button(host, 'Save').click())
    const p = useStore.getState().S.equipProfiles
    expect(p).toHaveLength(1)
    expect(p[0].name).toBe('Home')
    expect(p[0].equipment).toEqual(['barbell', 'body weight'])
  })
})
