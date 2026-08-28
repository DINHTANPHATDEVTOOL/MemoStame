---
name: fix-ci
description: Autonomous CI repair workflow for MemoStamp repository tasks.
---

# MEMOSTAMP FIX-CI WORKFLOW

## Execution Steps

1. **Fetch Origin**: Run `git fetch origin` to ensure local repository references are up to date.
2. **Read Assigned AI-TASK Issue**: Read the complete GitHub Issue for scope, required commands, and definition of done.
3. **Verify Base HEAD**: Record and verify the requested base HEAD from origin/main.
4. **Create Agent Branch**: Checkout or create `agent/<issue-number>-<short-name>`.
5. **Inspect Latest Relevant CI**: Examine GitHub Actions run logs and annotations for failing jobs.
6. **Find First Real Failure**: Locate the earliest compiler error or failing test assertion.
7. **Fix Root Cause**: Apply targeted code edits to fix the root cause.
8. **Run Exact Local Gate**: Execute local regression commands (e.g. `./gradlew :shared:build :androidApp:testDebugUnitTest :androidApp:assembleDebug --no-daemon` or `xcodebuild`).
9. **Iterative Repair**: Repeat steps 6-8 if secondary deterministic compiler errors appear.
10. **Autonomous Limit**: Stop after a maximum of 5 iterations (`MAX_FIX_ITERATIONS = 5`). If still failing, stop and report BLOCKED.
11. **Commit Passing Fixes**: Stage and commit changes only when local gates succeed.
12. **Push Agent Branch**: Push to `origin agent/<issue-number>-<short-name>`.
13. **Monitor CI**: Wait for and verify GitHub Actions CI status.
14. **Output Agent Result**: Report final standardized AGENT RESULT template.
