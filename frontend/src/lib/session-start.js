// How a session's exercise entries are built from one day of the dated-weeks plan. Shared by
// the live start and by "log a past workout", which is the same screen pointed at another day —
// both must walk up to identical entries, or the two paths drift apart the first time a
// prescription rule changes.
// Imports both history.js and progression.js (which itself imports history.js); nothing in
// either imports this file, so there is no cycle.
import { buildSets, applyIntensifierPlan, modeOf } from './history.js'
import { nextPrescription, applyPrescription, defaultIncrement, weightIncrement } from './progression.js'

// Returns a bare array of session entries. "Excluded from progression" is per-entry
// (`entry.noProg`, written only when true) rather than a wrapper flag on the session: a day
// planned as rehab excludes only its own exercises, and a day with no flag excludes nothing.
// A day is atomic, so no entry needs to remember which routine it came from.
export function buildSessionEntries(st, day) {
  // The prescription is applied as the session is built, so you walk up to the bar with the
  // right weight already on the screen instead of being told about it afterwards. `plan` is
  // kept on the entry purely so the workout can explain the number it chose. The day is also
  // the policy source, exactly as its routine was before the dated-weeks model.
  const noProg = day?.excludeFromProgression === true
  return (day ? day.ex : []).map(cfg => {
    const plan = noProg ? { policy: 'off', kind: 'off' } : nextPrescription(st, cfg, day)
    // The warm-up ramp and the prescription snap to the exercise's own increment (1.25 kg
    // plates exist), not the unit default; a timed exercise's `inc` is seconds, so it keeps the
    // default for its optional load.
    const step = modeOf(cfg) === 'reps' ? weightIncrement(cfg, st.unit) : defaultIncrement(cfg.id, st.unit)
    const sets = applyIntensifierPlan(applyPrescription(buildSets(st, cfg, { step, useTarget: plan.kind === 'off' }), plan, step), cfg)
    const target = { ...cfg }
    if (plan.weight != null) target.weight = plan.weight
    if (plan.reps != null) target.reps = plan.reps
    if (plan.sec != null) target.sec = plan.sec
    if (plan.sets != null) target.sets = plan.sets
    return { id: cfg.id, sg: cfg.sg, target, plan, sets, ...(noProg ? { noProg: true } : {}) }
  })
}
