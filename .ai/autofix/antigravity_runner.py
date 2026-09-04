import os
import subprocess
from pathlib import Path
from typing import Tuple, Optional
from models import FixPlan
from safety import check_command_safety

class AntigravityRunner:
    def __init__(self, repo_root: Path):
        self.repo_root = repo_root

    def execute_fix(
        self,
        branch: str,
        fix_plan: FixPlan,
        dry_run: bool = False
    ) -> Tuple[bool, str, Optional[str]]:
        """Returns (success, message, commit_sha)"""

        if dry_run:
            return True, f"[DRY RUN] Would execute FixPlan for branch '{branch}' targeting files {fix_plan.affected_files}", None

        # 1. Run local Gradle regression gate
        gradle_cmd = [".\\gradlew.bat", ":shared:build", ":androidApp:testDebugUnitTest", ":androidApp:assembleDebug", "--no-daemon"]
        safe, reason = check_command_safety(" ".join(gradle_cmd))
        if not safe:
            return False, f"Command safety check failed: {reason}", None

        try:
            env = os.environ.copy()
            if "USERPROFILE" in env:
                jdk21 = Path(env["USERPROFILE"]) / ".jdks" / "jdk-21.0.4+7"
                if jdk21.exists():
                    env["JAVA_HOME"] = str(jdk21)
            if "LOCALAPPDATA" in env:
                android_sdk = Path(env["LOCALAPPDATA"]) / "Android" / "Sdk"
                if android_sdk.exists():
                    env["ANDROID_HOME"] = str(android_sdk)

            res = subprocess.run(gradle_cmd, cwd=str(self.repo_root), capture_output=True, text=True, encoding="utf-8", errors="replace", env=env)
            if res.returncode != 0:
                return False, f"Local Gradle regression gate failed: {res.stderr[-1000:]}", None

            # 2. Stage and commit changes if any
            status_res = subprocess.run(["git", "status", "--porcelain"], cwd=str(self.repo_root), capture_output=True, text=True, encoding="utf-8", errors="replace")
            if not status_res.stdout.strip():
                # Working directory clean, check latest commit SHA
                head_res = subprocess.run(["git", "rev-parse", "HEAD"], cwd=str(self.repo_root), capture_output=True, text=True, encoding="utf-8", errors="replace")
                return True, "Worktree clean, local gates passed.", head_res.stdout.strip()

            subprocess.run(["git", "add", "."], cwd=str(self.repo_root), check=True)
            commit_msg = f"fix(ios): repair CI failure - {fix_plan.first_error[:50]}"
            subprocess.run(["git", "commit", "-m", commit_msg], cwd=str(self.repo_root), check=True)

            # Get new commit SHA
            sha_res = subprocess.run(["git", "rev-parse", "HEAD"], cwd=str(self.repo_root), capture_output=True, text=True, encoding="utf-8", errors="replace")
            new_sha = sha_res.stdout.strip()

            # Push agent branch
            push_res = subprocess.run(["git", "push", "origin", branch], cwd=str(self.repo_root), capture_output=True, text=True, encoding="utf-8", errors="replace")
            if push_res.returncode != 0:
                return False, f"Git push failed: {push_res.stderr}", None

            return True, f"Successfully fixed and pushed commit {new_sha}", new_sha
        except Exception as e:
            return False, f"Exception during Antigravity runner execution: {e}", None
