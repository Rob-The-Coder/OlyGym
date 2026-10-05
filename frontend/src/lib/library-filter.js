import { equipmentOf, searchExercises } from './exercises.js'

/**
 * One pass of the Library's filters, in the order the screen has always applied them: body
 * part, then the search text, then whoever is allowed on this device (the active equipment
 * profile, unless the reader asked for everything), then the equipment filter.
 *
 * The equipment choice is dropped when it is not one of the options the rest of the filters
 * left (issue: a search that narrows past it used to leave you on a dead end with no result
 * and no way to tell why). That is why the effective value comes back as `eq`: the caller
 * renders the chip it actually has, not the one it asked for.
 *
 * `available` is a predicate or null; `all` is the whole catalogue for this reader.
 */
export function libraryResults({ all, q = '', bp = '', eq = '', available = null }) {
  const byPart = bp ? all.filter(e => e.bp === bp) : all
  const searched = searchExercises(byPart, q)
  const narrowed = available ? searched.filter(available) : searched
  const eqOpts = equipmentOf(narrowed)
  const eqOn = eqOpts.includes(eq) ? eq : ''
  return { list: eqOn ? narrowed.filter(e => e.eq === eqOn) : narrowed, eqOpts, eq: eqOn }
}
