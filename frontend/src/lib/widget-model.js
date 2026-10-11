// Android home-screen widget — the model (docs/MOBILE.md has the native half).
//
// The widget is a native RemoteViews surface. It cannot read localStorage, it cannot run React,
// and it must not decide what the training numbers mean — CONTRIBUTING.md puts that in a tested
// pure helper, and Java re-deriving "what am I training today" from S.weeks would be a second
// implementation of lib/weeks.js that drifts on the first change.
//
// So the meaning is computed here, once per save, and handed over as finished strings: lib/mobile.js
// drops the result into widget.json beside the state mirror, and TodayWidgetProvider reads
// days[today] and sets the views. Java composes no sentence and formats no date.
//
// It carries the whole week rather than today alone on purpose. The system redraws the widget from
// this file on its own (updatePeriodMillis) with the app closed, and a widget still showing
// Wednesday's session on Thursday morning is worse than no widget at all. Seven small entries cost
// nothing and make midnight a lookup instead of a recomputation.
//
// The card is Home's hero, condensed: the overline, the mark in its circle, the title and the
// status line are Home.jsx / the .hero rules in m3.components.css. The two colours the hero carries
// are the accent's (--m3-primary-container and its on-), which is why they travel in this file
// rather than in Android resources — the accent is one of eight user-chosen seeds, and a resource
// would have to be shipped sixteen times over.
import { dateLocale, exerciseNameFor, t } from './i18n-core.js'
import { capWords, DAYN, isoOf, startOfWeek, weekStartOf } from './format.js'
import { addDays } from './weeks.js'
import { effectiveDay, exLine, nextTrainingDay, setLabel, setsDone, supersetUnits } from './history.js'
import { exOr } from './exercises.js'

/** Bumped when the JSON the Java side reads changes shape. */
export const WIDGET_VERSION = 3

/* The app's accent scheme, as the two roles the hero card uses. Only a fallback: lib/mobile.js
   reads the live values off the DOM, so the widget follows whatever accent and theme the app is
   showing. These are the violet pair from m3.tokens.css, used only when that read comes back empty
   — an uncoloured card would be worse than a violet one for the instant before the styles land. */
const FALLBACK = { theme: 'dark', container: '#4c3889', onContainer: '#e9ddff' }

/**
 * The strings each day of the current week needs, keyed by ISO date, plus the palette to draw them
 * in.
 *
 * `now` is injectable so the week boundary, the "logged" lookup and the next-training-day nudge
 * stay deterministic in tests. `view` is `{ theme, container, onContainer }`: the theme is the
 * app's own choice, not the system's — someone running the app dark wants the widget dark on a
 * light phone — and the two colours are read from the DOM by the caller, which keeps this pure.
 */
export function widgetModel(S, now = new Date(), view = {}) {
  const theme = view.theme === 'light' ? 'light' : 'dark'
  const container = isColor(view.container) ? view.container : FALLBACK.container
  const onContainer = isColor(view.onContainer) ? view.onContainer : FALLBACK.onContainer
  const today = isoOf(now)
  const first = isoOf(startOfWeek(today, weekStartOf(S)))
  const days = {}
  // A profile with no dated plan is not seven rest days: it has nothing to read, and saying so is
  // the one action the widget can offer.
  const planned = !!(S?.weeks || []).length
  for (let i = 0; i < 7; i++) {
    const iso = addDays(first, i)
    days[iso] = planned
      ? dayEntry(S, iso)
      : { ...emptyEntry(iso), icon: 'moon', title: t('No plan yet'), scheme: t('Open OlyGym to build one') }
  }
  return {
    v: WIDGET_VERSION,
    today,
    theme,
    // The hero's mark is a 14% wash of its own ink over the container (the .hero-i rule), which is a
    // colour-mix no view attribute can express and no drawable can carry for eight accents.
    colors: { container, onContainer, circle: mix(onContainer, container, 0.14) },
    days,
  }
}

function emptyEntry(iso) {
  return {
    overline: t('Today · {0}', new Date(iso + 'T12:00:00').toLocaleDateString(dateLocale(), { weekday: 'long', day: 'numeric', month: 'long' })),
    icon: 'moon',
    title: '',
    subtitle: '',
    // The one exercise the card names and its scheme. Empty unless there is something to name.
    now: '',
    scheme: '',
  }
}

/** A routine exercise as the card writes it: a complex reads as one thing, joined with " + ". */
const lineOf = cfg => capWords(exerciseNameFor(exOr(cfg.id)))

