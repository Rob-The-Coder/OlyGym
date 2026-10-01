// @vitest-environment happy-dom
// The chooser is where a starter plan can quietly do the wrong thing: pile a second week on top
// of the current one without asking, ask when there was nothing to overwrite, or apply a plan the
// user cancelled.
import React, { act } from 'react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createRoot } from 'react-dom/client'
import { useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { loadStarterPlan, starterPlanSheet } from './sheets.jsx'
import { isoOf, startOfWeek, todayISO } from './lib/format.js'

const mounted = []

const S = () => useStore.getState().S
// The starter plan appends a dated week; its days are the ones the plan names.
const nameOn = dow => S().weeks.at(-1)?.days.find(d => d.dow === dow)?.name
// A current week whose days are occupied, so the chooser has something to collide with.
const monday = () => isoOf(startOfWeek(todayISO(), 1))
const curWeek = dows => [{
  id: 'cur', startIso: monday(), name: '',
  days: dows.map(dow => ({ dow, name: 'Mine', ex: [{ id: 'wl58', sets: 1, reps: 1 }] })),
}]

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

const rowFor = (host, name) => [...host.querySelectorAll('.item')].find(el => el.querySelector('.tt')?.textContent === name)
const buttonFor = (host, label) => [...host.querySelectorAll('button')].find(b => b.textContent === label)

// Opens the chooser and taps one of its rows.
function choose(name) {
  starterPlanSheet()
  const host = renderTop()
  act(() => { rowFor(host, name).click() })
}

beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  useUI.setState({ sheets: [], toastMsg: '' })
  useStore.setState(s => ({
    S: { ...s.S, routines: [{ id: 'mine', name: 'My routine', emoji: 'star', ex: [] }], week: {}, weeks: [], active: null },
  }))
  document.body.innerHTML = ''
})

afterEach(() => {
  act(() => { mounted.splice(0).forEach(root => root.unmount()) })
})

describe('starter plan chooser', () => {
  it('lists every plan with its day count', () => {
    starterPlanSheet()
    const host = renderTop()
    expect([...host.querySelectorAll('.item .tt')].map(el => el.textContent))
      .toEqual(['Snatch / Clean & Jerk / Squat', 'Technique / Strength', 'Full Body', '5×5'])
    expect(rowFor(host, 'Technique / Strength').querySelector('.ss').textContent).toContain('4 days per week')
  })

  it('loads straight away when the plan’s weekdays are free, without asking', () => {
    useStore.setState(s => ({ S: { ...s.S, weeks: curWeek([0, 6]) } }))
    choose('Technique / Strength')

    expect(useUI.getState().sheets).toHaveLength(0)   // no confirmation was raised
    expect(nameOn(1)).toBe('Technique A')
    expect(nameOn(2)).toBe('Strength A')
    expect(nameOn(4)).toBe('Technique B')
    expect(nameOn(5)).toBe('Strength B')
    expect(S().weeks).toHaveLength(2)                 // the current week is untouched
    expect(S().weeks[0].days.map(d => d.dow)).toEqual([0, 6])
    expect(S().routines[0].name).toBe('My routine')   // and nothing is deleted
    expect(useUI.getState().toastMsg).toBe('Technique / Strength loaded')
  })

  it('asks first when a weekday the plan wants is already taken', () => {
    useStore.setState(s => ({ S: { ...s.S, weeks: curWeek([3]) } }))
    choose('Full Body')

    const confirm = renderTop()
    expect(confirm.querySelector('h3').textContent).toBe('Load Full Body?')
    expect(confirm.textContent).toContain('Monday, Wednesday and Friday')
    expect(S().weeks).toHaveLength(1)                      // nothing applied yet
    expect(S().routines).toHaveLength(1)

    act(() => { buttonFor(confirm, 'Load plan').click() })
    expect(nameOn(1)).toBe('Full Body A')
    expect(nameOn(3)).toBe('Full Body B')
    expect(nameOn(5)).toBe('Full Body C')
  })

  it('leaves everything alone when the confirmation is cancelled', () => {
    useStore.setState(s => ({ S: { ...s.S, weeks: curWeek([1]) } }))
    const before = structuredClone(S())
    choose('5×5')

    const confirm = renderTop()
    act(() => { buttonFor(confirm, 'Cancel').click() })
    expect(S()).toEqual(before)
    expect(useUI.getState().toastMsg).toBe('')
  })

  it('does not ask when only weekdays outside the plan are occupied', () => {
    useStore.setState(s => ({ S: { ...s.S, weeks: curWeek([2, 4]) } }))   // Tue + Thu
    choose('5×5')                                                    // wants Mon/Wed/Fri
    expect(useUI.getState().sheets).toHaveLength(0)
    expect(nameOn(1)).toBe('5×5 A')
  })
})

describe('loadStarterPlan', () => {
  it('appends an independent week each time the same plan is loaded', () => {
    loadStarterPlan('ppl')
    const first = S().weeks[0]
    loadStarterPlan('ppl')
    expect(S().weeks).toHaveLength(2)
    expect(S().weeks[0].id).not.toBe(S().weeks[1].id)
    expect(S().weeks[0].days[0].ex[0]).not.toBe(S().weeks[1].days[0].ex[0])
    // and the static definition survives a caller mutating what it got back
    first.days[0].ex[0].sets = 99
    loadStarterPlan('ppl')
    expect(S().weeks[2].days[0].ex[0].sets).toBe(5)
  })

  it('refuses a plan it does not know and changes nothing', () => {
    const before = structuredClone(S())
    expect(loadStarterPlan('nope')).toBe(false)
    expect(loadStarterPlan(undefined)).toBe(false)
    expect(S()).toEqual(before)
    expect(useUI.getState().toastMsg).toBe('')
  })
})
