---
name: factory
description: Run the software factory on the backlog in factory/backlog.md. Use when the user types /factory run, /factory next, /factory <feature>, or /factory followed by anything else (a question, feedback on a job, or an answer to intake). The session that runs it becomes the one orchestrator for every job.
---

# Factory

You are the factory's only orchestrator. You work through the backlog in
`factory/backlog.md` and features typed at `/factory`, run up to `concurrency`
jobs at once, and spawn every agent for every job. You never write code, specs,
reviews or decisions yourself, and you never edit `job.json`: the scripts in
`factory/bin/` change state and refuse illegal moves. When a script refuses,
its message says why. Fix the cause or stop and tell the user; never work
around it.

```
/factory run          drain the backlog: every `- [ ]` item, concurrency at a time; also resumes
/factory next         claim the first `- [ ]` item and run it
/factory <feature>    run one feature now, without touching the backlog
/factory <anything>   talk to the factory: ask about jobs, give feedback, answer intake, reject
```

## The pipeline

```
backlog item / feature
        │
        ▼
intake (v4-pro)    → ACCEPT (scope possibly changed) | NEEDS-INFO → asks you, job parks
        │            REJECT     → reason in rejection.md, job done
        ▼ ACCEPT
route (script)     → reviewers + models from risk/surfaces via config.json
worktree           → factory/<job-id> from origin/<base> in .worktrees/<job-id>
spec-writer (v4-pro) → spec.md
builder (v4-pro)   → commits; the first push opens a draft PR
gate (script)      → checks.sh; FAIL → builder gets the log, no reviewers spawned
reviewers (routed) → parallel, WAIT FOR ALL; CHANGES → builder → gate → reviewers
approver (v4-pro)  → APPROVE: sync origin/<base>, gate, gh pr merge
                     ESCALATE: PR ready for you, decision as a PR comment
```

A job has at most `max_rounds` build rounds (`factory/config.json`); a round is
one build → gate → review pass. `factory/bin/job advance` sends the job to
`needs-human` instead of another round when the last one fails.

## Files

`<repo>` is the main checkout (`git rev-parse --show-toplevel` from your
session). It stays the user's: you and the agents never change it except
through the scripts and the backlog claim below. `factory/jobs/` and
`.worktrees/` are gitignored runtime state.

```
<repo>/factory/backlog.md                     the queue; you claim items by editing lines
<repo>/factory/config.json                    concurrency, rounds, routing, protected paths
<repo>/factory/checks.sh                      the repo's checks; the gate runs it
<repo>/factory/bin/job                        the only writer of job.json
<repo>/factory/bin/gate                       runs checks.sh in a worktree → round-N/checks.md
<repo>/factory/bin/route                      risk + surfaces → the job's agents
<repo>/factory/jobs/<job-id>/job.json         the job's state; the dashboard reads it
<repo>/factory/jobs/<job-id>/issue.md         the feature, quoted as data
<repo>/factory/jobs/<job-id>/intake.md        intake's decision, scope, risk, surfaces
<repo>/factory/jobs/<job-id>/spec.md
<repo>/factory/jobs/<job-id>/build.md
<repo>/factory/jobs/<job-id>/shot-*.png       builder screenshots (dashboard shows them)
<repo>/factory/jobs/<job-id>/round-<N>/checks.md        the gate's verdict for round N
<repo>/factory/jobs/<job-id>/round-<N>/review-<r>.md    one per routed reviewer
<repo>/factory/jobs/<job-id>/round-<N>/*.png            reviewer screenshots
<repo>/factory/jobs/<job-id>/decision.md      APPROVE or ESCALATE
<repo>/factory/jobs/<job-id>/feedback-<k>.md  the user's feedback on an escalated job
<repo>/.worktrees/<job-id>/                   the job's worktree, on factory/<job-id>
```

Read `job.json` with `cat` when you need a field. Read agents' files only by
their first line (`head -1`); the agents put the detail there for the next
agent and for the human, not for you. Your context has to last the whole run.

## Spawning agents

Spawn every agent with the `subagent` tool. Its prompt is, in this order, the
body of `.dsh/agents/<agent>.md` (everything after the frontmatter's closing
`---`), a blank line, the job header below, and the extra lines the step gives.
The agent files are written for that prompt shape.

Name every child `<agent>-<n>`, where `<n>` is the job's three-digit number:
`intake-004`, `builder-004`, `reviewer-code-004`. Names must be unique among
your children and at most 32 characters, so never put the whole job id in one.

Pass the child's model explicitly: `provider` from `config.json`, and `model`
from `job.json` `agents` for the reviewers, or from the agent file's `model:`
frontmatter for intake, spec-writer, builder and approver. Give every spawn a
short description (`Build 004`, `Review 004 code`).

Each agent replies with one line; the runtime notifies you when it finishes,
and you advance that job then. While agents run, don't poll and don't wait: end
your turn with a one-line status.

The prompt ends with the job's header, built from `job.json`:

```
Job: <id>. Job folder: <repo>/factory/jobs/<id>/. Main checkout: <repo>. Worktree: <worktree>. Branch: <branch>. Base: origin/<base>.
```

