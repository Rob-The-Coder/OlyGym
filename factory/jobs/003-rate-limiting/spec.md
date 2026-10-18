# Spec — 003-rate-limiting

## Goal
Throttle Zustand persist writes so rapid workout state changes (timer ticks,
set edits) don't rewrite localStorage — and the Android file mirror — on every
change.

## Out of scope
- Changing cadence except setItem; debouncing input; any network behavior.

## Acceptance criteria
- [ ] frontend/src/lib/throttle.js trailing-edge helper + co-located throttle.test.js.
- [ ] Leading call fires immediately; trailing call always fires at window end.
- [ ] Zustand persist storage wrapper throttles setItem at ~1000ms.
- [ ] `cd frontend && npm test` passes (vitest run).
- [ ] No new dependencies and no network code.

## Test plan
cd frontend && npm test
