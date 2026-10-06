import { create } from 'zustand'
import { uid } from '../lib/format.js'
import { beep, vibrate } from '../lib/sound.js'
import { t } from '../lib/i18n.js'
import { useStore } from './useStore.js'
import { restNotificationPayload, showRestNotification, hideRestNotification } from '../lib/rest-notification.js'

// Set the moment the tab goes hidden, never cleared here — timerTick/workTick read and
// clear it themselves once they're running visible again. Lets a completion tick tell
// "the countdown hit zero while the app was actually open" from "it hit zero while
// backgrounded/closed and we're only just catching up now that it's open again".
let pageHiddenAt = null
if (typeof document !== 'undefined') {
  document.addEventListener('visibilitychange', () => { if (document.hidden) pageHiddenAt = Date.now() })
}

let toastTm = null
let restNoticeShown = false
let timerInt = null
let timerTick = null
let workInt = null
let workTick = null
let workDone = null

export const useUI = create((set, get) => {
  // Arm the one rest interval. Kept in one place so a normal start and a rest that was
  // controlled from the notification while the app was away (adoptRest) run the same machinery.
  const armRest = () => {
    if (timerInt) clearInterval(timerInt)
    if (timerTick) document.removeEventListener('visibilitychange', timerTick)
    timerTick = () => {
      const tm = get().timer
      if (!tm) return
      const left = Math.max(0, Math.round((tm.endsAt - Date.now()) / 1000))
      const seenLive = !document.hidden && pageHiddenAt === null
      if (!document.hidden) pageHiddenAt = null
      if (left === tm.left) return
      const snd = useStore.getState().S.sound
      if (left <= 0) {
        if (seenLive) {
          beep(snd, 880, 0.15); beep(snd, 880, 0.15, 0.25); beep(snd, 1320, 0.4, 0.5)
          vibrate([200, 100, 200]); get().flashTimer()
        }
        get().toast(t('Rest over — next set!'))
        get().stopRest(); return
      }
      if (left <= 3) beep(snd, 660, 0.1)
      set({ timer: { ...tm, left } })
    }
    timerInt = setInterval(timerTick, 1000)
    document.addEventListener('visibilitychange', timerTick)
  }
  // Push the mirror the lock-screen notification renders. SystemUI ticks the countdown from
  // `endsAt`, so this runs only when a rest starts or changes — never once per second.
  const pushRest = () => {
    const tm = get().timer
    const active = useStore.getState().S.active
    const wo = (active && active.name) || ''
    const ex = (tm && tm.label) || ''
    // The expanded card names both; the collapsed line names the exercise (or the workout).
    const payload = restNotificationPayload(
      tm, tm && tm.forIdx, ex || wo, t('Rest'), wo,
      wo && ex ? `${wo} · ${ex}` : (ex || wo),
    )
    if (!payload) return
    showRestNotification(payload).then(res => {
      // null = not the native Android build. On a real phone this is the only place a blocked
      // permission or a missing plugin becomes visible at all, so report the outcome once per
      // session rather than letting the lock-screen timer fail in silence. The in-app timer runs
      // either way.
      if (!res || restNoticeShown) return
      restNoticeShown = true
      if (res.ok) return
      if (res.reason === 'permission') { get().toast(t('Notifications are off for OlyGym')); return }
      get().toast(res.message
        ? t('Lock-screen timer failed: {0}', res.message)
        : t('Lock-screen timer unavailable'))
    }).catch(() => {})
  }

  return {
  sheets: [],          // { id, render:(close)=>JSX, kind:'sheet'|'center', locked }
  toastMsg: '',
  timer: null,         // rest countdown between sets — { left, total, endsAt, forIdx }
                       // forIdx: index of the active entry whose set started the rest (undefined when unknown)
  work: null,          // work countdown DURING a timed set (issue #16) — { left, total, endsAt, label }
  timerFlashId: 0,     // changing the id retriggers the theme-blink visual alert

  flashTimer() {
    if (!useStore.getState().S.timerFlash) return
    set(s => ({ timerFlashId: s.timerFlashId + 1 }))
  },

  openSheet(render, { kind = 'sheet', locked = false } = {}) {
    const id = uid()
    set(s => ({ sheets: [...s.sheets, { id, render, kind, locked }] }))
    const close = () => get().closeSheet(id)
    return { id, close, lock: v => set(s => ({ sheets: s.sheets.map(x => x.id === id ? { ...x, locked: v } : x) })) }
  },
  closeSheet(id) { set(s => ({ sheets: s.sheets.filter(x => x.id !== id) })) },
  closeAll() { set({ sheets: [] }) },

  toast(msg) {
    set({ toastMsg: msg })
    clearTimeout(toastTm)
    toastTm = setTimeout(() => set({ toastMsg: '' }), 2200)
  },

  startRest(sec, forIdx, label) {
    get().stopRest()
    // Rest timer set to Off. Stopping and returning rather than starting a zero-length timer
    // keeps every caller honest: the four places that start a rest do not each need to know.
    if (!(sec > 0)) return
    const endsAt = Date.now() + sec * 1000
    set({ timer: { left: sec, total: sec, endsAt, forIdx, label } })
    armRest()
    pushRest()
  },
  addRest(sec) {
    const tm = get().timer
    if (!tm) return
    const left = tm.left + sec
    // taking off more than is left means "I'm ready now" — same as skipping, and it keeps a
    // negative duration out of both the progress bar and the timer state
    if (left <= 0) { get().stopRest(); return }
    set({ timer: { ...tm, left, total: tm.total + sec, endsAt: tm.endsAt + sec * 1000 } })
    pushRest()
  },
  // The active list changed shape (an exercise removed or inserted at `at`): keep the rest
  // pointing at the same exercise. Returns nothing; the caller decides whether to stop instead.
  shiftRestOwner(at, delta) {
    const tm = get().timer
    if (!tm || !(tm.forIdx >= at)) return
    set({ timer: { ...tm, forIdx: tm.forIdx + delta } })
  },
  stopRest() {
    if (timerInt) clearInterval(timerInt); timerInt = null
    if (timerTick) document.removeEventListener('visibilitychange', timerTick); timerTick = null
    set({ timer: null })
    hideRestNotification().catch(() => {})
  },
  // A rest controlled from the notification while the app was away. The native mirror is the
  // authority (its +15s/skip happened while JS was asleep), so this replaces the JS timer with
  // it and re-arms — no push back, or the two would ping-pong.
  adoptRest(timer) {
    set({ timer })
    armRest()
  },

  /* ---- work timer (issue #16) ----
     Times the set itself, not the recovery after it. Kept separate from the rest timer on
     purpose: the two mean opposite things, they must never run together, and a work set is
     something you are watching — so it gets no rest-over alert of its own (a plank does not
     need a notification you are staring at anyway).
     `onDone(elapsedSec)` is called both when the countdown reaches zero and on an early
     finish; the elapsed time is what actually gets logged, so stopping at 0:38 of a 0:45
     hold records 0:38 rather than crediting the full target. */
  startWork(sec, label, onDone) {
    get().stopWork()
    get().stopRest()
    const total = Math.max(1, Math.round(sec) || 1)
    const endsAt = Date.now() + total * 1000
    workDone = onDone
    set({ work: { left: total, total, endsAt, label } })
    workTick = () => {
      const wk = get().work
      if (!wk) return
      const left = Math.max(0, Math.round((wk.endsAt - Date.now()) / 1000))
      const seenLive = !document.hidden && pageHiddenAt === null
      if (!document.hidden) pageHiddenAt = null
      if (left === wk.left) return
      const snd = useStore.getState().S.sound
      if (left <= 0) {
        if (seenLive) {
          beep(snd, 880, 0.15); beep(snd, 880, 0.15, 0.25); beep(snd, 1320, 0.4, 0.5)
          vibrate([200, 100, 200]); get().flashTimer()
        }
        const done = workDone
        get().stopWork()
        if (done) done(wk.total)
        return
      }
      if (left <= 3) beep(snd, 660, 0.1)
      set({ work: { ...wk, left } })
    }
    workInt = setInterval(workTick, 1000)
    document.addEventListener('visibilitychange', workTick)
  },
  // Ended the hold early — log what was actually held.
  finishWorkEarly() {
    const wk = get().work
    if (!wk) return
    const elapsed = Math.max(1, wk.total - wk.left)
    const done = workDone
    vibrate(30)
    get().stopWork()
    if (done) done(elapsed)
  },
  // Abandon without logging anything.
  stopWork() {
    if (workInt) clearInterval(workInt); workInt = null
    if (workTick) document.removeEventListener('visibilitychange', workTick); workTick = null
    workDone = null
    set({ work: null })
  }
  }
})
