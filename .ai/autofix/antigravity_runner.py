import os
import shutil
import subprocess
from pathlib import Path
from typing import Tuple, Optional

from models import FixPlan
from safety import check_command_safety


class AntigravityRunner:
    def __init__(self, repo_root: Path):
        self.repo_root = repo_root
        self.cli_binary = self._find_antigravity_cli()

    def _find_antigravity_cli(self) -> str:
        for cmd in ["agy", "antigravity", "/usr/bin/antigravity", "/home/rd/.local/bin/agy"]:
            if shutil.which(cmd) or Path(cmd).exists():
                return cmd
        return "agy"

    def _run_git(self, args, check: bool = False) -> subprocess.CompletedProcess:
        return subprocess.run(
            ["git"] + list(args),
            cwd=str(self.repo_root),
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            check=check,
        )

    def _worktree_status(self) -> str:
        res = self._run_git(["status", "--porcelain"])
        return res.stdout.strip()

    def create_agent_branch(self, branch_name: str, dry_run: bool = False) -> Tuple[bool, str]:
        if dry_run:
            return True, f"[DRY RUN] Would create/update agent branch {branch_name} from origin/main"

        try:
            if self._worktree_status():
                return False, "Worktree is not clean; refusing to switch/create an agent branch."

            self._run_git(["fetch", "origin", "main"], check=True)
            self._run_git(["checkout", "main"], check=True)
            self._run_git(["pull", "--ff-only", "origin", "main"], check=True)

            exists = self._run_git(["show-ref", "--verify", "--quiet", f"refs/heads/{branch_name}"])
            if exists.returncode == 0:
                self._run_git(["checkout", branch_name], check=True)
                ff = self._run_git(["merge", "--ff-only", "origin/main"])
                if ff.returncode != 0:
                    return False, (
                        f"Existing branch {branch_name} cannot fast-forward to origin/main safely: "
                        f"{ff.stderr[-500:]}"
                    )
            else:
                self._run_git(["checkout", "-b", branch_name, "origin/main"], check=True)

            return True, f"Successfully created/checked out branch {branch_name}"
        except subprocess.CalledProcessError as e:
            return False, f"Failed creating agent branch {branch_name}: {e}"
        except Exception as e:
            return False, f"Failed creating agent branch {branch_name}: {e}"

    def _run_antigravity_prompt(self, prompt: str) -> Tuple[bool, str]:
        """
        Run Antigravity CLI in documented non-interactive headless mode.

        - -p/--print sends a single prompt and exits.
        - --mode=accept-edits allows file writes without an interactive diff prompt.
        - --print-timeout controls Antigravity's own run timeout.
        """
        cmd = [
            self.cli_binary,
            "--mode=accept-edits",
            "--print-timeout",
            "10m",
            "-p",
            prompt,
        ]
        try:
            res = subprocess.run(
                cmd,
                cwd=str(self.repo_root),
                capture_output=True,
                text=True,
                timeout=660,
                encoding="utf-8",
                errors="replace",
            )
        except subprocess.TimeoutExpired:
            return False, "Antigravity headless session exceeded the 11 minute controller timeout."
        except Exception as e:
            return False, f"Failed to invoke Antigravity CLI: {e}"

        stdout = res.stdout.strip()
        stderr = res.stderr.strip()

        if res.returncode != 0:
            diagnostic = stderr or stdout or "no diagnostic output"
            return False, f"Antigravity CLI exited with code {res.returncode}: {diagnostic[:1000]}"

        if not stdout:
            diagnostic = stderr[:1000] if stderr else "no stdout/stderr"
            return False, f"Antigravity CLI returned no model response: {diagnostic}"

        return True, stdout[:2000]

    def invoke_ai_code_edit(self, fix_plan: FixPlan) -> Tuple[bool, str]:
        """Invokes Antigravity CLI to analyze and edit source code files."""
        prompt = (
            f"Fix CI failure: {fix_plan.first_error}\n"
            f"Summary: {fix_plan.failure_summary}\n"
            f"Target Files: {', '.join(fix_plan.affected_files)}\n"
            f"Recommendations: {' '.join(fix_plan.recommended_changes)}\n"
            f"Do not change: {' '.join(fix_plan.do_not_change)}\n"
            f"Security: {' '.join(fix_plan.security_notes)}\n\n"
            "Inspect the repository, make the smallest production-safe edit that resolves the failure, "
            "and write the changes to the current branch. Do not commit, push, merge, or modify secrets."
        )
        ok, detail = self._run_antigravity_prompt(prompt)
        if not ok:
            return False, detail
        if not self._worktree_status():
            return False, "Antigravity completed but produced no file changes for the CI repair."
        return True, f"Antigravity code edit completed. Response: {detail[:500]}"

    def run_local_regression(self) -> Tuple[bool, str]:
        """Runs local Gradle regression gate."""
        env = os.environ.copy()

        linux_jdk = Path("/home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2")
        if linux_jdk.exists():
            env["JAVA_HOME"] = str(linux_jdk)
        elif "USERPROFILE" in env:
            jdk21 = Path(env["USERPROFILE"]) / ".jdks" / "jdk-21.0.4+7"
            if jdk21.exists():
                env["JAVA_HOME"] = str(jdk21)

        gradle_bin = "./gradlew" if (self.repo_root / "gradlew").exists() and os.name != "nt" else ".\\gradlew.bat"
        gradle_cmd = [gradle_bin, ":shared:build", "--no-daemon"]

        safe, reason = check_command_safety(" ".join(gradle_cmd))
        if not safe:
            return False, f"Command safety check failed: {reason}"

        try:
            res = subprocess.run(
                gradle_cmd,
                cwd=str(self.repo_root),
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
                env=env,
            )
            if res.returncode != 0:
                output = (res.stderr or res.stdout)[-2000:]
                return False, f"Local Gradle regression gate failed: {output}"
            return True, "Local Gradle regression gate passed."
        except Exception as e:
            return False, f"Exception running local regression: {e}"

    def execute_task_bootstrap(
        self,
        branch: str,
        issue_num: int,
        issue_title: str,
        issue_body: str,
        fix_plan: FixPlan,
        dry_run: bool = False,
    ) -> Tuple[bool, str, Optional[str]]:
        if dry_run:
            return True, f"[DRY RUN] Would bootstrap Issue #{issue_num} on branch '{branch}' using Antigravity CLI", None

        prompt = (
            f"Implement GitHub Issue #{issue_num}: {issue_title}\n\n"
            f"Task Description:\n{issue_body}\n\n"
            "Instructions:\n"
            "- Inspect the current repository before editing.\n"
            "- Modify repository files to satisfy the task requirement.\n"
            "- Maintain auth, RLS, database, realtime, and security policies unless the issue explicitly requires a safe change there.\n"
            "- Keep edits scoped strictly to the task.\n"
            "- Do not commit, push, merge, or print secrets; the controller handles Git operations.\n"
            "- Finish only after actual file changes have been written to the current branch."
        )

        ai_ok, ai_detail = self._run_antigravity_prompt(prompt)
        if not ai_ok:
            return False, ai_detail, None

        if not self._worktree_status():
            return False, "Antigravity completed but produced no file changes; refusing to create an empty PR.", None

        reg_ok, reg_msg = self.run_local_regression()
        if not reg_ok:
            return False, reg_msg, None

        try:
            self._run_git(["add", "."], check=True)
            commit_msg = f"feat(ai-task): #{issue_num} - {issue_title[:50]}"
            self._run_git(["commit", "-m", commit_msg], check=True)

            sha_res = self._run_git(["rev-parse", "HEAD"], check=True)
            new_sha = sha_res.stdout.strip()

            push_res = self._run_git(["push", "-u", "origin", branch])
            if push_res.returncode != 0:
                return False, f"Git push failed: {push_res.stderr[-1000:]}", None

            return True, f"Successfully bootstrapped and pushed commit {new_sha}", new_sha
        except Exception as e:
            return False, f"Exception during task bootstrap execution: {e}", None

    def execute_fix(
        self,
        branch: str,
        fix_plan: FixPlan,
        dry_run: bool = False,
    ) -> Tuple[bool, str, Optional[str]]:
        """Returns (success, message, commit_sha)."""
        if dry_run:
            return True, f"[DRY RUN] Would invoke Antigravity CLI ({self.cli_binary}) with prompt for files {fix_plan.affected_files}", None

        ai_ok, ai_msg = self.invoke_ai_code_edit(fix_plan)
        if not ai_ok:
            return False, ai_msg, None

        reg_ok, reg_msg = self.run_local_regression()
        if not reg_ok:
            return False, reg_msg, None

        try:
            if not self._worktree_status():
                return False, "Repair produced no file changes; refusing to claim a successful repair.", None

            self._run_git(["add", "."], check=True)
            commit_msg = f"fix(autofix): repair CI failure - {fix_plan.first_error[:50]}"
            self._run_git(["commit", "-m", commit_msg], check=True)

            sha_res = self._run_git(["rev-parse", "HEAD"], check=True)
            new_sha = sha_res.stdout.strip()

            push_res = self._run_git(["push", "origin", branch])
            if push_res.returncode != 0:
                return False, f"Git push failed: {push_res.stderr[-1000:]}", None

            return True, f"Successfully fixed and pushed commit {new_sha}", new_sha
        except Exception as e:
            return False, f"Exception during Antigravity runner execution: {e}", None
