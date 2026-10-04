// @vitest-environment happy-dom
import React, { act } from 'react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createRoot } from 'react-dom/client'
import { DEF, useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { effortPickerSheet } from './sheets.jsx'

// The picker is one tap per preset, with a line above it saying what the numbers mean. That line was
// drawn with .helpbtn, which index.css builds for an icon alone — line-height 0 and negative margins
// tuned to sit inside a row — so the words landed on top of their own glyph. .fieldhelp is the same
// affordance for a line that has to name itself, and the config sheet has used it since it was added.

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

beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  useUI.setState({ sheets: [], toastMsg: '' })
  useStore.setState({ S: structuredClone(DEF), user: null })
  document.body.innerHTML = ''
})
afterEach(() => { act(() => { mounted.splice(0).forEach(r => r.unmount()) }) })

describe('the effort picker', () => {
  it('names its help rather than drawing the words on an icon-only button', () => {
    effortPickerSheet('rir', 2, () => {})
    const host = renderTop()
    const help = host.querySelector('.fieldhelp')
    expect(help).toBeTruthy()
    expect(help.textContent).toContain('What are RIR and RPE?')
    expect(host.querySelector('.helpbtn')).toBeNull()
  })

  // Six presets (0, 0.5, 1, 2, 3, 4+) and the exact field under them.
  it('offers one tap per preset and the exact field, with the current value ticked', () => {
    effortPickerSheet('rir', 2, () => {})
    const host = renderTop()
    expect(host.querySelectorAll('.effpick .menu-item')).toHaveLength(7)
    expect(host.querySelector('.effpick .menu-item.on')).toBeTruthy()
  })

  it('reports the preset it was tapped on', () => {
    const picked = []
    effortPickerSheet('rir', null, v => picked.push(v))
    const host = renderTop()
    const items = [...host.querySelectorAll('.effpick .menu-item')]
    act(() => items[0].click())
    expect(picked).toEqual([0])
  })
})
