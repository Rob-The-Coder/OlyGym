import { afterEach, describe, expect, it } from 'vitest'
import italian from '../locales/it.js'
import { compareSemver } from './update.js'
import { CHANGELOG, changesSince, latestEntry, shouldAnnounce } from './changelog.js'

// The seeded entries are the shipping data. The selection cases that need entries on both sides
// of an acknowledgement swap a temporary list in; every test puts the seeded one back.
const seeded = CHANGELOG.map(entry => ({ version: entry.version, bullets: [...entry.bullets] }))
afterEach(() => {
  CHANGELOG.splice(0, CHANGELOG.length, ...seeded.map(entry => ({ version: entry.version, bullets: [...entry.bullets] })))
})
const useEntries = entries => { CHANGELOG.splice(0, CHANGELOG.length, ...entries) }

describe('changesSince', () => {
  it('returns every entry when nothing has been acknowledged yet', () => {
    for (const ack of [undefined, null, '']) {
      expect(changesSince(ack)).toEqual(CHANGELOG)
    }
  })

  it('returns nothing when the acknowledgement is the newest entry', () => {
    expect(changesSince(latestEntry().version)).toEqual([])
  })

  it('returns only the entries above an older acknowledgement', () => {
    useEntries([
      { version: '1.4.0', bullets: ['Per-set weights that follow your progression'] },
      { version: '1.3.12', bullets: ['PR badges on the lifts you beat'] },
      { version: '1.3.9', bullets: ['A rest timer between sets'] },
    ])
    expect(changesSince('1.3.12').map(entry => entry.version)).toEqual(['1.4.0'])
    expect(changesSince('1.3.9').map(entry => entry.version)).toEqual(['1.4.0', '1.3.12'])
  })

  it('returns nothing when the acknowledgement is newer than every entry', () => {
    expect(changesSince('9.9.9')).toEqual([])
  })
})

describe('shouldAnnounce', () => {
  it('is false for an equal or newer acknowledgement', () => {
    expect(shouldAnnounce('1.3.12', '1.3.12')).toBe(false)
    expect(shouldAnnounce('1.3.13', '1.3.12')).toBe(false)
  })

  it('is true for an older or missing acknowledgement', () => {
    expect(shouldAnnounce('1.3.11', '1.3.12')).toBe(true)
    expect(shouldAnnounce(undefined, '1.3.12')).toBe(true)
    expect(shouldAnnounce(null, '1.3.12')).toBe(true)
    expect(shouldAnnounce('', '1.3.12')).toBe(true)
  })
})

describe('the bundled data', () => {
  it('has at least one entry, each with non-empty bullets, newest first', () => {
    expect(CHANGELOG.length).toBeGreaterThan(0)
    expect(latestEntry()).toBe(CHANGELOG[0])
    for (const entry of CHANGELOG) {
      expect(entry.version).toMatch(/^\d+\.\d+\.\d+$/)
      expect(entry.bullets.length).toBeGreaterThan(0)
      for (const bullet of entry.bullets) expect(typeof bullet, bullet).toBe('string')
    }
    for (let i = 1; i < CHANGELOG.length; i++) {
      expect(compareSemver(CHANGELOG[i - 1].version, CHANGELOG[i].version)).toBe(1)
    }
  })

  // check-source-strings.mjs cannot see t(variable), so a bullet missing from the Italian pack
  // would quietly ship English. This is the guard for that.
  it('has an Italian key for every bullet in every entry', () => {
    const keys = new Set(Object.keys(italian))
    for (const entry of CHANGELOG) {
      for (const bullet of entry.bullets) expect(keys.has(bullet), `missing Italian key: ${bullet}`).toBe(true)
    }
  })
})
