// Android lock-screen rest timer — the JS half of the native `RestNotification` plugin
// (android/app/src/main/java/olygym/app/RestNotification*.java).
//
// The countdown is rendered by SystemUI from a `when` timestamp (setChronometerCountDown), so it
// keeps ticking with the process dead and costs nothing to keep alive. This module keeps a native
// mirror of the rest while the app runs, and adopts whatever the notification's actions left
// behind when the app comes back. Everything is a no-op off the Android build: `MOBILE` folds
// away in web bundles and the plugins are imported dynamically, like every other Capacitor
// dependency (lib/mobile.js).
import { MOBILE } from './mobile.js'

/** The wire payload for the native mirror, or null when there is no rest to show. Pure. */
export function restNotificationPayload(timer, forIdx, text, title, sub = '', big = '') {
  if (!timer || !(timer.endsAt > 0)) return null
  return {
    endsAtMs: timer.endsAt,
    totalSec: Math.max(1, Math.round(timer.total || 0)),
    forIdx: forIdx == null ? null : forIdx,
    title: String(title || ''),
    text: String(text || ''),
    // subText sits in the header next to the app name; big drives BigTextStyle when expanded.
    sub: String(sub || ''),
    big: String(big || text || ''),
  }
}

/**
 * The timer to run after the notification was used while the app was away: the native mirror is
 * the authority (its +15s/skip happened while JS was asleep). Returns null when the rest is over
 * or was skipped, which the caller reads as "stop".
 */
export function adoptNativeState(native, timer, now = Date.now()) {
  if (!native || !native.active || !(native.endsAtMs > now)) return null
  return {
    left: Math.max(0, Math.ceil((native.endsAtMs - now) / 1000)),
    total: Math.max(1, Math.round(native.totalSec || 0)),
    endsAt: native.endsAtMs,
    forIdx: native.forIdx != null ? native.forIdx : (timer ? timer.forIdx : undefined),
  }
}

// A Capacitor plugin is a Proxy whose every property — `then` included — is a function, so it is
// a *thenable*. Returning it from an async function makes the promise machinery call `.then()`,
// which Capacitor forwards to the native plugin as a `then` method call and rejects with
// "RestNotification.then() is not implemented on android". The handle therefore never travels as a
// promise's value; it only lives inside this wrapper (the same reason mobile.js keeps its plugins
// in local variables and returns plain data).
let handle = null
let loading = null
async function plugin() {
  if (!MOBILE) return null
  if (!loading) {
    loading = (async () => {
      try {
        const { Capacitor, registerPlugin } = await import('@capacitor/core')
        if (Capacitor.getPlatform() === 'android') handle = { p: registerPlugin('RestNotification') }
      } catch (e) { handle = null }
    })()
  }
  await loading
  return handle
}

// start(rest) is always preceded by stop() — startRest replaces whatever was running — so the
// calls must reach the plugin in call order or a late stop cancels the notification just posted.
// One promise chain serialises them.
let chain = Promise.resolve()
function enqueue(fn) {
  const next = chain.then(fn, fn)
  chain = next.then(() => {}, () => {})
  return next
}

// The notification permission is the same one the workout reminder uses; ask once per session,
// only when a rest actually wants to post (the user just checked a set off), and never nag after
// a denial.
let asked = false
async function allowed() {
  try {
    const { LocalNotifications } = await import('@capacitor/local-notifications')
    const perm = await LocalNotifications.checkPermissions()
    if (perm.display === 'granted') return true
    if (asked) return false
    asked = true
    return (await LocalNotifications.requestPermissions()).display === 'granted'
  } catch (e) { return false }
}

// null off the native Android build; otherwise { ok }, or { ok:false, reason, message } where
// reason is 'plugin' (no Android bridge at all), 'permission' (notifications off for the app) or
// 'error' (the bridge call itself failed — e.g. the native plugin is not registered). Callers use
// the reason to say what actually went wrong instead of failing silently.
export function showRestNotification(payload) {
  if (!payload || !MOBILE) return Promise.resolve(null)
  return enqueue(async () => {
    const h = await plugin()
    if (!h) return { ok: false, reason: 'plugin' }
    if (!(await allowed())) return { ok: false, reason: 'permission' }
    await h.p.start(payload)
    return { ok: true }
  }).catch(e => ({ ok: false, reason: 'error', message: String((e && e.message) || e) }))
}

export function hideRestNotification() {
  return enqueue(async () => { const h = await plugin(); if (h) await h.p.stop() }).catch(() => {})
}

/** null off Android; { active:false } when the plugin is there but no rest is running. */
export async function readRestNotification() {
  try {
    const h = await plugin()
    if (!h) return null
    await chain
    return await h.p.getState()
  } catch (e) { return null }
}

export function listenRestActions(cb) {
  if (!MOBILE) return
  plugin().then(h => { if (h) h.p.addListener('action', cb) }).catch(() => {})
}

export function onAppResume(cb) {
  if (!MOBILE) return
  document.addEventListener('visibilitychange', () => { if (!document.hidden) cb() })
  import('@capacitor/app').then(({ App }) => {
    App.addListener('appStateChange', ({ isActive }) => { if (isActive) cb() })
  }).catch(() => {})
}
