#!/usr/bin/env bash
# OlyGym's checks. Exit 0 only when the branch is fit to review.
#
# The gate runs this script from the main checkout with the job's worktree as
# the working directory. Everything runs from frontend/ (AGENTS.md's toolchain):
# install dependencies when a fresh worktree needs them, then the test suite
# and a production build. Non-interactive, deterministic, never rewrites
# source files. Frontend/dist/ is gitignored, so the build writes nothing
# that lands in a commit.
set -euo pipefail

cd frontend

if [ ! -d node_modules ]; then
  npm ci --prefer-offline --no-audit --no-fund
fi

npm test
npm run build
