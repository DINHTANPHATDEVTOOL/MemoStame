import sys
import time
import argparse
import os
import shutil
import subprocess
import re
from pathlib import Path

# Add autofix directory to sys.path
sys.path.insert(0, str(Path(__file__).parent))

from dotenv import load_dotenv

# Load .env if present
env_path = Path(__file__).parent / ".env"
if env_path.exists():
    load_dotenv(dotenv_path=env_path)

from models import ControllerState, ControllerStatus, FixPlan
from state import SingleInstanceLock, load_state, save_state, append_log
from github_client import GitHubClient
from reviewer import Reviewer
from antigravity_runner import AntigravityRunner

REPO_NAME = os.getenv("MEMOSTAMP_REPO", "DINHTANPHATDEVTOOL/MemoStame")
POLL_INTERVAL = int(os.getenv("MEMOSTAMP_POLL_SECONDS", "120"))
MAX_ITERATIONS = int(os.getenv("MEMOSTAMP_MAX_ITERATIONS", "5"))

def check_system_status() -> dict:
    cli_bin = shutil.which("agy") or shutil.which("antigravity") or ("/usr/bin/antigravity" if Path("/usr/bin/antigravity").exists() else None)
    cli_found = cli_bin is not None
    
    auth_status = "NOT AUTHENTICATED"
    if cli_found:
        try:
            res = subprocess.run([cli_bin, "-s"], capture_output=True, text=True, timeout=5)
            if res.returncode == 0 and ("cloudcode" in res.stdout.lower() or "workspace stats" in res.stdout.lower() or "antigravity" in res.stdout.lower()):
                auth_status = "AUTHENTICATED"
            else:
                auth_status = "UNKNOWN"
        except Exception:
            auth_status = "UNKNOWN"

    gh_bin = shutil.which("gh")
    gh_found = gh_bin is not None
    gh_auth_status = "NOT AUTHENTICATED"
    if gh_found:
        try:
            env = os.environ.copy()
            res = subprocess.run([gh_bin, "auth", "status"], capture_output=True, text=True, timeout=5, env=env)
            if res.returncode == 0:
                gh_auth_status = "AUTHENTICATED"
        except Exception:
            pass

    openai_key = os.getenv("OPENAI_API_KEY")
    gemini_key = os.getenv("GEMINI_API_KEY")

    if auth_status == "AUTHENTICATED":
        mode = "ACCOUNT_AUTH"
    elif openai_key or gemini_key:
        mode = "API"
    else:
        mode = "ACCOUNT_AUTH"

    is_ai_ready = (mode == "ACCOUNT_AUTH" and auth_status == "AUTHENTICATED") or (mode == "API" and bool(openai_key or gemini_key))
    is_gh_ready = (gh_found and gh_auth_status == "AUTHENTICATED")

    ready = "YES" if (is_ai_ready and is_gh_ready) else "NO"

    return {
        "cli": "FOUND" if cli_found else "MISSING",
        "auth": auth_status,
        "openai": "AVAILABLE" if openai_key else "OPTIONAL",
        "gemini": "AVAILABLE" if gemini_key else "OPTIONAL",
        "mode": mode,
        "gh_cli": "FOUND" if gh_found else "MISSING",
        "gh_auth": gh_auth_status,
        "ready": ready
    }

def print_status(state: ControllerState):
    sys_info = check_system_status()
    print("MEMOSTAMP AUTOFIX STATUS")
    print("=========================")
    print(f"Status:      {state.status.value}")
    print(f"Active Issue: #{state.active_issue if state.active_issue else 'None'}")
    print(f"Branch:       {state.active_branch or 'None'}")
    print(f"Active PR:    #{state.active_pr if state.active_pr else 'None'}")
    print(f"Iteration:    {state.iteration} / {MAX_ITERATIONS}")
    print(f"Last Error:   {state.last_error or 'None'}")
    print("")
    print(f"ANTIGRAVITY CLI:  {sys_info['cli']}")
    print(f"ANTIGRAVITY AUTH: {sys_info['auth']}")
    print(f"OPENAI API:       {sys_info['openai']}")
    print(f"GEMINI API:       {sys_info['gemini']}")
    print(f"FULL AUTO MODE:   {sys_info['mode']}")
    print(f"GITHUB CLI:       {sys_info['gh_cli']}")
    print(f"GITHUB AUTH:      {sys_info['gh_auth']}")
    print(f"READY:            {sys_info['ready']}")

