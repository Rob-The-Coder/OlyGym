// @vitest-environment happy-dom
// Issue #71, through the UI: a body part chosen after an equipment type must not reset the
// equipment filter to "all equipment" when the new group still has work for it.
//
// The chips that used to carry this on the picker's surface are in the shared filter sheet now
// (components/LibraryFilters.jsx), so the reproduction drives the real path: open the sheet from
// the picker's bar, make the choices there, and check the sheet's own draft state holds. The
// helper underneath it (lib/library-filter.js) has the order and the dead-end guard, and its own
// test file.
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

const chipByText = (host, text) =>
  [...host.querySelectorAll('.chip')].find(b => b.textContent.trim() === text)
const isOn = el => el.className.split(/\s+/).includes('on')
const buttonBy = (host, re) => [...host.querySelectorAll('button')].find(b => re.test(b.textContent.trim()))

beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  useUI.setState({ sheets: [], toastMsg: '' })
  useStore.setState({ S: structuredClone(DEF), user: null })
  document.body.innerHTML = ''
})

afterEach(() => {
  act(() => { mounted.splice(0).forEach(root => root.unmount()) })
})

describe('the exercise picker', () => {
  it('offers the catalogue filters through the shared sheet rather than on its own surface', () => {
    exercisePicker(vi.fn())
    const host = renderTop()
    // The strips are gone: 205px of chrome before the first result was two unnamed chip rows and
    // a banner with its own toggle, and the Library had already stopped doing that.
    expect(chipByText(host, 'Accessory - Lower/Whole Body'), 'no body-part strip on the surface').toBeFalsy()
    expect(chipByText(host, 'Any equipment'), 'no equipment strip on the surface').toBeFalsy()
    // What replaced them: a count, and one chip to the sheet.
    expect(buttonBy(host, /^Filters/), 'the bar should offer the filters').toBeTruthy()
    expect(host.querySelector('.lib-bar, .lib-bar *') || host.textContent).toBeTruthy()
  })

  it('keeps the dumbbell filter when switching category', () => {
    exercisePicker(vi.fn())
    let host = renderTop()
    act(() => buttonBy(host, /^Filters/).click())

    host = renderTop()   // the filter sheet is the top of the stack now
    // Accessory - Lower/Whole Body → dumbbell
    act(() => chipByText(host, 'Accessory - Lower/Whole Body').click())
    const eqChip = chipByText(host, 'dumbbell')
    expect(eqChip, 'dumbbell should be offered under Accessory - Lower/Whole Body').toBeTruthy()
    act(() => eqChip.click())
    expect(isOn(chipByText(host, 'dumbbell'))).toBe(true)

    // switch to Accessory - Upper Body — dumbbell is still valid there, so it must remain selected
    act(() => chipByText(host, 'Accessory - Upper Body').click())
    expect(isOn(chipByText(host, 'Accessory - Upper Body'))).toBe(true)
    const eqAfter = chipByText(host, 'dumbbell')
    expect(eqAfter, 'dumbbell should still be present under Accessory - Upper Body').toBeTruthy()
    expect(isOn(eqAfter)).toBe(true)
    expect(isOn(chipByText(host, 'Any equipment'))).toBe(false)
  })
})
