// The fallback that reads a movement family off an exercise name when the export has no category
// column (Hevy). First match wins, so the order of the rules is the behaviour — reported on
// Discord by rubik_97 with the three cases below.
//
// The value used to be a body part ('upper legs', 'back', 'waist'); the OlyGym catalogue's `bp` is
// a movement family instead (see OLYGYM_PLAN.md), so the same cases now expect families. What the
// file pins is unchanged: the order of the rules, and that a qualifier is not mistaken for the
// movement.
import { describe, expect, it } from 'vitest'
import { bpFromName } from './import-csv.js'

const bp = name => bpFromName(name.toLowerCase())
const UPPER = 'Accessory - Upper Body'
const LOWER = 'Accessory - Lower/Whole Body'
const TRUNK = 'Trunk (Ab & Back)'
const GENERAL = 'General Exercises'

describe('movement family from an exercise name', () => {
  it('reads a grip as a modifier, not as the movement', () => {
    expect(bp('Chest Supported T Row Neutral Grip')).toBe(UPPER)
    expect(bp('Wide Grip Lat Pulldown')).toBe(UPPER)
    expect(bp('Close Grip Bench Press')).toBe(UPPER)
  })

  it('files wrist and reverse curls with the other arm work, not the upper arms', () => {
    // The family that used to be split into "lower arms" and "upper arms" is one family here: the
    // catalogue's accessory-upper-body bucket covers forearms and biceps alike.
    expect(bp('Wrist Curl')).toBe(UPPER)
    expect(bp('Barbell Reverse Curl')).toBe(UPPER)
    expect(bp('Hammer Curl')).toBe(UPPER)
  })

  it('keeps leg curls and the hamstring deadlifts on the legs', () => {
    expect(bp('Lying Leg Curl')).toBe(LOWER)
    expect(bp('Romanian Deadlift')).toBe(LOWER)
    expect(bp('Stiff Leg Deadlift')).toBe(LOWER)
    expect(bp('RDL')).toBe(LOWER)
    // Main work, not "back": the old rules matched the word "back" first and filed every deadlift
    // and back squat under it. Head words now outrank the equipment words around them.
    expect(bp('Deadlift')).toBe(GENERAL)
  })

  // The rules are written with spaces, but people hyphenate these names as often as not (QA C28).
  // The matcher already treats "chest-supported row" and "chest supported row" as one exercise;
  // the family fallback has to agree with it, or the two spellings land on different maps.
  it('reads the hyphenated, underscored and slashed spellings like the spaced ones', () => {
    expect(bp('Stiff-Legged Deadlift')).toBe(LOWER)
    expect(bp('Straight-Leg Deadlift')).toBe(LOWER)
    expect(bp('Stiff_Leg Deadlift')).toBe(LOWER)
    expect(bp('Chest-Supported Row')).toBe(UPPER)
    expect(bp('Chest-Supported T-Bar Row')).toBe(UPPER)
    expect(bp('Chest/Supported Row')).toBe(UPPER)
    expect(bp('Sit-Up')).toBe(TRUNK)
  })

  it('still reads a bare grip exercise as arm work, and the rest as before', () => {
    expect(bp('Grip Trainer')).toBe(UPPER)
    expect(bp('Back Squat')).toBe(GENERAL)   // a squat is a squat
    expect(bp('Standing Calf Raise')).toBe(LOWER)
    expect(bp('Something Unheard Of')).toBe(null)
  })

  // The catalogue has no cardio entry, and 'cardio' is the only route to the cardio logging mode
  // for an exercise we invent — a burpee has to ask for time and speed, not weight and reps.
  it('still recognises the cardio vocabulary', () => {
    expect(bp('Burpee')).toBe('cardio')
    expect(bp('Treadmill Run')).toBe('cardio')
    expect(bp('Rowing Erg')).toBe('cardio')
  })
})