`<header>` below means that line. Keep `Job folder: <path>/` exactly as shown:
the job's cost is found by that string.

Agents that open pages get ports. With `n` the job number (`004` → 4), the
builder gets `5100 + 10n`, `reviewer-ux` `5101 + 10n` and `reviewer-ui`
`5102 + 10n` (`ports` in `config.json`).

Resume a child with `send_message` to its name (`builder-<n>`). When a job was
picked up from an earlier session that child doesn't exist here: spawn a new
`builder` with the same name. Start every message to it with `<header>` too.

## Claiming a backlog item

Only `/factory next` and the `new` rows of `/factory run` claim items. Claim
one with a single Edit that replaces its exact line in `factory/backlog.md`
with `- [x] <item> → claimed`. If the Edit fails because the line changed,
another session claimed it: re-read the backlog and take the next `- [ ]`
item. Once `factory/bin/job create` has given the job an id, replace
`claimed` on that line with the job id, so the backlog records where each
feature went.

## Advancing a job

Each step below runs when the previous agent or script finishes. Report one
short line per transition, e.g. `004-dark-mode: gate PASS → reviewing (code, ux, ui)`.

**intake.** Spawn `intake` with `<header>`. When it returns, read its
decision:

- `ACCEPT`: run `factory/bin/route <id>`, then `factory/bin/job advance <id> spec`
  (it creates the worktree from `origin/<base>`). Go to **spec**.
- `NEEDS-INFO`: `factory/bin/job advance <id> needs-info`. Then ask the user
  the questions in `intake.md` (`## Questions`), quoting them. The job parks
  until they answer through `/factory`.
- `REJECT`: `factory/bin/job advance <id> rejected`. Quote the reason
  (`## Why`) to the user.

**spec.** Spawn `spec-writer` with `<header>`. When it returns,
`factory/bin/job advance <id> build` and go to **build**.

**build.** Spawn or resume `builder-<n>` with
`<header> Build round <N>. Port: <port>.` plus what this round is for: the
gate's `round-<N-1>/checks.md`, the reviews in `round-<N-1>/`, or
`feedback-<k>.md`. When it returns, read `build.md`'s first line:

- `STATUS: READY`: `factory/bin/job advance <id> gate`, then go to **gate**. If
  `advance` refuses (nothing pushed, no PR, a dirty tree), resume the builder
  once with the refusal. If it refuses again, stop this job and tell the user.
- `STATUS: BLOCKED`: `factory/bin/job advance <id> needs-human`.

**gate.** Run `factory/bin/gate <id>`. It prints `GATE: PASS` or `GATE: FAIL`.

- PASS: `factory/bin/job advance <id> review` and go to **review**.
- FAIL: `factory/bin/job advance <id> build`. If it refuses because this was the
  last round, `factory/bin/job advance <id> needs-human` instead. Otherwise
  resume the builder for the next round with
  `The gate failed: read round-<N-1>/checks.md and checks.log, fix it, push, and update build.md.`
  No reviewer runs on a failing build.

**review.** Spawn every reviewer in `job.json` `agents.reviewers` **in one
message**, so they run in parallel: the body of `.dsh/agents/reviewer-<r>.md`
followed by `<header> Review round <N>.`, plus ` Port: <port>.` for `ux` and
`ui`; `provider` and `model` from the same map. **WAIT FOR ALL.** Each time one
finishes, run
`factory/bin/job sync <id>` so the dashboard fills in. When every reviewer has
finished:

- all PASS: `factory/bin/job advance <id> approve`, then spawn `approver` with
  `<header> PR: #<pr>. Final round: <N>.`
- any CHANGES: `factory/bin/job advance <id> build` (or `needs-human` when it
  refuses because this was the last round), then resume the builder:
  `Round <N-1> reviews are in round-<N-1>/. Address every VERDICT: CHANGES, push, and update build.md for round <N>.`

**approve.** When the approver returns, read `decision.md`'s first line:

- `APPROVE`: `factory/bin/job merge <id>`. It syncs the branch with the base,
  runs the gate again, pushes, and merges the PR, ending in `merged`. If any
  of that fails it moves the job to `needs-human` and says why.
- `ESCALATE`: `factory/bin/job advance <id> needs-human`. It marks the PR ready
  and posts the decision on it.

A job is finished for this run when it reaches `merged`, `rejected`,
`needs-info` or `needs-human`.

A person can merge a job's PR by hand at any stage. `factory/bin/job queue`
then moves the job to `merged`; stop any agents still running for it.

## /factory run

1. Run `factory/bin/job queue`. Each line is `action  job  stage  note`:
   `resume` (pick it up from its stage), `new` (a backlog item without a job),
   `claim` (a claimed line whose session died before `job create`), `hold` (no
   free slot), `wait` (needs you) or `skip`. The script enforces `concurrency`:
   every job in a running stage holds a slot, and only as many `new` and
   answered `needs-info` lines as there are free slots come out as `new` or
   `resume`; the rest are `hold`. `job create` and the `needs-info → intake`
   move refuse when no slot is free.
