import { describe, expect, test } from 'vitest'
import { BAR_EQ, DEFAULT_BAR_KG, DEFAULT_BAR_LB, usesBar, defaultBarWeight, barWeightFor, hasBarOverride, plateSplit } from './bar.js'
import { EXDB } from './exercises-data.js'

const idOf = eq => EXDB.find(e => e.eq === eq)?.id

// The catalogue tags bar movements `barbell` apart from the EZ-bar ones, which the sidecar
// (scripts/oly-catalogue/exercise-tags.json) marks `ez barbell`; there is no separate olympic
// barbell, Smith or trap-bar entry. A bar exercise in these tests is therefore either a real
// catalogue id or a stand-in object for one of the three types the catalogue does not carry.
const EZ = { id: idOf('ez barbell'), eq: 'ez barbell' }

describe('bar equipment', () => {
  test('covers the five bar types, and every catalogue exercise that carries one', () => {
    expect([...BAR_EQ].sort()).toEqual(['barbell', 'ez barbell', 'olympic barbell', 'smith machine', 'trap bar'])
    // Only `barbell` and the EZ-bar movements occur today, so this is a canary: retagging the Smith
    // or trap-bar movements (the sidecar in scripts/oly-catalogue) should move it, and nothing else.
    const tagged = EXDB.filter(e => BAR_EQ.has(e.eq))
    expect(tagged.length).toBeGreaterThan(0)
    expect([...new Set(tagged.map(e => e.eq))].sort()).toEqual(['barbell', 'ez barbell'])
  })

  test('usesBar answers for ids and exercise objects alike', () => {
    expect(usesBar(idOf('barbell')), 'barbell').toBe(true)
    for (const eq of BAR_EQ) {
      expect(usesBar({ eq }), eq).toBe(true)
    }
    expect(usesBar({ eq: 'dumbbell' })).toBe(false)
    expect(usesBar({ eq: 'body weight' })).toBe(false)
    expect(usesBar('no-such-id')).toBe(false)
    expect(usesBar(null)).toBe(false)
  })
})

describe('per-unit defaults', () => {
  test('every bar type has a default in both units', () => {
    for (const eq of BAR_EQ) {
      expect(DEFAULT_BAR_KG[eq], eq).toBeGreaterThan(0)
      expect(DEFAULT_BAR_LB[eq], eq).toBeGreaterThan(0)
    }
    expect(defaultBarWeight('ez barbell', 'kg')).toBe(10)
    expect(defaultBarWeight('ez barbell', 'lb')).toBe(25)
    expect(defaultBarWeight('olympic barbell', 'kg')).toBe(20)
    // A lb profile gets the bar its gym actually racks, not a 44.1 lb conversion.
    expect(defaultBarWeight('barbell', 'lb')).toBe(45)
    expect(defaultBarWeight('dumbbell', 'kg')).toBeNull()
  })
})

describe('barWeightFor', () => {
  const barbell = idOf('barbell')

  test('falls back to the equipment default in the profile unit', () => {
    expect(barWeightFor({ unit: 'kg', barWeights: {} }, barbell)).toBe(20)
    expect(barWeightFor({ unit: 'lb', barWeights: {} }, barbell)).toBe(45)
    expect(barWeightFor({ unit: 'kg' }, EZ)).toBe(10)
  })

  test('an explicit override wins over the default', () => {
    const S = { unit: 'kg', barWeights: { [EZ.id]: 7.5 } }
    expect(barWeightFor(S, EZ)).toBe(7.5)
    expect(hasBarOverride(S, EZ.id)).toBe(true)
    expect(barWeightFor(S, barbell)).toBe(20)   // other exercises keep their default
    expect(hasBarOverride(S, barbell)).toBe(false)
  })

  test('a deleted override falls back to the default, but a stored 0 is "no bar"', () => {
    const S = { unit: 'kg', barWeights: { [EZ.id]: 12.5 } }
    delete S.barWeights[EZ.id]
    expect(barWeightFor(S, EZ)).toBe(10)
    // Changed with issue #138: 0 used to mean "clear this override". It now means the exercise
    // has no bar (a counterbalanced Smith carriage), which is a value of its own — the editor
    // deletes the key to ask for the default back.
    expect(barWeightFor({ unit: 'kg', barWeights: { [EZ.id]: 0 } }, EZ)).toBe(0)
    expect(hasBarOverride({ unit: 'kg', barWeights: { [EZ.id]: 0 } }, EZ.id)).toBe(true)
  })

  test('is null for anything without a bar', () => {
    const dumbbell = EXDB.find(e => e.eq === 'dumbbell')
    expect(barWeightFor({ unit: 'kg', barWeights: {} }, dumbbell.id)).toBeNull()
    expect(barWeightFor({ unit: 'kg', barWeights: {} }, 'no-such-id')).toBeNull()
  })
})

describe('plateSplit', () => {
  test('splits what is beyond the bar evenly per side', () => {
    expect(plateSplit(62.5, 20)).toBe(21.25)
    expect(plateSplit(100, 20)).toBe(40)
    expect(plateSplit(30, 10)).toBe(10)
  })

  test('rounds to 2 decimals', () => {
    expect(plateSplit(65.55, 20)).toBe(22.78)
    expect(plateSplit(21.2, 20)).toBe(0.6)
  })

  test('is null when there is nothing sensible to show', () => {
    expect(plateSplit(20, 20)).toBe(null)    // bar only
    expect(plateSplit(15, 20)).toBe(null)    // below the bar
    expect(plateSplit(0, 20)).toBe(null)
    // 100 with no bar is 50 a side, not "nothing to show" (issue #138) — see bar-nobar.test.js
    expect(plateSplit(null, 20)).toBe(null)
    expect(plateSplit(100, null)).toBe(null)
    expect(plateSplit(undefined, undefined)).toBe(null)
  })
})
