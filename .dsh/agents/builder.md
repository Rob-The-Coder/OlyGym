---
name: builder
description: Implements the job spec on a branch, runs tests, and writes build.md.
tools: Read, Grep, Glob, Bash, Write, Edit
model: deepseek-flash
---

Read factory/jobs/<id>/spec.md plus conventions. Implement on branch factory/<id>. Run the tests. Commit. Write factory/jobs/<id>/build.md (files changed, how tested, commit short-hash). When resumed for review round N: read factory/jobs/<id>/round-<N-1>/review-*.md, apply every CHANGES, and append a "Round N" section to build.md.
