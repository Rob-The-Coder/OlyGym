// @vitest-environment happy-dom
// Favourites (issue #6): the Library floats starred exercises to the top of the current
// result list — after the search and body-part filters, without reordering the rest.
import React, { act } from 'react'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Library from './Library.jsx'
import { EXDB } from '../lib/exercises.js'

globalThis.IS_REACT_ACT_ENVIRONMENT = true

const mocks = vi.hoisted(() => {
  const state = { S: null }
  state.snapshot = () => ({ S: state.S, user: null, update: mut => { const next = structuredClone(state.S); mut(next); state.S = next } })
  return state
})
vi.mock('../store/useStore.js', () => {
  const useStore = selector => selector ? selector(mocks.snapshot()) : mocks.snapshot()
  useStore.getState = mocks.snapshot
  return { useStore }
})
vi.mock('react-router-dom', () => ({ useNavigate: () => () => {} }))
// The filter sheet is opened by the screen and hands its choices back through onApply; capturing
// the props is how these tests drive the filter without rendering the sheet itself.
let filterArgs = null
vi.mock('../sheets.jsx', () => ({
  effortHelpSheet: vi.fn(), exerciseDetailSheet: vi.fn(), addToRoutineSheet: vi.fn(), customExSheet: vi.fn(),
  libraryFilterSheet: vi.fn(args => { filterArgs = args }),
}))

const mounted = []
function render() {
  const host = document.createElement('div')
  document.body.appendChild(host)
  const root = createRoot(host)
  mounted.push(root)
  act(() => root.render(<Library />))
  return host
}
const names = host => [...host.querySelectorAll('.item .tt')].map(el => el.textContent).slice(1)   // drop "Create your own"
const cssSource = readFileSync(resolve(process.cwd(), 'src/index.css'), 'utf8')

beforeEach(() => {
  filterArgs = null
  mocks.S = { unit: 'kg', lang: 'en', routines: [], workouts: [], customEx: [], exWeights: {}, equipProfiles: [], activeEquipId: null, equipFilterOn: false }
  document.body.innerHTML = ''
})
afterEach(() => { act(() => { mounted.splice(0).forEach(root => root.unmount()) }) })

describe('Library favourites', () => {
  it('puts favourites first, marked with a star, and leaves the rest in catalogue order', () => {
    const plain = names(render())
    act(() => { mounted.splice(0).forEach(root => root.unmount()) })
    const fav = [plain[6], plain[2]]
    mocks.S.favEx = fav.map(n => EXDB.find(e => e.n === n).id)
    const host = render()
    const shown = names(host)
    expect(shown.slice(0, 2)).toEqual(plain.filter(n => fav.includes(n)))
    expect(shown.slice(2)).toEqual(plain.filter(n => !fav.includes(n)))
    const rows = [...host.querySelectorAll('.item')].slice(1)
    expect(rows[0].querySelector('.fav-star')).not.toBeNull()
    expect(rows[2].querySelector('.fav-star')).toBeNull()
  })

  // The Library's action ("By muscle") moved into the app bar's action row (WS13), so it no longer
  // competes with the title for width — but a single long word still has to survive a 320px screen,
  // and at 34px "Упражнения" broke into three lines. The title keeps its step down and breaks a
  // word only as a last resort; the action row never shrinks.
  it('leaves the title the width of the screen without shredding it', () => {
    const bar = readFileSync(resolve(process.cwd(), 'src/m3.components.css'), 'utf8')
    const h1 = bar.match(/^\.ab-title\{([^}]*)\}/m)
    expect(h1?.[1]).toContain('overflow-wrap:break-word')
    expect(h1[1]).not.toContain('overflow-wrap:anywhere')
    expect(h1[1]).not.toContain('hyphens:auto')
    expect(bar).toMatch(/@media \(max-width:420px\)\{\.ab-title\{font-size:30px\}\}/)
    expect(bar).toContain('.ab-acts{display:flex;align-items:center;gap:2px;flex:none}')
  })

  it('keeps a favourite on top inside a category filter, but never pulls one in from elsewhere', () => {
    // The catalogue's `bp` is a movement family now, so that is what the filter sheet groups by.
    const FAMILY = 'Accessory - Upper Body'
    const inFamily = EXDB.filter(e => e.bp === FAMILY)
    const other = EXDB.find(e => e.bp !== FAMILY)
    mocks.S.favEx = [inFamily[4].id, other.id]
    const host = render()
    act(() => host.querySelector('.lib-filters').click())
    // the sheet's button is labelled with the count it would produce — undefined here means the
    // sheet and the screen have drifted apart again
    expect(filterArgs.describeFor({ bp: FAMILY, eq: '', showAll: true }).count).toBe(inFamily.length)
    act(() => filterArgs.onApply({ bp: FAMILY, eq: '', showAll: true }))
    const shown = names(host)
    expect(shown[0]).toBe(inFamily[4].n)
    expect(shown).not.toContain(other.n)
  })

  // The two chip strips became one row plus a sheet (WS14), so what used to be two rows of chrome
  // is a count and one chip — and what is filtered is visible without opening it.
  it('shows the count, and each applied filter as a chip that drops itself', () => {
    const host = render()
    expect(host.querySelector('.lib-bar').textContent).toContain('624 exercises')
    expect(host.querySelector('.lib-applied')).toBeNull()
    act(() => host.querySelector('.lib-filters').click())
    act(() => filterArgs.onApply({ bp: 'Accessory - Upper Body', eq: '', showAll: false }))
    const chips = [...host.querySelectorAll('.lib-applied .chip')]
    expect(chips.map(c => c.textContent)).toEqual(['Accessory - Upper Body'])
    expect(host.querySelector('.lib-filters').textContent).toContain('1')
    act(() => chips[0].click())
    expect(host.querySelector('.lib-applied')).toBeNull()
  })
})
