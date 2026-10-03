// The colour roles are CSS, and happy-dom neither lays out nor cascades — so what is worth
// asserting is arithmetic on the shipped values. Same approach as m3.empty-icon.test.js: the
// stylesheet is the artefact under test.
//
// Every scheme is checked, not just the default one: the eight accents are generated from seeds
// (scripts/design/m3-scheme.mjs), and the whole point of a scheme is that swapping it cannot make
// the UI unreadable. A failure names the scheme and the pair.
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { contrastOn, readCustomProperties, resolveCustomProperties } from './contrast.js'

const read = f => readFileSync(resolve(process.cwd(), 'src/' + f), 'utf8')
const tokens = read('m3.tokens.css')
const components = read('m3.components.css')

const darkVars = readCustomProperties(tokens, ':root')
const lightVars = { ...darkVars, ...readCustomProperties(tokens, ':root[data-theme="light"]') }
const accentKeys = [...new Set([...tokens.matchAll(/:root\[data-accent="([a-z0-9-]+)"\]/g)].map(m => m[1]))]

// name -> the variables in force when that scheme is on screen
const schemes = [['default', darkVars], ['default (light)', lightVars]]
for (const key of accentKeys) {
  schemes.push([key, { ...darkVars, ...readCustomProperties(tokens, ':root[data-accent="' + key + '"]') }])
  schemes.push([key + ' (light)', { ...lightVars, ...readCustomProperties(tokens, ':root[data-theme="light"][data-accent="' + key + '"]') }])
}

const SURFACES = ['surface', 'surface-container-low', 'surface-container', 'surface-container-high', 'surface-container-highest']

describe('the token file', () => {
  it('ships a scheme for every accent in the picker', () => {
    expect(accentKeys.length).toBeGreaterThanOrEqual(8)
  })
})

describe.each(schemes.map(([name, vars]) => [name, resolveCustomProperties(vars)]))('the %s scheme', (name, t) => {
  const at = key => t['--m3-' + key]

  it('keeps body text at 7:1 and secondary text at 4.5:1 on every surface', () => {
    for (const s of SURFACES) {
      expect(contrastOn(t['--label'], at(s)), name + ': --label on --m3-' + s).toBeGreaterThanOrEqual(7)
      expect(contrastOn(t['--label-2'], at(s)), name + ': --label-2 on --m3-' + s).toBeGreaterThanOrEqual(4.5)
    }
  })

  it('draws component edges at 3:1 or better', () => {
    // everywhere the outline is used. On container-highest (a segmented or switch track) nothing
    // is outlined, so that surface is deliberately not in this list.
    for (const s of SURFACES.slice(0, 4)) {
      expect(contrastOn(t['--m3-outline'], at(s)), name + ': --m3-outline on --m3-' + s).toBeGreaterThanOrEqual(3)
    }
  })

  it('keeps the accent readable as text and as a fill', () => {
    for (const s of ['surface', 'surface-container-high']) {
      expect(contrastOn(t['--m3-primary'], at(s)), name + ': --m3-primary on --m3-' + s).toBeGreaterThanOrEqual(4.5)
    }
    expect(contrastOn(t['--m3-on-primary'], at('primary')), name + ': on a primary fill').toBeGreaterThanOrEqual(4.5)
    expect(contrastOn(t['--m3-on-primary-container'], at('primary-container')), name + ': on a primary container').toBeGreaterThanOrEqual(4.5)
    expect(contrastOn(t['--m3-primary'], at('primary-container')), name + ': primary on its own container').toBeGreaterThanOrEqual(3)
  })

  it('keeps the third level out of the text range on purpose', () => {
    expect(contrastOn(t['--label-3'], at('surface-container')), name + ': --label-3 must stay decorative').toBeLessThan(4.5)
  })
})

describe('the component sheet', () => {
  it('never paints text with the decorative tone', () => {
    expect(components).not.toMatch(/color:\s*var\(--label-3\)/)
  })

  it('re-points every index.css selector that used --label-3 for words', () => {
    const block = components.slice(components.indexOf('Contrast — the text that used to sit'))
    expect(block.length).toBeGreaterThan(0)
    for (const sel of ['.dim', '.helpbtn', '.wday .lbl', '.field::placeholder', '.searchf .lead', '.searchf .clear', '.search>svg', '.exmedia-x', '.sethead', '.setph', '.sidetag', '.subrow .subn', '.hm-months span', '.hm-legend', '.cal-h', '.cal-legend', '.today-row .lbl2', '.steps-list::marker']) {
      expect(block, sel + ' is no longer re-pointed to --label-2').toContain(sel)
    }
    expect(components).toMatch(/\.cx-plus\{[^}]*color:var\(--label-2\)/)
    expect(components).toMatch(/\.ss-amp \.ss-plus\{[^}]*color:var\(--label-2\)/)
  })
})
