/* ============================================================================
   OlyGym — M3 colour schemes
   ----------------------------------------------------------------------------
   Regenerates the colour section of frontend/src/m3.tokens.css from the eight
   accent seeds, using the Material 3 rule: a role is a *tone* of the seed's hue,
   where tone is CIELAB L* (0 black, 100 white), with the chroma pulled in until
   the colour exists in sRGB.

   Run:  node scripts/design/m3-scheme.mjs            (writes the token file)
         node scripts/design/m3-scheme.mjs --check    (prints the ratios only)

   Checked against the published baseline: seed #6750A4 gives primary #6750a4 in
   light (exact), #d2bbff in dark (spec #D0BCFF) and containers #e9ddff / #523d8e
   (spec #EADDFF / #4F378B) — CIELAB rather than CAM16/HCT, so a point or two off.

   Every text and edge tone is chosen by measurement, not by taste: the generator
   walks tones until the WCAG ratio is met, so a new seed cannot ship an
   unreadable palette. src/lib/contrast.test.js re-checks the result from the
   written CSS.
   ============================================================================ */
import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const HERE = dirname(fileURLToPath(import.meta.url))
const TOKENS = resolve(HERE, '../../frontend/src/m3.tokens.css')

/* ---------- sRGB <-> CIELAB (D65) ---------- */
const hex2rgb = h => { h = h.replace('#', ''); return [0, 2, 4].map(i => parseInt(h.slice(i, i + 2), 16)) }
const rgb2hex = r => '#' + r.map(v => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')).join('')
const srgb2lin = c => { c /= 255; return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4) }
const lin2srgb = c => 255 * (c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055)
const WHITE = [0.95047, 1.0, 1.08883]
function rgb2lab(rgb) {
  const [r, g, b] = rgb.map(srgb2lin)
  const f = t => t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116
  const [fx, fy, fz] = [
    f((0.4124 * r + 0.3576 * g + 0.1805 * b) / WHITE[0]),
    f((0.2126 * r + 0.7152 * g + 0.0722 * b) / WHITE[1]),
    f((0.0193 * r + 0.1192 * g + 0.9505 * b) / WHITE[2]),
  ]
  return [116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)]
}
function lab2rgbRaw([L, a, b]) {
  const fy = (L + 16) / 116, fx = fy + a / 500, fz = fy - b / 200
  const inv = t => t > 0.206897 ? t * t * t : (t - 16 / 116) / 7.787
  const X = inv(fx) * WHITE[0], Y = inv(fy) * WHITE[1], Z = inv(fz) * WHITE[2]
  return [
    3.2406 * X - 1.5372 * Y - 0.4986 * Z,
    -0.9689 * X + 1.8758 * Y + 0.0415 * Z,
    0.0557 * X - 0.2040 * Y + 1.0570 * Z,
  ].map(lin2srgb)
}
const inGamut = rgb => rgb.every(v => v >= -0.5 && v <= 255.5)
const tone = (seed, L, chroma = 1) => {
  const [, a0, b0] = rgb2lab(hex2rgb(seed))
  let lo = 0, hi = 1
  for (let i = 0; i < 22; i++) {
    const t = (lo + hi) / 2
    if (inGamut(lab2rgbRaw([L, a0 * chroma * t, b0 * chroma * t]))) lo = t; else hi = t
  }
  return rgb2hex(lab2rgbRaw([L, a0 * chroma * lo, b0 * chroma * lo]).map(v => Math.max(0, Math.min(255, Math.round(v)))))
}
const contrast = (a, b) => {
  const lum = h => { const s = hex2rgb(h).map(srgb2lin); return 0.2126 * s[0] + 0.7152 * s[1] + 0.0722 * s[2] }
  const [x, y] = [lum(a), lum(b)].sort((m, n) => n - m)
  return (x + 0.05) / (y + 0.05)
}
const r2 = n => Math.round(n * 100) / 100

/* M3's tone for the role, and only if that tone cannot do its job does the walk move: outward
   from the canonical value until the ratio is met. (Starting at the *edge* of the target instead
   put body text at the palest tone that technically passed, which read as grey mud.) */
function atTone(seed, canonical, bg, target, { chroma = 1, step = 2, limit = 100 }) {
  const fit = L => contrast(tone(seed, L, chroma), bg) >= target
  if (fit(canonical)) return tone(seed, canonical, chroma)
  const down = step < 0
  for (let L = canonical; down ? L >= 0 : L <= limit; L += step) if (fit(L)) return tone(seed, L, chroma)
  return tone(seed, canonical, chroma)
}

