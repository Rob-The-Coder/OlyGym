// Match the coach's Italian to an exercise in the catalogue.
//
// The coach writes in gym Italian: one idea has three names, the words arrive in any order
// ("tirate strappo alte" is a snatch high pull) and a good half of the modifiers — "no piedi",
// "touch n go", "sosp bassa" — have no Catalyst equivalent at all. So this is not a dictionary
// lookup: a name is turned into the catalogue's words, the catalogue is searched for the entry
// that covers the most of them, and whatever it cannot cover stays in the note in the coach's
// own words.
//
// Three outcomes, the three tiers agreed before this was written (OLYGYM_PLAN.md WS5):
//   1. the catalogue has it                    → the exercise, and the coach's words only when the
//                                                catalogue name says a word he never said
//   2. the base lift exists, the rest does not → the base, plus the coach's words in the note
//   3. nothing at all                          → a custom exercise under the coach's own name
// plus the shapes that are not exercises on their own: a *fragment* ("+ sosp bassa", "touch n go",
// "due normali") describes the exercise before it, so it lands in that exercise's note rather than
// inventing a second one.

import { EXDB } from './exercises.js'
import { splitComplex } from './coach-sheet.js'

/** Lower case, no accents, no punctuation: the key both the matcher and S.planAliases use. */
export function normName(text) {
  return String(text == null ? '' : text)
    .toLowerCase()
    .normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

// What the coach says -> what the catalogue says. Longest phrase first, so "spinta in piedi" is a
// push jerk and not a "spinta" plus an "in piedi". An empty value is a word that carries no
// exercise meaning ("di", "secondi", "kg") and is dropped.
const GLOSSARY = {
  // heads — the lift itself
  strappo: 'snatch', strappi: 'snatch', girata: 'clean', girate: 'clean', slancio: 'clean-jerk',
  tirata: 'pull', tirate: 'pull', stacco: 'deadlift', stacchi: 'deadlift',
  'gambe avanti': 'front squat', 'gambe dietro': 'back squat',
  rdl: 'romanian deadlift rdl',
  'spinta di forza': 'push press', 'spinte di forza': 'push press',
  'spinta in piedi': 'push jerk', 'spinte in piedi': 'push jerk',
  'spinta in spaccata': 'split jerk', 'spinte in spaccata': 'split jerk',
  'spinta dalla mezza spaccata': 'push jerk', 'spinte strappo no piedi': 'snatch push press',
  'snatch press': 'snatch press', 'sots press': 'sots press', 'oh squat': 'overhead squat',
  'snatch balance': 'snatch balance', 'push press': 'push press', 'push jerk': 'push jerk',
  'power jerk': 'power jerk', 'power clean': 'power clean', 'hip snatch': 'hip snatch',
  'military press con bilanciere': 'press',
  'piegamenti alle parallele': 'dip', 'piegamenti alla parallele': 'dip', parallele: 'dip',
  piegamenti: 'dip', trazioni: 'pull-up', trazione: 'pull-up',
  'trazioni con elastico': 'pull-up', 'chin ups': 'chin-up', 'chin up': 'chin-up',
  'alzate laterali': 'dumbbell lateral raise', 'alzate a y con disco': 'y raise',
  'rematore con manubrio': 'single arm dumbbell row',
  'stacchi rumeni ad una gamba': 'single leg romanian deadlift',
  'stacchi rumeni a una gamba': 'single leg romanian deadlift',
  'stacchi rumeni': 'romanian deadlift rdl',
  'good morning': 'good morning', plank: 'plank', 'dead bug': 'dead bug',
  'box jump': 'box jump', 'pogo jump': 'pogo jump', 'lu raises': 'lu raise', 'lu raise': 'lu raise',
  'seesaw bent over row': 'seesaw bent over row', pullover: 'pullover',
  'one arm pullover': 'pullover',
  'clamshell side plank': 'side plank clamshell', 'side bend alla spalliera': 'side bend',
  'wall sit': 'wall sit',
  // modifiers — where the bar starts, how fast, what is paused
  'sosp alta': 'hang', 'sosp bassa': 'low hang', sosp: 'hang', 'da sosp': 'hang',
  'dai blocchi': 'block', blocchi: 'block', 'da blocco': 'block',
  'dal deficit': 'on riser', 'da deficit': 'on riser', deficit: 'on riser',
  'no piedi': 'no foot', 'touch n go': 'touch and go', tng: 'touch and go',
  'in continuita': 'touch and go', 'con ritorno': '', 'in piedi': 'from power position',
  inguine: 'from power position', spaccata: 'split', 'spinta strappo': 'snatch push press',
  'spinte strappo': 'snatch push press', spinta: 'jerk', spinte: 'jerk',
  incastro: '', cavalletti: 'rack', carico: '',
  'in buca': 'pause', 'stop in buca': 'pause', 'stop sopra ginocchio': 'pause',
  'stop pre': 'pause', 'stop post': 'pause', stop: 'pause', pausa: 'pause', fermo: 'pause',
  alta: 'high', alte: 'high', georgiane: 'georgian', velocita: 'speed',
  'strappo di forza': 'muscle snatch', 'strappi di forza': 'muscle snatch',
  'girata di forza': 'muscle clean', 'girate di forza': 'muscle clean',
  // words that are structure, tempo or noise rather than an exercise
  e: 'and', di: '', del: '', della: '', delle: '', dalla: '', dal: '', da: '', dai: '',
  con: 'with', presa: '', muovere: '', muovo: '',
  senza: 'without', una: '', un: '', due: '', tre: '', secondi: '', each: '', a: '', al: '',
  ai: '', alla: '', allo: '', in: '', il: '', la: '', le: '', lo: '', gli: '', ma: '', fino: '',
  sopra: '', sotto: '', testa: '', petto: '', disco: '', caricamento: '', parallelo: 'parallel',
  parallela: 'parallel', roba: '', stessa: '', stesso: '', normale: '', normali: '', kg: '',
  max: '', uscita: '', discesa: '', salita: '', risalita: '', lenta: 'slow', lentissima: 'slow',
  lentissimo: 'slow', controllata: '', controllato: '', dinamiche: '', dinamica: '',
  impugnatura: 'grip', media: '', 'dietro la testa': 'behind the neck', ginocchio: '', gin: '',
  buca: '', prima: '', pre: '', post: '', fase: '', piedi: ''
}

// Words the translation drops that still say something about how to lift — a tempo, a paused
// position, a plate. A name holding one of these is never a plain tier 1: the catalogue has the
// lift but not what the coach asked for, and in a year nobody remembers which of the two it was.
const MEANINGFUL = new Set([
  'dinamiche', 'dinamica', 'lentissima', 'lentissimo', 'lenta', 'lento', 'discesa', 'salita',
  'risalita', 'velocita', 'uscita', 'ritorno', 'caricamento', 'controllata', 'controllato',
  'impugnatura', 'parallelo', 'testa', 'petto', 'disco', 'max', 'continuita', 'ginocchio', 'gin',
  'buca', 'sopra', 'sotto', 'prima', 'pre', 'post', 'fermo', 'ferma', 'isometrico'
])

// Words that mean "this is an exercise", in the coach's language or in the catalogue's. A
// component with none of these describes the exercise above it instead of naming a new one.
const HEAD_WORDS = new Set([
  'strappo', 'strappi', 'girata', 'girate', 'slancio', 'tirata', 'tirate', 'stacchi', 'stacco',
  'gambe', 'rdl', 'spinta', 'spinte', 'piegamenti', 'trazioni', 'alzate', 'rematore',
  'snatch', 'clean', 'jerk', 'press', 'pull', 'push', 'squat', 'deadlift', 'plank', 'good',
  'box', 'pogo', 'dead', 'wall', 'sots', 'oh', 'seesaw', 'lu', 'chin', 'military', 'bug',
  'jump', 'raise', 'raises', 'row', 'pullover', 'dip', 'bend', 'morning', 'balance', 'sit',
  'clamshell', 'side'
])

// Words that carry no exercise at all in a catalogue name: they never have to be covered, and they
// never count as something the coach did not say.
const FUNCTION = new Set(['and', 'with', 'from', 'the', 'of', 'in', 'on', 'to'])

// With a pull, a deadlift or an RDL the second word names the grip, and "slancio" there is the
// clean rather than the whole clean and jerk: "tirate slancio" is a clean pull and "stacchi
// strappo" a snatch deadlift. Read as "girata" each lands on the catalogue name by itself.
const GRIP_HEAD = /\b(stacchi|stacco|tirata|tirate|rdl)\b/

// The body part a custom exercise gets, since there is no catalogue entry to copy one from.
const CUSTOM_BP = {
  'y raise': 'Accessory - Upper Body',
  'lu raise': 'Accessory - Upper Body',
  pullover: 'Accessory - Upper Body',
  'seesaw bent over row': 'Accessory - Upper Body',
  'pogo jump': 'Jumping & Plyometrics',
  'single leg romanian deadlift': 'Accessory - Lower/Whole Body'
}
const DEFAULT_BP = 'General Exercises'

const PHRASES = Object.keys(GLOSSARY).filter(k => k.includes(' ')).sort((a, b) => b.length - a.length)
const SINGLES = Object.keys(GLOSSARY).filter(k => !k.includes(' ')).sort((a, b) => b.length - a.length)

let index = null
function catalogueIndex() {
  if (!index) {
    index = []
    const seen = new Set()
    for (const ex of EXDB) {
      const n = normName(ex.n)
      // The catalogue holds a couple of duplicate names (clean-jerk twice). The first wins, the
      // way the exercise id index behaves everywhere else.
      if (seen.has(n)) continue
      seen.add(n)
      index.push({ ex, n, words: n.split(' ') })
    }
  }
  return index
}

const exactName = candidate => {
  const n = normName(candidate)
  return n ? (catalogueIndex().find(e => e.n === n) || null) : null
}

const words = text => normName(text).split(' ').filter(Boolean)

// A rep count the coach wrote ("1-2 secondi", "3 dinamiche") is not an exercise word, so a bare
// "3" can never match. Numbers stay in the *catalogue's* names, where they tell "1-14 front squat"
// apart from a plain front squat.
const exerciseWords = list => list.filter(w => !FUNCTION.has(w) && !/^\d+$/.test(w))

/** How many of `wanted` the entry's own words account for, and how many it says on top. */
function coverage(entry, wanted) {
  const pool = entry.words.filter(w => !FUNCTION.has(w))
  let cover = 0
  for (const w of wanted) {
    const at = pool.indexOf(w)
    if (at < 0) continue
    pool.splice(at, 1)
    cover++
  }
  return { cover, extra: entry.words.filter(w => !FUNCTION.has(w)).length - cover }
}

/**
 * The catalogue entry covering the most of these words, then the one adding the fewest — word
 * order does not matter ("pull snatch high" is a snatch high pull), and the catalogue's own order
 * settles the ties, which puts the snatch family before the clean family: the right bias here.
 *
 * With `plain` only names the coach's words fully account for are considered. That is what keeps a
 * half-understood name on its own lift: "strappo con fase lenta" must not become the first
 * catalogue name that happens to contain both *slow* and *snatch*.
 */
function bestMatch(parts, plain = false) {
  const wanted = exerciseWords(parts)
  if (!wanted.length) return null
  let best = null
  let bestCover = 0
  let bestExtra = Infinity
  for (const entry of catalogueIndex()) {
    const { cover, extra } = coverage(entry, wanted)
    if (!cover || (plain && extra)) continue
    if (cover > bestCover || (cover === bestCover && extra < bestExtra)) {
      best = { entry, cover, extra }
      bestCover = cover
      bestExtra = extra
    }
  }
  return best
}

function translate(text) {
  const gripped = GRIP_HEAD.test(normName(text)) ? String(text).replace(/\bslancio\b/g, 'girata') : text
  let s = ' ' + normName(gripped) + ' '
  for (const p of PHRASES) s = s.split(' ' + p + ' ').join(' ' + GLOSSARY[p] + ' ')
  return s.split(' ').map(w => (w in GLOSSARY ? GLOSSARY[w] : w)).filter(Boolean).join(' ')
}

/**
 * The lift a component starts with, and whatever the coach wrote after it — the base a partly
 * understood name falls back to, and whether there is a modifier beyond that base at all.
 */
function headOf(text) {
  const n = normName(text)
  for (const p of [...PHRASES, ...SINGLES]) {
    if (n !== p && !n.startsWith(p + ' ')) continue
    return { english: GLOSSARY[p], rest: n.slice(p.length).trim() }
  }
  return { english: '', rest: n }
}

const hasHead = text => words(text).some(w => HEAD_WORDS.has(w))
const carriesMeaning = text => words(text).some(w => MEANINGFUL.has(w))

/**
 * Match one component of the coach's name.
 *
 *   { tier: 1|2|3, id, name, bp, note, exact, raw }
 *
 * or `{ fragment: true, raw }` when it is not an exercise but a note on the one before it.
 * `aliases` is S.planAliases: a component the user corrected on the review screen, keyed by
 * normName, wins over the matcher — which is what makes a fix stick for every later week.
 */
export function matchComponent(raw, aliases = {}) {
  const text = String(raw == null ? '' : raw).trim()
  if (!text) return { fragment: true, raw: text }
  const corrected = aliases[normName(text)]
  const fixed = corrected ? EXDB.find(e => e.id === corrected) : null
  if (fixed) return { tier: 1, id: fixed.id, name: fixed.n, bp: fixed.bp, exact: true, note: '', raw: text }

  // Checked before any matching, so a bare "3 dinamiche", "touch n go" or "sosp bassa" can never
  // land on a catalogue name that happens to share a word with it.
  if (!hasHead(text)) return { fragment: true, raw: text }

  const candidate = translate(text)
  // A translation that names a catalogue entry outright is the answer, before any word-overlap
  // scoring: "spinta in spaccata" is a split jerk, not the "jerk from split" that shares its words.
  const exact = exactName(candidate)
  if (exact) {
    const plain = !carriesMeaning(text)
    return { tier: 1, id: exact.ex.id, name: exact.ex.n, bp: exact.ex.bp, exact: plain, note: plain ? '' : text, raw: text }
  }

  const parts = words(candidate)
  const full = bestMatch(parts)
  if (full && full.cover === exerciseWords(parts).length) {
    const entry = full.entry.ex
    // "Wall sit con disco" and "Gambe avanti prima stop in buca" match a catalogue name word for
    // word and still ask for something that name does not say. The note is what keeps that on the
    // record, so a name carrying one of those words is never a plain tier 1.
    const plain = !full.extra && !carriesMeaning(text)
    return {
      tier: 1,
      id: entry.id,
      name: entry.n,
      bp: entry.bp,
      exact: plain,
      note: plain ? '' : text,
      raw: text
    }
  }

  // Half understood: the best name the coach's own words fully account for, else the head lift.
  // Deliberately not "the name sharing the most words": a head that resolves to nothing ("y raise",
  // "pogo jump") is a custom exercise, not the first catalogue name containing *raise*. The head is
  // only allowed to pick a longer name when the coach wrote something past it to leave in the note.
  const head = headOf(text)
  const exactHead = exactName(head.english)
  // When the component is nothing but a head ("Pogo jump", "y raise") the catalogue either has
  // that name or has nothing: sharing one word with a longer name is not an answer.
  const whole = !head.rest
  const base = whole
    ? (exactHead ? { entry: exactHead } : null)
    : bestMatch(parts, true) ||
      (exactHead ? { entry: exactHead } : null) ||
      bestMatch(words(head.english), true) ||
      bestMatch(words(head.english))
  if (base) {
    const entry = base.entry.ex
    return { tier: 2, id: entry.id, name: entry.n, bp: entry.bp, exact: false, note: text, raw: text }
  }

  return {
    tier: 3,
    id: '',
    name: text,
    bp: CUSTOM_BP[normName(translate(text))] || CUSTOM_BP[normName(text)] || DEFAULT_BP,
    exact: false,
    note: '',
    raw: text
  }
}

/**
 * Match a whole row: the complexes split on "+", a component that names no exercise folded into
 * the note of the one before it.
 *
 *   { items: [{ tier, id, name, bp, note, exact, raw }], ignored: [raw] }
 *
 * `items` is what becomes the routine's exercises — in order, one per component, sharing a
 * superset group when the row had more than one.
 */
export function matchName(rawName, aliases = {}) {
  const items = []
  const ignored = []
  for (const part of splitComplex(rawName)) {
    const hit = matchComponent(part, aliases)
    if (hit.fragment) {
      const last = items[items.length - 1]
      // "+ sosp bassa", "touch n go", "due normali": the coach describing what he just named.
      if (last) last.note = [last.note, part].filter(Boolean).join(' · ')
      else ignored.push(part)
      continue
    }
    items.push({ tier: hit.tier, id: hit.id, name: hit.name, bp: hit.bp, note: hit.note, exact: hit.exact, raw: hit.raw })
  }
  return { items, ignored }
}
