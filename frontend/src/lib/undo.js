// An undo stack for the one destructive action worth undoing: deleting a logged workout.
// The stack is ephemeral UI state (store/useUI.js), never persisted — a reload forgets it,
// exactly like the snackbar that offered the Undo. Everything here is pure: inputs are never
// mutated, every call returns a fresh array.

import { insertChronological } from './backfill.js'

// How many deleted workouts the stack remembers. Oldest drops first once full.
export const UNDO_MAX = 5

// Append the deleted workout (most recent last), dropping the oldest beyond `max`.
export function pushUndo(stack, workout, max = UNDO_MAX) {
  const next = [...stack, workout]
  return next.length > max ? next.slice(next.length - max) : next
}

// Pop the most recent entry. Empty yields { stack, workout: null }.
export function takeUndo(stack) {
  if (!stack.length) return { stack: stack.slice(), workout: null }
  return { stack: stack.slice(0, -1), workout: stack[stack.length - 1] }
}

// Re-insert a restored workout in chronological order, so History's reversed view puts it
// back in the right month. Delegates to backfill.js, which owns the ordering.
export function restoreWorkout(workouts, workout) {
  return insertChronological(workouts, workout)
}
