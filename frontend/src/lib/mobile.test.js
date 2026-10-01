import { describe, expect, it } from 'vitest'
import { isoOf } from './format.js'
import { buildReminderNotifications } from './mobile.js'

// A day of the dated weeks is the whole session, so a reminder names the day planned for that
// date. 2026-06-01 is a Monday, so this week covers 06-01..06-07 and the next starts 06-08.
const day = (dow, name) => ({ dow, name, ex: [{ id: '0001' }] })
const week = (days, startIso = '2026-06-01') => ({ id: 'w' + startIso, startIso, name: '', days })
const state = (patch = {}) => ({
  weeks: [week([day(1, 'Push'), day(3, 'Pull')]), week([day(1, 'Push')], '2026-06-08')],
  workouts: [], reminder: { on: true, time: '08:00' }, ...patch,
})
const iso = d => isoOf(d)

describe('buildReminderNotifications', () => {
  it('expands the dated weeks into future dated notifications', () => {
    const now = new Date(2026, 5, 1, 7, 0) // Monday
    const notifications = buildReminderNotifications(state(), now)

    expect(notifications.slice(0, 2).map(n => iso(n.schedule.at))).toEqual([
      iso(now), iso(new Date(2026, 5, 3)),
    ])
    expect(notifications[0].body).toContain('Push')
    expect(notifications[0].schedule.allowWhileIdle).toBe(true)
  })

  it('names the day, and still names something when the day is unnamed', () => {
    const now = new Date(2026, 5, 1, 7, 0) // Monday
    expect(buildReminderNotifications(state(), now)[0].body).toContain('Push')
    const unnamed = buildReminderNotifications(state({ weeks: [week([day(1, '')])] }), now)[0]
    expect(unnamed.body).toContain('Workout')
  })

  it('skips a rest day and a date no week covers', () => {
    const now = new Date(2026, 5, 1, 7, 0) // Monday
    const notifications = buildReminderNotifications(state({
      weeks: [week([day(1, 'Push')]), week([day(1, 'Push')], '2026-06-29')],
    }), now)
    // Tuesday–Sunday of this week and the whole of the next are rest/gap days; nothing lands
    // between 06-02 and 06-28.
    expect(notifications.every(n => {
      const d = iso(n.schedule.at)
      return d === '2026-06-01' || d === '2026-06-29'
    })).toBe(true)
  })

  it('suppresses dates that already have a completed workout', () => {
    const now = new Date(2026, 5, 1, 7, 0) // Monday
    const notifications = buildReminderNotifications(state({ workouts: [{ d: iso(now) }] }), now)

    expect(notifications.some(n => iso(n.schedule.at) === iso(now))).toBe(false)
  })

  it("skips today's reminder after the configured local time has passed", () => {
    const now = new Date(2026, 5, 1, 9, 0) // Monday
    const notifications = buildReminderNotifications(state(), now)

    expect(notifications.some(n => iso(n.schedule.at) === iso(now))).toBe(false)
    expect(notifications.some(n => iso(n.schedule.at) === iso(new Date(2026, 5, 8)))).toBe(true)
  })
})
