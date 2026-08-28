import subprocess
import json
import re
from typing import List, Optional, Dict, Any, Tuple
from safety import sanitize_text

class GitHubClient:
    def __init__(self, repo: str):
        self.repo = repo

    def _run_gh(self, args: List[str]) -> Tuple[int, str]:
        cmd = ["gh"] + args + ["-R", self.repo] if "-R" not in args else ["gh"] + args
        try:
            res = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", check=False)
            return res.returncode, res.stdout.strip()
        except Exception as e:
            return 1, str(e)

    def fetch_open_ai_tasks(self) -> List[Dict[str, Any]]:
        code, out = self._run_gh(["issue", "list", "--label", "ai-task", "--state", "open", "--json", "number,title,body,labels"])
        if code == 0 and out:
            try:
                return json.loads(out)
            except Exception:
                pass
        return []

    def fetch_active_prs(self) -> List[Dict[str, Any]]:
        code, out = self._run_gh(["pr", "list", "--state", "open", "--json", "number,title,headRefName,baseRefName,commits"])
        if code == 0 and out:
            try:
                return json.loads(out)
            except Exception:
                pass
        return []

    def fetch_latest_pr_checks(self, pr_number: int) -> Dict[str, Any]:
        code, out = self._run_gh(["pr", "checks", str(pr_number), "--json", "name,state,bucket,link"])
        checks = []
        if code == 0 and out:
            try:
                checks = json.loads(out)
            except Exception:
                pass
        return {"pr_number": pr_number, "checks": checks}

    def fetch_failed_run_log_snippet(self, run_id: str, max_chars: int = 50000) -> Tuple[str, str, str]:
        """Returns (job_name, first_error, bounded_log_snippet)"""
        code, out = self._run_gh(["run", "view", run_id, "--json", "jobs"])
        if code != 0 or not out:
            return "Unknown Job", "CI Run Failed", ""
        
        try:
            data = json.loads(out)
            jobs = data.get("jobs", [])
            for job in jobs:
                if job.get("conclusion") in ["failure", "cancelled"]:
                    job_name = job.get("name", "Failed Job")
                    job_id = str(job.get("id"))
                    
                    # Fetch log via gh
                    log_code, log_out = self._run_gh(["run", "view", "--job", job_id, "--log-failed"])
                    if log_code == 0 and log_out:
                        log_clean = sanitize_text(log_out)
                        # Extract first error line
                        first_err = self._extract_first_error(log_clean)
                        # Truncate if exceeds max_chars
                        snippet = log_clean[-max_chars:] if len(log_clean) > max_chars else log_clean
                        return job_name, first_err, snippet
        except Exception:
            pass

        return "Failed Job", "CI Failed", ""

    def _extract_first_error(self, log_text: str) -> str:
        lines = log_text.splitlines()
        for line in lines:
            if "error:" in line.lower() or "failure:" in line.lower() or "execution failed" in line.lower():
                return line.strip()
        return lines[0] if lines else "Unknown Error"

    def create_or_update_issue(self, title: str, body: str, labels: List[str], existing_issue_num: Optional[int] = None) -> int:
        if existing_issue_num:
            self._run_gh(["issue", "comment", str(existing_issue_num), "--body", body])
            return existing_issue_num
        else:
            args = ["issue", "create", "--title", title, "--body", body]
            for lbl in labels:
                args.extend(["--label", lbl])
            code, out = self._run_gh(args)
            if code == 0 and out:
                # Extract issue URL or number
                match = re.search(r"/issues/(\d+)", out)
                if match:
                    return int(match.group(1))
        return 0

    def create_pr(self, head: str, base: str, title: str, body: str) -> int:
        code, out = self._run_gh(["pr", "create", "--head", head, "--base", base, "--title", title, "--body", body])
        if code == 0 and out:
            match = re.search(r"/pull/(\d+)", out)
            if match:
                return int(match.group(1))
        return 0

    def update_issue_labels(self, issue_num: int, add_labels: List[str], remove_labels: List[str]):
        for lbl in add_labels:
            self._run_gh(["issue", "edit", str(issue_num), "--add-label", lbl])
        for lbl in remove_labels:
            self._run_gh(["issue", "edit", str(issue_num), "--remove-label", lbl])
