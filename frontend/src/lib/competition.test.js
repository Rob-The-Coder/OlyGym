import { describe, expect, test } from 'vitest'
import {
  MAX_ATTEMPTS, DEFAULT_CLASSES, classLists, listFor, classOptions, cleanClasses, attemptMade, bestAttempt, totalOf, hasResult,
  hasAttempts, sortedMeets, upcomingMeets, pastMeets, nextMeet, daysUntil, competitionBests,
  attemptRows, cleanAttempts, blankMeet, upsertMeet, removeMeet,
} from './competition.js'

const meet = (d, snatch, cj, extra = {}) => ({ id: 'm-' + d, d, snatch, cj, ...extra })
const made = w => ({ w, made: true })
const miss = w => ({ w, made: false })

describe('attempts', () => {
  test('only a made attempt with a weight counts', () => {
    expect(attemptMade(made(100))).toBe(true)
    expect(attemptMade(miss(100))).toBe(false)
    expect(attemptMade(made(0))).toBe(false)
    expect(attemptMade(null)).toBe(false)
  })

  test('best skips no-lifts even when they were heavier', () => {
    expect(bestAttempt([miss(120), made(115), made(110)])).toBe(115)
    expect(bestAttempt([miss(120), miss(115)])).toBeNull()
    expect(bestAttempt([])).toBeNull()
    expect(bestAttempt(undefined)).toBeNull()
  })
})

describe('total', () => {
  test('needs something made in both lifts', () => {
    expect(totalOf(meet('2026-01-01', [made(100)], [made(130)]))).toBe(230)
    expect(totalOf(meet('2026-01-01', [miss(100)], [made(130)]))).toBeNull()
    expect(totalOf(meet('2026-01-01', [made(100)], []))).toBeNull()
    expect(totalOf(null)).toBeNull()
  })

  test('is the best made attempt of each lift, not the opener', () => {
    expect(totalOf(meet('2026-01-01', [miss(110), made(105)], [miss(140), made(135)]))).toBe(240)
  })

  test('hasResult / hasAttempts', () => {
    expect(hasResult(meet('2026-01-01', [made(100)], [made(130)]))).toBe(true)
    expect(hasResult(meet('2026-01-01', [made(100)], []))).toBe(false)
    expect(hasAttempts(meet('2026-01-01', [miss(100)], []))).toBe(true)
    expect(hasAttempts(meet('2026-01-01', [], []))).toBe(false)
  })
})

describe('ordering and the next meet', () => {
  const list = [
    meet('2026-03-10', [made(100)], [made(130)]),
    meet('2025-12-01', [made(90)], [made(120)]),
    meet('2026-06-20', [made(105)], [made(135)]),
  ]
  test('sorted oldest first', () => {
    expect(sortedMeets(list).map(m => m.d)).toEqual(['2025-12-01', '2026-03-10', '2026-06-20'])
  })
  test('upcoming is today or later, soonest first; past is most recent first', () => {
    expect(upcomingMeets(list, '2026-03-10').map(m => m.d)).toEqual(['2026-03-10', '2026-06-20'])
    expect(pastMeets(list, '2026-03-10').map(m => m.d)).toEqual(['2025-12-01'])
  })
  test('nextMeet is the soonest one ahead, and null when there is none', () => {
    expect(nextMeet(list, '2026-03-10').d).toBe('2026-03-10')
    expect(nextMeet(list, '2026-07-01')).toBeNull()
  })
  test('daysUntil counts whole days across a month boundary', () => {
    expect(daysUntil(meet('2026-03-10'), '2026-03-01')).toBe(9)
    expect(daysUntil(meet('2026-03-01'), '2026-03-10')).toBe(-9)
    expect(daysUntil(meet('2026-03-01'), '2026-03-01')).toBe(0)
    expect(daysUntil({ d: '' }, '2026-03-01')).toBeNull()
  })
})

