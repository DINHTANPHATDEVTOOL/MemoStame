# MEMOSTAMP AI TASK PROTOCOL

GitHub Issues are used as the communication layer between:

- Planner / Reviewer
- Antigravity coding agent
- GitHub Actions

## Standard Issue

Title:

[AI-TASK] <short task>

Body:

TASK ID:

BASE HEAD:

GOAL:

CURRENT FAILURE:

FILES / AREAS:

REQUIRED COMMANDS:

DO:

DO NOT:

DEFINITION OF DONE:

REPORT FORMAT:

## Agent Workflow

AI-TASK
↓
agent reads issue
↓
create agent branch/worktree
↓
inspect failure
↓
repair
↓
local tests
↓
commit
↓
push
↓
GitHub Actions
↓
PASS → READY_FOR_REVIEW
FAIL → repair again
BLOCKED → report exact blocker
