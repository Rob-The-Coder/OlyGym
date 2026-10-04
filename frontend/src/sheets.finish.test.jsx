// @vitest-environment happy-dom
// The two dialogs that end a session. Reaching them by hand needs a completed workout, which is
// expensive to drive, so this checks what can be checked: the render for the prompt, and the
// source for the summary — which is the same idiom m3.empty-icon.test.js uses on the stylesheet.
//
// What it is guarding: both were centred blocks with a bare 44px glyph (centred on one axis
// only, the shape that put the empty state's icon at the top of its circle) and hand-built
// buttons instead of the dialog the confirm already used. The summary also forced three tiles to
// 1.1rem inline and a fourth to 20px.
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import React, { act } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRoot } from 'react-dom/client'
import { DEF, useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { workoutCompleteSheet } from './sheets.jsx'

const src = readFileSync(resolve(process.cwd(), 'src/sheets.jsx'), 'utf8')
const between = (a, b) => src.slice(src.indexOf(a), src.indexOf(b))

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

afterEach(() => {
  act(() => { mounted.splice(0).forEach(root => root.unmount()) })
})

describe('“That’s the whole workout!”', () => {
  it('is the shared dialog, not a hand-built centred block', () => {
    workoutCompleteSheet()
    const host = renderTop()
    expect(host.querySelector('.dlg'), 'the M3 dialog the confirm uses').toBeTruthy()
    expect(host.querySelector('.dlg-ico.ok'), 'a non-error icon circle').toBeTruthy()
    expect(host.querySelector('h3.ctr')).toBeTruthy()
    expect(host.querySelector('.dlg-body.ctr')).toBeTruthy()
    // Two quiet text actions. It used to be two full-width buttons, the second of which looked
    // as important as the first.
    expect(host.querySelectorAll('.dlg-acts .dlg-btn')).toHaveLength(2)
    expect(host.querySelectorAll('.btn')).toHaveLength(0)
  })
})

describe('“Workout complete!”', () => {
  const finish = () => between('function FinishSummary', 'export function finishWorkout')

  it('is the same dialog, with no inline type sizes', () => {
    expect(finish()).toContain('className="dlg"')
    expect(finish()).toContain('dlg-ico ok')
    expect(finish()).not.toMatch(/fontSize:\s*'1\.1rem'/)
    expect(finish()).not.toMatch(/fontSize:\s*20\b/)
    expect(finish()).not.toMatch(/fontSize:\s*44/)
  })

  it('puts the section headings and the records on the roles', () => {
    // h4.sec was the old heading weight; the records were small-accent run-on lines.
    expect(finish()).not.toContain('h4 className="sec"')
    expect(finish()).toContain('className="sech"')
    expect(finish()).toContain('className="chip on nocap capitalize"')
  })

  it('gives all four tiles the same value role', () => {
    // The rules live in m3.components.css, because a dialog tile is a third of the width a
    // screen tile gets and neither the tile's own 30px nor an inline 1.1rem fits in it.
    const css = readFileSync(resolve(process.cwd(), 'src/m3.components.css'), 'utf8')
    expect(css).toMatch(/\.sheet \.tile \.v\{[^}]*font-size:18px/)
    expect(css).toMatch(/\.dlg-ico\.ok\{[^}]*--m3-primary-container/)
    expect(finish().match(/className="tile"/g)).toHaveLength(4)
    expect(finish().match(/className="v"/g)).toHaveLength(4)
  })
})
