// The M3 layer is CSS, and happy-dom does not lay anything out — so this checks the one thing a
// user had to report instead: the empty state's glyph must be centred in its own circle.
//
// index.css centres it horizontally only (`display:flex;justify-content:center`), and a fixed-size
// SVG in a flex row with the default `align-items` sits at the top: the icon was 18px high in a
// 64px circle on every empty screen in the app (Plan, a new routine, History, Library…). Reading
// the stylesheet is the closest a test can get to the rendering, and it is enough to stop the
// rule losing `align-items` again.
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const rule = (css, selector) => {
  const at = css.indexOf('\n' + selector + ' {') >= 0 ? css.indexOf('\n' + selector + ' {') : css.indexOf('\n' + selector + '{')
  if (at < 0) return ''
  return css.slice(at, css.indexOf('}', at))
}

const m3 = readFileSync(resolve(process.cwd(), 'src/m3.components.css'), 'utf8')
const base = readFileSync(resolve(process.cwd(), 'src/index.css'), 'utf8')

describe('the empty state’s icon', () => {
  it('is centred in its circle, on both axes', () => {
    const own = rule(m3, '.empty .ico')
    const inherited = rule(base, '.empty .ico')
    expect(own, '.empty .ico must set align-items itself: the base rule only centres it across').toMatch(/align-items:\s*center/)
    expect(own).toMatch(/justify-content:\s*center/)
    // …and the base rule is still the reason the override exists.
    expect(inherited).toMatch(/justify-content:\s*center/)
  })

  it('is why the override exists at all', () => {
    // Every empty state goes through the same two rules, so fixing it once fixes them all: the
    // base rule centres the glyph across and nothing else, and this is the assertion that says so.
    expect(rule(base, '.empty .ico')).not.toMatch(/align-items/)
  })
})