describe('competition bests', () => {
  test('picks the best made lift of each kind across meets', () => {
    const b = competitionBests([
      meet('2026-01-01', [made(100), miss(110)], [made(130)]),
      meet('2026-02-01', [made(105)], [miss(140), made(138)]),
    ])
    // The total is the best single-meet total (243), not best snatch + best cj, which could
    // have come from two different meets and never happened on one platform.
    expect(b).toEqual({ snatch: 105, cj: 138, total: 243 })
  })
  test('nulls when nothing was ever made', () => {
    expect(competitionBests([])).toEqual({ snatch: null, cj: null, total: null })
    expect(competitionBests([meet('2026-01-01', [miss(100)], [])])).toEqual({ snatch: null, cj: null, total: null })
  })
})

describe('edit shape', () => {
  test('attemptRows always pads to three', () => {
    expect(attemptRows(meet('2026-01-01', [made(100)], []), 'snatch')).toEqual([
      { w: 100, made: true }, { w: 0, made: true }, { w: 0, made: true },
    ])
    expect(attemptRows(meet('2026-01-01', [made(100)], []), 'cj')).toHaveLength(MAX_ATTEMPTS)
    expect(attemptRows(null, 'snatch')).toHaveLength(MAX_ATTEMPTS)
  })
  test('cleanAttempts drops weightless rows and rounds to a tenth', () => {
    expect(cleanAttempts([{ w: 100.04, made: true }, { w: 0, made: true }, null])).toEqual([{ w: 100, made: true }])
  })
})

describe('list editing', () => {
  test('upsert replaces by id and keeps date order', () => {
    const a = meet('2026-01-01', [], [])
    const b = meet('2026-03-01', [], [])
    const withB = upsertMeet([a], b)
    expect(withB.map(m => m.id)).toEqual(['m-2026-01-01', 'm-2026-03-01'])
    const edited = upsertMeet(withB, { ...a, name: 'Regionale' })
    expect(edited).toHaveLength(2)
    expect(edited.find(m => m.id === a.id).name).toBe('Regionale')
  })
  test('removeMeet drops only that id', () => {
    expect(removeMeet([meet('2026-01-01', [], [])], 'm-2026-01-01')).toEqual([])
  })
  test('blankMeet is a complete, empty record', () => {
    const m = blankMeet('2026-05-05')
    expect(m.d).toBe('2026-05-05')
    expect(m.snatch).toEqual([])
    expect(m.cj).toEqual([])
    expect(typeof m.id).toBe('string')
    expect(m.id.length).toBeGreaterThan(0)
  })
})

describe('weight classes', () => {
  test('labels a list for a picker', () => {
    expect(classOptions(['60', '65'])).toEqual([{ value: '60', label: '60 kg' }, { value: '65', label: '65 kg' }])
    expect(classOptions(['110+'])).toEqual([{ value: '110+', label: '110+ kg' }])
    expect(classOptions(undefined)).toEqual([])
  })
  test('classLists normalises, and a pre-split flat list reads as the male one', () => {
    expect(classLists(undefined)).toEqual({ male: DEFAULT_CLASSES.male, female: [] })
    expect(classLists({ male: ['60'], female: ['55'] })).toEqual({ male: ['60'], female: ['55'] })
    expect(classLists(['60', ' 60 ', '65'])).toEqual({ male: ['60', '65'], female: [] })
  })
  test('listFor follows the profile body, defaulting to male', () => {
    expect(listFor({ body: 'female', classes: { male: ['60'], female: ['55'] } })).toEqual(['55'])
    expect(listFor({ body: 'male', classes: { male: ['60'], female: ['55'] } })).toEqual(['60'])
    expect(listFor({})).toEqual(DEFAULT_CLASSES.male)
  })
  test('cleanClasses trims, drops empties and de-duplicates, keeping order', () => {
    expect(cleanClasses([' 60 ', '', '65', '60', null, '110+'])).toEqual(['60', '65', '110+'])
    expect(cleanClasses(undefined)).toEqual([])
  })
})
