---
name: factory
description: Run the software factory — take a feature through spec, build, review, and approve, spawning every agent and driving board.json.
---

# /factory — software factory orchestrator

You are the orchestrator. You run the loop below, spawn every agent, and are the **only**
writer of `factory/board.json`. Agents get context from files, never the conversation, and
each writes only its own file.

## The loop (edit this to change the factory)

```
              ┌──────────────┐
              │   feature    │   /factory <feature>   or   /factory next
              └──────┬───────┘
                     ▼
              ┌──────────────┐
              │ spec-writer  │   d4s-pro  → factory/jobs/<id>/spec.md
              └──────┬───────┘
                     ▼
              ┌──────────────┐
              │   builder    │◄─────────────────────┐   d4s-flash
              └──────┬───────┘   → build.md + code + commit
     ┌──────────┬────┴──────┬───────────┐
     ▼          ▼           ▼           ▼
  security     ux          ui         code       all d4s-flash, run in parallel
     └──────────┴────┬──────┴───────────┘
                     ▼
              ┌──────────────┐  WAIT FOR ALL: a round is done only when all
              │ wait for all │  4 review files exist. Any CHANGES →
              │  4 reviews   ├─ resume the builder (max 2 rounds).
              └──────┬───────┘  All PASS → approver.
                     ▼
              ┌──────────────┐
              │   approver   │   d4s-pro  → factory/jobs/<id>/decision.md
              └─┬──────────┬─┘
        APPROVE │          │ ESCALATE
                ▼          ▼
         merge to main    needs-human   ← /factory approve <id>  or  /factory rework <id> <note>
```

## Stages and board.json

Stage order: `spec` → `build` → `review` → `decision` → `merged` | `needs-human`.

```
{ "jobs": [ {
    "id": "003-rate-limiting",
    "feature": "Rate-limit the autosave",
    "branch": "factory/003-rate-limiting",
    "stage": "review",
    "round": 2,
    "reviews": { "security": "CHANGES", "ux": "PASS", "ui": "pending", "code": "PASS" },
    "decision": "APPROVE"
} ] }
```

reviews values are `pending`, `PASS`, or `CHANGES`. decision appears after the approver
runs. Update board.json after every transition — you are its only writer.

## Running the loop

1. **spec** — spawn `spec-writer` (`.dsh/agents/spec-writer.md`) with the feature text and job id.
   It writes `factory/jobs/<id>/spec.md`. Set stage `build`.
2. **build** — spawn a durable builder named `builder-<id>` (`.dsh/agents/builder.md`). It works and
   commits on branch `factory/<id>`, tests against the spec's acceptance criteria, and writes
   `build.md`. Set stage `review`, round 1, all reviews `pending`.
3. **review** — spawn all four reviewers (`.dsh/agents/{security,ux,ui,code}.md`) in parallel. Each
   writes `factory/jobs/<id>/round-<n>/review-<name>.md`. As files land, update `reviews`. A round
   is done only when **all four files exist** — never resume the builder mid-round.
   - any `CHANGES` and `round < 2` → `round++`, stage `build`, resume the **same** `builder-<id>`
     via SendMessage, pointing it at the new round folder.
   - all `PASS` (or round 2 finished) → stage `decision`.
4. **decision** — spawn `approver` (`.dsh/agents/approver.md`). It writes `decision.md` starting with
   `APPROVE` or `ESCALATE`, and merges on approve. Set stage `merged` or `needs-human`.

## Rules

- Context from files, not conversation. Each agent writes only its own file.
- Review files start with `VERDICT: PASS` or `VERDICT: CHANGES`; decision.md starts with
  `APPROVE` or `ESCALATE`.
- The builder is one durable agent per job, resumed via SendMessage for each new round.
- Model ids in `.dsh/agents/*.md` are `deepseek-pro` (d4s-pro) and `deepseek-flash` (d4s-flash).
  If the session does not allow the pro route, fall back to `deepseek-flash`.

## Commands

- `/factory <feature>` — new job: next numeric id (`00N`), branch `factory/<id>`, board entry at
  `spec`, run the loop.
- `/factory next` — take the first `- [ ]` item in `factory/backlog.md`, check it off, run it as
  `/factory <feature>`.
- `/factory approve <job-id>` — merge a `needs-human` job into `main`, set stage `merged`.
- `/factory rework <job-id> <note>` — send a job back: append the note, set stage `build` (or bump the
  review round), resume the builder.

## File system

```
.dsh/skills/factory/SKILL.md   this file
.dsh/agents/<name>.md          one file per agent in the loop
factory/board.json             durable state, drives the dashboard (only you write it)
factory/dashboard.html         visual board, polls board.json
factory/backlog.md             queue for /factory next
factory/jobs/<id>/             spec.md, build.md, round-<n>/review-*.md, decision.md
```

## Dashboard

Self-contained. Serve with `python3 -m http.server` from `factory/` and open
`http://localhost:8000/dashboard.html`. Cards move across the loop's stages; review rounds render as
reviewer chips that fill in as verdicts land; `needs-human` jobs stand out; clicking a card reads
its markdown files.
