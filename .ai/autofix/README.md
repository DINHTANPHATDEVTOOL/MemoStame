# MemoStamp Autonomous AI Bug-Fix Orchestrator

## Overview

The MemoStamp Autofix Orchestrator is a local autonomous controller that connects GitHub CI failures, OpenAI Reviewer analysis, Antigravity local repair execution, Git PRs, and GitHub Actions verification in an automated loop.

```text
GitHub CI Failure
  ↓
OpenAI Reviewer (FixPlan)
  ↓
Antigravity Runner / Worktree
  ↓
Local Gradle Regression Gate
  ↓
Commit & Push Agent Branch / PR
  ↓
GitHub Actions Verification
  ↓
PASS → READY_FOR_REVIEW
FAIL → Automatic Retry (max 5 iterations)
```

---

## Installation & Setup

1. Copy `.env.example` to `.env`:
   ```bash
   cp .ai/autofix/.env.example .ai/autofix/.env
   ```

2. Configure environment variables in `.ai/autofix/.env`:
   ```text
   OPENAI_API_KEY=your_openai_api_key
   OPENAI_MODEL=gpt-4o
   GEMINI_API_KEY=your_gemini_api_key
   MEMOSTAMP_REPO=DINHTANPHATDEVTOOL/MemoStame
   MEMOSTAMP_POLL_SECONDS=60
   MEMOSTAMP_MAX_ITERATIONS=5
   ```

---

## Usage

### 1. Check Controller Status
```powershell
python .ai/autofix/orchestrator.py --status
```

### 2. Run Dry Run (Analysis Only, No Edits/Pushes)
```powershell
python .ai/autofix/orchestrator.py --dry-run --once
```

### 3. Run Single Repair Cycle
```powershell
python .ai/autofix/orchestrator.py --once
```

### 4. Run Continuous Watch Mode
```powershell
python .ai/autofix/orchestrator.py --watch
```

---

## Background Startup & Scheduled Task (Windows)

To automatically launch the controller on logon:

```powershell
.\.ai\autofix\register-autofix-task.ps1
```

To run manually in background:

```powershell
.\.ai\autofix\start-autofix.ps1
```

---

## Security & Safety Rules

- Never commit `.env` or hardcode tokens.
- Unsafe commands (e.g. `git push --force`, `git reset --hard main`, `DROP TABLE`, secret dumping) are automatically blocked by `safety.py`.
- All tokens/secrets in logs are sanitized and redacted.
