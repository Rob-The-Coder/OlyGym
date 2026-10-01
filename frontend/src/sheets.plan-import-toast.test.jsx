// @vitest-environment happy-dom
// Importing a one-week plan file toasted "Added 1 routines to your plan" (QA copy): the sheet's
// summary line already picked the singular, the toast after the merge did not. The container is
// the dated weeks now, so the toast counts weeks.
import React, { act } from 'react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createRoot } from 'react-dom/client'
import { useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { planImportSheet } from './sheets.jsx'
import { parsePlan } from './lib/plan-share.js'
import { EXDB } from './lib/exercises.js'

const clone = v => JSON.parse(JSON.stringify(v))
const ids = EXDB.filter(e => e.bp !== 'cardio').slice(0, 2).map(e => e.id)
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
const buttonFor = (host, label) => [...host.querySelectorAll('button')].find(b => b.textContent === label)
const bundleOf = weeks => parsePlan({ opengym_plan: 2, unit: 'kg', weeks, customEx: [] }, 'kg')
const week = (id, name) => ({ id, startIso: '2026-03-02', name, days: [{ dow: 1, name, ex: [{ id: ids[0], sets: 3, reps: 8, weight: 40 }] }] })

beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  useUI.setState({ sheets: [], toastMsg: '' })
  document.body.innerHTML = ''
  const S = clone(useStore.getState().S)
  S.routines = []
  S.week = {}
  S.weeks = []
  useStore.setState({ S, user: null })
})
afterEach(() => { act(() => { mounted.splice(0).forEach(r => r.unmount()) }) })

describe('Plan import sheet — the toast counts what it added', () => {
  it('says "1 week" for a single week', () => {
    planImportSheet(bundleOf([week('w1', 'Push')]))
    const host = renderTop()
    act(() => { buttonFor(host, 'Add to my plan').click() })
    expect(useStore.getState().S.weeks).toHaveLength(1)
    expect(useUI.getState().toastMsg).toBe('Added 1 week to your plan')
  })

  it('keeps the plural for several', () => {
    planImportSheet(bundleOf([week('w1', 'Push'), week('w2', 'Pull')]))
    const host = renderTop()
    act(() => { buttonFor(host, 'Add to my plan').click() })
    expect(useStore.getState().S.weeks).toHaveLength(2)
    expect(useUI.getState().toastMsg).toBe('Added 2 weeks to your plan')
  })

  it('shows the week, day and exercise counts before anything is written', () => {
    planImportSheet(bundleOf([week('w1', 'Push')]))
    const host = renderTop()
    expect(host.textContent).toContain('1 week')
    expect(host.textContent).toContain('1 day')
    expect(host.textContent).toContain('1 exercise')
  })
})
