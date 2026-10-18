# Factory

A tiny software factory for OlyGym. One skill orchestrates a loop of subagents;
all state lives in board.json and dashboard.html draws it.

- Run a job: open a DeepSeek harness session and run `/factory <feature>` (or `/factory next`).
- Watch it: `cd factory && python3 -m http.server 8000`, then open http://localhost:8000/dashboard.html.
- Change the loop: edit .dsh/skills/factory/SKILL.md (the diagram is the loop).
- Change an agent: edit .dsh/agents/<name>.md.

jobs/003-rate-limiting/ is a worked example of a finished job's file format;
no real code was merged. It shows the shape of each file.
