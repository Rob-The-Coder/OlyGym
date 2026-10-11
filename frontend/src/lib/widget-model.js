// Android home-screen widget — the model (docs/MOBILE.md has the native half).
//
// The widget is a native RemoteViews surface. It cannot read localStorage, it cannot run React,
// and it must not decide what the training numbers mean — CONTRIBUTING.md puts that in a tested
// pure helper, and Java re-deriving "what am I training today" from S.weeks would be a second
// implementation of lib/weeks.js that drifts on the first change.
//
// So the meaning is computed here, once per save, and handed over as finished strings: lib/mobile.js
// drops the result into widget.json beside the state mirror, and TodayWidgetProvider reads
// days[today] and sets four TextViews. Java composes no sentence and formats no date.
//
// It carries the whole week rather than today alone on purpose. The system redraws the widget from
// this file on its own (updatePeriodMillis) with the app closed, and a widget still showing
// Wednesday's session on Thursday morning is worse than no widget at all. Seven small entries cost
// nothing and make midnight a lookup instead of a recomputation.
import { dateLocale, exerciseNameFor, t } from './i18n-core.js'
import { capWords, DAYN, isoOf, startOfWeek, weekStartOf } from './format.js'
import { addDays } from './weeks.js'
import { effectiveDay, nextTrainingDay, setsDone, setsDoneActive, setUnitsTotal } from './history.js'
import { exOr } from './exercises.js'

/** Exercise lines the widget body shows before it summarises the rest. */
export const WIDGET_BODY_LINES = 3
/** Bumped when the JSON the Java side reads changes shape. */
export const WIDGET_VERSION = 2

/**
 * The four strings each day of the current week needs, keyed by ISO date, plus which of the two
 * palettes to draw them in.
 *
 * `now` is injectable so the week boundary, the "logged" lookup and the next-training-day nudge
 * stay deterministic in tests.
 *
 * `theme` is the app's own choice, not the system's: someone running the app dark wants the widget
 * dark even on a light phone. It is passed in rather than read from the DOM here so this stays
 * pure — App.jsx has already resolved 'system' onto <html data-theme>, and that is what the caller
 * reads. The default is the app's own default. The two palettes themselves live in Android
 * resources (res/values/colors.xml), so no hex travels in this file.
 */
export function widgetModel(S, now = new Date(), theme = 'dark') {
  const today = isoOf(now)
  const first = isoOf(startOfWeek(today, weekStartOf(S)))
  const days = {}
  // A profile with no dated plan is not seven rest days: it has nothing to read, and saying so is
  // the one action the widget can offer.
  const planned = !!(S?.weeks || []).length
  for (let i = 0; i < 7; i++) {
    const iso = addDays(first, i)
    days[iso] = planned ? dayEntry(S, iso) : { ...emptyEntry(iso), title: t('No plan yet'), body: t('Open OlyGym to build one') }
  }
  return { v: WIDGET_VERSION, today, theme: theme === 'light' ? 'light' : 'dark', days }
}

function emptyEntry(iso) {
  return {
    overline: new Date(iso + 'T12:00:00').toLocaleDateString(dateLocale(), { weekday: 'long', day: 'numeric', month: 'long' }),
    title: '',
    body: '',
    footer: '',
  }
}

/** One day of the plan, as the widget draws it. */
function dayEntry(S, iso) {
  const out = emptyEntry(iso)
  const day = effectiveDay(S, iso)
  const hasWork = !!(day && (day.ex || []).length)
  // Last wins, so a second session on one day names itself — the same reading Home uses.
  const logged = (S.workouts || []).filter(w => w.d === iso).at(-1) || null
  const active = S.active && S.active.d === iso ? S.active : null

  if (hasWork) {
    out.title = day.name || t('Workout')
    out.body = bodyLines(day.ex)
  } else if (logged) {
    // A freestyle session on a rest day still happened; naming it beats calling the day empty.
    out.title = t('Workout done')
    out.footer = t('{0} sets', setsDone(logged))
    return out
  } else {
    out.title = t('Rest day')
    const next = nextTrainingDay(S, iso)
    if (next) out.footer = t('Next: {0}, {1}', t(DAYN[next.weekday]), next.day.name || t('Workout'))
    return out
  }

  // A running session outranks a logged one: if both exist the widget is about the one you are in.
  if (active) out.footer = t('In progress') + ' · ' + t('{0} sets', setsDoneActive(active) + '/' + setUnitsTotal(active.entries))
  else if (logged) out.footer = t('Done') + ' · ' + t('{0} sets', setsDone(logged))
  return out
}

/**
 * The exercise names, one movement per line, a complex joined with " + " (the coach writes
 * "Snatch + Clean" as one thing and the widget reads it as one line).
 *
 * Names are stored lower-case and shown through CSS `capitalize` everywhere else in the app;
 * a RemoteViews TextView has no CSS, so the capitalisation has to happen here.
 */
function bodyLines(ex) {
  const units = []
  for (const e of ex) {
    const last = units[units.length - 1]
    if (e.sg && last && last.sg === e.sg) last.names.push(e.id)
    else units.push({ sg: e.sg, names: [e.id] })
  }
  const lines = units.map(u => u.names.map(id => capWords(exerciseNameFor(exOr(id)))).join(' + '))
  if (lines.length <= WIDGET_BODY_LINES) return lines.join('\n')
  return [...lines.slice(0, WIDGET_BODY_LINES), t('+{0} more', lines.length - WIDGET_BODY_LINES)].join('\n')
}
