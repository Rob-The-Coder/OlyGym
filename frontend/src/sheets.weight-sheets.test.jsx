// @vitest-environment happy-dom
import React, { act } from 'react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createRoot } from 'react-dom/client'
import { useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { bwSheet, goalSheet } from './sheets.jsx'
import { isoOf } from './lib/format.js'

// The two sheets about body weight. Both asked for a number with nothing to measure it against —
// the curve Home and Stats already draw was nowhere in the sheet that feeds it — and the log
// sheet's rows were hand-rolled markup with a literal "delete" label. These pin what would go
// quiet if the markup drifted back.

const mounted = []
const open = fn => { act(() => fn()); return useUI.getState().sheets.at(-1) }
function render(sheet) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  const root = createRoot(host)
  mounted.push(root)
  act(() => root.render(sheet.render(() => useUI.getState().closeSheet(sheet.id))))
  return host
}
const daysAgo = n => { const d = new Date(); d.setDate(d.getDate() - n); return isoOf(d) }
const TODAY = daysAgo(0)

beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  useUI.setState({ sheets: [], toastMsg: '' })
  useStore.setState(s => ({
    S: {
      ...s.S, unit: 'kg', targetW: 77, active: null,
      bodyweight: [
        { d: daysAgo(10), w: 80.5, t: Date.parse(daysAgo(10) + 'T07:00:00Z') },
        { d: daysAgo(6), w: 80.1, t: Date.parse(daysAgo(6) + 'T07:00:00Z') },
        { d: daysAgo(3), w: 79.7, t: Date.parse(daysAgo(3) + 'T07:00:00Z') },
        { d: TODAY, w: 79.2, t: Date.parse(TODAY + 'T07:00:00Z') },
      ],
    },
  }))
  document.body.innerHTML = ''
})

afterEach(() => { act(() => { mounted.splice(0).forEach(root => root.unmount()) }) })

describe('log body weight', () => {
  it('draws the curve the sheet feeds, with the goal line on it', () => {
    const host = render(open(() => bwSheet()))
    expect(host.querySelector('.chart svg')).toBeTruthy()
  })

  it('says what moved since the previous weigh-in instead of leaving the number bare', () => {
    const host = render(open(() => bwSheet()))
    const line = [...host.querySelectorAll('.row')].find(r => /since /.test(r.textContent))
    expect(line).toBeTruthy()
    expect(line.textContent).toMatch(/0\.5 kg/)
  })

  it('has no ± chips left — the stepper and the slider already cover it', () => {
    const host = render(open(() => bwSheet()))
    expect(host.querySelectorAll('.chip')).toHaveLength(0)
  })

  it('lists recent weigh-ins as the app’s own rows, with a delete that names the date', () => {
    const host = render(open(() => bwSheet()))
    const rows = [...host.querySelectorAll('.lrow')]
    expect(rows).toHaveLength(3)
    const del = rows[0].querySelector('button.bw-del')
    expect(del).toBeTruthy()
    expect(del.getAttribute('aria-label')).toMatch(/^Remove weigh-in from /)
    expect(del.getAttribute('aria-label')).not.toBe('delete')
  })

  it('says so when a weigh-in is removed rather than deleting it silently', () => {
    const host = render(open(() => bwSheet()))
    act(() => host.querySelector('button.bw-del').click())
    expect(useStore.getState().S.bodyweight).toHaveLength(3)
    expect(useUI.getState().toastMsg).toBe('Weigh-in removed')
  })
})

describe('target weight', () => {
  it('measures the goal against today instead of asking for a number in a vacuum', () => {
    const host = render(open(() => goalSheet()))
    const tiles = [...host.querySelectorAll('.tile')]
    expect(tiles).toHaveLength(2)
    expect(tiles[0].textContent).toMatch(/79\.2/)
    expect(tiles[1].textContent).toMatch(/77/)
    expect(tiles[1].textContent).toMatch(/2\.2 kg to lose/)
  })

  it('keeps the explanation behind the ⓘ on the tile it is about', () => {
    const host = render(open(() => goalSheet()))
    expect(host.textContent).not.toMatch(/drawn as a line through the weight charts/)
    act(() => host.querySelector('.helpbtn').click())
    const sheets = useUI.getState().sheets
    expect(sheets).toHaveLength(2)
    const help = document.createElement('div')
    document.body.appendChild(help)
    const root = createRoot(help)
    mounted.push(root)
    act(() => root.render(sheets.at(-1).render(() => {})))
    expect(help.textContent).toMatch(/drawn as a line through the weight charts/)
  })

  it('offers Remove goal as a ⋯, not as a button the width of the sheet', () => {
    const host = render(open(() => goalSheet()))
    expect([...host.querySelectorAll('button')].some(b => /Remove goal/.test(b.textContent))).toBe(false)
    expect(host.querySelector('.ab-ico')).toBeTruthy()
  })

  it('does not congratulate you for a goal you have not set yet', () => {
    useStore.setState(s => ({ S: { ...s.S, targetW: null } }))
    const host = render(open(() => goalSheet()))
    const tiles = [...host.querySelectorAll('.tile')]
    expect(tiles[1].textContent).toMatch(/same as today/)
    expect(tiles[1].textContent).not.toMatch(/reached/)
  })
})
