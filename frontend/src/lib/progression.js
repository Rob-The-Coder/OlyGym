// Automatic progression (issue #17).
//
// Everything here is a pure function of the workout history. Nothing writes back into a
// finished workout: the log is what happened, and the next prescription is *derived* from
// it every time it is needed.
//
// One policy remains: linear. Hit every rep in every set and the weight goes up; repeated
// misses trigger a deload. It is opt-in now: off unless Settings → Automatic progression turns
// it on, or an exercise names a rule of its own, so a planned weight stays the planned weight.
// Reading a session honestly is the whole game:
//   · a set checked off with at least its target reps  → hit
//   · a set checked off with fewer reps                → miss (you logged what you got)
//   · a set never checked off                          → miss (it was not performed)
//   · fewer sets than prescribed                       → miss
// So a session that fell apart can never advance the load as though it had succeeded.

import { modeOf, rerampWarmups, entryExcluded } from './history.js'
import { EXIDX } from './exercises.js'
import { musclesOf } from './muscles.js'
import { isWarmupRow } from './workout-model.js'

export const POLICIES = ['off', 'linear']

// Which policies can sensibly drive which logging mode.
export const POLICIES_FOR = {
  reps: ['off', 'linear'],
  time: ['off']
}

export const POLICY_NAME = {
  off: 'No automatic progression',
  linear: 'Linear progression'
}
export const POLICY_DESC = {
  off: 'Targets stay where you set them.',
  linear: 'Hit every rep in every set and the weight goes up. Repeated misses trigger a deload.'
}

// Back off by this factor when a linear session stalls.
export const DELOAD_FACTOR = 0.9
export const DELOAD_AFTER = { linear: 3 }

// Muscles where a 5 kg jump is normal rather than brutal: the lower body and the posterior
// chain. Reading the exercise's own muscles keeps the rule honest for any catalogue: a back
// squat is quadriceps-led, a bench press is not, and a competition snatch (trapezius-led)
// stays on the 2.5 kg step the sport actually uses.
const HEAVY_MUSCLES = ['quadriceps', 'gluteal', 'hamstring', 'lower-back', 'adductors', 'calves', 'tibialis']

// The catalogue's exercise objects are stable (EXIDX is built once), so the muscles-to-increment
// lookup is worth caching: the steppers ask for it on every render.
const heavyCache = new WeakMap()
const isHeavy = ex => {
  if (!ex) return false
  let hit = heavyCache.get(ex)
  if (hit === undefined) {
    const weights = musclesOf(ex)
    hit = HEAVY_MUSCLES.some(slug => (weights[slug] || 0) >= 1)
    heavyCache.set(ex, hit)
  }
  return hit
}

// Default load step. Lower-body lifts take the bigger jump — that is the "lift-specific
// increment" a linear program lives on; an exercise can override it with cfg.inc.
export function defaultIncrement(exId) {
  return isHeavy(EXIDX[exId]) ? 5 : 2.5
}
// Resolve the load step for reps-mode weight controls and progression.
export function weightIncrement(cfg) {
  return cfg && cfg.inc > 0 ? cfg.inc : defaultIncrement(cfg?.id)
}

// What automatic progression does when neither the exercise nor its day names a rule: nothing,
// unless the profile switched it on (Settings → During a workout → Automatic progression writes
// S.autoProg). A planned weight is a target, not a starting point to add to.
export const defaultPolicy = S => (S && S.autoProg === true ? 'linear' : 'off')

// The policy in force for one exercise: its own override, else the routine's default, else the
// profile's default. A rule set by hand still beats the setting, in both directions.
export function policyFor(cfg, routine, mode, fallback = 'off') {
  const m = mode || modeOf(cfg || {})
  const allowed = POLICIES_FOR[m] || ['off']
  const pick = (cfg && cfg.prog) || (routine && routine.prog) || fallback
  return allowed.includes(pick) ? pick : 'off'
}

const round1 = v => Math.round(v * 10) / 10
// Snap to a loadable multiple of the step. Manual weight controls use this same normalization
// so fractional increments produce the same number as automatic progression.
export function snapWeight(v, step) {
  if (!(step > 0)) return round1(v)
  return round1(Math.round(v / step) * step)
}
// Add `step` to a weight the way a stepper tap does: from a weight that sits on the increment's
// grid the sum is snapped to it, from one off the grid the step is simply added (issue #175).
export function addStep(w, step, inc) {
  const v = +w || 0
  const onGrid = inc > 0 && Math.abs(v - Math.round(v / inc) * inc) <= 0.1
  const next = v + step
  return Math.max(0, onGrid ? snapWeight(next, inc) : round1(next))
}
export function stepWeight(value, step, direction) {
  const v = Number(value) || 0
  return addStep(v, direction * step, step)
}
// Back off by a factor, landing on something you can actually load.
export function deloadTo(cur, step, factor = DELOAD_FACTOR) {
  // Below one increment there is nothing left to take off, and snapping the cut would round
  // straight back up to the step: hold the load instead of "deloading" to a heavier weight.
  if (cur <= step) return cur
  let next = snapWeight(cur * factor, step)
  if (next >= cur) next = snapWeight(cur - step, step)
  return Math.max(step, next)
}

// The load the session is judged by.
function loadOf(entry, sets) {
  const done = sets.filter(s => s.done).map(s => s.w || 0)
  return Math.max(0, ...done)
}

