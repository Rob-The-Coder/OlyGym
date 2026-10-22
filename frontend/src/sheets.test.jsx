// @vitest-environment happy-dom
import React, { act } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRoot } from 'react-dom/client'
import { EXDB } from './lib/exercises.js'
import { DEF, useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { exConfigSheet, complexConfigSheet } from './sheets.jsx'

const ex = EXDB.find(e => e.id === '0009')
const mounted = []

function type(el, value) {
  Object.getOwnPropertyDescriptor(el.constructor.prototype, 'value').set.call(el, value)
  el.dispatchEvent(new Event('input', { bubbles: true }))
}

function renderConfig(onSave = vi.fn()) {
  exConfigSheet(ex, { sets: 3, reps: 10, weight: 0, mode: 'reps', prog: 'linear' }, onSave)
  const sheet = useUI.getState().sheets.at(-1)
  const host = document.createElement('div')
  document.body.appendChild(host)
  const root = createRoot(host)
  mounted.push(root)
  act(() => root.render(sheet.render(() => useUI.getState().closeSheet(sheet.id))))
  const stepper = [...host.querySelectorAll('.stp-w')]
    .find(el => el.querySelector('.stp-l')?.textContent.startsWith('Step'))
  return { host, step: stepper.querySelector('input'), onSave }
}

describe('exercise configuration progression step', () => {
  beforeEach(() => {
    globalThis.IS_REACT_ACT_ENVIRONMENT = true
    useUI.setState({ sheets: [] })
    useStore.setState(s => ({ S: { ...s.S, unit: 'kg' } }))
    document.body.innerHTML = ''
  })

  afterEach(() => {
    act(() => { mounted.splice(0).forEach(root => root.unmount()) })
  })

  it('keeps the draft empty while clearing the default so a sub-one comma decimal can be entered', () => {
    const { step } = renderConfig()
    expect(step.value).toBe('2.5')

    act(() => { type(step, '') })
    expect(step.value).toBe('')

    act(() => { type(step, '0,5') })
    expect(step.value).toBe('0.5')
  })

  it('blocks saving without a positive progression step but accepts a positive decimal', () => {
    const config = renderConfig()
    const save = [...config.host.querySelectorAll('button')]
      .find(b => /^(save|add to routine)$/i.test(b.textContent.trim()))

    act(() => { type(config.step, '') })
    expect(config.step.value).toBe('')
    expect(config.step.getAttribute('aria-invalid')).toBe('true')
    expect(save.disabled).toBe(true)
    expect(config.host.textContent).toContain('Enter a positive step to use this progression rule.')

    act(() => { save.click() })
    expect(config.onSave).not.toHaveBeenCalled()
    expect(useUI.getState().sheets).toHaveLength(1)

    act(() => { type(config.step, '0') })
    expect(save.disabled).toBe(true)

    act(() => { type(config.step, '0,5') })
    expect(config.step.value).toBe('0.5')
    expect(config.step.getAttribute('aria-invalid')).not.toBe('true')
    expect(save.disabled).toBe(false)

    act(() => { save.click() })
    expect(config.onSave).toHaveBeenCalledWith(expect.objectContaining({ inc: 0.5 }))
    expect(useUI.getState().sheets).toHaveLength(0)
  })
})
// A planned ladder: the row in the config sheet, and the stepper-per-set sheet it opens. The
// exercise config keeps the ladder in its draft until Save; the complex config writes it to every
// movement at once, like its sets and load.
const mountTop = () => {
  const sheet = useUI.getState().sheets.at(-1)
  const host = document.createElement('div')
  document.body.appendChild(host)
  const root = createRoot(host)
  mounted.push(root)
  act(() => root.render(sheet.render(() => useUI.getState().closeSheet(sheet.id))))
  return host
}
const fieldOf = (host, label) => [...host.querySelectorAll('.stp-w')]
  .find(w => w.querySelector('.stp-l')?.textContent === label)?.querySelector('input')
const rowNamed = (host, title) => [...host.querySelectorAll('.lrow')].find(r => r.textContent.includes(title))
const saveButton = host => [...host.querySelectorAll('button')]
  .find(b => /^(save|add to routine)$/i.test(b.textContent.trim()))
const doneButton = host => [...host.querySelectorAll('button')].find(b => b.textContent.trim() === 'Done')

describe('exercise config — weights per set', () => {
  beforeEach(() => {
    globalThis.IS_REACT_ACT_ENVIRONMENT = true
    useUI.setState({ sheets: [] })
    useStore.setState(s => ({ S: { ...s.S, unit: 'kg' } }))
    document.body.innerHTML = ''
  })
  afterEach(() => { act(() => { mounted.splice(0).forEach(root => root.unmount()) }) })

  const openConfig = (cfg = {}, options = {}) => {
    const onSave = vi.fn()
    exConfigSheet(ex, { sets: 4, reps: 10, weight: 60, mode: 'reps', ...cfg }, onSave, null, null, null, options)
    return { host: mountTop(), onSave }
  }

  it('shows the row on a multi-set config and one stepper per set in its sheet', () => {
    const { host } = openConfig()
    const row = rowNamed(host, 'Weights per set')
    expect(row).toBeTruthy()
    expect(row.textContent).toContain('60 kg')

    act(() => row.click())
    const sheet = mountTop()
    expect(sheet.querySelector('h3').textContent).toBe('Weights per set')
    expect([...sheet.querySelectorAll('.stp-l')].map(el => el.textContent))
      .toEqual(['Set 1', 'Set 2', 'Set 3', 'Set 4'])
    expect([...sheet.querySelectorAll('.stp-w input')].map(el => el.value)).toEqual(['60', '60', '60', '60'])

    // The visible "Set N" caption is a span, not a label, so the field and its ± buttons carry
    // the set name themselves — four identical "Decrease / 60 / Increase" groups say nothing.
    const first = sheet.querySelector('.stp-w')
    expect(first.querySelector('input').getAttribute('aria-label')).toBe('Set 1')
    expect(first.querySelector('button[aria-label="Decrease Set 1"]')).toBeTruthy()
    expect(first.querySelector('button[aria-label="Increase Set 1"]')).toBeTruthy()
    // …and the unit every other weight control shows.
    expect(first.querySelector('.val i').textContent).toBe('kg')
  })

  it('saves the ladder the sheet set, with the first load as the weight', () => {
    const { host, onSave } = openConfig()
    act(() => rowNamed(host, 'Weights per set').click())
    const sheet = mountTop()
    act(() => type(fieldOf(sheet, 'Set 3'), '65'))
    act(() => type(fieldOf(sheet, 'Set 4'), '65'))
    act(() => doneButton(sheet).click())
    expect(useUI.getState().sheets).toHaveLength(1)

    act(() => saveButton(host).click())
    expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ sets: 4, weight: 60, setWeights: [60, 60, 65, 65] }))
  })

  it('resizes the ladder when the Sets count changes, cutting the tail', () => {
    const { host, onSave } = openConfig()
    act(() => rowNamed(host, 'Weights per set').click())
    const sheet = mountTop()
    act(() => type(fieldOf(sheet, 'Set 3'), '65'))
    act(() => type(fieldOf(sheet, 'Set 4'), '65'))
    act(() => doneButton(sheet).click())

    act(() => type(fieldOf(host, 'Sets'), '3'))
    act(() => saveButton(host).click())
    expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ sets: 3, setWeights: [60, 60, 65] }))
  })

  it('clears the ladder when the uniform Weight is edited', () => {
    const { host, onSave } = openConfig({ setWeights: [60, 60, 65, 65] })
    expect(rowNamed(host, 'Weights per set')).toBeTruthy()
    act(() => type(fieldOf(host, 'Weight (kg)'), '70'))
    act(() => saveButton(host).click())
    const payload = onSave.mock.calls[0][0]
    expect(payload.weight).toBe(70)
    expect(payload.setWeights).toBeUndefined()
  })

  // A running session's options sheet edits one weight per set row, so the row is hidden there —
  // but the draft still carries, resizes and clears the ladder it was opened with.
  it('hides the row in a session and keeps the ladder it was given', () => {
    const { host, onSave } = openConfig({ setWeights: [60, 60, 65, 65] }, { hideSetWeights: true })
    expect(rowNamed(host, 'Weights per set')).toBeUndefined()
    act(() => saveButton(host).click())
    expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ weight: 60, setWeights: [60, 60, 65, 65] }))
  })

  it('resizes a hidden ladder when Sets changes, and drops it when Weight does', () => {
    let config = openConfig({ setWeights: [60, 60, 65, 65] }, { hideSetWeights: true })
    act(() => type(fieldOf(config.host, 'Sets'), '3'))
    act(() => saveButton(config.host).click())
    expect(config.onSave).toHaveBeenCalledWith(expect.objectContaining({ sets: 3, setWeights: [60, 60, 65] }))

    config = openConfig({ setWeights: [60, 60, 65, 65] }, { hideSetWeights: true })
    act(() => type(fieldOf(config.host, 'Weight (kg)'), '70'))
    act(() => saveButton(config.host).click())
    expect(config.onSave.mock.calls[0][0].setWeights).toBeUndefined()
  })
})

