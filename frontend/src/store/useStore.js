import { create } from 'zustand'
import { localTZ } from '../lib/format.js'
import { registerCustom } from '../lib/exercises.js'
import { MOBILE, initReminderSync, nativeLoad, nativeSave, syncReminder, writeAutoBackup } from '../lib/mobile.js'

import { WC_DEFAULT } from '../lib/workout-controls.js'
import { migrateToWeeks, needsWeekMigration } from '../lib/migrate-weeks.js'
import { convertStateUnit } from '../lib/units.js'

const KEY = 'gym_state_v1'
export const DEF = {
  unit: 'kg', restSec: 90, sound: true, soundOnSilent: false, timerFlash: false, keepAwake: true, lang: 'en',
  theme: 'dark', accent: 'lime', body: 'male', targetW: null,
  bodyweight: [], weeks: [],
  exWeights: {}, workouts: [], active: null, customEx: [], gifSize: 'full',
  // Corrections to the reading of a coach's spreadsheet, keyed by the words he wrote (see
  // lib/plan-aliases.js). Every week's sheet repeats the same Italian phrases, so a fix made once
  // has to be there the next time round. Absent reads as empty.
  planAliases: {},
  // The demo video of each exercise, a layer of its own, next to the poster frame `gifSize`
  // governs: 'button' (the badge on the poster opens it — the default), 'inline' (the player
  // loads with the exercise) or 'off' (the poster only, nothing from YouTube is ever embedded).
  video: 'button',
  // How the active workout is laid out — 'cards' (one exercise at a time with Prev/Next),
  // 'list' (every exercise stacked and scrollable) or 'compact' (that stack stripped to just
  // names and set rows — no media, tags, notes, last-time or progression line). Purely
  // presentational: profiles written before this setting existed overlay onto DEF and keep the
  // 'cards' behaviour. beginWorkout copies the value onto s.active, so the header ⋮ menu can
  // override it for the running session without touching this saved default.
  workoutView: 'cards',
  // Which controls the workout screen shows besides the sets themselves. The default is the
  // lean layout: one "more" button per exercise and a menu on each set number. Every switch
  // brings one of the old always-visible button groups back (Settings → During a workout).
  wc: { ...WC_DEFAULT },
  // effort: which per-set effort scale is logged — 'none' | 'rir' | 'rpe'. null, not 'none', so
  // that a profile which never chose (loaded state is overlaid on DEF, on every path) still falls
  // back to the `showRir` boolean this replaced and keeps the column it had. See effortOf.
  reminder: { on: false, time: '08:00', tz: null }, effort: null, autoBackup: false,
  // Equipment profiles (issue: filter Library/picker/routines by what you actually own —
  // e.g. "Home" vs "Gym" — building on the session-only equipment filter from issue #6).
  equipProfiles: [], activeEquipId: null, equipFilterOn: false,
  // Standing per-exercise notes, keyed by exercise id: the gym-specific facts that are true
  // every time you do the movement ("seat 4, pin 7"). Distinct from a routine's `note`, which
  // belongs to one exercise in one plan, and from a session note, which belongs to one day.
  exNotes: {},
  // Favourite exercise ids (issue #6) — sorted to the top of the picker/Library.
  favEx: [],
  // First day of the week as a getDay() index — 1 Monday, 0 Sunday. Monday is the default so
  // every profile written before this setting existed keeps the week it has been looking at.
  // See lib/format.js: nothing reads this field directly, everything goes through the helpers.
  weekStart: 1,
  // Decimals on displayed weights: 1 by default, 2 for anyone loading quarter plates or
  // microplates (issue #139). Display only — nothing is stored or rounded differently.
  wdec: 1,
  // Per-exercise bar weight overrides, keyed by exercise id, in the profile unit (see
  // lib/bar.js). Logged weights stay the total — this only feeds the plate math.
  barWeights: {},
  // Whether Start opens the quick weigh-in first (sheets.jsx startFlow, issue #137). Off starts
  // the session straight away; weight can still be logged from Home/Stats. Defaults on; an
  // older profile without the key reads as on (`!== false`).
  weighIn: true,
  // Automatic progression (lib/progression.js policyFor / defaultPolicy): when neither an
  // exercise nor its day names a rule, does the weight go up on its own? Off by default, so the
  // weight on the plan is the weight on the bar; on restores the linear rule for everything that
  // does not override it. Absent reads as off.
  autoProg: false,
}
const clone = o => JSON.parse(JSON.stringify(o))