def run_cycle(dry_run: bool, state: ControllerState, gh: GitHubClient, reviewer: Reviewer, runner: AntigravityRunner) -> ControllerState:
    append_log(f"Starting cycle. Current status: {state.status.value}, Iteration: {state.iteration}")

    prs = gh.fetch_active_prs()
    agent_prs = [pr for pr in prs if pr.get("headRefName", "").startswith("agent/")]

    if not agent_prs:
        issues = gh.fetch_open_ai_tasks()
        if not issues:
            state.status = ControllerStatus.IDLE
            save_state(state)
            append_log("IDLE: No active agent PRs or ai-task issues found.")
            return state

        # Sort issues by number ascending (oldest / highest priority first)
        issues.sort(key=lambda x: x.get("number", 0))
        target_issue = issues[0]
        issue_num = target_issue["number"]
        issue_title = target_issue.get("title", f"task-{issue_num}")
        issue_body = target_issue.get("body", "")

        slug = re.sub(r"[^a-zA-Z0-9]+", "-", issue_title.lower()).strip("-")
        branch_name = f"agent/{issue_num}-{slug}"

        append_log(f"Bootstrapping new task from Issue #{issue_num}: '{issue_title}' onto branch '{branch_name}'")

        # 1. Create safe agent branch from latest origin/main
        branch_ok, branch_msg = runner.create_agent_branch(branch_name, dry_run=dry_run)
        if not branch_ok:
            state.last_error = f"Failed to create branch {branch_name}: {branch_msg}"
            save_state(state)
            return state

        # 2. Establish ownership
        state.active_issue = issue_num
        state.active_branch = branch_name
        state.iteration = 0
        save_state(state)

        if not dry_run:
            gh.update_issue_labels(issue_num, add_labels=["ai-working"], remove_labels=["ai-task"])

        # 3. Invoke Antigravity CLI using the complete Issue body as task instruction
        fix_plan = FixPlan(
            failure_summary=f"Task Issue #{issue_num}: {issue_title}",
            first_error="",
            root_cause=issue_body,
            affected_files=[],
            search_patterns=[],
            recommended_changes=[issue_body],
            commands_to_run=[],
            do_not_change=["Do not remove auth/RLS security rules."],
            security_notes=["Never log or expose secret tokens."],
            confidence=1.0
        )

        exec_ok, exec_msg, commit_sha = runner.execute_task_bootstrap(
            branch=branch_name,
            issue_num=issue_num,
            issue_title=issue_title,
            issue_body=issue_body,
            fix_plan=fix_plan,
            dry_run=dry_run
        )

        if not exec_ok:
            state.last_error = f"Task bootstrap execution failed: {exec_msg}"
            save_state(state)
            return state

        # 4. Create PR to main
        pr_num = 0
        if not dry_run:
            pr_num = gh.create_pr(
                head=branch_name,
                base="main",
                title=f"fix(ai-task): #{issue_num} {issue_title}",
                body=f"Automated resolution for Issue #{issue_num}\n\n{issue_body}"
            )
            state.active_pr = pr_num if pr_num > 0 else None

        state.status = ControllerStatus.CI_PENDING
        save_state(state)
        append_log(f"Successfully bootstrapped Issue #{issue_num} into PR #{pr_num} on branch {branch_name}. Status: CI_PENDING")
        return state

    # Active agent PR exists
    target_pr = agent_prs[0]
    pr_num = target_pr["number"]
    branch = target_pr["headRefName"]
    state.active_pr = pr_num
    state.active_branch = branch

    match = re.search(r"^agent/(\d+)-", branch)
    if match:
        state.active_issue = int(match.group(1))

    checks_data = gh.fetch_latest_pr_checks(pr_num)
    checks = checks_data.get("checks", [])
    
    pending_checks = [c for c in checks if c.get("state") in ["PENDING", "QUEUED", "IN_PROGRESS"]]
    failed_checks = [c for c in checks if c.get("state") in ["FAILURE", "ERROR", "CANCELLED"]]
    passed_checks = [c for c in checks if c.get("state") in ["SUCCESS"]]

    if pending_checks:
        state.status = ControllerStatus.CI_PENDING
        save_state(state)
        append_log(f"CI_PENDING: PR #{pr_num} has {len(pending_checks)} pending checks.")
        return state

    if not failed_checks and passed_checks and not pending_checks:
        state.status = ControllerStatus.READY_FOR_REVIEW
        save_state(state)
        append_log(f"READY_FOR_REVIEW: All checks passed for PR #{pr_num}!")
        if not dry_run and state.active_issue:
            gh.update_issue_labels(state.active_issue, add_labels=["ai-review"], remove_labels=["ai-working", "ai-task"])
        return state

    if failed_checks:
        if state.iteration >= MAX_ITERATIONS:
            state.status = ControllerStatus.BLOCKED
            state.last_error = f"Max iterations ({MAX_ITERATIONS}) reached for PR #{pr_num}"
            save_state(state)
            append_log(f"BLOCKED: Max iterations reached for PR #{pr_num}")
            if not dry_run and state.active_issue:
                gh.update_issue_labels(state.active_issue, add_labels=["ai-blocked"], remove_labels=["ai-working"])
            return state

        state.status = ControllerStatus.REPAIRING
        state.iteration += 1
        save_state(state)
        append_log(f"CI Failure detected on PR #{pr_num}. Starting repair iteration {state.iteration}/{MAX_ITERATIONS}")

        first_fail = failed_checks[0]
        job_name = first_fail.get("name", "Failed Check")
        run_link = first_fail.get("link", "")
        
        rules_path = Path(__file__).parents[2] / ".agents" / "rules" / "memostamp-ci.md"
        agent_rules = rules_path.read_text(encoding="utf-8") if rules_path.exists() else ""

        fix_plan = reviewer.generate_fix_plan(
            repo=REPO_NAME,
            task_id=f"PR #{pr_num}",
            branch=branch,
            commit=target_pr.get("commits", [{}])[-1].get("oid", "HEAD") if target_pr.get("commits") else "HEAD",
            job_name=job_name,
            first_error=job_name,
            log_snippet=f"Failed check link: {run_link}",
            agent_rules=agent_rules,
            iteration=state.iteration
        )

        append_log(f"Reviewer produced FixPlan: {fix_plan.failure_summary}")

        success, msg, new_sha = runner.execute_fix(branch, fix_plan, dry_run=dry_run)
        if success:
            state.status = ControllerStatus.CI_PENDING
            save_state(state)
            append_log(f"Repair executed successfully: {msg}")
        else:
            state.last_error = msg
            save_state(state)
            append_log(f"Repair execution failed: {msg}")

    return state

