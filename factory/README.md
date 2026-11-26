# Factory

A software factory for OlyGym, modelled on
[zazencodes' software-factory-claude-code-v2](https://github.com/zazencodes/zazencodes-season-3/tree/main/src/software-factory-claude-code-v2)
and adapted to the DeepSeek harness (`.dsh/`) and to a local backlog instead of
GitHub issues. Scripts handle the deterministic stages; agents do the judgment;
every job ends as a pull request.

- Run a feature: open a DeepSeek harness session and run `/factory next`
  (first backlog item), `/factory <feature>` (ad hoc), or `/factory run`
  (drain the backlog, `concurrency` jobs at a time).
- Talk to it: `/factory <anything>` — status questions, feedback on a job that
  escalated, answers to intake's questions, rejections.
- Watch it: `cd factory && python3 -m http.server 8000`, then open
  http://localhost:8000/dashboard.html.
- Tune it: `factory/config.json` (concurrency, rounds, reviewer routing,
  protected paths, ports).
- Change the loop: edit `.dsh/skills/factory/SKILL.md` (the diagram is the
  loop). Change an agent: edit `.dsh/agents/<name>.md`.

## How a job runs

1. **Claim.** `/factory next` claims the first `- [ ]` line of
   `factory/backlog.md` (`- [x] <item> → <job-id>` once created).
2. **intake** reads the quoted feature and decides ACCEPT / NEEDS-INFO /
   REJECT, rates risk and surfaces (`ui`, `security`).
3. **route** maps risk + surfaces to the reviewers and models via
   `config.json` — a low-risk, non-UI job runs a single code reviewer.
4. A **worktree** is created at `.worktrees/<job-id>` on `factory/<job-id>`.
5. **spec-writer** turns intake's scope into `spec.md` with testable criteria.
6. **builder** implements it, pushes, and opens a draft PR.
7. **gate** runs `factory/checks.sh` (tests + build) before any reviewer; a
   failing build goes straight back to the builder.
8. **reviewers** read the diff in parallel; CHANGES sends it back through the
   gate, at most `max_rounds` rounds.
9. **approver** merges what it would merge itself (`gh pr merge`) and
   escalates the rest: the PR is marked ready for you, and merging it is your
   approval.

`jobs/` is gitignored runtime state: one folder per job, holding its
`job.json` (written only by `factory/bin/job`), the quoted feature, intake's
decision, spec, build notes, per-round check logs, reviews, screenshots and
decision. `old_jobs/` archives finished jobs (003–008, v1 era) as examples of
a completed job's files.

Cost per job is optional: set `transcripts_dir` and `prices_usd_per_mtok` in
`config.json` and the dashboard shows the API-equivalent price when the
harness writes Claude-Code-style subagent transcripts; otherwise it shows a
dash.
