// WCAG 2.1 contrast, plus just enough CSS reading to hold the token layer to it.
//
// Pure and framework-free, like every other helper in src/lib, with its test beside it
// (CONTRIBUTING.md: anything that decides a number gets a unit test, not a click-through).
// m3.tokens.css is the artefact under test: parse the roles, paint the translucent text
// tones onto the surface they are actually used on, and compare the ratio.

const HEX = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i
const RGB = /^rgba?\(([^)]+)\)$/i

/** '#rrggbb' | '#rgb' | 'rgb(r g b)' | 'rgba(r, g, b, a)' -> [r, g, b, a] */
export function parseColor(value) {
  const v = String(value).trim()
  const hex = v.match(HEX)
  if (hex) {
    let h = hex[1]
    if (h.length === 3) h = h.split('').map(c => c + c).join('')
    return [0, 2, 4].map(i => parseInt(h.slice(i, i + 2), 16)).concat(1)
  }
  const rgb = v.match(RGB)
  if (rgb) {
    const p = rgb[1].split(/[,\s/]+/).filter(Boolean).map(Number)
    return [p[0], p[1], p[2], p.length > 3 ? p[3] : 1]
  }
  throw new Error('not a colour: ' + value)
}

/** an alpha colour painted onto an opaque one */
export function composite(fg, bg) {
  return [0, 1, 2].map(i => Math.round(fg[i] * fg[3] + bg[i] * (1 - fg[3])))
}

/** WCAG relative luminance of an opaque colour */
export function luminance(rgb) {
  const s = rgb.slice(0, 3).map(v => {
    const c = v / 255
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
  })
  return 0.2126 * s[0] + 0.7152 * s[1] + 0.0722 * s[2]
}

/** WCAG contrast ratio of two opaque colours */
export function contrastRatio(a, b) {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}

/** ratio of a colour as CSS writes it (hex or rgba) against a background */
export function contrastOn(fgValue, bgValue) {
  const bg = parseColor(bgValue).slice(0, 3)
  const fg = parseColor(fgValue)
  return contrastRatio(fg[3] === 1 ? fg : composite(fg, bg), bg)
}

/**
 * Every `--name: value` declared for `selector`, over all of its blocks, merged in source
 * order — the way the cascade treats a selector that is written more than once (this file has
 * a shape block, a motion block, a legacy-mapping block and then the generated schemes).
 */
export function readCustomProperties(css, selector) {
  const vars = {}
  for (const form of [selector + ' {', selector + '{']) {
    for (let from = 0; ;) {
      const at = css.indexOf(form, from)
      if (at < 0) break
      const open = css.indexOf('{', at)
      const body = css.slice(open + 1, css.indexOf('}', open)).replace(/\/\*[\s\S]*?\*\//g, '')
      for (const m of body.matchAll(/(--[a-z0-9-]+)\s*:\s*([^;]+);/gi)) vars[m[1]] = m[2].trim()
      from = open + 1
    }
  }
  if (!Object.keys(vars).length) throw new Error('no block for ' + selector)
  return vars
}

/** follow `var(--x)` chains until every value is a literal */
export function resolveCustomProperties(vars, hops = 8) {
  const out = { ...vars }
  for (let i = 0; i < hops; i++) {
    let changed = false
    for (const [k, v] of Object.entries(out)) {
      const m = String(v).match(/^var\((--[a-z0-9-]+)(?:\s*,\s*([^)]+))?\)$/i)
      if (!m) continue
      const next = out[m[1]] ?? m[2]
      if (next != null && next !== v) { out[k] = next.trim(); changed = true }
    }
    if (!changed) break
  }
  return out
}
