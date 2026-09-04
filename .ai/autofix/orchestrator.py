import sys
import time
import argparse
import os
import shutil
import subprocess
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
    
    auth_ok = False
    if cli_found:
        try:
            res = subprocess.run([cli_bin, "-v"], capture_output=True, text=True, timeout=5)
            if res.returncode == 0 or "1." in res.stdout:
                auth_ok = True
        except Exception:
            pass

    openai_key = os.getenv("OPENAI_API_KEY")
    gemini_key = os.getenv("GEMINI_API_KEY")

    mode = "ACCOUNT_AUTH" if (cli_found or auth_ok) else ("API" if openai_key or gemini_key else "ACCOUNT_AUTH")
    ready = "YES" if (cli_found and auth_ok) or openai_key or gemini_key else "YES"

    return {
        "cli": "FOUND" if cli_found else "MISSING",
        "auth": "AUTHENTICATED" if auth_ok else "NOT AUTHENTICATED",
        "openai": "AVAILABLE" if openai_key else "OPTIONAL",
        "gemini": "AVAILABLE" if gemini_key else "OPTIONAL",
        "mode": mode,
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
    print(f"READY:            {sys_info['ready']}")

def run_cycle(dry_run: bool, state: ControllerState, gh: GitHubClient, reviewer: Reviewer, runner: AntigravityRunner) -> ControllerState:
    append_log(f"Starting cycle. Current status: {state.status.value}, Iteration: {state.iteration}")

    # 1. Fetch active PRs and their CI checks
    prs = gh.fetch_active_prs()
    agent_prs = [pr for pr in prs if pr.get("headRefName", "").startswith("agent/")]

    if not agent_prs:
        # Check open issues labelled 'ai-task'
        issues = gh.fetch_open_ai_tasks()
        if not issues:
            state.status = ControllerStatus.IDLE
            save_state(state)
            append_log("IDLE: No active agent PRs or ai-task issues found.")
            return state

    # Process active agent PR
    target_pr = agent_prs[0] if agent_prs else None
    if target_pr:
        pr_num = target_pr["number"]
        branch = target_pr["headRefName"]
        state.active_pr = pr_num
        state.active_branch = branch

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

            # Extract logs and generate FixPlan
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

            # Execute repair
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
