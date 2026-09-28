// @vitest-environment happy-dom
// Issue #71: choosing a body part after picking an equipment type used to reset the equipment
// filter to "all equipment", even when the new muscle group still had exercises for it. The
// per-body-part chips no longer clear the equipment selection; the eqOn fallback only drops it
// for the current view when the new body part has nothing under that equipment.
import React, { act } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRoot } from 'react-dom/client'
import { DEF, useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { exercisePicker } from './sheets.jsx'

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

// The two chip strips render in order: body parts first, then equipment. Grab a chip by its
// visible label out of the whole picker.
const chipByText = (host, text) =>
  [...host.querySelectorAll('.chips .chip')].find(b => b.textContent.trim() === text)
const isOn = el => el.className.split(/\s+/).includes('on')

beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  useUI.setState({ sheets: [], toastMsg: '' })
  useStore.setState({ S: structuredClone(DEF), user: null })
  document.body.innerHTML = ''
})

afterEach(() => {
  act(() => { mounted.splice(0).forEach(root => root.unmount()) })
})

describe('exercise picker equipment filter', () => {
  // The exact reproduction from issue #71, on the OlyGym catalogue's families: a category with
  // dumbbell work + "dumbbell", then a switch to another category that also has dumbbell work, so
  // the equipment filter must stay selected across the switch.
  it('keeps the dumbbell filter when switching category', () => {
    exercisePicker(vi.fn())
    const host = renderTop()

    // Accessory - Lower/Whole Body → dumbbell
    act(() => chipByText(host, 'Accessory - Lower/Whole Body').click())
    const eqChip = chipByText(host, 'dumbbell')
    expect(eqChip, 'dumbbell chip should be offered under Accessory - Lower/Whole Body').toBeTruthy()
    act(() => eqChip.click())
    expect(isOn(chipByText(host, 'dumbbell'))).toBe(true)

    // switch to Accessory - Upper Body — dumbbell is still valid there, so it must remain selected
    act(() => chipByText(host, 'Accessory - Upper Body').click())
    expect(isOn(chipByText(host, 'Accessory - Upper Body'))).toBe(true)
    const eqAfter = chipByText(host, 'dumbbell')
    expect(eqAfter, 'dumbbell chip should still be present under Accessory - Upper Body').toBeTruthy()
    expect(isOn(eqAfter)).toBe(true)
    expect(isOn(chipByText(host, 'Any equipment'))).toBe(false)
  })
})