/* ---------- the seeds (tone-40 values of M3-typical hues) ---------- */
export const SEEDS = {
  lime: '#146C2E', sky: '#0B57D0', teal: '#006A6A', violet: '#6750A4',
  pink: '#984061', red: '#B3261E', orange: '#8F4C00', gold: '#7A5900',
}
export const DEFAULT_ACCENT = 'violet'
const NEUTRAL = 0.10   // how much of the seed the neutrals keep: M3 neutral, not grey

// M3 keeps one error hue for every scheme — a red is only recognisable as a warning if it stays
// the same red. Published baseline values, so a destructive row looks the same under any accent.
const ERROR = {
  light: { error: '#B3261E', onError: '#FFFFFF', errorContainer: '#F9DEDC', onErrorContainer: '#410E0B' },
  dark: { error: '#F2B8B5', onError: '#601410', errorContainer: '#8C1D18', onErrorContainer: '#F9DEDC' },
}

const TARGET = { body: 7, text: 4.5, edge: 3 }

/* ---------- one scheme ---------- */
export function scheme(seed, theme) {
  const n = L => tone(seed, L, NEUTRAL)
  const p = L => tone(seed, L, 1)
  const light = theme === 'light'
  const err = ERROR[light ? 'light' : 'dark']
  const s = light
    ? { surface: n(98), low: n(96), container: n(100), high: n(92), highest: n(90) }
    : { surface: n(6), low: n(10), container: n(14), high: n(18), highest: n(24) }
  const walk = { step: light ? -2 : 2 }
  // M3's canonical tones first (10/30/50/40 light, 90/80/60/80 dark); the walk is only a
  // safety net for a seed whose hue makes the canonical tone too weak.
  const primary = atTone(seed, light ? 40 : 80, s.high, TARGET.text, { chroma: 1, ...walk })
  const primaryContainer = p(light ? 90 : 30)
  // on-primary is M3's tone 100 in light and tone 20 in dark — a tint of the seed rather than
  // white/black — as long as it can be read; otherwise the better of the two absolutes.
  const onPrimaryTone = p(light ? 100 : 20)
  const white = contrast('#ffffff', primary), black = contrast('#000000', primary)
  const onPrimary = contrast(onPrimaryTone, primary) >= TARGET.text
    ? onPrimaryTone
    : (white >= black ? '#ffffff' : '#000000')
  return {
    ...s,
    onSurface: atTone(seed, light ? 10 : 90, s.high, TARGET.body, { chroma: NEUTRAL, ...walk }),
    onSurfaceVariant: atTone(seed, light ? 30 : 80, s.high, TARGET.text, { chroma: NEUTRAL, ...walk }),
    // Decorative + disabled only, never text — so it is deliberately *below* the 4.5:1 floor
    // (a tone 30 would be the secondary-text tone in light and would read as a label).
    onSurfaceDisabled: n(light ? 75 : 30),
    outline: atTone(seed, light ? 50 : 60, s.high, TARGET.edge, { chroma: NEUTRAL, ...walk }),
    outlineVariant: n(light ? 80 : 30),         // dividers
    hairline: n(light ? 16 : 20),
    primary,
    onPrimary,
    primaryContainer,
    onPrimaryContainer: atTone(seed, light ? 10 : 90, primaryContainer, TARGET.text, { chroma: 1, step: light ? -2 : 2 }),
    error: err.error,
    onError: err.onError,
    errorContainer: err.errorContainer,
    onErrorContainer: err.onErrorContainer,
  }
}

/* ---------- what the generator promises about every scheme ---------- */
export function audit(name, theme, s) {
  const checks = [
    ['body on a card', s.onSurface, s.container, TARGET.body],
    ['body on a nested fill', s.onSurface, s.high, TARGET.body],
    ['secondary on a card', s.onSurfaceVariant, s.container, TARGET.text],
    ['secondary on a nested fill', s.onSurfaceVariant, s.high, TARGET.text],
    ['accent as text on a card', s.primary, s.container, TARGET.text],
    ['accent as text on a nested fill', s.primary, s.high, TARGET.text],
    ['outline on a card', s.outline, s.container, TARGET.edge],
    ['outline on a nested fill', s.outline, s.high, TARGET.edge],
    ['label on an accent fill', s.onPrimary, s.primary, TARGET.text],
    ['label on an accent container', s.onPrimaryContainer, s.primaryContainer, TARGET.text],
    ['error as text on a card', s.error, s.container, TARGET.text],
    ['label on an error fill', s.onError, s.error, TARGET.text],
    ['label on an error container', s.onErrorContainer, s.errorContainer, TARGET.text],
  ]
  return checks.map(([what, a, b, target]) => ({ name, theme, what, ratio: r2(contrast(a, b)), target, ok: contrast(a, b) >= target }))
}

