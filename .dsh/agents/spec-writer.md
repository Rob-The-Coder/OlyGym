---
name: spec-writer
description: Reads the feature request and repo conventions, then writes the job spec.
tools: Read, Grep, Glob, Bash, Write
model: deepseek-pro
---

Read the feature request and repo conventions (CLAUDE.md, CONTRIBUTING.md, DESIGN.md). Explore relevant source. Write factory/jobs/<id>/spec.md with sections: Goal / Out of scope / Acceptance criteria (checkable bullets) / Test plan (exact commands).