/** The unit the profile weighs in — never a constant. */
const unitOf = S => (S && S.unit) || 'kg'

/** One day of the plan, as the widget draws it. */
function dayEntry(S, iso) {
  const out = emptyEntry(iso)
  const day = effectiveDay(S, iso)
  const hasWork = !!(day && (day.ex || []).length)
  // Last wins, so a second session on one day names itself — the same reading Home uses.
  const logged = (S.workouts || []).filter(w => w.d === iso).at(-1) || null
  const active = S.active && S.active.d === iso ? S.active : null

  if (hasWork) {
    // The routine's own set count (a number per exercise), not the built session: the widget shows
    // what the day asks for, whether or not it has been started.
    const sets = day.ex.reduce((n, e) => n + Math.max(1, e.sets || 1), 0)
    out.icon = active ? 'timer' : logged ? 'check' : 'dumbbell'
    out.title = day.name || t('Workout')
    if (active) {
      // Mid-session the card carries the session itself: the widget is what you look at between
      // sets, and it is also where the three controls act from. now/scheme stay empty — the
      // provider draws those out of \`session\`, which it re-derives after the queued taps.
      out.session = sessionView(active)
      out.subtitle = out.session.position
      return out
    }
    // Waiting to be started: the one exercise you are about to do, and its scheme. The whole list
    // used to live here and read as a wall of names; one line is what the card is for.
    const first = (day.ex || [])[0]
    if (first) {
      out.now = lineOf(first)
      out.scheme = exLine(first, unitOf(S))
    }
    out.subtitle = t('{0} exercises', day.ex.length) + ' · ' + t('{0} sets', sets)
    if (logged) out.scheme = t('Done') + ' · ' + t('{0} sets', setsDone(logged))
    return out
  }

  if (logged) {
    // A freestyle session on a rest day still happened; naming it beats calling the day empty.
    out.icon = 'check'
    out.title = t('Workout done')
    out.scheme = t('{0} sets', setsDone(logged))
    return out
  }

  out.title = t('Rest day')
  const next = nextTrainingDay(S, iso)
  if (next) out.scheme = t('Next: {0}, {1}', t(DAYN[next.weekday]), next.day.name || t('Workout'))
  return out
}

/**
 * The running session, as the widget draws it and as its three controls act on it.
 *
 * Every string is finished here, including one per set: the widget has to redraw the instant you
 * tick something with the app closed, which means the native side applies the queued taps to this
 * structure and repaints. If it had to compose `Set 3 of 4 · 100 kg × 5` itself there would be a
 * second copy of the app's wording and numbering in Java.
 *
 * A complex is one unit — its members share the round, so a tick marks set i on all of them, and
 * the first member's rows are what the round is counted from.
 */
function sessionView(active) {
  const entries = active.entries || []
  const units = supersetUnits(entries)
  const found = units.findIndex(u => u.includes(active.cur))
  const index = found < 0 ? 0 : found
  return {
    d: active.d || null,
    index,
    count: units.length,
    position: t('Exercise {0} of {1}', index + 1, units.length),
    units: units.map(u => {
      const e = entries[u[0]]
      const sets = (e && e.sets) || []
      const cfg = { ...(e && e.target) || {}, id: e && e.id }
      return {
        title: u.map(i => capWords(exerciseNameFor(exOr(entries[i].id)))).join(' + '),
        schemes: sets.map((s, i) => t('Set {0} of {1}', i + 1, sets.length) + ' · ' + setLabel(e.id, s, cfg)),
        sets: sets.length,
        done: sets.filter(s => s.done).length,
      }
    }),
  }
}


/* ---------------------------------------------------------------- colour --
   Two small helpers so the one derived colour (the mark's wash) can be computed here, where it
   is tested, instead of in Java. Input is `#rrggbb` from a computed CSS custom property. */
const isColor = c => /^#?[0-9a-f]{6}$/i.test(String(c || '').trim())

/** `t` of `a` over `b`, both `#rrggbb`. The same maths as the app's color-mix(in srgb, …). */
export function mix(a, b, t) {
  const pa = String(a).replace('#', '').match(/../g).map(h => parseInt(h, 16))
  const pb = String(b).replace('#', '').match(/../g).map(h => parseInt(h, 16))
  return '#' + pa.map((v, i) => Math.round(v * t + pb[i] * (1 - t)).toString(16).padStart(2, '0')).join('')
}
