import { describe, it, expect } from 'vitest'
import { matchComponent, matchName, normName } from './plan-aliases.js'
import { EXDB } from './exercises-data.js'

// Ids come from the catalogue by name, never written by hand: a rename in the dataset should fail
// here in one place instead of pointing at an id that quietly belongs to something else.
const idOf = name => {
  const ex = EXDB.find(e => e.n === name)
  if (!ex) throw new Error(`"${name}" is not in the catalogue any more`)
  return ex.id
}

const match = (text, aliases) => {
  const hit = matchComponent(text, aliases)
  return { tier: hit.tier, id: hit.id, name: hit.name, note: hit.note, fragment: !!hit.fragment }
}

describe('matchComponent — the confirmed glossary', () => {
  it('reads the heads', () => {
    expect(match('Strappo').id).toBe(idOf('snatch'))
    expect(match('Strappo').tier).toBe(1)
    expect(match('Girata').id).toBe(idOf('clean'))
    expect(match('Slancio').id).toBe(idOf('clean-jerk'))
    expect(match('Gambe avanti').id).toBe(idOf('front squat'))
    expect(match('Gambe dietro').id).toBe(idOf('back squat'))
    expect(match('Piegamenti alle parallele').id).toBe(idOf('dip'))
    expect(match('Piegamenti alla parallele').id).toBe(idOf('dip'))
    expect(match('Trazioni con elastico').id).toBe(idOf('pull-up'))
    expect(match('Chin ups').id).toBe(idOf('chin-up'))
    expect(match('Alzate laterali').id).toBe(idOf('dumbbell lateral raise'))
    expect(match('Rematore con manubrio').id).toBe(idOf('single arm dumbbell row'))
    expect(match('Military press con bilanciere').id).toBe(idOf('press'))
    expect(match('Clamshell side plank').id).toBe(idOf('side plank clamshell'))
    expect(match('Good morning').id).toBe(idOf('good morning'))
    expect(match('Power jerk').id).toBe(idOf('power jerk'))
    expect(match('Sots press').id).toBe(idOf('press in snatch sots press'))
    expect(match('Oh squat').id).toBe(idOf('overhead squat'))
  })

  it('reads the jerks apart from each other', () => {
    expect(match('Spinta di forza').id).toBe(idOf('push press'))
    expect(match('spinte di forza').id).toBe(idOf('push press'))
    expect(match('Spinta in piedi').id).toBe(idOf('push jerk'))
    expect(match('Spinta in spaccata').id).toBe(idOf('split jerk'))
    expect(match('Spinta dalla mezza spaccata').id).toBe(idOf('push jerk'))
    expect(match('Spinte strappo no piedi').id).toBe(idOf('snatch push press'))
  })

  it('reads \u00abdi forza\u00bb as the muscle version, not the power one', () => {
    // The user's correction (2026-09-30): "strappo di forza" is a muscle snatch — the bar never
    // comes back down to the thighs and the knees do not re-bend. It used to read as power snatch.
    expect(match('Strappo di forza').id).toBe(idOf('muscle snatch'))
    expect(match('strappi di forza').id).toBe(idOf('muscle snatch'))
    expect(match('Girata di forza').id).toBe(idOf('muscle clean'))
    // Still a push press: this one the user confirmed, and it is the same two words.
    expect(match('Spinte di forza').id).toBe(idOf('push press'))
  })

  it('keeps the coach\u2019s tempo words on a muscle snatch', () => {
    const paused = match('strappo di forza con pausa sotto e sopra ginocchio di 5 secondi')
    expect(paused.tier).toBe(2)
    expect(paused.id).toBe(idOf('muscle snatch'))
    expect(paused.note).toBe('strappo di forza con pausa sotto e sopra ginocchio di 5 secondi')
  })

  it('takes the grip from the lift that follows a pull, a deadlift or an RDL', () => {
    // «tirate slancio» is a clean pull, not a "pull" plus a clean and jerk.
    expect(match('Tirate slancio').id).toBe(idOf('clean pull'))
    expect(match('Tirate strappo').id).toBe(idOf('snatch pull'))
    expect(match('Tirata alta strappo').id).toBe(idOf('snatch high pull'))
    expect(match('Tirate strappo alte').id).toBe(idOf('snatch high pull'))
    expect(match('Stacchi slancio').id).toBe(idOf('clean deadlift'))
    expect(match('Stacchi strappo').id).toBe(idOf('snatch deadlift'))
    expect(match('Stacchi strappo dal deficit').id).toBe(idOf('snatch deadlift on riser'))
    expect(match('Stacchi slancio da deficit').id).toBe(idOf('clean deadlift on riser'))
    expect(match('RDL strappo').id).toBe(idOf('snatch grip romanian deadlift (rdl)'))
    expect(match('RDL slancio').id).toBe(idOf('romanian deadlift (rdl)'))
  })

  it('reads the modifiers that the catalogue does have', () => {
    expect(match('Strappo da sosp alta').id).toBe(idOf('hang snatch'))
    expect(match('Strappo dai blocchi').id).toBe(idOf('block snatch'))
    expect(match('Strappo dall\u2019inguine').id).toBe(idOf('snatch from power position'))
    expect(match('Strappo in piedi').id).toBe(idOf('snatch from power position'))
    expect(match('Gambe avanti stop in buca').id).toBe(idOf('pause front squat'))
    expect(match('Gambe dietro stop a parallelo').id).toBe(idOf('pause parallel back squat'))
    expect(match('Strappo no contact').id).toBe(idOf('snatch with no contact'))
  })

  it('keeps a catalogue name that says more than the coach did, without a note', () => {
    // "Strappo no contact" *is* "snatch with no contact": nothing was lost, so nothing goes in
    // the note, and the word order of "tirate strappo alte" is the catalogue's business.
    expect(match('Strappo no contact').note).toBe('')
    expect(match('Tirate strappo alte').note).toBe('')
  })
})

