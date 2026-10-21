# Factory

A tiny software factory for OlyGym. One skill runs one job through a loop of subagents;
every job works in its own git worktree on its own branch, so several can run at once.

- Run a job: open a DeepSeek harness session and run `/factory <feature>` (or `/factory next`).
- Watch it: `cd factory && python3 -m http.server 8000`, then open http://localhost:8000/dashboard.html.
- Change the loop: edit .dsh/skills/factory/SKILL.md (the diagram is the loop).
- Change an agent: edit .dsh/agents/<name>.md.

`jobs/` is gitignored runtime state: one folder per live job, holding its `job.json` (the
dashboard reads it directly), spec, build notes, per-round reviews and decision. `old_jobs/`
archives finished jobs (003–006) as examples of a completed job's files.