function loadState() {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return clone(DEF)
    const state = Object.assign(clone(DEF), JSON.parse(raw))
    // A profile saved before the dated-weeks model has a repeating plan but no weeks, so the new
    // field is derived from it on the way in (lib/migrate-weeks.js owns that read). Additive —
    // the old fields are left exactly as they are. Every plan producer now writes `weeks`, so this
    // is the one-time load path for a profile from before the model, not a standing translation.
    if (needsWeekMigration(state)) state.weeks = migrateToWeeks(state, new Date())
    return migrateUnit(state)
  } catch (e) { /* ignore */ }
  return clone(DEF)
}

const hasData = st => !!((st.workouts || []).length || (st.weeks || []).length || (st.bodyweight || []).length)

// One-time: a profile written while pounds were offered has its stored weights converted to
// kilos and is pinned there, so an old number is never silently relabelled. Idempotent — a kg
// profile passes straight through.
const migrateUnit = S => (S.unit === 'lb' ? convertStateUnit(S, 'kg') : S)

export const useStore = create((set, get) => {
  let saveTm = null

  initReminderSync(() => get().S)

  // Mobile build: mirror the state into a file in the app's data directory (survives WebView
  // storage eviction) and keep the native reminder schedule in step with the weekly plan.
  const nativePersist = () => {
    clearTimeout(saveTm)
    saveTm = setTimeout(() => { saveTm = null; nativeSave(get().S); syncReminder(get().S) }, 800)
  }

  // `_ts` is when this device last changed the data. Local-only now, so it is only a stamp.
  const persist = (S, stamp = true) => {
    if (stamp) S._ts = Date.now()
    registerCustom(S.customEx)
    localStorage.setItem(KEY, JSON.stringify(S))
    set({ S })
    if (MOBILE) nativePersist()
  }

  // A setting changed right before switching away/closing the tab must not get lost mid-debounce.
  // On mobile the same applies to the file mirror — backgrounding is often the last thing before
  // the OS kills the app.
  const flush = () => {
    if (MOBILE && saveTm) {
      clearTimeout(saveTm)
      saveTm = null
      nativeSave(get().S)
      syncReminder(get().S)
    }
  }
  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'hidden') flush() })
  window.addEventListener('pagehide', flush)   // Safari kills the home-screen app without a visibilitychange at times

  return {
    S: (() => { const s = loadState(); registerCustom(s.customEx); return s })(),
    ready: false,

    // Mutate a draft of S via producer fn, then persist locally.
    update(mut) {
      const S = clone(get().S)
      mut(S)
      persist(S)
    },
    // A wholesale replacement (backup import, reset).
    replaceState(S) { persist(migrateUnit(clone(S))) },

    // Fires after the moments where losing local data would actually hurt — a workout just
    // logged, a routine just edited — not on every keystroke. No-op off mobile or with the
    // setting off; the private file mirror (nativePersist, above) already covers every change.
    autoBackupNow() {
      const S = get().S
      if (MOBILE && S.autoBackup) writeAutoBackup(S)
    },

    // Boot: local-only. On mobile restore from the file mirror (the durable copy; localStorage
    // may have been evicted since the last run), then go straight in.
    async boot() {
      if (MOBILE) {
        const saved = await nativeLoad()
        const S = get().S
        if (saved && (!hasData(S) || (saved._ts || 0) >= (S._ts || 0))) {
          persist(migrateUnit(Object.assign(clone(DEF), saved)), false)
        } else if (hasData(S)) {
          nativeSave(S)   // first run after an update from a file-less version: seed the mirror
        }
        syncReminder(get().S)
        set({ ready: true })
        return
      }
      // Re-stamp the reminder's timezone on every load — keeps it correct if you're travelling,
      // without needing to revisit Settings.
      const tz = localTZ()
      if (get().S.reminder?.on && get().S.reminder.tz !== tz) {
        get().update(s => { s.reminder = { ...s.reminder, tz } })
      }
      set({ ready: true })
    }
  }
})

export { hasData }