/**
 * Reduce one finished workout entry to what a policy needs to judge it.
 *
 * Workouts only started recording their prescription in v1.2.2, so most existing history has
 * no `target` at all. Judging those against nothing would score every past session as a miss.
 * An entry without its own target is judged against `fallback`, the exercise's current plan.
 */
export function readSession(entry, fallback) {
  const target = (entry && entry.target) || fallback || {}
  const mode = modeOf({ ...target, id: entry && entry.id })
  const logged = ((entry && entry.sets) || []).filter(s => !isWarmupRow(s))
  const planned = target.sets || logged.length
  const enough = logged.length >= planned
  const sets = logged.slice(0, Math.max(1, planned))
  if (mode === 'time') {
    const goal = target.sec || 0
    const held = sets.map(s => (s.done ? (s.sec || 0) : 0))
    return { mode, target, goal, held, weight: loadOf(entry, sets), best: Math.max(0, ...held), ok: goal > 0 && enough && held.length > 0 && held.every(h => h >= goal) }
  }
  const goal = target.reps || 0
  const reps = sets.map(s => (s.done ? (s.r || 0) : 0))
  return {
    mode, target, goal, reps,
    weight: loadOf(entry, sets),
    ok: goal > 0 && enough && reps.length > 0 && reps.every(r => r >= goal)
  }
}

/** Every past session for one exercise, oldest first. `fallback` — see readSession. */
export function sessionsFor(S, exId, fallback) {
  const out = []
  ;(S.workouts || []).forEach(w => {
    const entry = w.entries.find(e => e.id === exId)
    if (!entry) return
    // A session that does not count for this exercise cannot become the baseline for its next
    // prescription. Exclusion is per-entry now (ENG-11).
    if (entryExcluded(w, entry)) return
    if (entry.sets.some(s => s.done && !isWarmupRow(s))) out.push({ d: w.d, ...readSession(entry, fallback) })
  })
  return out
}

// Count how many sessions in a row ended in a miss, counting back from the most recent. A hit
// ends the streak, and so does a change of weight: a deload should reflect the failures at the
// weight that earned it, not the lighter weight that follows.
export function stallCount(sessions) {
  let n = 0
  for (let i = sessions.length - 1; i >= 0; i--) {
    if (sessions[i].ok) break
    if (i < sessions.length - 1 && sessions[i].weight !== sessions[i + 1].weight) break
    n++
  }
  return n
}

/**
 * The next prescription for one exercise.
 *
 * Returns `{ weight, reps, sec, why, kind }` — `kind` being one of
 * first | up | hold | deload | off, and `why` a translatable template + args so the app can
 * always answer "why this number?". A field the policy has no opinion on comes back
 * undefined and the caller keeps whatever the plan said.
 */
export function nextPrescription(S, cfg, routine) {
  const mode = modeOf(cfg)
  const policy = policyFor(cfg, routine, mode, defaultPolicy(S))
  const unit = S.unit || 'kg'
  const inc = weightIncrement(cfg)
  if (policy === 'off') return { policy, kind: 'off' }

  const sessions = sessionsFor(S, cfg.id, cfg).filter(s => s.mode === mode)
  const last = sessions[sessions.length - 1]
  if (!last) return { policy, kind: 'first', why: ['Nothing logged yet — this session sets the baseline.'] }

  const stalls = stallCount(sessions)
  const deloadAt = DELOAD_AFTER[policy] || 3

  const w = last.weight
  // Bodyweight work carries no external load, so there is nothing to add or take away —
  // "deload your push-ups to 2.5 kg" is not advice. Progress in reps instead. Note the trigger
  // is the *logged* weight, not the `bw` flag: a dip done with a belt has a load to progress
  // and belongs on the normal path.
  if (w <= 0) {
    const goal = last.goal || cfg.reps || 0
    if (!last.ok || goal <= 0) return { policy, kind: 'hold', weight: 0, reps: goal || undefined, why: ['Bodyweight — same target again until every set is clean.'] }
    const next = goal + 1
    return { policy, kind: 'up', weight: 0, reps: next, why: ['Bodyweight — every rep last time, so go for {0} this time.', next] }
  }

  if (last.ok) {
    return { policy, kind: 'up', weight: addStep(w, inc, inc), why: ['Every rep last time — {0} {1} more.', inc, unit] }
  }
  if (stalls >= deloadAt) {
    const dw = deloadTo(w, inc)
    return {
      policy, kind: 'deload', weight: dw,
      why: stalls > 1
        ? ['Missed reps {0} sessions running — reset to {1} {2} and work back up.', stalls, dw, unit]
        : ['Missed reps — reset to {0} {1} and work back up.', dw, unit]
    }
  }
  return { policy, kind: 'hold', weight: w, why: ['Missed reps last time — same weight again ({0} of {1} to go).', deloadAt - stalls, deloadAt] }
}

/**
 * Apply a prescription to freshly built sets. Only the fields the policy actually decided
 * are touched, and only on sets that have not been logged yet.
 */
export function applyPrescription(sets, p, step = 2.5) {
  if (!p || p.kind === 'off' || p.kind === 'first') return sets
  const out = sets.map(s => {
    // Never rewrite a logged set, and never rewrite a warm-up: the prescription speaks to
    // the work rows only.
    if (s.done || isWarmupRow(s)) return s
    const o = { ...s }
    if (p.weight != null) o.w = p.weight
    if (p.reps != null) o.r = p.reps
    if (p.sec != null) o.sec = p.sec
    return o
  })
  // Last, because the work rows now carry their final weight: the warm-up block ramps toward
  // what you are actually about to lift, not toward what you lifted last time.
  return rerampWarmups(out, step)
}
