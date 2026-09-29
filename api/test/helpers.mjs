/* Shared scaffolding for the api tests.
 *
 * Every module under coach/ resolves DATA_DIR at import time (the same way server.js does),
 * so a test that wants its own data directory has to set the variable before the first
 * import. Hence dynamic imports everywhere below, and one helper that does it in the right
 * order. node:test runs each file in its own process, so one directory per file is enough.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

/* Fixture ids come from the catalogue, never from memory. The dataset was swapped once
   (WS1 — the old ExerciseDB rows became Catalyst Athletics'), and a hardcoded id is a test
   that then fails for the wrong reason: the validator resolves every id against the real
   library, so an id that no longer exists makes a well-formed payload look invalid. Picking
   by name means a future swap breaks this file loudly, in one place, with the name in hand. */
import { LIBRARY } from '../coach/core/library.js';

const byName = name => {
  const found = LIBRARY.find(e => e.n === name);
  if (!found) throw new Error(`fixture: "${name}" is not in the catalogue any more — pick another name here`);
  return found.id;
};

export const EX_LOADED = byName('back squat');            // the slot with a weight, reps and history
export const EX_TIMED = byName('plank');                  // the slot logged in seconds
export const EX_SIDE = byName('single arm dumbbell row'); // a per-side movement
export const EX_OTHER = byName('bench press');            // a second distinct lift
export const EX_THIRD = byName('dumbbell lateral raise'); // a third distinct id

export function tempData() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'coach-test-'));
  fs.writeFileSync(path.join(dir, 'secret'), 'a'.repeat(64), { mode: 0o600 });
  process.env.DATA_DIR = dir;
  return dir;
}

export function writeState(dir, uid, S) {
  fs.writeFileSync(path.join(dir, 'state-' + uid + '.json'), JSON.stringify(S));
}

/** A profile that has consented and has some history — the usual starting point. */
export function sampleState(over = {}) {
  return {
    unit: 'kg', lang: 'en', effort: 'rpe', targetW: 80,
    coach: { consent: { agreedAt: new Date().toISOString(), version: 1 }, profile: { goal: 'muscle', daysPerWeek: 3, equipment: ['dumbbell'] } },
    routines: [{
      id: 'r1', name: 'Full body A', emoji: '💪', prog: 'linear',
      ex: [
        { id: EX_LOADED, sets: 3, reps: 10, mode: 'reps', weight: 20, prog: 'linear' },
        { id: EX_TIMED, sets: 3, sec: 45, mode: 'time' }
      ]
    }],
    week: { 1: 'r1', 3: 'r1', 5: 'r1' },
    dayPlan: {},
    exWeights: { [EX_LOADED]: { w: 20 } },
    bodyweight: [{ d: '2026-07-01', w: 78 }, { d: '2026-07-20', w: 78.5 }],
    customEx: [],
    workouts: [{
      id: 'w1', d: '2026-07-20', name: 'Full body A', start: 1000, end: 1000 + 45 * 60000, vol: 600, prs: [],
      entries: [{
        id: EX_LOADED, target: { sets: 3, reps: 10, weight: 20 },
        sets: [{ w: 20, r: 10, done: true, rpe: 9.5 }, { w: 20, r: 9, done: true, rpe: 10 }, { w: 20, r: 8, done: true, rpe: 10 }]
      }]
    }],
    ...over
  };
}
