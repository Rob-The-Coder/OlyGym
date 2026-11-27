// @vitest-environment happy-dom
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { WhatsNew } from './WhatsNew.jsx'

globalThis.IS_REACT_ACT_ENVIRONMENT = true

const ENTRIES = [
  { version: '1.3.12', bullets: ['Per-set weights that follow your progression', 'A rest timer between sets'] },
  { version: '1.3.11', bullets: ['PR badges on the lifts you beat'] },
]

let host, root
beforeEach(() => {
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
})
afterEach(() => {
  act(() => root.unmount())
  host.remove()
})

describe('WhatsNew', () => {
  it('shows the title, the version headings newest first and one item per bullet', () => {
    act(() => { root.render(<WhatsNew entries={ENTRIES} close={() => {}} />) })
    expect(host.querySelector('.dlg h3').textContent).toBe("What's new")
    expect([...host.querySelectorAll('.wn-ver')].map(el => el.textContent)).toEqual(['v1.3.12', 'v1.3.11'])
    expect([...host.querySelectorAll('.wn-list li')].map(el => el.textContent)).toEqual([
      'Per-set weights that follow your progression',
      'A rest timer between sets',
      'PR badges on the lifts you beat',
    ])
  })

  it('calls close once when Got it is tapped', () => {
    const close = vi.fn()
    act(() => { root.render(<WhatsNew entries={ENTRIES} close={close} />) })
    const gotIt = [...host.querySelectorAll('.dlg-btn')].find(btn => btn.textContent === 'Got it')
    expect(gotIt).toBeTruthy()
    act(() => { gotIt.click() })
    expect(close).toHaveBeenCalledTimes(1)
  })
})