/* ---------- the file ---------- */
const block = (selector, s, indent = '  ') => {
  const rows = [
    ['--m3-surface', s.surface], ['--m3-surface-container-low', s.low], ['--m3-surface-container', s.container],
    ['--m3-surface-container-high', s.high], ['--m3-surface-container-highest', s.highest],
    ['--m3-on-surface', s.onSurface], ['--m3-on-surface-variant', s.onSurfaceVariant],
    ['--m3-on-surface-disabled', s.onSurfaceDisabled], ['--m3-hairline', s.hairline],
    ['--m3-outline', s.outline], ['--m3-outline-variant', s.outlineVariant],
    ['--m3-primary', s.primary], ['--m3-on-primary', s.onPrimary],
    ['--m3-primary-container', s.primaryContainer], ['--m3-on-primary-container', s.onPrimaryContainer],
    ['--m3-error', s.error], ['--m3-on-error', s.onError],
    ['--m3-error-container', s.errorContainer], ['--m3-on-error-container', s.onErrorContainer],
  ]
  const width = Math.max(...rows.map(([k]) => k.length))
  return selector + '{\n' + rows.map(([k, v]) => indent + k.padEnd(width) + ': ' + v + ';').join('\n')
    + '\n' + indent + '--acc: var(--m3-primary); --on-acc: var(--m3-on-primary); --red: var(--m3-error);\n}'
}

export function colourSection() {
  const defaultScheme = { light: scheme(SEEDS[DEFAULT_ACCENT], 'light'), dark: scheme(SEEDS[DEFAULT_ACCENT], 'dark') }
  const parts = []
  parts.push('/* == begin generated: colour schemes — scripts/design/m3-scheme.mjs == */')
  parts.push('/* The default is the M3 baseline purple (' + SEEDS[DEFAULT_ACCENT] + '). Every other accent re-declares')
  parts.push('   the same roles, so a scheme is always complete: swap --acc and the whole UI follows. */')
  parts.push('')
  parts.push(':root ' + block('', defaultScheme.dark).replace(/^\{/, '{').replace(/\n\}/, '\n}').replace('{\n', '{\n').replace('}', '}').replace('^', ''))
  parts.push(':root[data-theme="light"] ' + block('', defaultScheme.light).trimStart())
  for (const [key, seed] of Object.entries(SEEDS)) {
    parts.push(':root[data-accent="' + key + '"] ' + block('', scheme(seed, 'dark')).trimStart())
  }
  for (const [key, seed] of Object.entries(SEEDS)) {
    parts.push(':root[data-theme="light"][data-accent="' + key + '"] ' + block('', scheme(seed, 'light')).trimStart())
  }
  parts.push('/* == end generated == */')
  return parts.join('\n')
}

/* ---------- run ---------- */
const checkOnly = process.argv.includes('--check')
const rows = []
for (const [key, seed] of Object.entries(SEEDS)) for (const theme of ['light', 'dark']) rows.push(...audit(key, theme, scheme(seed, theme)))
const failed = rows.filter(r => !r.ok)
for (const theme of ['light', 'dark']) {
  const worst = rows.filter(r => r.theme === theme).sort((a, b) => (a.ratio / a.target) - (b.ratio / b.target))[0]
  console.log(theme + ': ' + rows.filter(r => r.theme === theme).length + ' pairs checked, worst is ' + worst.name + ' — ' + worst.what + ' at ' + worst.ratio + ':1 (target ' + worst.target + ')')
}
console.log(failed.length ? 'FAILURES:\n' + failed.map(f => '  ' + f.name + ' ' + f.theme + ' ' + f.what + ' ' + f.ratio).join('\n') : 'all ' + rows.length + ' pairs pass')
if (!checkOnly) {
  const css = readFileSync(TOKENS, 'utf8')
  const start = css.indexOf('/* == begin generated: colour schemes')
  const end = css.indexOf('/* == end generated == */')
  if (start < 0 || end < 0) throw new Error('the generated markers are missing from ' + TOKENS)
  writeFileSync(TOKENS, css.slice(0, start) + colourSection() + css.slice(end + '/* == end generated == */'.length))
  console.log('wrote ' + TOKENS)
}