def main():
    parser = argparse.ArgumentParser(description="MemoStamp Full Auto AI Bug-Fix Orchestrator")
    parser.add_argument("--once", action="store_true", help="Run one cycle and exit")
    parser.add_argument("--watch", action="store_true", help="Run continuously in watch mode")
    parser.add_argument("--dry-run", action="store_true", help="Analyze without executing modifications")
    parser.add_argument("--status", action="store_true", help="Display current controller status")

    args = parser.parse_args()

    state = load_state()

    if args.status:
        print_status(state)
        sys.exit(0)

    lock = SingleInstanceLock()
    if not lock.acquire():
        print("Another instance of MemoStamp Autofix controller is currently running. Exiting.")
        sys.exit(0)

    try:
        gh = GitHubClient(repo=REPO_NAME)
        reviewer = Reviewer()
        repo_root = Path(__file__).parents[2]
        runner = AntigravityRunner(repo_root=repo_root)

        if args.once or args.dry_run:
            state = run_cycle(dry_run=args.dry_run, state=state, gh=gh, reviewer=reviewer, runner=runner)
            print_status(state)
        elif args.watch:
            print(f"Starting MemoStamp Autofix Controller in WATCH mode (poll interval: {POLL_INTERVAL}s)...")
            while True:
                state = run_cycle(dry_run=False, state=state, gh=gh, reviewer=reviewer, runner=runner)
                time.sleep(POLL_INTERVAL)
        else:
            parser.print_help()
    finally:
        lock.release()

if __name__ == "__main__":
    main()
