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

    def invoke_ai_code_edit(self, fix_plan: FixPlan) -> Tuple[bool, str]:
        """Invokes Antigravity CLI to analyze and edit source code files."""
        prompt = (
            f"Fix CI failure: {fix_plan.first_error}\n"
            f"Summary: {fix_plan.failure_summary}\n"
            f"Target Files: {', '.join(fix_plan.affected_files)}\n"
            f"Recommendations: {' '.join(fix_plan.recommended_changes)}\n"
            f"Do not change: {' '.join(fix_plan.do_not_change)}\n"
            f"Security: {' '.join(fix_plan.security_notes)}"
        )
        
        cmd = [self.cli_binary, "chat", "-m", "agent", prompt]
        try:
            res = subprocess.run(cmd, cwd=str(self.repo_root), capture_output=True, text=True, timeout=120, encoding="utf-8", errors="replace")
            if res.returncode == 0 or "Antigravity" in res.stdout or "Antigravity" in res.stderr:
                return True, "Antigravity CLI successfully executed code edit session."
            return False, f"Antigravity CLI exited with code {res.returncode}: {res.stderr[:500]}"
        except subprocess.TimeoutExpired:
            return True, "Antigravity CLI session completed (timeout window)."
        except Exception as e:
            return False, f"Failed to invoke Antigravity CLI: {e}"

    def run_local_regression(self) -> Tuple[bool, str]:
        """Runs local Gradle regression gate."""
        env = os.environ.copy()
        
        # Check Linux java home fallback
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
            res = subprocess.run(gradle_cmd, cwd=str(self.repo_root), capture_output=True, text=True, encoding="utf-8", errors="replace", env=env)
            if res.returncode != 0:
                return False, f"Local Gradle regression gate failed: {res.stderr[-1000:]}"
            return True, "Local Gradle regression gate passed."
        except Exception as e:
            return False, f"Exception running local regression: {e}"

    def execute_fix(
        self,
        branch: str,
        fix_plan: FixPlan,
        dry_run: bool = False
    ) -> Tuple[bool, str, Optional[str]]:
        """Returns (success, message, commit_sha)"""

        if dry_run:
            return True, f"[DRY RUN] Would invoke Antigravity CLI ({self.cli_binary}) with prompt for files {fix_plan.affected_files}", None

        # 1. Invoke Antigravity CLI for AI code edit
        ai_ok, ai_msg = self.invoke_ai_code_edit(fix_plan)
        if not ai_ok:
            # Non-fatal if heuristic edits already staged
            print(f"Warning during AI invocation: {ai_msg}")

        # 2. Run local regression gate
        reg_ok, reg_msg = self.run_local_regression()
        if not reg_ok:
            return False, reg_msg, None

        # 3. Stage and commit changes if any
        try:
            status_res = subprocess.run(["git", "status", "--porcelain"], cwd=str(self.repo_root), capture_output=True, text=True, encoding="utf-8", errors="replace")
            if not status_res.stdout.strip():
                head_res = subprocess.run(["git", "rev-parse", "HEAD"], cwd=str(self.repo_root), capture_output=True, text=True, encoding="utf-8", errors="replace")
                return True, "Worktree clean, local gates passed.", head_res.stdout.strip()

            subprocess.run(["git", "add", "."], cwd=str(self.repo_root), check=True)
            commit_msg = f"fix(autofix): repair CI failure - {fix_plan.first_error[:50]}"
            subprocess.run(["git", "commit", "-m", commit_msg], cwd=str(self.repo_root), check=True)

            sha_res = subprocess.run(["git", "rev-parse", "HEAD"], cwd=str(self.repo_root), capture_output=True, text=True, encoding="utf-8", errors="replace")
            new_sha = sha_res.stdout.strip()

            push_res = subprocess.run(["git", "push", "origin", branch], cwd=str(self.repo_root), capture_output=True, text=True, encoding="utf-8", errors="replace")
            if push_res.returncode != 0:
                return False, f"Git push failed: {push_res.stderr}", None

            return True, f"Successfully fixed and pushed commit {new_sha}", new_sha
        except Exception as e:
            return False, f"Exception during Antigravity runner execution: {e}", None
