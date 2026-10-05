// The M3 layer is two stylesheets and no build step, so the failure worth a test is a
// token one sheet spends and another never defines. An undefined custom property does not
// fall back to anything sensible: it makes the whole declaration invalid at computed-value
// time, so the property takes whatever it would have inherited. That is how
// `color: var(--dim)` in index.css painted the set-table phase headings in the body colour
// for as long as nobody looked, and why m3.components.css had to patch the symptom.
//
// Same approach as m3.empty-icon.test.js: the stylesheet is the artefact under test.
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const read = f => readFileSync(resolve(process.cwd(), 'src/' + f), 'utf8')
const base = read('index.css')            // upstream — never edited, but it spends too
const tokens = read('m3.tokens.css')      // the roles
const components = read('m3.components.css')

const bare = css => css.replace(/\/\*[\s\S]*?\*\//g, '')
const defined = css => new Set([...bare(css).matchAll(/(--[a-z0-9-]+)\s*:/g)].map(m => m[1]))
const spent = css => new Set([...bare(css).matchAll(/var\(\s*(--[a-z0-9-]+)/g)].map(m => m[1]))
const rules = css => [...bare(css).matchAll(/([^{}]+)\{([^{}]*)\}/g)].map(m => [m[1].trim(), m[2]])
const decls = (css, sel) => rules(css)
  .filter(([s]) => s.split(',').map(x => x.trim()).includes(sel))
  .map(([, body]) => body).join(';')

const allDefined = new Set([...defined(base), ...defined(tokens), ...defined(components)])
const allSpent = new Set([...spent(base), ...spent(tokens), ...spent(components)])

// Properties the JS writes onto the element before the stylesheet reads them. No stylesheet
// can show that, so the list is explicit — and the next test fails if it goes stale.
const FROM_JS = new Set([
  '--bc',                    // the accent a bar or superset unit is drawn in
  '--tint',                  // the colour a logged workout is tagged with
  '--i', '--n',              // index and count of a list: stagger, and the segmented thumb
  '--whdr-h',                // the measured height of the workout header
  '--picker-keyboard-bottom', '--picker-visual-height',   // the keyboard-aware sheet layout
])

describe('the token layer', () => {
  it('defines every custom property the app spends', () => {
    const missing = [...allSpent].filter(n => !allDefined.has(n) && !FROM_JS.has(n)).sort()
    expect(missing, 'never defined anywhere: ' + missing.join(', ')).toEqual([])
  })

  it('keeps the JS-set allowlist honest', () => {
    // An entry that nothing spends any more is a lie about how the app works.
    const stale = [...FROM_JS].filter(n => !allSpent.has(n)).sort()
    expect(stale, 'no longer spent by any stylesheet: ' + stale.join(', ')).toEqual([])
  })

  it('keeps the roles in one file', () => {
    // The state, shadow, motion and shape scales are roles; a component sheet that defines
    // one has started a second vocabulary. This is what the private :root in
    // m3.components.css was, and it is why the app had two duration scales.
    const stray = [...defined(components)].filter(n => /^--m3-(state|shadow|ease|dur|shape|type)/.test(n))
    expect(stray, 'defined outside m3.tokens.css: ' + stray.join(', ')).toEqual([])
  })

  it('leaves one name for one duration', () => {
    // --m3-dur-spatial (350ms) and --m3-dur-enter (400ms) were a second scale for the same
    // three durations, and --m3-ease-spring-soft was spent by nothing at all.
    const gone = ['--m3-dur-spatial', '--m3-dur-enter', '--m3-spring-soft', '--m3-ease-spring-soft']
    const still = gone.filter(n => bare(base + tokens + components).includes(n))
    expect(still, 'still referenced: ' + still.join(', ')).toEqual([])
  })

  it('ships a percentage for all four interaction states', () => {
    for (const state of ['hover', 'focus', 'press', 'drag']) {
      expect(tokens, '--m3-state-' + state).toMatch(new RegExp('--m3-state-' + state + ':{0,2}\\s*\\d+%'))
    }
  })

  it('ships a shadow scale, because tone cannot express a cast shadow', () => {
    for (const level of [0, 1, 2, 3]) expect(tokens).toContain('--m3-shadow-' + level + ':')
  })
})

describe('the press', () => {
  // Every control that answered a press with a number of its own: 10% on chips, rows, day
  // chips and tiles; 12% on icon buttons, checkboxes and steppers.
  const PRESSED = ['.iconbtn:active', '.chip:active', '.lrow.tap:active', '.item:active',
                   '.chk:active', '.ck:active', '.stp button:active', '.wday:active',
                   '.tile.tappable:active', '.hero-rail .wday:active']

  it('layers every control on the state token', () => {
    for (const sel of PRESSED) {
      const body = decls(components, sel)
      expect(body, sel + ' must still have a rule of its own').not.toBe('')
      expect(body, sel + ' must use the token rather than a percentage of its own').toContain('--m3-state-press')
    }
  })

  it('paints the filled button with its on-colour, not a second hue', () => {
    const body = decls(components, '.btn.primary:active')
    expect(body).toContain('--m3-state-press')
    expect(body).toContain('--m3-on-primary')
    // The old rule reset the fill to --acc and leaned on a brightness filter for feedback,
    // which on the light theme moved the accent toward the page.
    expect(body).not.toMatch(/brightness/)
    expect(decls(components, '.btn:active')).not.toMatch(/brightness/)
  })
})

describe('the type roles', () => {
  // Every role carries a weight for both M3 scales and one tracking value. Material's rule for
  // the emphasized counterpart is mechanical — 400 becomes 500, and 500 becomes 700 — so a role
  // whose emphasis is not heavier than its baseline is a typo, not a decision.
  const weights = () => {
    const out = {}
    for (const m of bare(tokens).matchAll(/--m3-type-([a-z-]+?)-weight(-emphasized)?:\s*(\d+)/g)) {
      out[m[1] + (m[2] ? '-emphasized' : '')] = +m[3]
    }
    return out
  }

  it('carries both scales for every role', () => {
    const w = weights()
    // 'overline' is not a role with two scales: uppercase text is always the emphatic one, so
    // it has a single weight rather than a baseline and an emphasized pair.
    const roles = Object.keys(w).filter(k => !k.endsWith('-emphasized') && k !== 'overline')
    expect(roles.length).toBeGreaterThanOrEqual(13)
    const missing = roles.filter(r => w[r + '-emphasized'] === undefined)
    expect(missing, 'no emphasized weight: ' + missing.join(', ')).toEqual([])
    const noTrack = roles.filter(r => !bare(tokens).includes('--m3-type-' + r + '-track'))
    expect(noTrack, 'no tracking: ' + noTrack.join(', ')).toEqual([])
  })

  it('makes emphasis heavier, never lighter', () => {
    const w = weights()
    const wrong = Object.keys(w).filter(k => k.endsWith('-emphasized') && !(w[k] > w[k.replace('-emphasized', '')]))
    expect(w.overline).toBe(700)
    expect(wrong, 'emphasis is not heavier: ' + wrong.join(', ')).toEqual([])
  })

  it('is spent by name, not by number', () => {
    for (const sel of ['.card .big', '.tile .v', '.sheet h3', '.sect-t', '.tile .l', '.ddow']) {
      expect(decls(components, sel), sel + ' should name a role').toMatch(/--m3-type-/)
    }
    expect(decls(components, '.card h2:not(.sect-t)')).toMatch(/--m3-type-title-m-weight\)/)
    expect(decls(components, '.btn')).toMatch(/--m3-type-label-l-weight\)/)
  })

  it('leaves the titles on the baseline weight and the labels on the emphasized one', () => {
    // The app's split, and the one the spec asks for: emphatic at the extremes, baseline in the
    // middle where the reading happens. If a title ever picks up -emphasized by accident, this
    // is the assertion that catches it.
    expect(decls(components, '.card h2:not(.sect-t)')).not.toMatch(/weight-emphasized/)
    expect(decls(components, '.btn')).not.toMatch(/weight-emphasized/)
    expect(decls(components, '.ab-title')).toMatch(/weight-emphasized/)
    expect(decls(components, '.sect-t')).toMatch(/--m3-type-label-m-weight\)/)
  })
})

describe('the component sheet', () => {
  it('has balanced braces', () => {
    // Forty-odd mechanical edits went through it in one sitting.
    const css = bare(components)
    expect((css.match(/\{/g) || []).length).toBe((css.match(/\}/g) || []).length)
  })
})
