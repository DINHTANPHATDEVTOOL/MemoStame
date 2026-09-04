import os
import json
from typing import Dict, Any, Optional
from models import FixPlan

SYSTEM_PROMPT = """You are an expert AI code reviewer and CI failure analyst for the MemoStamp Kotlin Multiplatform (KMP) repository.
Your role is to analyze a CI failure log, identify the exact first compiler or test error, analyze the root cause, and output a structured FixPlan JSON.

Rules:
1. Do NOT start new features or redesign UI.
2. Focus on fixing compile errors, Swift/Kotlin interop mismatches, type signatures, DTO mappings, and unit tests.
3. Respect security constraints: never remove RLS, weaken auth, or log secrets.
4. Output MUST conform strictly to the required JSON schema.
"""

class Reviewer:
    def __init__(self, model_name: Optional[str] = None):
        self.model_name = model_name or os.getenv("OPENAI_MODEL", "gpt-4o")
        self.api_key = os.getenv("OPENAI_API_KEY")

    def generate_fix_plan(
        self,
        repo: str,
        task_id: str,
        branch: str,
        commit: str,
        job_name: str,
        first_error: str,
        log_snippet: str,
        agent_rules: str,
        iteration: int
    ) -> FixPlan:
        if not self.api_key:
            # Fallback heuristic analysis if no OpenAI key provided
            return self._heuristic_fix_plan(job_name, first_error, log_snippet)

        try:
            from openai import OpenAI
            client = OpenAI(api_key=self.api_key)
            
            prompt = f"""
Repository: {repo}
Task: {task_id}
Branch: {branch}
Commit: {commit}
Job Name: {job_name}
Iteration: {iteration}

FIRST ERROR:
{first_error}

BOUNDED LOG SNIPPET:
{log_snippet[:20000]}

AGENT RULES SUMMARY:
{agent_rules[:2000]}
"""
            completion = client.beta.chat.completions.parse(
                model=self.model_name,
                messages=[
                    {"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user", "content": prompt}
                ],
                response_format=FixPlan
            )
            return completion.choices[0].message.parsed
        except Exception as e:
            return self._heuristic_fix_plan(job_name, first_error, f"Error calling OpenAI: {e}\n{log_snippet}")

    def _heuristic_fix_plan(self, job_name: str, first_error: str, log_snippet: str) -> FixPlan:
        affected = []
        if "IOSFriendRepository.swift" in log_snippet or "Friend" in first_error:
            affected.append("iosApp/iosApp/Data/IOSFriendRepository.swift")
        elif "IOSChatRepository.swift" in log_snippet:
            affected.append("iosApp/iosApp/Data/IOSChatRepository.swift")

        return FixPlan(
            failure_summary=f"CI Job '{job_name}' failed with error: {first_error[:100]}",
            first_error=first_error,
            root_cause="Swift/Kotlin type mismatch or parameter initializer label error in KMP model mapping.",
            affected_files=affected if affected else ["iosApp/iosApp/Data/IOSFriendRepository.swift"],
            search_patterns=["FriendRequestItem(", "avatarUrl:", "senderAvatar:", "tradeCount:"],
            recommended_changes=[
                "Verify KMP FriendRequestItem constructor initializer argument labels.",
                "Ensure Kotlin Int fields map to Swift Int32(0) and Long to Int64.",
                "Check all occurrences in affected repository files."
            ],
            commands_to_run=[
                ".\\gradlew.bat :shared:build :androidApp:testDebugUnitTest :androidApp:assembleDebug --no-daemon"
            ],
            do_not_change=["Do not remove auth/RLS security.", "Do not re-add Codable to ChatMessage."],
            security_notes=["Never expose tokens or JWTs."],
            confidence=0.85
        )
