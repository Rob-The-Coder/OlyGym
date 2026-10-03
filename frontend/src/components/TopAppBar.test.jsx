// @vitest-environment happy-dom
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import TopAppBar from './TopAppBar.jsx'

globalThis.IS_REACT_ACT_ENVIRONMENT = true

// happy-dom has no layout, so every rect is 0x0 and the bar — which decides whether to pin by
// comparing two of them — could never pin. Handing the two elements the geometry a scrolled page
// would produce is the entire input to that decision.
let origRect, bigBottom, rowBottom
beforeEach(() => {
  origRect = Element.prototype.getBoundingClientRect
  bigBottom = 200   // the heading is still on screen
  rowBottom = 64
  Element.prototype.getBoundingClientRect = function () {
    const zero = { top: 0, left: 0, right: 0, width: 0, height: 0 }
    if (this.classList?.contains('ab-big')) return { ...zero, bottom: bigBottom }
    if (this.classList?.contains('ab-row')) return { ...zero, bottom: rowBottom }
    return origRect.call(this)
  }
})

let host, root
const mount = ui => {
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
  act(() => { root.render(ui) })
  return host
}
afterEach(() => {
  if (root) act(() => root.unmount())
  host?.remove()
  host = root = null
  Element.prototype.getBoundingClientRect = origRect
})

const BAR = { title: 'Exercises', subtitle: '624 exercises with video demos' }

describe('TopAppBar', () => {
  it('draws the title as the page heading, with the supporting line under it', () => {
    const h = mount(<TopAppBar {...BAR} />)
    expect(h.querySelector('h1').textContent).toBe('Exercises')
    expect(h.querySelector('.ab-big .ab-sub').textContent).toBe('624 exercises with video demos')
    expect(h.querySelector('.ab-big').contains(h.querySelector('h1'))).toBe(true)
  })

  // The row and the heading are siblings, and the heading is the reason the row can stay 64dp:
  // it scrolls underneath instead of the bar shrinking, which is what would move the page.
  it('puts the row before the heading, and hides the small title until it is needed', () => {
    const h = mount(<TopAppBar {...BAR} />)
    const row = h.querySelector('.ab-row')
    expect(row.classList.contains('pinned')).toBe(false)
    expect(h.querySelector('.ab-t')).toBeNull()
    // a tab screen has no nav icon, and the small title then sits on the 16dp content edge
    expect(row.classList.contains('nolead')).toBe(true)
    expect(row.compareDocumentPosition(h.querySelector('.ab-big')) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('pins the row and swaps in the small title once the heading has gone behind it', () => {
    bigBottom = 10   // 10 < 64 - 4
    const h = mount(<TopAppBar {...BAR} />)
    expect(h.querySelector('.ab-row').classList.contains('pinned')).toBe(true)
    expect(h.querySelector('.ab-t').textContent).toBe('Exercises')
    // the heading is still in the DOM — it is the scroll target, and it comes back on scroll up
    expect(h.querySelector('h1').textContent).toBe('Exercises')
  })

  it('keeps the nav icon and the actions in the row, not in the title block', () => {
    const h = mount(<TopAppBar {...BAR}
      leading={<button className="ab-ico" aria-label="Back" />}
      actions={<button className="ab-ico" aria-label="Share" />} />)
    expect(h.querySelector('.ab-row').classList.contains('nolead')).toBe(false)
    expect(h.querySelector('.ab-row > .ab-ico').getAttribute('aria-label')).toBe('Back')
    expect(h.querySelector('.ab-acts .ab-ico').getAttribute('aria-label')).toBe('Share')
    expect(h.querySelector('.ab-big .ab-ico')).toBeNull()
  })

  // WeekEdit edits the week name in place, so the title is a field. An input cannot be copied into
  // the small title, and must not be rendered twice — smallTitle supplies the text instead.
  it('takes the small title separately when the title is a node', () => {
    bigBottom = 10
    const h = mount(<TopAppBar title={<input className="ab-title" defaultValue="Deload" />} smallTitle="Deload" />)
    expect(h.querySelector('.ab-big input')).not.toBeNull()
    expect(h.querySelector('.ab-t').textContent).toBe('Deload')
    expect(h.querySelectorAll('input')).toHaveLength(1)
    expect(h.querySelector('h1')).toBeNull()
    expect(h.querySelector('.ab-sub')).toBeNull()
  })

  it('shows no small title at all when a node title has no smallTitle', () => {
    bigBottom = 10
    const h = mount(<TopAppBar title={<input className="ab-title" />} />)
    expect(h.querySelector('.ab-t')).toBeNull()
  })
})

// The layout contract the behaviour above depends on: the row is the sticky element, the heading
// is not, and the hairline belongs to the pinned state only.
describe('the top app bar stylesheet', () => {
  const css = readFileSync(resolve(process.cwd(), 'src/m3.components.css'), 'utf8')
  const rule = sel => css.match(new RegExp('^' + sel.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\{([^}]*)\\}', 'm'))?.[1]

  it('sticks the row, not the heading', () => {
    const row = rule('.ab-row')
    expect(row).toContain('position:sticky')
    expect(row).toContain('top:0')
    expect(rule('.ab-big') || '').not.toContain('sticky')
  })

  it('draws the hairline only when pinned', () => {
    expect(css).toContain('.ab-row.pinned::after{')
  })

  it('gives the bar the 48dp icon button M3 asks for', () => {
    expect(rule('.ab-ico')).toContain('width:48px')
    expect(rule('.ab-ico')).toContain('height:48px')
  })

  it('pulls the safe area into the row instead of leaving the icons under the notch', () => {
    expect(rule('.ab-row')).toContain('padding:var(--sat) var(--pad) 0')
  })
})
