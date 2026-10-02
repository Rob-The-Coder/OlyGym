// Per-exercise bar weight (issue: plate math for barbell work). Logged weights stay the
// TOTAL on the bar — history and progression keep meaning exactly what they always did. The
// bar weight is display metadata: it feeds the "X per side" plate math and nothing else.
//
// Unit: kilos. Weights are stored in kg, so the equipment defaults are the kg bars a real gym
// racks (a 20 kg olympic bar), never a converted pound figure.

import { EXIDX } from './exercises.js'

/** Equipment values (e.eq) that put a bar in your hands. */
export const BAR_EQ = new Set(['barbell', 'olympic barbell', 'ez barbell', 'smith machine', 'trap bar'])

/** Typical bar weights per equipment type, in kg. */
export const DEFAULT_BAR_KG = { barbell: 20, 'olympic barbell': 20, 'ez barbell': 10, 'smith machine': 9, 'trap bar': 25 }

const exOf = exOrId => (typeof exOrId === 'string' ? EXIDX[exOrId] : exOrId)

/** Whether this exercise (object or id) is done with a bar. */
export const usesBar = exOrId => BAR_EQ.has(exOf(exOrId)?.eq)

/** The default bar weight for an equipment type. null off the list. */
export const defaultBarWeight = eq => DEFAULT_BAR_KG[eq] ?? null

/**
 * A stored 0 is "no bar", not "unset" (issue #138). Smith machines that counterbalance their
 * carriage put nothing in your hands, so the plate math has to start from the weight you
 * logged, not from a 9 kg bar that is not there. The key being absent is what means "use the
 * default for this bar type" — which is why the editor clears the key rather than writing a 0
 * when you ask for the default back.
 */
export const isNoBar = (S, exId) => (S?.barWeights || {})[exId] === 0

/** True when the user has set their own bar weight for this exercise — including "no bar". */
export const hasBarOverride = (S, exId) => {
  const own = (S?.barWeights || {})[exId]
  return own === 0 || own > 0
}

/**
 * Effective bar weight for one exercise, in kg: the explicit S.barWeights[exId] if set, else
 * the default for the bar type. null for anything that is not a bar exercise.
 */
export function barWeightFor(S, exOrId) {
  const ex = exOf(exOrId)
  if (!BAR_EQ.has(ex?.eq)) return null
  const own = (S?.barWeights || {})[ex.id]
  if (own === 0) return 0
  if (own > 0) return own
  return defaultBarWeight(ex.eq)
}

/**
 * Plates per side: (total − bar) / 2, rounded to 2 decimals. null when there is nothing
 * sensible to show — a missing number, or a total at or below the bar itself.
 */
export function plateSplit(total, bar) {
  // `bar` of 0 is a real answer, not a missing one: with no bar every kilo you logged is on the
  // ends, so the split is simply half of it (issue #138).
  if (!(total > 0) || !(bar >= 0) || bar === null || bar === undefined || total <= bar) return null
  return Math.round(((total - bar) / 2) * 100) / 100
}