describe('complex config — weights per set', () => {
  beforeEach(() => {
    globalThis.IS_REACT_ACT_ENVIRONMENT = true
    useUI.setState({ sheets: [] })
    useStore.setState({
      S: {
        ...structuredClone(DEF),
        unit: 'kg',
        weeks: [{ id: 'w1', startIso: '2026-02-09', name: 'Block 1', days: [{ dow: 1, name: 'Push', ex: [
          { id: '0025', sets: 4, reps: 3, weight: 60, mode: 'reps', sg: 'g1' },
          { id: '0031', sets: 4, reps: 1, weight: 60, mode: 'reps', sg: 'g1' },
        ] }] }],
      },
      user: null,
    })
    document.body.innerHTML = ''
  })
  afterEach(() => { act(() => { mounted.splice(0).forEach(root => root.unmount()) }) })

  const members = () => useStore.getState().S.weeks[0].days[0].ex
  const open = () => { complexConfigSheet({ weekId: 'w1', dayIndex: 0 }, 0); return mountTop() }

  it('writes the ladder to every movement of the complex', () => {
    const host = open()
    const row = rowNamed(host, 'Weights per set')
    expect(row).toBeTruthy()
    act(() => row.click())
    const sheet = mountTop()
    act(() => type(fieldOf(sheet, 'Set 3'), '65'))
    act(() => type(fieldOf(sheet, 'Set 4'), '65'))
    act(() => doneButton(sheet).click())

    expect(members().map(e => e.setWeights)).toEqual([[60, 60, 65, 65], [60, 60, 65, 65]])
    expect(members().map(e => e.weight)).toEqual([60, 60])
  })

  it('leaves the members’ ladders equal and normalized when Sets or Weight change', () => {
    const host = open()
    act(() => rowNamed(host, 'Weights per set').click())
    const sheet = mountTop()
    act(() => type(fieldOf(sheet, 'Set 3'), '65'))
    act(() => type(fieldOf(sheet, 'Set 4'), '65'))
    act(() => doneButton(sheet).click())

    act(() => type(fieldOf(host, 'Sets'), '3'))
    expect(members().map(e => e.setWeights)).toEqual([[60, 60, 65], [60, 60, 65]])

    act(() => type(fieldOf(host, 'Weight (kg)'), '70'))
    expect(members().map(e => e.setWeights)).toEqual([undefined, undefined])
    expect(members().map(e => e.weight)).toEqual([70, 70])
  })

  // Pairing two configs does not unify their set counts, so a ladder written on the card has to
  // be measured against each movement's own sets — a 4-set line must print four loads.
  it('writes the ladder against each movement’s own set count', () => {
    useStore.setState(s => ({ S: { ...s.S, weeks: [{ id: 'w1', startIso: '2026-02-09', name: 'Block 1', days: [{ dow: 1, name: 'Push', ex: [
      { id: '0025', sets: 3, reps: 5, weight: 60, mode: 'reps', sg: 'g1' },
      { id: '0031', sets: 4, reps: 3, weight: 60, mode: 'reps', sg: 'g1' },
    ] }] }] } }))
    const host = open()
    act(() => rowNamed(host, 'Weights per set').click())
    const sheet = mountTop()
    // the card is the head's prescription: three sets, so three steppers
    expect([...sheet.querySelectorAll('.stp-l')].map(el => el.textContent)).toEqual(['Set 1', 'Set 2', 'Set 3'])
    act(() => type(fieldOf(sheet, 'Set 3'), '65'))
    act(() => doneButton(sheet).click())

    expect(members()[0].setWeights).toEqual([60, 60, 65])
    expect(members()[1].setWeights).toEqual([60, 60, 65, 65])
  })

  // A movement's own row edits its weight alone, which clears its ladder. When the card's ladder
  // reaches it again through the Sets stepper, its first load comes along: `weight` and
  // `setWeights[0]` never disagree, so every older reader of `weight` sees the same plan.
  it('writes the ladder’s first load as the movement’s weight', () => {
    useStore.setState(s => ({ S: { ...s.S, weeks: [{ id: 'w1', startIso: '2026-02-09', name: 'Block 1', days: [{ dow: 1, name: 'Push', ex: [
      { id: '0025', sets: 4, reps: 3, weight: 60, setWeights: [60, 60, 65, 65], mode: 'reps', sg: 'g1' },
      { id: '0031', sets: 4, reps: 1, weight: 70, mode: 'reps', sg: 'g1' },
    ] }] }] } }))
    const host = open()
    act(() => type(fieldOf(host, 'Sets'), '5'))

    expect(members().map(e => e.setWeights)).toEqual([[60, 60, 65, 65, 65], [60, 60, 65, 65, 65]])
    expect(members().map(e => e.weight)).toEqual([60, 60])
  })
})

