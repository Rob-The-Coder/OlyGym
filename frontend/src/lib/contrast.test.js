// The colour roles are CSS, and happy-dom neither lays out nor cascades — so what is worth
// asserting is arithmetic on the shipped values. Same approach as m3.empty-icon.test.js: the
// stylesheet is the artefact under test. Failures name the pair, so a future tone can be fixed
// without re-deriving anything.
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { contrastOn, readCustomProperties, resolveCustomProperties } from './contrast.js'

const read = f => readFileSync(resolve(process.cwd(), 'src/' + f), 'utf8')
const tokens = read('m3.tokens.css')
const components = read('m3.components.css')

// Merge the light block over the dark one *before* resolving: resolving first would bake the
// dark values of --m3-on-surface into --label, and the light theme would then be tested with
// dark text on a white card.
const darkVars = readCustomProperties(tokens, ':root')
const raw = resolveCustomProperties(darkVars)
const lightRaw = resolveCustomProperties({ ...darkVars, ...readCustomProperties(tokens, ':root[data-theme="light"]') })

const SURFACES = ['surface', 'surface-container-low', 'surface-container', 'surface-container-high', 'surface-container-highest']

describe.each([['dark', raw], ['light', lightRaw]])('the %s theme', (theme, t) => {
  const at = name => t['--m3-' + name]

  it('keeps secondary text at 4.5:1 on every surface', () => {
    for (const s of SURFACES) {
      expect(contrastOn(t['--label-2'], at(s)), theme + ': --label-2 on --m3-' + s).toBeGreaterThanOrEqual(4.5)
    }
  })

  it('keeps body text well above the floor', () => {
    for (const s of SURFACES) {
      expect(contrastOn(t['--label'], at(s)), theme + ': --label on --m3-' + s).toBeGreaterThanOrEqual(7)
    }
  })

  it('draws component edges at 3:1 or better', () => {
    // everywhere the outline is used. On container-highest (a segmented track, a switch track)
    // nothing is outlined, so that surface is deliberately not in this list.
    for (const s of SURFACES.slice(0, 4)) {
      expect(contrastOn(t['--m3-outline'], at(s)), theme + ': --m3-outline on --m3-' + s).toBeGreaterThanOrEqual(3)
    }
  })

  it('keeps the third level out of the text range on purpose', () => {
    expect(contrastOn(t['--label-3'], at('surface-container')), theme + ': --label-3 must stay a decorative tone').toBeLessThan(4.5)
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