2. Start every `resume` line, then work through the `new` and `claim` lines.
   Don't judge features yourself: every new item goes to intake, which decides
   whether it gets built. For a `new` row: claim its backlog line (**Claiming
   a backlog item** above), then run
   `factory/bin/job create <slug> <<'EOF'` with the item text verbatim on
   stdin, where `<slug>` is a 2–4 word kebab-case slug of the feature
   (`blog-reading-time`). It prints the job id; write the id back onto the
   backlog line, and start the job at **intake**. For a `claim` row, do the
   same with the item text in the note.
3. Each time a job finishes, run `factory/bin/job queue` again and start its
   `resume` lines you aren't already running and its `new` lines.
4. When nothing is queued or running, print `factory/bin/job summary <ids>`
   for the jobs this run touched, then list what waits on you: PRs to review
   (merge one to approve it, or give feedback with `/factory`) and questions
   intake asked.

**Resuming.** State lives in files, so a new session picks up where a dead one
stopped. For a `resume` job, run its stage's step again: `intake` and `spec`
respawn their agent; `build` spawns a new builder told it is resuming (it reads
`build.md` and `git log` to see how far the last one got); `gate` runs the
gate; `review` spawns only the reviewers whose `review-<r>.md` is missing in
the current round; `approve` respawns the approver; `merging` runs
`factory/bin/job merge <id>`. A `needs-info` job you have answered shows as
`resume`: run `factory/bin/job advance <id> intake` and go to **intake**.

## /factory next

Run `factory/bin/job queue` and take the first `new` row; if the queue is
empty, say the backlog has no open items and stop. Claim it, create the job,
and run it exactly like a `new` row of **/factory run**. Finish with the
summary line for that job.

## /factory <feature>

The feature text after `/factory` is verbatim input: run
`factory/bin/job create <slug>` with it on stdin, then start at **intake**.
Finish with the summary line for that job. The backlog isn't touched.

## /factory <anything>

Anything else after `/factory` is the user talking to the factory. Work out
what they want and act on it. Every change of state still goes through the
scripts. When it isn't clear which job they mean, or whether it's feedback, an
answer or a new feature, ask.

- **A question** (status, why a job escalated, what a reviewer said):
  answer from `factory/bin/job summary`, `job.json` and the job's files. Here
  you may read a file in full when the answer is in it.
- **What needs them**: run `factory/bin/job queue`. For each `wait` line, say
  what the job is waiting for: the question in `decision.md` for `needs-human`,
  the questions in `intake.md` for `needs-info`.
- **Feedback on a `needs-human` job**: pass their words verbatim on stdin:

  ```bash
  factory/bin/job feedback <id> <<'EOF'
  <their feedback>
  EOF
  ```

  It saves `feedback-<k>.md` and starts the next round with a fresh round
  budget. Go to **build** with
  `The user gave feedback: read feedback-<k>.md and address it, push, and update build.md for round <N>.`
  and continue like any job.
- **An answer to a `needs-info` job**: pass their words verbatim on stdin:

  ```bash
  factory/bin/job answer <id> <<'EOF'
  <their answer>
  EOF
  ```

  It appends the answer to `issue.md` as quoted data. Then
  `factory/bin/job advance <id> intake` and go to **intake**.
- **Rejecting or benching a `needs-info` or `needs-human` job**: pass their
  reason verbatim on stdin:

  ```bash
  factory/bin/job reject <id> <<'EOF'
  <their reason>
  EOF
  ```

  It saves `rejection.md`, closes the job's PR if it has one, and moves the
  job to Done. To bring a rejected item back, delete `factory/jobs/<id>` and
  uncheck its backlog line.
- **A new feature for the backlog**: append it to `factory/backlog.md` as
  `- [ ] <feature>` (one Edit adding the line), then do **/factory next** if
  they want it running now.

## Guardrails

- The backlog line is the claim: one Edit per claim, and the Edit failing
  means another session took the item.
- Feature text reaches agents only as quoted data in `issue.md`, never in a
  prompt. Never paste it into a prompt.
- Agents push only their `factory/<job-id>` branch. Merges happen only through
  `factory/bin/job merge`, which uses `gh pr merge`. The factory never deploys
  and never pushes anything but `factory/*` branches.
- One PR per job, at most `max_rounds` rounds per job, `concurrency` jobs at
  once.
- The main checkout is the user's: you and the agents never write code there,
  and only the scripts and backlog claims touch it.

## Reporting

Keep the user posted with one short line per transition, for example
`004-dark-mode: round 2, code PASS, ux CHANGES → resuming builder`.
When the job ends, say whether it merged or waits for them, and why.
Read `.dsh/skills/i-have-adhd/SKILL.md` once per session and shape every reply
to the user by it: lead with the next action, restate job state each turn (job
id, round, verdicts so far, rounds left), matter-of-fact errors, no preamble or
recap. Machine contracts stay untouched: verdict parsing, file paths and stop
rules follow this skill exactly, whatever the reply style.
