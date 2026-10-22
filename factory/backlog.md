# Backlog

/factory next takes the first unchecked item, checks it, and runs it.
Keep one feature per line as `- [ ]`, small enough for one pass through the loop.

- [x] Undo deleting a logged workout (confirm, then restore from a small undo stack)
- [x] Show best-lift PR badges on the History list
- [x] Configurable rest timer between sets (default off) — already shipped in `main`: Settings → Rest timer (Off, 60–180 s), per-exercise rest seconds, pause/resume, lock-screen support. The fresh-install default stays 90 s (not flipped; that would contradict the shipped design and the existing profile)
- [x] Export session history as CSV from the History screen
- [x] When importing a week and deciding whether an exercise is correct or not, as of today I only have the option to swap to a different exercise, I have no agency in correcting the sets, reps and weight of the exercises. Therefore, this should be added. → 007-edit-imported-set-details
- [x] When importing a week and when creating one, for each exercise/complex the sets are considered to have the same weight, whereas in reality what happens is that my coach often gives me progressive sets (maybe 4, 2 at a weight and the other 2 heavier). Add the option to individually adjust the sets' weight. → 008-progressive-set-weights