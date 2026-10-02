// Converting a profile's stored weights from lb to kg, once, on the way in.
//
// The app logs kilos only now. A profile that was written while it still offered pounds is
// converted exactly once (see useStore's load path) and its unit pinned to 'kg', so an old
// number is never silently relabelled. Rounded to what a gym can load: kg to the nearest
// 0.25, body weight to 0.1.
import { workoutVolume } from './history.js'

const LB_PER_KG = 2.2046226218

export function convertWeight(value, from, to) {
  if (from === to || value == null || value === '' || !Number.isFinite(Number(value))) return value
  const v = Number(value)
  if (to === 'lb') return Math.round(v * LB_PER_KG * 2) / 2
  return Math.round(v / LB_PER_KG * 4) / 4
}

// Body weight is not loaded on a bar: the weigh-in sheet steps and stores it at 0.1, so plate
// rounding would move most weigh-ins on a kg → lb → kg round trip (QA C15).
export function convertBodyWeight(value, from, to) {
  if (from === to || value == null || value === '' || !Number.isFinite(Number(value))) return value
  const v = Number(value)
  return Math.round((to === 'lb' ? v * LB_PER_KG : v / LB_PER_KG) * 10) / 10
}

const convSet = (set, from, to) => {
  if (!set || typeof set !== 'object') return set
  const out = { ...set }
  if (out.w != null) out.w = convertWeight(out.w, from, to)
  return out
}
const convTarget = (cfg, from, to) => {
  if (!cfg || typeof cfg !== 'object') return cfg
  const out = { ...cfg }
  if (out.weight != null) out.weight = convertWeight(out.weight, from, to)
  // A per-exercise increment is a load too — 2.5 kg is 5 lb, not 2.5 lb.
  if (out.inc > 0 && (out.mode == null || out.mode === 'reps')) out.inc = convertWeight(out.inc, from, to)
  if (Array.isArray(out.warmup)) out.warmup = out.warmup.map(w => (w && w.weight != null ? { ...w, weight: convertWeight(w.weight, from, to) } : w))
  return out
}
const convEntry = (e, from, to) => {
  if (!e || typeof e !== 'object') return e
  return {
    ...e,
    ...(e.topW != null ? { topW: convertWeight(e.topW, from, to) } : {}),
    ...(e.target ? { target: convTarget(e.target, from, to) } : {}),
    ...(Array.isArray(e.sets) ? { sets: e.sets.map(s => convSet(s, from, to)) } : {}),
  }
}

/** A new state object with every weight expressed in `to`, and `unit` set to it. */
export function convertStateUnit(S, to) {
  const from = S.unit || 'kg'
  if (from === to) return S
  const c = v => convertWeight(v, from, to)
  const bw = v => convertBodyWeight(v, from, to)
  // A session carries its body weight of the day and a cached total volume; History rows, the
  // detail header, the month calendar and the heatmap tooltips read those rather than summing
  // sets, so they must move with the sets. The volume is re-added from the converted sets.
  const convSession = s => {
    const out = { ...s, entries: (s.entries || []).map(e => convEntry(e, from, to)) }
    if (out.bw != null) out.bw = bw(out.bw)
    if (Number.isFinite(out.vol)) out.vol = workoutVolume({ entries: out.entries.filter(e => Array.isArray(e?.sets)) })
    return out
  }
  const out = { ...S, unit: to }
  if (Array.isArray(S.bodyweight)) out.bodyweight = S.bodyweight.map(b => ({ ...b, w: bw(b.w) }))
  if (S.targetW != null) out.targetW = bw(S.targetW)
  if (S.exWeights) out.exWeights = Object.fromEntries(Object.entries(S.exWeights).map(([k, v]) => [k, v && typeof v === 'object' ? { ...v, w: c(v.w) } : c(v)]))
  if (S.barWeights) out.barWeights = Object.fromEntries(Object.entries(S.barWeights).map(([k, v]) => [k, c(v)]))
  if (Array.isArray(S.weeks)) out.weeks = S.weeks.map(w => ({ ...w, days: (w.days || []).map(d => ({ ...d, ex: (d.ex || []).map(cfg => convTarget(cfg, from, to)) })) }))
  if (Array.isArray(S.workouts)) out.workouts = S.workouts.map(convSession)
  if (S.active) out.active = convSession(S.active)
  return out
}
