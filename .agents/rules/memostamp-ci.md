# MEMOSTAMP AUTONOMOUS AGENT RULES

Repository:
DINHTANPHATDEVTOOL/MemoStame

## Mission

Execute assigned GitHub AI repair tasks safely.

The agent may:

- inspect repository files
- inspect GitHub Issues
- inspect GitHub Actions results
- create agent branches
- edit source code
- run local tests
- commit fixes
- push agent branches
- create/update pull requests
- report results in GitHub

The agent must not start unrelated features.

---

## Git Safety

Never:

- force push
- rewrite main history
- delete main
- use destructive reset against user work
- push directly to main unless explicitly authorized
- bypass branch protection
- disable CI to make checks green

Preferred working branch:

agent/<issue-number>-<short-name>

Examples:

agent/134-ios-ci
agent/137-chat-compile
agent/140-auth-regression

Always fetch origin before starting work.

---

## Task Source

GitHub Issue is the source of truth for assigned work.

Preferred task label:

ai-task

Other task states may include:

ai-working
ai-review
ai-blocked
ai-done

Before implementing a task:

1. Read the complete GitHub Issue.
2. Record the requested base HEAD.
3. Check current repository HEAD.
4. Read existing CI results.
5. Identify exact requested Definition of Done.

Do not expand task scope unless required to fix the root cause.

---

## CI Debugging Loop

When a build or test fails:

1. Identify the exact failing job.
2. Find the FIRST real compiler/test error.
3. Determine root cause.
4. Fix that root cause.
5. Search affected source for the same broken pattern.
6. Run the exact failing command locally.
7. If another error appears, repeat.
8. Do not stop after fixing only the first visible error.
9. Commit only after requested local gates pass.

Do not report success from static inspection alone.

---

## iOS Compile Gate

When an iOS task requires the standard simulator build, run:

```bash
xcodebuild build \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  ONLY_ACTIVE_ARCH=NO
```

Required result:

```text
** BUILD SUCCEEDED **
```

Do not disable Swift files or production functionality merely to make this pass.

---

## Android / Shared Regression Gate

Run when required by the task:

```bash
./gradlew \
  :shared:build \
  :androidApp:testDebugUnitTest \
  :androidApp:assembleDebug \
  --no-daemon
```

Required result:

```text
BUILD SUCCESSFUL
```

---

## Production Safety

Never expose, log, commit, or paste:

* GitHub access tokens
* JWT access tokens
* Supabase service_role keys
* signing secrets
* database passwords
* keystore passwords
* Apple signing secrets
* private API keys

Never replace authenticated-user authorization with service_role merely to make tests pass.

---

## Database Safety

Do not automatically perform:

* destructive production migrations
* DROP TABLE
* irreversible production data changes
* production RLS removal
* production security weakening

These require explicit human approval.

---

## Authentication Safety

Do not weaken:

* JWT validation
* auth.uid() authority
* user isolation
* RLS
* account separation

Never log JWT/token values.

---

## Autonomous Repair Limit

For one task:

MAX_FIX_ITERATIONS = 5

If five meaningful repair iterations still do not satisfy local gates:

STOP.

Report:

* current commit
* remaining first error
* attempted fixes
* suspected root cause
* BLOCKED status

Do not enter an infinite repair loop.

---

## Definition of Done

A repair task is complete only when all gates requested by its GitHub Issue pass.

Examples:

Backend PASS
Android PASS
iOS PASS

If GitHub Actions is still pending:

status = CI_PENDING

If GitHub Actions fails:

status = FAILED or BLOCKED

Do not mark task DONE while required CI is red or pending.

---

## Final Agent Report

Always report:

TASK:
BASE HEAD:
NEW HEAD:
BRANCH:
COMMIT:

FILES CHANGED:

ROOT CAUSE:

FIXES:

LOCAL SHARED:
PASS / FAIL / NOT RUN

LOCAL ANDROID:
PASS / FAIL / NOT RUN

LOCAL IOS:
PASS / FAIL / NOT RUN

GITHUB BACKEND:
PASS / FAIL / PENDING / NOT REQUIRED

GITHUB ANDROID:
PASS / FAIL / PENDING / NOT REQUIRED

GITHUB IOS:
PASS / FAIL / PENDING / NOT REQUIRED

REMAINING ERROR:

STATUS:
READY_FOR_REVIEW / CI_PENDING / FAILED / BLOCKED
