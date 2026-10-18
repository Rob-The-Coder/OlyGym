// @vitest-environment happy-dom
// The History row used to mark a PR session with a bare count ("2 PR") — how many, not which
// lift and not the load. It now names the lifts and the loads they hit: one .pr badge per
// recorded PR, heaviest first, inside the row's text column. The row is shared by History, the
// Stats recent list and the day-sessions sheet, so this pins the one rendering all three get.
import React, { act } from 'react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createRoot } from 'react-dom/client'
import { useStore } from './store/useStore.js'
import { useUI } from './store/useUI.js'
import { WorkoutRow } from './sheets.jsx'
import { EXIDX } from './lib/exercises.js'
import { exerciseNameFor, t } from './lib/i18n.js'
import { fmtNum } from './lib/format.js'

const mounted = []

function renderRow(w) {
  const host = document.createElement('div')
  document.body.appendChild(host)
  const root = createRoot(host)
  mounted.push(root)
  act(() => root.render(<WorkoutRow w={w} onClick={() => {}} />))
  return host
}

// Real catalogue lifts, so the badge's name goes through the same reader the screen uses.
const SNATCH = Object.keys(EXIDX).find(id => EXIDX[id].n === 'snatch')
const CLEAN = Object.keys(EXIDX).find(id => EXIDX[id].n === 'clean')
const T0 = Date.UTC(2026, 2, 2, 9)

const workout = prs => ({
  id: 'w1', d: '2026-03-02', start: T0, end: T0 + 3600000, name: 'Heavy day', vol: 600,
  entries: [
    // 80 then 102: the badge must show the session's best, not the first or the lightest set.
    { id: SNATCH, target: { mode: 'reps', bodyweight: false }, sets: [{ w: 80, r: 2, done: true }, { w: 102, r: 1, done: true }] },
    { id: CLEAN, target: { mode: 'reps', bodyweight: false }, sets: [{ w: 120, r: 1, done: true }] },
  ],
  ...(prs ? { prs } : {}),
})

beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  useUI.setState({ sheets: [], toastMsg: '' })
  useStore.setState(s => ({ S: { ...s.S, unit: 'kg', workouts: [], active: null } }))
  document.body.innerHTML = ''
})

afterEach(() => {
  act(() => { mounted.splice(0).forEach(root => root.unmount()) })
})

describe('WorkoutRow PR badges', () => {
  it('renders one badge per PR, heaviest first, naming the lift and its load', () => {
    const host = renderRow(workout([SNATCH, CLEAN]))
    const row = host.querySelector('.item')
    expect(row.getAttribute('role')).toBe('button')

    const strip = row.querySelector('.grow .prs')
    expect(strip).toBeTruthy()
    const badges = [...strip.querySelectorAll('.pr')]
    expect(badges).toHaveLength(2)

    // The heavier PR leads, each badge reads "<lift> PR <load> <unit>".
    expect(badges[0].textContent).toContain(exerciseNameFor(EXIDX[CLEAN]))
    expect(badges[0].textContent).toContain(t('PR'))
    expect(badges[0].textContent).toContain(fmtNum(120) + ' kg')
    expect(badges[1].textContent).toContain(exerciseNameFor(EXIDX[SNATCH]))
    expect(badges[1].textContent).toContain(fmtNum(102) + ' kg')
    // The load is the session's best, not an earlier, lighter set in the same entry.
    expect(badges[1].textContent).not.toContain(fmtNum(80) + ' kg')

    // Below the metadata line, inside the text column — and part of the row's readable label.
    expect(row.querySelector('.grow .ss').nextElementSibling).toBe(strip)
    expect(row.textContent).toContain(exerciseNameFor(EXIDX[CLEAN]) + ' ' + t('PR') + ' ' + fmtNum(120) + ' kg')

    // The old trailing "{n} PR" count is gone.
    expect(row.textContent).not.toMatch(/\d+\s*PR/)
  })

  it('renders no PR strip for a workout with no recorded PRs', () => {
    const host = renderRow(workout(null))
    expect(host.querySelector('.prs')).toBeNull()
    expect(host.querySelector('.pr')).toBeNull()
    expect(host.querySelector('.item').textContent).not.toMatch(/\d+\s*PR/)
  })

  it('collapses a lift recorded twice into one badge', () => {
    const host = renderRow(workout([SNATCH, CLEAN, SNATCH]))
    // The duplicate id counts once, so a row can never show the same lift twice.
    const badges = [...host.querySelectorAll('.grow .prs .pr')]
    expect(badges).toHaveLength(2)
    expect(badges.every(b => b.querySelector('.pr-name'))).toBe(true)
  })
})
