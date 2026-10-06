import { describe, expect, test } from 'vitest'
import { restNotificationPayload, adoptNativeState } from './rest-notification.js'

describe('restNotificationPayload', () => {
  test('null without a timer or with a dead one', () => {
    expect(restNotificationPayload(null, 0, 'x', 'Rest')).toBeNull()
    expect(restNotificationPayload({ endsAt: 0, total: 90 }, 0, 'x', 'Rest')).toBeNull()
  })
  test('carries the end time, duration, owner and copy', () => {
    expect(restNotificationPayload({ endsAt: 5000, total: 90, left: 90 }, 2, 'Snatch day', 'Rest'))
      .toEqual({ endsAtMs: 5000, totalSec: 90, forIdx: 2, title: 'Rest', text: 'Snatch day', sub: '', big: 'Snatch day' })
  })
  test('rounds a duration up to at least one second and nulls a missing owner', () => {
    expect(restNotificationPayload({ endsAt: 5000, total: 0.4 }, null, '', ''))
      .toEqual({ endsAtMs: 5000, totalSec: 1, forIdx: null, title: '', text: '', sub: '', big: '' })
  })
})

describe('adoptNativeState', () => {
  const now = 1000000
  test('adopts a running rest, rounding the remaining seconds up', () => {
    expect(adoptNativeState({ active: true, endsAtMs: now + 42400, totalSec: 90, forIdx: 3 }, null, now))
      .toEqual({ left: 43, total: 90, endsAt: now + 42400, forIdx: 3 })
  })
  test('keeps the JS owner when the native one is absent', () => {
    expect(adoptNativeState({ active: true, endsAtMs: now + 5000, totalSec: 90, forIdx: null }, { forIdx: 1 }, now))
      .toEqual({ left: 5, total: 90, endsAt: now + 5000, forIdx: 1 })
  })
  test('null when inactive, already over, or unreadable', () => {
    expect(adoptNativeState({ active: false }, null, now)).toBeNull()
    expect(adoptNativeState({ active: true, endsAtMs: now - 1, totalSec: 90 }, null, now)).toBeNull()
    expect(adoptNativeState(null, null, now)).toBeNull()
  })
})
