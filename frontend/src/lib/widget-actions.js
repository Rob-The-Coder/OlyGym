// The widget's three controls, as the app applies them (lib/mobile.js reads the queue the native
// side collected; useStore adopts it when the app comes back).
//
// The widget itself cannot touch a running session: the state lives in this WebView, and the widget
// is looked at when the app is closed. So a tap is queued natively, the widget paints it
// immediately on top of the last snapshot it has, and the queue is applied here the next time the
// app runs. Same shape as the rest timer's -15s/+15s/Skip (lib/rest-notification.js).
//
// What a tick does NOT do: start the rest timer. The rest belongs to the moment you tapped, and
// by the time the app comes back that moment has passed. A tick from the widget changes the log,
// not the clock.
import { supersetUnits } from './history.js'

/** The three actions the widget can post, for the native side's PendingIntents. */
export const WIDGET_ACTIONS = ['prev', 'next', 'check']

/**
 * Apply queued widget actions to an active session. Pure: returns the same object when nothing
 * changed, so a caller can skip a save. Actions aimed at another day's session are dropped — a
 * queue that outlived its session must not tick the next one.
 */
export function applyWidgetActions(active, actions = []) {
  if (!active || !Array.isArray(actions) || !actions.length) return active
  let out = active
  // Copy-on-write: only a move or a tick needs the deep copy, and most queues are one tap.
  const touch = () => {
    if (out === active) {
      out = { ...active, entries: (active.entries || []).map(e => ({ ...e, sets: (e.sets || []).map(s => ({ ...s })) })) }
    }
  }
  for (const a of actions) {
    if (!a || WIDGET_ACTIONS.indexOf(a.k) < 0) continue
    if (a.d && active.d && a.d !== active.d) continue
    const units = supersetUnits(out.entries || [])
    if (!units.length) continue
    const found = units.findIndex(u => u.includes(out.cur))
    const at = found < 0 ? 0 : found
    if (a.k === 'next' || a.k === 'prev') {
      const to = Math.min(units.length - 1, Math.max(0, at + (a.k === 'next' ? 1 : -1)))
      if (to !== at) { touch(); out.cur = units[to][0] }
      continue
    }
    // check: the round's first unfinished set, marked on every member of the unit. Nothing left to
    // tick is a no-op, which is what the widget showed you too.
    const i = (out.entries[units[at][0]].sets || []).findIndex(s => !s.done)
    if (i < 0) continue
    touch()
    for (const ui of units[at]) { const s = out.entries[ui].sets[i]; if (s) s.done = true }
  }
  return out
}
