import { describe, expect, it } from 'vitest'
import { libraryResults } from './library-filter.js'

// A stand-in catalogue: enough shape for the two real helpers this wraps (searchExercises
// reads n/bp/eq/tg, equipmentOf reads eq) without dragging the 624-lift list into a test
// about the order filters are applied in.
const EX = [
  { id: 'a', n: 'snatch', bp: 'snatch', eq: 'Barbell', tg: 'quads' },
  { id: 'b', n: 'power snatch', bp: 'snatch', eq: 'Barbell', tg: 'quads' },
  { id: 'c', n: 'back squat', bp: 'squat', eq: 'Barbell', tg: 'quads' },
  { id: 'd', n: 'goblet squat', bp: 'squat', eq: 'Dumbbell', tg: 'quads' },
  { id: 'e', n: 'push up', bp: 'accessory', eq: 'Body Weight', tg: 'chest' },
]
const ids = r => r.list.map(e => e.id)

describe('libraryResults', () => {
  it('returns everything, and every equipment present, with no filters', () => {
    const r = libraryResults({ all: EX })
    expect(ids(r)).toEqual(['a', 'b', 'c', 'd', 'e'])
    expect(r.eqOpts).toEqual(['Barbell', 'Body Weight', 'Dumbbell'])
    expect(r.eq).toBe('')
  })

  it('keeps only the body part', () => {
    expect(ids(libraryResults({ all: EX, bp: 'squat' }))).toEqual(['c', 'd'])
  })

  it('searches inside whatever the body part left', () => {
    expect(ids(libraryResults({ all: EX, q: 'snatch' }))).toEqual(['a', 'b'])
    expect(ids(libraryResults({ all: EX, bp: 'squat', q: 'snatch' }))).toEqual([])
    expect(ids(libraryResults({ all: EX, bp: 'squat', q: 'squat' }))).toEqual(['c', 'd'])
  })

  // The options are derived from the already-filtered list, so a body part with only barbell
  // work does not offer "Dumbbell" and leave a chip that matches nothing.
  it('offers only the equipment the other filters left', () => {
    const r = libraryResults({ all: EX, bp: 'squat' })
    expect(r.eqOpts).toEqual(['Barbell', 'Dumbbell'])
    expect(ids(libraryResults({ all: EX, bp: 'squat', eq: 'Dumbbell' }))).toEqual(['d'])
  })

  it('drops an equipment choice the rest of the filters narrowed away', () => {
    const r = libraryResults({ all: EX, bp: 'snatch', eq: 'Dumbbell' })
    expect(r.eq).toBe('')
    expect(r.eqOpts).toEqual(['Barbell'])
    expect(ids(r)).toEqual(['a', 'b'])
  })

  it('applies the availability predicate after the search, before the equipment choice', () => {
    const r = libraryResults({ all: EX, q: 'squat', available: e => e.eq !== 'Dumbbell' })
    expect(ids(r)).toEqual(['c'])
    expect(r.eqOpts).toEqual(['Barbell'])
  })

  it('treats a predicate of null as no narrowing at all', () => {
    expect(ids(libraryResults({ all: EX, available: null }))).toEqual(['a', 'b', 'c', 'd', 'e'])
  })
})