describe('matchComponent — the three tiers', () => {
  it('level 2: the base lift plus the coach\u2019s own words', () => {
    const noFeet = match('Strappo no piedi')
    expect(noFeet.tier).toBe(2)
    expect(noFeet.id).toBe(idOf('snatch'))
    expect(noFeet.note).toBe('Strappo no piedi')

    // "sosp bassa" (a low hang) has no Catalyst name: the catalogue's hang snatch is the base and
    // the coach's words say which hang he wanted.
    const lowHang = match('Strappo sosp bassa')
    expect(lowHang.tier).toBe(2)
    expect(lowHang.id).toBe(idOf('hang snatch'))
    expect(lowHang.note).toBe('Strappo sosp bassa')

    // The catalogue has wall sit, not the plate on the belly.
    const plate = match('Wall sit con disco')
    expect(plate.id).toBe(idOf('wall sit'))
    expect(plate.note).toBe('Wall sit con disco')
    expect(match('Gambe avanti prima stop in buca').note).toBe('Gambe avanti prima stop in buca')
  })

  it('level 3: nothing in the catalogue, so the coach\u2019s name becomes the exercise', () => {
    for (const name of ['Pogo jump', 'Y raise', 'Lu raises', 'one arm pullover', 'Stacchi rumeni ad una gamba']) {
      const hit = match(name)
      expect(hit.tier).toBe(3)
      expect(hit.id).toBe('')
      expect(hit.name).toBe(name)
    }
  })

  it('gives a custom exercise a body part the map can draw', () => {
    expect(match('Pogo jump').name).toBe('Pogo jump')
    expect(matchComponent('Pogo jump').bp).toBe('Jumping & Plyometrics')
    expect(matchComponent('one arm pullover').bp).toBe('Accessory - Upper Body')
  })

  it('a fragment names no exercise of its own', () => {
    expect(match('sosp bassa').fragment).toBe(true)
    expect(match('touch n go').fragment).toBe(true)
    expect(match('due normali').fragment).toBe(true)
    expect(match('3 dinamiche').fragment).toBe(true)
    expect(match('massima velocità in uscita').fragment).toBe(true)
    // …which is also how a bare "3" cannot land on the catalogue's "3 position snatch".
    expect(match('tre in velocità').fragment).toBe(true)
  })
})

describe('matchComponent — corrections', () => {
  it('lets a corrected component win over the matcher', () => {
    const key = normName('Piegamenti alle parallele')
    const aliases = { [key]: idOf('dip') }
    expect(match('Piegamenti alle parallele', aliases).id).toBe(idOf('dip'))
    // A correction keyed to something else is the correction: the matcher does not second-guess it.
    const other = { [key]: idOf('push press') }
    expect(match('Piegamenti alle parallele', other).id).toBe(idOf('push press'))
  })

  it('ignores a correction pointing at an exercise that no longer exists', () => {
    expect(match('Strappo', { strappo: 'wl-does-not-exist' }).id).toBe(idOf('snatch'))
  })
})

describe('matchName', () => {
  it('splits a complex into its exercises', () => {
    const { items } = matchName('Strappo + strappo sosp alta')
    expect(items.map(i => i.id)).toEqual([idOf('snatch'), idOf('hang snatch')])
    expect(items.map(i => i.name)).toEqual(['snatch', 'hang snatch'])
  })

  it('folds a fragment into the exercise before it', () => {
    const { items, ignored } = matchName('Strappo + strappo sosp alta + sosp bassa')
    expect(items).toHaveLength(2)
    expect(items[1].note).toBe('sosp bassa')
    expect(ignored).toEqual([])
  })

  it('reports a fragment that has nothing to attach to', () => {
    const { items, ignored } = matchName('touch n go')
    expect(items).toEqual([])
    expect(ignored).toEqual(['touch n go'])
  })

  it('keeps the order the coach trained in', () => {
    const { items } = matchName('High Pull + Hip snatch + snatch balance + oh squat')
    expect(items.map(i => i.id)).toEqual([
      idOf('snatch high pull'), idOf('hip snatch'), idOf('snatch balance'), idOf('overhead squat')
    ])
  })
})
